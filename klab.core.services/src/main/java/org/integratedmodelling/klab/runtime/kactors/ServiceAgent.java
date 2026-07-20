package org.integratedmodelling.klab.runtime.kactors;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class ServiceAgent implements Agent {

  RuntimeAgentBase runtimeAgent;

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
