package me.coley.recaf.simulation.instructions;

import me.coley.recaf.simulation.ExecutionContext;
import me.coley.recaf.simulation.InstructionHandler;
import me.coley.recaf.simulation.InvalidBytecodeException;
import me.coley.recaf.simulation.VMTop;
import org.objectweb.asm.tree.AbstractInsnNode;

public final class InstructionHandlerDoubleToFloat implements InstructionHandler<AbstractInsnNode> {
	@Override
	public void process(AbstractInsnNode instruction, ExecutionContext ctx) throws Throwable {
		if (ctx.pop() != VMTop.INSTANCE) {
			throw new InvalidBytecodeException("VMTop missing");
		}
		Object v = ctx.pop();
		if (!(v instanceof Double)) {
			throw new InvalidBytecodeException("Attempted to load double, but value was: " + v);
		}
		ctx.push(((Double) v).floatValue());
	}
}
