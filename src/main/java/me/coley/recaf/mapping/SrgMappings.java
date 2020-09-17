package me.coley.recaf.mapping;

import me.coley.recaf.util.ClassUtil;
import me.coley.recaf.util.StringUtil;
import me.coley.recaf.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static me.coley.recaf.util.Log.trace;

/**
 * SRG mappings file implementation.
 *
 * @author Matt
 */
public class SrgMappings extends FileMappings {
	private static final String FAIL = "Invalid SRG mappings, ";

	/**
	 * Constructs mappings from a given file.
	 *
	 * @param path
	 * 		A path to a file containing SRG styled mappings.
	 * @param workspace
	 * 		Workspace to pull names from when using hierarchy lookups.
	 *
	 * @throws IOException
	 * 		Thrown if the file could not be read.
	 */
	SrgMappings(Path path, Workspace workspace) throws IOException {
		super(path, workspace);
	}

	@Override
	protected Map<String, String> parse(String text, Workspace workspace) {
		Map<String, String> map = new HashMap<>();
		String[] lines = StringUtil.splitNewline(text);
		int line = 0;
		for(String lineStr : lines) {
			line++;
			String[] args = lineStr.trim().split(" ");
			String type = args[0];
			try {
				switch(type) {
					case "CL:":
						String obfClass = args[1];
						String renamedClass = args[2];
						map.put(obfClass, renamedClass);
						// Map inners as well
						String prefix = obfClass + "$";
						workspace.getPrimaryClassNames().stream()
								.filter(n -> n.startsWith(prefix))
								.forEach(n -> map.put(n, renamedClass + n.substring(obfClass.length())));
						break;
					case "FD:": {
						String obfKey = args[1];
						int splitPos = obfKey.lastIndexOf('/');
						String obfOwner = obfKey.substring(0, splitPos);
						String obfName = obfKey.substring(splitPos + 1);
						String obfDesc = args[2];
						String renamedKey = args[2];
						splitPos = renamedKey.lastIndexOf('/');
						String renamedName = renamedKey.substring(splitPos + 1);
						map.put(obfOwner + "." + obfName, renamedName);
						// Field references may not be based on the direct class they are declared in.
						// A child class may refer to a parent class member, using the child class as an owner.
						// However, once a child class introduces a shadowing field name, we want to stop introducing
						// children as owners for this mapping run.
						workspace.getHierarchyGraph()
						.getAllDescendantsWithBreakCondition(obfOwner,
								n -> ClassUtil.containsField(workspace.getClassReader(n), obfName, obfDesc))
						.forEach(childOwner -> map.put(childOwner + "." + obfName, renamedName));
						break;
					}
					case "MD:": {
						String obfKey = args[1];
						int splitPos = obfKey.lastIndexOf('/');
						String obfOwner = obfKey.substring(0, splitPos);
						String obfName = obfKey.substring(splitPos + 1);
						String obfDesc = args[2];
						String renamedKey = args[3];
						splitPos = renamedKey.lastIndexOf('/');
						String renamedName = renamedKey.substring(splitPos + 1);
						map.put(obfOwner + "." + obfName + obfDesc, renamedName);
						break;
					}
					default:
						trace("Unknown SRG mappings line type: \"{}\" @line {}", type, line);
						break;
				}
			} catch(IndexOutOfBoundsException ex) {
				throw new IllegalArgumentException(FAIL + "failed parsing line " + line, ex);
			}
		}
		return map;
	}
}
