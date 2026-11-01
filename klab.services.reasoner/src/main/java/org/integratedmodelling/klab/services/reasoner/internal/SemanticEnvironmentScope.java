package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.integratedmodelling.common.authentication.scope.AbstractServiceDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** No service discovery or live-service fallback. The only semantic callback is this actor. */
public final class SemanticEnvironmentScope extends AbstractServiceDelegatingScope {
  private Reasoner reasoner;
  private final ResourcesService sourceResolver;
  private final java.util.List<Notification> messages = new java.util.concurrent.CopyOnWriteArrayList<>();

  public SemanticEnvironmentScope(ResourcesService capturedSourceResolver) {
    super(new ChannelImpl((org.integratedmodelling.klab.api.identities.Identity)null) {
      private final String id = "candidate-" + UUID.randomUUID();
      private boolean errors;
      @Override public String getDispatchId() { return id; }
      @Override public void info(Object... values) { dispatch(Message.Queue.Info, Notification.info(values)); }
      @Override public void warn(Object... values) { dispatch(Message.Queue.Warnings, Notification.warning(values)); }
      @Override public void error(Object... values) { errors = true; dispatch(Message.Queue.Errors, Notification.error(values)); }
      @Override public boolean hasErrors() { return errors; }
      @Override public void debug(Object... values) {}
      private void dispatch(Message.Queue queue, Notification notification) {
        for (var listener : getListeners(queue).values()) listener.accept(this, Message.create(this, notification));
      }
    });
    this.sourceResolver = capturedSourceResolver;
    onMessage((channel, message) -> {
      if (message.getPayload(Object.class) instanceof Notification notification) messages.add(notification);
    }, Message.Queue.Errors, Message.Queue.Warnings);
    setMaintenanceMode(false);
  }
  @Override public java.util.List<Notification> getNotifications() { return java.util.List.copyOf(messages); }

  public void bind(Reasoner privateReasoner) {
    if (reasoner != null) throw new IllegalStateException("Candidate scope is already bound");
    reasoner = java.util.Objects.requireNonNull(privateReasoner);
  }
  @Override public <T extends KlabService> T getService(Class<T> type) {
    if (type == Reasoner.class && reasoner != null) return type.cast(reasoner);
    if (type == ResourcesService.class && sourceResolver != null) return type.cast(sourceResolver);
    throw new UnsupportedOperationException("CANDIDATE_ISOLATION_UNSUPPORTED: no captured service for " + type.getName());
  }
  @Override public <T extends KlabService> Collection<T> getServices(Class<T> type) {
    if (type == Reasoner.class && reasoner != null) return List.of(type.cast(reasoner));
    if (type == ResourcesService.class && sourceResolver != null) return List.of(type.cast(sourceResolver));
    return List.of();
  }
  @Override public <T extends KlabService> Optional<T> findService(Class<T> type, Predicate<T> selector) {
    return getServices(type).stream().filter(selector).findFirst();
  }
}
