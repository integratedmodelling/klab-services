package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;

import java.io.Serializable;

public abstract class AbstractReactiveScopeImpl extends MessagingChannelImpl implements ReactiveScope {

    protected KActorsBehavior.Ref agent;

    public AbstractReactiveScopeImpl(Identity identity, boolean isSender, boolean isReceiver) {
        super(identity, isSender, isReceiver);
    }

    @Override
    public KActorsBehavior.Ref getAgent() {
        return this.agent;
    }

    public void setAgent(KActorsBehavior.Ref agent) {
        this.agent = agent;
    }

    @Override
    public Message send(Object... args) {
        var message = Message.create(this, args);
        if (message.getMessageClass() == Message.MessageClass.ActorCommunication && agent != null && !agent.isEmpty()) {
            agent.tell(message);
            return message;
        } else {
            return super.send(args);
        }
    }

    @Override
    public <T extends Serializable> T ask(Class<T> responseClass, Object... args) {

        if (agent == null || agent.isEmpty()) {
            return null;
        }

        var message = Message.create(this, args);
        if (message.getMessageClass() == null && message instanceof MessageImpl message1) {
            message1.setMessageClass(Message.MessageClass.ActorCommunication);
        }
        if (message.getMessageClass() == Message.MessageClass.ActorCommunication) {
            return agent.ask(message, responseClass);
        }

        throw new KlabInternalErrorException("wrong message with class " + message.getMessageClass() + " sent to ReactiveScope::ask");
    }
}
