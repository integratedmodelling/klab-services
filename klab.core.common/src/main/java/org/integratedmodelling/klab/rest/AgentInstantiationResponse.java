package org.integratedmodelling.klab.rest;

import java.util.List;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * This is returned by the runtime both after initial agent creation and at any inquiry about the
 * state of the agent. TODO should have other status fields, time of instantiation, etc.
 */
public class AgentInstantiationResponse {

  private List<Notification> notifications;
  private String agentUrl;
  private String agentName;
  private String behaviorUrn;
  private boolean alive;

  /**
   * Notifications should be those from the last inquiry only, using the requester ID as filter.
   *
   * @return
   */
  public List<Notification> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  public String getAgentUrl() {
    return agentUrl;
  }

  public void setAgentUrl(String agentUrl) {
    this.agentUrl = agentUrl;
  }

  public String getAgentName() {
    return agentName;
  }

  public void setAgentName(String agentName) {
    this.agentName = agentName;
  }

  public String getBehaviorUrn() {
    return behaviorUrn;
  }

  public void setBehaviorUrn(String behaviorUrn) {
    this.behaviorUrn = behaviorUrn;
  }

  public boolean isAlive() {
    return alive;
  }

  public void setAlive(boolean alive) {
    this.alive = alive;
  }
}
