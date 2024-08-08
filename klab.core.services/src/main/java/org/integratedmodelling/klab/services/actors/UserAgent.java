package org.integratedmodelling.klab.services.actors;

import io.reacted.core.reactorsystem.ReActorContext;
import org.integratedmodelling.klab.api.exceptions.KlabActorException;
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
			reActorContext
					.spawnChild(new SessionAgent(message.getPayload(SessionScope.class)))
					.ifSuccess(reActorContext::reply)
					.ifError(ex-> scope.error("error creating session agent", new KlabActorException(ex)));
		} else {
			super.handleMessage(reActorContext, message);
		}
	}
}
