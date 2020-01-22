package me.coley.recaf.simulation;

import me.coley.recaf.util.InsnUtil;
import me.coley.recaf.util.OpcodeUtil;
import me.coley.recaf.workspace.Workspace;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import java.util.List;

public final class ExecutionContext {
	private final Workspace workspace;
	private final ClassLoader cl;
	private final ExecutionStack stack;
	private final Object[] locals;
	private final InsnList instructions;
	private final List<TryCatchBlockNode> tryCatchNodes;
	private int cursor;
	private boolean run;

	public ExecutionContext(Workspace workspace, ClassLoader cl, int maxStack, int maxLocals, InsnList instructions, List<TryCatchBlockNode> tryCatchNodes) {
		this.workspace = workspace;
		this.cl = cl;
		this.stack = new ExecutionStack(maxStack);
		this.locals = new Object[maxLocals];
		this.instructions = instructions;
		this.tryCatchNodes = tryCatchNodes;
	}

	public ExecutionContext(Workspace workspace, ClassLoader cl, MethodNode method) {
		this(workspace, cl, method.maxStack, method.maxLocals, method.instructions, method.tryCatchBlocks);
	}

	public Object run() throws SimulationException {
		run = true;
		return execute();
	}

	public Object execute() throws SimulationException {
		while (run) {
			AbstractInsnNode node = this.instructions.get(cursor);
			int opcode = node.getOpcode();
			InstructionHandler handler = InstructionHandlers.getHandlerForOpcode(opcode);
			if (handler == null) {
				throw new InvalidBytecodeException("No handler for opcode: " + opcode);
			}
			try {
				handler.process(node, this);
				cursor++;
			} catch (Throwable t) {
				List<TryCatchBlockNode> tryCatchBlockNodes = this.tryCatchNodes;
				if (tryCatchBlockNodes == null) {
					throw new SimulationExecutionException(processingException(opcode, cursor), t);
				}
				for (TryCatchBlockNode tryCatchBlockNode : tryCatchBlockNodes) {
					if (cursor >= InsnUtil.getLabelOffset(tryCatchBlockNode.start) &&
							cursor <= InsnUtil.getLabelOffset(tryCatchBlockNode.end)) {
						String type = tryCatchBlockNode.type;
						// TODO type check
						if (true) {
							stack.clear();
							stack.push(t);
							cursor = InsnUtil.getLabelOffset(tryCatchBlockNode.handler);
							continue;
						}
					}
				}
				throw new SimulationExecutionException(processingException(opcode, cursor), t);
			}
		}
		throw new UnsupportedOperationException("Not implemented yet.");
	}

	public void push(Object v) {
		stack.push(v);
	}

	public <V> V pop() {
		return stack.pop();
	}

	public void store(int index, Object v) {
		locals[index] = v;
	}

	public <V> V load(int index) {
		return (V) locals[index];
	}

	public void stop() {
		if (!run) {
			throw new IllegalStateException("Already stopped!");
		}
		run = false;
	}

	private String processingException(int opcode, int at) {
		return String.format("Error processing instruction %s at %d", OpcodeUtil.opcodeToName(opcode), at);
	}
}
