package org.integratedmodelling.common.runtime.actors;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** The client-side Agent incarnates the service-side agent in the runtime. */
public class ClientAgent implements Agent {

  private List<Notification> notifications = new ArrayList<>();
  private boolean alive;
  private String behaviorUrn;
  private String urn;

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public String getBehaviorUrn() {
    return this.behaviorUrn;
  }

  @Override
  public boolean isAlive() {
    return this.alive;
  }

  @Override
  public boolean stop() {
    return false;
  }

  @Override
  public List<Notification> getNotifications() {
    return this.notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  public void setAlive(boolean alive) {
    this.alive = alive;
  }

  public void setBehaviorUrn(String behaviorUrn) {
    this.behaviorUrn = behaviorUrn;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  @Override
  public <T extends Serializable> void tell(T message) {}

  @Override
  public <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
      T message, Class<? extends R> responseClass) {
    return null;
  }
}
