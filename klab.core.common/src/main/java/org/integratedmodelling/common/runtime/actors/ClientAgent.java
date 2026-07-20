package org.integratedmodelling.common.runtime.actors;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** The client-side Agent incarnates the service-side agent in the runtime. */
public class ClientAgent implements Agent {

  private Scope scope;

  @Override
  public String getUrn() {
    return "";
  }

  @Override
  public String getBehaviorUrn() {
    return "";
  }

  @Override
  public boolean isAlive() {
    return false;
  }

  @Override
  public boolean stop() {
    return false;
  }

  @Override
  public List<Notification> getNotifications() {
    return List.of();
  }

  @Override
  public <T extends Serializable> void tell(T message) {}

  @Override
  public <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
      T message, Class<? extends R> responseClass) {
    return null;
  }
}
