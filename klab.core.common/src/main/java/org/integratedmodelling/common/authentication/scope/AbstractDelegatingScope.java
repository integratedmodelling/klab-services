package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.*;
import java.util.function.Consumer;

/**
 * An abstract scope delegating all communication to an externally supplied Channel. Provides the basic API to
 * set and retrieve services according to the context of usage.
 */
public abstract class AbstractDelegatingScope implements Scope {

    Channel delegateChannel;
    Parameters<String> data = Parameters.create();
    Status status = Status.EMPTY;

    public AbstractDelegatingScope(Channel delegateChannel) {
        this.delegateChannel = delegateChannel;
    }

    @Override
    public Parameters<String> getData() {
        return data;
    }

    @Override
    public KActorsBehavior.Ref getAgent() {
        return null;
    }

    @Override
    public Status getStatus() {
        return this.status;
    }

    @Override
    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public Identity getIdentity() {
        return delegateChannel.getIdentity();
    }

    @Override
    public void info(Object... info) {
        delegateChannel.info(info);
    }

    @Override
    public void warn(Object... o) {
        delegateChannel.warn(o);
    }

    @Override
    public void error(Object... o) {
        delegateChannel.error(o);
    }

    @Override
    public void debug(Object... o) {
        delegateChannel.debug(o);
    }

    @Override
    public void send(Object... message) {
        delegateChannel.send(message);
    }

    @Override
    public void post(Consumer<Message> handler, Object... message) {
        delegateChannel.post(handler, message);
    }

    @Override
    public void interrupt() {
        delegateChannel.interrupt();
    }

    @Override
    public boolean isInterrupted() {
        return delegateChannel.isInterrupted();
    }

    @Override
    public boolean hasErrors() {
        return delegateChannel.hasErrors();
    }


    @Override
    public void setData(String key, Object value) {
        this.data.put(key, value);
    }

    @Override
    public boolean connect(KlabService service) {
        return delegateChannel.connect(service);
    }

    @Override
    public boolean disconnect(KlabService service) {
        return delegateChannel.disconnect(service);
    }
}
