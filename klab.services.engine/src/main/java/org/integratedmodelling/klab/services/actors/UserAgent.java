package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.exceptions.KlabActorException;
import org.integratedmodelling.klab.services.actors.messages.user.CreateApplication;
import org.integratedmodelling.klab.services.actors.messages.user.CreateSession;

import io.reacted.core.reactors.ReActions.Builder;
import io.reacted.core.reactorsystem.ReActorContext;

public class UserAgent extends KAgent {

	public UserAgent(String name, Scope scope) {
		super(name, scope);
	}

	@Override
	protected Builder setBehavior() {
		return super.setBehavior().reAct(CreateSession.class, this::createSession).reAct(CreateApplication.class,
				this::createApplication);
	}

	private void createSession(ReActorContext rctx, CreateSession message) {
		rctx.spawnChild(new SessionAgent(message.getSessionId(), message.getScope()))
				.ifSuccess((ref) -> rctx.reply(ref));
	}

	private void createApplication(ReActorContext rctx, CreateApplication message) {
		if (message.getBehaviorType() == KActorsBehavior.Type.UNITTEST) {
			rctx.spawnChild(new TestCaseAgent(message.getBehaviorName(), message.getScope()))
					.ifSuccess((ref) -> rctx.reply(ref));
		} else if (message.getBehaviorType() == KActorsBehavior.Type.SCRIPT) {
			rctx.spawnChild(new ScriptAgent(message.getBehaviorName(), message.getScope()))
					.ifSuccess((ref) -> rctx.reply(ref));
		} else if (message.getBehaviorType() == KActorsBehavior.Type.APP) {
			rctx.spawnChild(new ApplicationAgent(message.getBehaviorName(), message.getScope()))
					.ifSuccess((ref) -> rctx.reply(ref));
		} else {
			throw new KlabActorException(
					"unexpected call to createApplication with behavior of type " + message.getBehaviorType());
		}
	}

}
