package me.coley.recaf.simulation.instructions;

import me.coley.recaf.simulation.ExecutionContext;
import me.coley.recaf.simulation.InstructionHandler;
import me.coley.recaf.simulation.InvalidBytecodeException;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.reflect.Array;

public final class InstructionHandlerLoadArrayFloat implements InstructionHandler<VarInsnNode> {
	@Override
	public void process(VarInsnNode instruction, ExecutionContext ctx) throws Throwable {
		int index = instruction.var;
		Object array = ctx.pop();
		if (!array.getClass().isArray()) {
			throw new InvalidBytecodeException("Object is not an array");
		}
		Object v = Array.get(array, index);
		if (!(v instanceof Float)) {
			throw new InvalidBytecodeException("Attempted to load float, but value was: " + v);
		}
		ctx.push(v);
	}
}
