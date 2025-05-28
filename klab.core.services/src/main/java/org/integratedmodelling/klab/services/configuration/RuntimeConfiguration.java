package org.integratedmodelling.klab.services.configuration;

import org.integratedmodelling.klab.api.data.Storage;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** FIXME this is a straight copy of the Reasoner config */
public class RuntimeConfiguration {

  private List<String> allowedGroups = new ArrayList<>();
  private String url = null;
  private String serviceId;
//  private URI brokerURI;
  private Storage.Type numericStorageType = Storage.Type.DOUBLE;

  public List<String> getAllowedGroups() {
    return allowedGroups;
  }

  public void setAllowedGroups(List<String> allowedGroups) {
    this.allowedGroups = allowedGroups;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  /**
   * Default numeric storage type when not specified by the model or observable through a <code>
   * @storage</code> annotation.
   *
   * @return
   */
  public Storage.Type getNumericStorageType() {
    return numericStorageType;
  }

  public void setNumericStorageType(Storage.Type numericStorageType) {
    this.numericStorageType = numericStorageType;
  }

//  /**
//   * If no broker URL is present, the service will install a local QPid broker for internal
//   * connections on port 5672.
//   *
//   * <p>This should be something like "amqp://userName:password@hostName:portNumber/virtualHost" to
//   * pass to a connectionfactory.
//   *
//   * @return
//   */
//  public URI getBrokerURI() {
//    return brokerURI;
//  }
//
//  public void setBrokerURI(URI brokerURI) {
//    this.brokerURI = brokerURI;
//  }
}
