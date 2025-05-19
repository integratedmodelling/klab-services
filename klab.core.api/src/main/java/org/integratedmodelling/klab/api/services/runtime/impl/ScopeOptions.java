package org.integratedmodelling.klab.api.services.runtime.impl;

import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

public class ScopeOptions implements Serializable {

  private int statusIntervalSeconds;

  private Notification.Level notificationLevel;
  private Set<Message.MessageClass> subscriptions = EnumSet.noneOf(Message.MessageClass.class);

  public int getStatusIntervalSeconds() {
    return statusIntervalSeconds;
  }

  public void setStatusIntervalSeconds(int statusIntervalSeconds) {
    this.statusIntervalSeconds = statusIntervalSeconds;
  }

  public Set<Message.MessageClass> getSubscriptions() {
    return subscriptions;
  }

  public void setSubscriptions(Set<Message.MessageClass> subscriptions) {
    this.subscriptions = subscriptions;
  }

  /**
   * Null means we don't want notifications sent over (default).
   *
   * @return
   */
  public Notification.Level getNotificationLevel() {
    return notificationLevel;
  }

  public void setNotificationLevel(Notification.Level notificationLevel) {
    this.notificationLevel = notificationLevel;
  }
}
