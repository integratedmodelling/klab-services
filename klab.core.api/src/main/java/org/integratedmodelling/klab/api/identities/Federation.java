package org.integratedmodelling.klab.api.identities;

import org.integratedmodelling.klab.api.services.runtime.Channel;

public class Federation {

  public static final String LOCAL_FEDERATION_ID = "local.federation";
  /** Explicit deployment-owner configuration; never supplied by a remote scope advertisement. */
  public static final String LOCAL_BROKER_PROPERTY = "klab.local.broker.uri";

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
    var configured = System.getProperty(LOCAL_BROKER_PROPERTY);
    if (configured == null) {
      return new Federation(LOCAL_FEDERATION_ID, Channel.LOCAL_BROKER_URL + Channel.LOCAL_BROKER_PORT);
    }
    try {
      var uri = new java.net.URI(configured);
      if (!("amqp".equals(uri.getScheme()) || "amqps".equals(uri.getScheme()))
          || uri.getHost() == null || uri.getHost().isBlank()
          || uri.getPort() < 1 || uri.getPort() > 65535
          || uri.getRawPath() == null || uri.getRawPath().length() < 2
          || uri.getRawQuery() != null || uri.getRawFragment() != null) {
        throw new IllegalArgumentException("Invalid configured local-federation broker URI");
      }
      return new Federation(LOCAL_FEDERATION_ID, configured);
    } catch (java.net.URISyntaxException invalid) {
      // URI syntax failures can contain credentials; do not retain their message/cause.
      throw new IllegalArgumentException("Invalid configured local-federation broker URI");
    }
  }
}
