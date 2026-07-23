//package org.integratedmodelling.klab.runtime.kactors;
//
//import java.io.Serializable;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import org.integratedmodelling.klab.api.actors.Agent;
//import org.integratedmodelling.klab.api.services.runtime.Notification;
//
//public class ServiceAgent implements Agent {
//
//  RuntimeAgentBase runtimeAgent;
//  private String urn;
//  private String behaviorUrn;
//  private List<Notification> notifications = new ArrayList<>();
//
//  @Override
//  public String getUrn() {
//    return urn;
//  }
//
//  @Override
//  public String getBehaviorUrn() {
//    return behaviorUrn;
//  }
//
//  @Override
//  public boolean isAlive() {
//    return false;
//  }
//
//  @Override
//  public boolean stop() {
//    return false;
//  }
//
//  @Override
//  public List<Notification> getNotifications() {
//    return notifications;
//  }
//
//  @Override
//  public <T extends Serializable> void tell(T message) {}
//
//  @Override
//  public <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
//      T message, Class<? extends R> responseClass) {
//    return null;
//  }
//
//  public RuntimeAgentBase getRuntimeAgent() {
//    return runtimeAgent;
//  }
//
//  public void setRuntimeAgent(RuntimeAgentBase runtimeAgent) {
//    this.runtimeAgent = runtimeAgent;
//  }
//
//  public void setUrn(String urn) {
//    this.urn = urn;
//  }
//
//  public void setBehaviorUrn(String behaviorUrn) {
//    this.behaviorUrn = behaviorUrn;
//  }
//
//  public void setNotifications(List<Notification> notifications) {
//    this.notifications = notifications;
//  }
//}
