package org.integratedmodelling.klab.api.identities;

public class Federation {

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
}
