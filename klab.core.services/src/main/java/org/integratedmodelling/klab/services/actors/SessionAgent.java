package org.integratedmodelling.klab.services.actors;

import io.reacted.core.reactorsystem.ReActorContext;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

public class SessionAgent extends KAgent {

    public SessionAgent(SessionScope scope) {
        super(scope.getId(), scope);
    }

    @Override
    protected void handleMessage(ReActorContext reActorContext, Message message) {
        if (message.getMessageType() == Message.MessageType.CreateContext) {
            reActorContext
                    .spawnChild(new ContextAgent(message.getPayload(ContextScope.class)))
                    .ifSuccess((ref) -> reActorContext.reply(ref));
            super.handleMessage(reActorContext, message);
        }
    }
}
