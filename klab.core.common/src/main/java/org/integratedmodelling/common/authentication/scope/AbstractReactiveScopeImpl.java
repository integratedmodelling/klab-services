package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;

import java.io.Serializable;
import java.util.List;

/**
 * A scope thqt hosts an agent ref and will route messages with
 * {@link org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#ActorCommunication} class to
 * them instead of sending through the normal channels. This scope also exposes an
 * {@link #ask(Class, Object...)} method that blocks until an agent's response is received.
 */
public abstract class AbstractReactiveScopeImpl extends MessagingChannelImpl implements ReactiveScope {

    protected KActorsBehavior.Ref agent;
    protected Persistence persistence = Persistence.SERVICE_SHUTDOWN;

    public AbstractReactiveScopeImpl(Identity identity, boolean isSender, boolean isReceiver) {
        super(identity, isSender, isReceiver);
    }

    protected AbstractReactiveScopeImpl(AbstractReactiveScopeImpl other) {
        super(other);
        this.agent = other.agent;
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

    public KlabService getService(String serviceId) {
        KlabService ret = null;
        for (var sc : List.of(Reasoner.class, ResourcesService.class, Resolver.class, RuntimeService.class)) {
            try {
                ret = getService(serviceId, sc);
            } catch (KlabResourceAccessException e) {
                // that's OK here
            }
            if (ret != null) {
                return ret;
            }
        }
        return null;
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

        throw new KlabInternalErrorException("wrong message with class " + message.getMessageClass() + " " + "sent to ReactiveScope::ask");
    }

    @Override
    public Persistence getPersistence() {
        return persistence;
    }

    public void setPersistence(Persistence expiration) {
        this.persistence = expiration;
    }
}
