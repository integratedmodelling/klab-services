//package org.integratedmodelling.klab.services.actors;
//
//import io.reacted.core.messages.reactors.ReActorInit;
//import io.reacted.core.reactors.ReActions;
//import io.reacted.core.reactorsystem.ReActorContext;
//import org.integratedmodelling.klab.api.scope.Scope;
//import org.integratedmodelling.klab.api.scope.Scope.Status;
//import org.integratedmodelling.klab.runtime.kactors.messages.ScriptEvent;
//import org.integratedmodelling.klab.runtime.kactors.messages.ScriptEvent.Type;
//
//public class TestCaseAgent extends SessionAgent {
//
//	private long start;
//	private long end;
//
//	public TestCaseAgent(String name, Scope scope) {
//		super(name, scope);
//	}
//
//	protected ReActions.Builder setBehavior() {
//		return super.setBehavior().reAct(ScriptEvent.class, this::handleScriptEvent);
//	}
//
//	private void handleScriptEvent(ReActorContext rctx, ScriptEvent message) {
//		if (message.getType() == Type.CASE_END) {
//			scope.setStatus(Status.FINISHED);
//			this.end = System.currentTimeMillis();
//		} else if (message.getType() == Type.CASE_START) {
//			this.start = System.currentTimeMillis();
//		}
//	}
//
//	@Override
//	protected void initialize(ReActorContext rctx, ReActorInit message) {
//		super.initialize(rctx, message);
//	}
//
//
//}
