package org.integratedmodelling.klab.api.identities;

import org.integratedmodelling.klab.api.services.runtime.Channel;

public class Federation {

  public static final String LOCAL_FEDERATION_ID = "local.federation";

  private String id;
  private String broker;

  public Federation() {}

  public Federation(String id, String broker) {
    this.id = id;
    this.broker = broker;
  }

  public String getId() {
    return id;
  }

  public String getBroker() {
    return broker;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setBroker(String broker) {
    this.broker = broker;
  }

  public static Federation local() {
    return new Federation(LOCAL_FEDERATION_ID, Channel.LOCAL_BROKER_URL + Channel.LOCAL_BROKER_PORT);
  }
}
