package me.coley.recaf.simulation.instructions;

import me.coley.recaf.simulation.ExecutionContext;
import me.coley.recaf.simulation.InstructionHandler;
import org.objectweb.asm.tree.AbstractInsnNode;

public final class InstructionHandlerDup2X2 implements InstructionHandler<AbstractInsnNode> {
	@Override
	public void process(AbstractInsnNode instruction, ExecutionContext ctx) throws Throwable {
		Object v1 = ctx.pop();
		Object v2 = ctx.pop();
		Object v3 = ctx.pop();
		Object v4 = ctx.pop();
		ctx.push(v2);
		ctx.push(v1);
		ctx.push(v4);
		ctx.push(v3);
		ctx.push(v2);
		ctx.push(v1);
	}
}
