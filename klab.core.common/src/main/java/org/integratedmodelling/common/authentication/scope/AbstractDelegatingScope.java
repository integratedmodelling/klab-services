package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An abstract scope delegating all communication to an externally supplied Channel. Provides the
 * basic API to set and retrieve services according to the context of usage.
 */
public abstract class AbstractDelegatingScope implements Scope {

  Channel delegateChannel;
  Parameters<String> data = Parameters.create();
  Status status = Status.EMPTY;
  Scope parentScope;
  private Persistence persistence = Persistence.SERVICE_SHUTDOWN;

  public AbstractDelegatingScope(Channel delegateChannel) {
    this.delegateChannel = delegateChannel;
  }

  @Override
  public String onMessage(BiConsumer<Channel, Message> consumer, Message.Queue... queues) {
    return this.delegateChannel.onMessage(consumer, queues);
  }

  public Channel getDelegateChannel() {
    return delegateChannel;
  }

  @Override
  public Parameters<String> getData() {
    return data;
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

  public String getDispatchId() {
    return delegateChannel.getDispatchId();
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
  public Message send(Object... message) {
    return delegateChannel.send(message);
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
  public void event(Message message) {
    delegateChannel.event(message);
  }

  //  @Override
  //  public String onEvent(
  //      Message.MessageClass messageClass,
  //      Message.MessageType messageType,
  //      Consumer<Message> runnable,
  //      Object... matchArguments) {
  //    return delegateChannel.onEvent(messageClass, messageType, runnable, matchArguments);
  //  }

  @Override
  public void unregisterMessageListener(String listenerId) {
    delegateChannel.unregisterMessageListener(listenerId);
  }

  @Override
  public <T extends KlabService> T getService(String serviceId, Class<T> serviceClass) {
    for (var service : getServices(serviceClass)) {
      if (serviceId.equals(service.serviceId())) {
        return service;
      }
    }
    throw new KlabResourceAccessException(
        "cannot find service with ID=" + serviceId + " in the scope");
  }

  @Override
  public void ui(Message message) {
    delegateChannel.ui(message);
  }

  public Scope getParentScope() {
    return parentScope;
  }

  @Override
  public Persistence getPersistence() {
    return persistence;
  }

  public void setExpiration(Persistence expiration) {
    this.persistence = expiration;
  }

  @Override
  public <T extends Scope> T getParentScope(Type type, Class<T> scopeClass) {
    if (delegateChannel instanceof Scope scope) {
      return scope.getParentScope(type, scopeClass);
    }
    return null;
  }

  @Override
  public void close() {
    delegateChannel.close();
  }
}
