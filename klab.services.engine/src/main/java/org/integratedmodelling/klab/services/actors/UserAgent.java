package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.exceptions.KlabActorException;
import org.integratedmodelling.klab.services.actors.messages.user.CreateApplication;
import org.integratedmodelling.klab.services.actors.messages.user.CreateSession;

import io.reacted.core.reactors.ReActions.Builder;
import io.reacted.core.reactorsystem.ReActorContext;
import io.reacted.core.reactorsystem.ReActorRef;

public class UserAgent extends KAgent {

	public UserAgent(String name) {
		super(name);
	}

	@Override
	protected Builder setBehavior() {
		return super.setBehavior().reAct(CreateSession.class, this::createSession).reAct(CreateApplication.class,
				this::createApplication);
	}

	private void createSession(ReActorContext rctx, CreateSession message) {
		rctx.spawnChild(new SessionAgent(message.getSessionId())).ifSuccess((ref) -> rctx.reply(ref));
	}

	private void createApplication(ReActorContext rctx, CreateApplication message) {
		KActorsBehavior behavior = message.getScope().getService(ResourceProvider.class)
				.resolveBehavior(message.getApplicationId(), message.getScope());
		if (behavior == null) {
			message.getScope().error("cannot find behavior " + message.getApplicationId());
			rctx.reply(ReActorRef.NO_REACTOR_REF);
		} else {
			if (behavior.getType() == KActorsBehavior.Type.UNITTEST) {
				rctx.spawnChild(new TestCaseAgent(behavior, message.getScope())).ifSuccess((ref) -> rctx.reply(ref));
			} else if (behavior.getType() == KActorsBehavior.Type.SCRIPT) {
				rctx.spawnChild(new ScriptAgent(behavior, message.getScope())).ifSuccess((ref) -> rctx.reply(ref));
			} else if (behavior.getType() == KActorsBehavior.Type.APP) {
				rctx.spawnChild(new ApplicationAgent(behavior, message.getScope())).ifSuccess((ref) -> rctx.reply(ref));
			} else {
				throw new KlabActorException(
						"unexpected call to createApplication with behavior of type " + behavior.getType());
			}
		}
	}

}
