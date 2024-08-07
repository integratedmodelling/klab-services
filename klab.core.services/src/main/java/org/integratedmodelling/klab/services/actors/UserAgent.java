package org.integratedmodelling.klab.services.actors;

import io.reacted.core.reactorsystem.ReActorContext;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

public class UserAgent extends KAgent {

	public UserAgent(String name, ReactiveScope scope) {
		super(name, scope);
	}

	@Override
	protected void handleMessage(ReActorContext reActorContext, Message message) {
		if (message.getMessageType() == Message.MessageType.CreateSession) {
			System.out.println("PORCO DIO MI CHIEDE UNA SESSION E IO GLIELA DÓ");
			reActorContext
					.spawnChild(new SessionAgent(message.getPayload(SessionScope.class)))
					.ifSuccess((ref) -> reActorContext.reply(ref));
		} else {
			super.handleMessage(reActorContext, message);
		}
	}
//
//	private void createSession(ReActorContext rctx, CreateSession message) {
//		rctx.spawnChild(new SessionAgent(message.getSessionId(), message.getScope()))
//				.ifSuccess((ref) -> rctx.reply(ref));
//	}
//
//	private void createApplication(ReActorContext rctx, CreateApplication message) {
//		if (message.getBehaviorType() == KActorsBehavior.Type.UNITTEST) {
//			rctx.spawnChild(new TestCaseAgent(message.getBehaviorName(), message.getScope()))
//					.ifSuccess((ref) -> rctx.reply(ref));
//		} else if (message.getBehaviorType() == KActorsBehavior.Type.SCRIPT) {
//			rctx.spawnChild(new ScriptAgent(message.getBehaviorName(), message.getScope()))
//					.ifSuccess((ref) -> rctx.reply(ref));
//		} else if (message.getBehaviorType() == KActorsBehavior.Type.APP) {
//			rctx.spawnChild(new ApplicationAgent(message.getBehaviorName(), message.getScope()))
//					.ifSuccess((ref) -> rctx.reply(ref));
//		} else {
//			throw new KlabActorException(
//					"unexpected call to createApplication with behavior of type " + message.getBehaviorType());
//		}
//	}

}
