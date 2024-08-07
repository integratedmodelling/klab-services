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
///**
// * This is normally dedicated to run one script, potentially with others run
// * explicitly by it in the same VM.
// *
// * @author Ferd
// *
// */
//public class ScriptAgent extends SessionAgent {
//
//	long start = 0;
//	long end = 0;
//
//	public ScriptAgent(String name, Scope scope) {
//		super(name, scope);
//	}
//
//	protected ReActions.Builder setBehavior() {
//		return super.setBehavior().reAct(ScriptEvent.class, this::handleScriptEvent);
//	}
//
////	@Override
////	protected void initialize(ReActorContext rctx, ReActorInit message) {
////		super.initialize(rctx, message);
////	}
//
//	private void handleScriptEvent(ReActorContext rctx, ScriptEvent message) {
//		if (message.getType() == Type.SCRIPT_END) {
//			scope.setStatus(Status.FINISHED);
//			end = System.currentTimeMillis();
//		} else if (message.getType() == Type.SCRIPT_START) {
//			start = System.currentTimeMillis();
//		}
//	}
//}
