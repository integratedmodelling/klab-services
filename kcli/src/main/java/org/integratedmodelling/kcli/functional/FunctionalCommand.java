package org.integratedmodelling.kcli.functional;

import java.util.Stack;

import org.integratedmodelling.klab.api.utils.Utils;

public abstract class FunctionalCommand implements Runnable {

	private static Stack<Object> stack = new Stack<>();

	protected Object lastPushed;
	
	public static Object variable(String variable) {
		if (variable.startsWith("$") && ("$".equals(variable) || Utils.Numbers.encodesInteger(variable.substring(1)))) {
			int depth = "$".equals(variable) ? 0 : Integer.parseInt(variable.substring(1));
			if (stack.size() > depth) {
				return stack.get(stack.size() - depth - 1);
			}
		}
		return variable;
	}

	protected void push(Object value) {
		stack.push(lastPushed = value);
	}

	public static void resetStack() {
		stack.clear();
	}

}
