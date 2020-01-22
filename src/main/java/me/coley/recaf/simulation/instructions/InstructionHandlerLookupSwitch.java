package me.coley.recaf.simulation.instructions;

import me.coley.recaf.simulation.ExecutionContext;
import me.coley.recaf.simulation.InstructionHandler;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

public final class InstructionHandlerLookupSwitch implements InstructionHandler<LookupSwitchInsnNode> {
	@Override
	public void process(LookupSwitchInsnNode instruction, ExecutionContext ctx) throws Throwable {
		Integer key = ctx.popInteger();
		int index = instruction.keys.indexOf(key);
		LabelNode node = index == -1 ? instruction.dflt : instruction.labels.get(index);
		ctx.jump(node);
	}
}
