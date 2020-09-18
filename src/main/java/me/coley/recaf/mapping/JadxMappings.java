package me.coley.recaf.mapping;

import me.coley.recaf.util.ClassUtil;
import me.coley.recaf.util.StringUtil;
import me.coley.recaf.workspace.Workspace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * JADX deobfuscation mapping file implementation.
 *
 * @author Matt
 */
public class JadxMappings extends FileMappings {
	private static final String FAIL = "Invalid JADX mappings, ";

	/**
	 * Constructs mappings from a given file.
	 *
	 * @param path
	 * 		A path to a file containing JADX styled mappings.
	 * @param workspace
	 * 		Workspace to pull names from when using hierarchy lookups.
	 *
	 * @throws IOException
	 * 		Thrown if the file could not be read.
	 */
	JadxMappings(Path path, Workspace workspace) throws IOException {
		super(path, workspace);
	}

	@Override
	protected Map<String, String> parse(String text, Workspace workspace) {
		// Example:
		// c android.support.a.b.a = C0005a
		// f android.support.a.b.a.a:Ljava/lang/Object; = f3a
		// m android.support.a.a.a.a(Landroid/app/Activity;[Ljava/lang/String;I)V = m0a
		Map<String, String> map = new HashMap<>();
		String[] lines = StringUtil.splitNewline(text);
		int line = 0;
		for (String lineStr : lines) {
			line++;
			String[] args = lineStr.trim().split("[\\s=:]+");
			String type = args[0];
			try {
				switch (type) {
				case "c":
					// 1: class-name
					// 2: renameed class (does not include package)
					// Replace "." in class name
					String original = args[1].replace('.', '/');
					// The new value is always in the same package.
					// Only the class is renamed, not the package.
					map.put(original, original.substring(0, original.lastIndexOf('/') + 1) + args[2]);
					// Map inners as well
					String prefix = original + "$";
					workspace.getPrimaryClassNames().stream().filter(n -> n.startsWith(prefix))
							.forEach(n -> map.put(n, original.substring(0, original.lastIndexOf('/') + 1) + args[2]
									+ n.substring(original.length())));
					break;
				case "f":
					// 1: class-name.field-name
					// 2: field-type
					// 3: renamed
					// Replace all "." except last one
					map.put(args[1].replaceAll("\\.(?=.+\\..+$)", "/"), args[3]);
					// Field references may not be based on the direct class they are declared in.
					// A child class may refer to a parent class member, using the child class as an
					// owner.
					// However, once a child class introduces a shadowing field name, we want to
					// stop introducing
					// children as owners for this mapping run.
					workspace.getHierarchyGraph()
							.getAllDescendantsWithBreakCondition(args[1].replace('.', '/'),
									n -> ClassUtil.containsField(workspace.getClassReader(n),
											args[1].replaceAll("\\.(?=.+\\..+$)", "/")
													.substring(args[1].replaceAll("\\.(?=.+\\..+$)", "/").indexOf(".")
															+ 1),
											args[2]))
							.forEach(childOwner -> map.put(
									childOwner + "."
											+ args[1].replaceAll("\\.(?=.+\\..+$)", "/").substring(
													args[1].replaceAll("\\.(?=.+\\..+$)", "/").indexOf(".") + 1),
									args[3]));
					break;
				case "m":
					// 1: class-name.method-name + method-desc
					// 2: renamed
					// Replace all "." except last one
					map.put(args[1].replaceAll("\\.(?=.+\\..+$)", "/"), args[2]);
					break;
				default:
					break;

				}
			} catch (IndexOutOfBoundsException ex) {
				throw new IllegalArgumentException(FAIL + "failed parsing line " + line, ex);
			}
		}
		return map;
	}
}
