package me.coley.recaf.parse.bytecode;

import me.coley.recaf.Recaf;
import me.coley.recaf.parse.bytecode.ast.*;
import me.coley.recaf.util.TypeUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * Variable analysis.
 *
 * @author Matt
 */
public class Variables {
	private final Map<String, Integer> nameToIndex = new HashMap<>();
	private final Map<Integer, Integer> indexToSort = new HashMap<>();
	private final Map<String, String> nameToDesc = new HashMap<>();
	private final Map<String, String> nameToStart = new HashMap<>();
	private final Map<String, String> nameToEnd = new HashMap<>();
	private int lastArgIndex = -1;
	private int next;
	private int maxIndex;

	/**
	 * @param isStatic
	 * 		Is the method static or not.
	 * @param currentType
	 * 		Internal name of declaring class.
	 */
	Variables(boolean isStatic, String currentType) {
		// Add "this" for instance methods
		if(!isStatic) {
			nameToIndex.put("this", 0);
			nameToIndex.put("0", 0);
			nameToDesc.put("this", "L" + currentType + ";");
			nameToDesc.put("0", "L" + currentType + ";");
			indexToSort.put(0, Type.OBJECT);
			setNext(next + 1);
		}
	}

	/**
	 * @param root
	 * 		AST root.
	 *
	 * @return Map of variable names to the variable instances.
	 *
	 * @throws AssemblerException
	 * 		When fetching type-information from an instruction fails.<br>
	 * 		Or when variables cannot fetch label information.
	 */
	void visit(RootAST root) throws AssemblerException {
		// Method descriptor
		// - contains explicit types & names
		// - highest priority due to being part of the method definition
		for(DefinitionArgAST arg : root.search(DefinitionArgAST.class)) {
			String name = arg.getVariableName().getName();
			Type type = Type.getType(arg.getDesc().getDesc());
			// Populate
			int index = next;
			nameToIndex.put(name, index);
			indexToSort.put(index, type.getSort());
			nameToDesc.put(name, type.getDescriptor());
			lastArgIndex = index;
			// Update next index
			setNext(index + getNextVarIncrement(index, type.getSize()));
		}
		// Variable instructions (VarInsnNode/IincInsnNode)
		// Pass 1: Add data for raw-index variables
		for(VariableReference ast : root.search(VariableReference.class)) {
			String name = ast.getVariableName().getName();
			if(!name.matches("\\d+") || nameToIndex.containsKey(name))
				continue;
			int index = Integer.parseInt(name);
			addVariable(ast, root, index);
			// Ensure "next" accounts for any taken indices
			fitNext();
		}
		// Pass 2: Add data for named variables
		for(VariableReference ast : root.search(VariableReference.class)) {
			String name = ast.getVariableName().getName();
			if(nameToIndex.containsKey(name))
				continue;
			int index = next;
			addVariable(ast, root, index);
			// Update next var position
			int size = TypeUtil.sortToSize(indexToSort.get(index));
			setNext(index + getNextVarIncrement(index, size));
		}
		// Figure out variable types that for local variables
		// Figure out the label ranges where variables apply
		findRanges(root);
		// Create the variable nodes
	}

	/**
	 * Fills missing index-to-descriptor mappings.
	 *
	 * @param frames
	 * 		Stack-frame analysis data.
	 */
	void fillMissingTypes(Frame<RValue>[] frames) throws AssemblerException {
		for(Map.Entry<String, Integer> entry : nameToIndex.entrySet()) {
			// Skip already visitied
			String name = entry.getKey();
			if(nameToDesc.containsKey(name))
				continue;
			// Collect the types stored in this index
			Set<Type> types = new HashSet<>();
			int index = entry.getValue();
			for(Frame<RValue> frame : frames) {
				if(frame == null)
					continue;
				RValue value = frame.getLocal(index);
				if(value != null && value.getType() != null)
					types.add(value.getType());
			}
			// Collect common type among useses
			Iterator<Type> it = types.iterator();
			Type last = it.next();
			int arrayLevel = TypeUtil.getArrayDepth(last);
			last = TypeUtil.getElementType(last);
			while (it.hasNext()) {
				Type type1 = it.next();
				if (arrayLevel != TypeUtil.getArrayDepth(type1))
					throw new VerifierException("Stored multiple array sizes in same variable slot: " + index);
				type1 = TypeUtil.getElementType(last);
				if (last.equals(type1))
					continue;
				if(Recaf.getCurrentWorkspace() != null)
					last = Type.getObjectType(Recaf.getCurrentWorkspace().getHierarchyGraph()
							.getCommon(last.getInternalName(), type1.getInternalName()));
				else break;
			}
			// Save type
			StringBuilder arr = new StringBuilder();
			for(int i = 0; i < arrayLevel; i++)
				arr.append('[');
			nameToDesc.put(name, arr.toString() + "L" + last.getInternalName() + ";");
		}
	}

	/**
	 * @param labels
	 * 		Map of label names to instances.
	 *
	 * @return Map of variable names to the variable instances.
	 *
	 * @throws AssemblerException When one of the following occurs:
	 * 		<ul>
	 * 		 <li>Conflicting variable indices</li>
	 * 		 <li>Failure to analyze type of variable</li>
	 * 		 <li>Missing label instance for variable range</li>
	 * 		</ul>
	 */
	List<LocalVariableNode> getVariables(Map<String, LabelNode> labels) throws AssemblerException {
		// TODO: Reuse variable slots of the same sort if the scope of the variables do not collide
		List<LocalVariableNode> vars = new ArrayList<>();
		boolean addedThis = false;
		// Variables of given indices can be reused (usually given different names per use)
		// And sometimes there are just portions of code that don't have debug info.
		//  - This seems to be correct...
		for(Map.Entry<String, Integer> entry : nameToIndex.entrySet()) {
			String name = entry.getKey();
			int index = entry.getValue();
			if (index == 0 && nameToIndex.containsKey("this"))
				name = "this";
			if(name.equals("this")) {
				if(addedThis)
					continue;
				addedThis = true;
			}
			String desc = nameToDesc.get(name);
			if(desc == null)
				continue;
			String startName = nameToStart.get(name);
			String endName = nameToEnd.get(name);
			LabelNode start = labels.get(startName);
			LabelNode end = labels.get(endName);
			if(start == null)
				continue;
			if(end == null)
				continue;
			vars.add(new LocalVariableNode(name, desc, null, start, end, index));
		}
		vars.sort(Comparator.comparingInt(lvn -> lvn.index));
		return vars;
	}

	/**
	 * Moves {@link #next} forward to the next unused spot.
	 */
	private void fitNext() {
		while(nameToIndex.containsValue(next)) {
			int size = TypeUtil.sortToSize(indexToSort.get(next));
			next += size;
		}
	}

	/**
	 * Find starting and ending labels for each variable.
	 *
	 * @param root
	 * 		Root AST.
	 *
	 * @throws AssemblerException
	 * 		When a variable could not find a label pair to associate its range with.
	 */
	private void findRanges(RootAST root) throws AssemblerException {
		List<LabelAST> labels = root.search(LabelAST.class);
		// Arguments: (index <= lastArgIndex)
		// - this - entire range
		// - args - entire range
		for(Integer index : indexToSort.keySet()) {
			if (index <= lastArgIndex) {
				Optional<String> x = nameToIndex.entrySet().stream()
						.filter(e -> e.getValue().equals(index))
						.map(Map.Entry::getKey)
						.findFirst();
				if (!x.isPresent())
					throw new AssemblerException("Failed to find index->name map for: " + index);
				String name = x.get();
				nameToStart.put(name, labels.get(0).getName().getName());
				nameToEnd.put(name, labels.get(labels.size() - 1).getName().getName());
			}
		}
		// Locals:
		// - Start = first label above reference
		// - End = first label after last reference
		for(VariableReference ast : root.search(VariableReference.class)) {
			// Skip non-instructions
			if(!(ast instanceof Instruction))
				continue;
			String name = ast.getVariableName().getName();
			int line = ((AST)ast).getLine();
			// Skip already matched variables
			if(nameToEnd.containsKey(name))
				continue;
			// Find start - first label before this declaration
			AST start = (AST) ast;
			do {
				start = start.getPrev();
			} while(start != null && !(start instanceof LabelAST));
			if(start == null)
				throw new AssemblerException("Failed to find start label for variable: " + name, line);
			// Find end - first label after the last time this variable is referenced
			AST end = (AST) ast;
			AST marker = end;
			do {
				marker = marker.getNext();
				if(marker instanceof LabelAST)
					end = marker;
				else if(marker instanceof VariableReference) {
					// Nullify the "end" since the variable has been referenced again.
					// The next label will be the end.
					String markerVName = ((VariableReference) marker).getVariableName().getName();
					if(markerVName.equals(name))
						end = null;
				}
			} while(marker != null);
			if(end == null)
				throw new AssemblerException("Failed to find end label for variable: " + name, line);
			// Update maps
			nameToStart.put(name, ((LabelAST) start).getName().getName());
			nameToEnd.put(name, ((LabelAST) end).getName().getName());
		}
	}

	/**
	 * Handle adding the variable to the maps.
	 *
	 * @param ast
	 * 		AST containing variable.
	 * @param root
	 * 		Root of AST.
	 * @param index
	 * 		Index to add variable to.
	 *
	 * @throws AssemblerException
	 * 		When fetching type information from the var-reference fails.
	 */
	private void addVariable(VariableReference ast, RootAST root, int index) throws AssemblerException {
		String name = ast.getVariableName().getName();
		// Fetch type information
		int sort = -1;
		if(ast instanceof Instruction)
			sort = getType(((Instruction) ast).getOpcode().getOpcode());
		else if(ast instanceof DefinitionArgAST) {
			String desc = ((DefinitionArgAST) ast).getDesc().getDesc();
			sort = Type.getType(desc).getSort();
		}
		if(sort == -1) {
			int line = ((AST)ast).getLine();
			throw new AssemblerException("Unknown variable type: " + ast, line);
		}
		// Update maps
		int used = index + TypeUtil.sortToSize(sort);
		if (used > maxIndex)
			maxIndex = used;
		nameToIndex.put(name, index);
		indexToSort.put(index, sort);
		if (sort >= Type.BOOLEAN && sort <= Type.DOUBLE)
			nameToDesc.put(name, sortToDesc(sort));
	}

	/**
	 * Finds the increment needed to fit the next variable slot. Will skip already used values.
	 *
	 * @param current
	 * 		Current variable index, without increment.
	 * @param size
	 * 		Size of variable just discovered.
	 *
	 * @return Variable increment amount.
	 */
	private int getNextVarIncrement(int current, int size) {
		int temp = current + size;
		// Prevent a named variable from taking the place of an existing indexed variable
		while (indexToSort.containsKey(temp)) {
			int sort = indexToSort.get(temp);
			temp += TypeUtil.sortToSize(sort);
		}
		return temp - current;
	}

	/**
	 * @param name
	 * 		Variable name.
	 *
	 * @return Index.
	 *
	 * @throws AssemblerException
	 * 		When index lookup fails.
	 */
	public int getIndex(String name) throws AssemblerException {
		try {
			return nameToIndex.get(name);
		} catch(Exception ex) {
			throw new AssemblerException("Failed to fetch index of: " + name);
		}
	}

	/**
	 * @return Max used locals.
	 */
	public int getMax() {
		return maxIndex;
	}

	private void setNext(int next) {
		this.next = next;
		if (next > maxIndex)
			maxIndex = next;
	}

	/**
	 * @param opcode
	 * 		Var opcode.
	 *
	 * @return Type derived from the opcode.
	 *
	 * @throws AssemblerException
	 * 		When the opcode is not supported.
	 */
	private static int getType(int opcode) throws AssemblerException {
		int type = -1;
		switch(opcode) {
			case ALOAD:
			case ASTORE:
				type = Type.OBJECT;
				break;
			case IINC:
			case ILOAD:
			case ISTORE:
				type = Type.INT;
				break;
			case FLOAD:
			case FSTORE:
				type = Type.FLOAT;
				break;
			case DLOAD:
			case DSTORE:
				type = Type.DOUBLE;
				break;
			case LLOAD:
			case LSTORE:
				type = Type.LONG;
				break;
			default:
				throw new AssemblerException("Unsupported opcode for variable reference: " + opcode);
		}
		return type;
	}

	/**
	 * @param sort
	 * 		Type sort<i>(kind)</i>
	 *
	 * @return Descriptor of primitive type.
	 *
	 * @throws AssemblerException
	 * 		When the type is not a primitive.
	 */
	private static String sortToDesc(int sort) throws AssemblerException {
		switch(sort) {
			case Type.INT:
				return "I";
			case Type.SHORT:
				return "S";
			case Type.CHAR:
				return "C";
			case Type.BYTE:
				return "B";
			case Type.LONG:
				return "J";
			case Type.FLOAT:
				return "F";
			case Type.DOUBLE:
				return "D";
			case Type.BOOLEAN:
				return "B";
			default:
				throw new AssemblerException("Unsupported");
		}
	}
}
