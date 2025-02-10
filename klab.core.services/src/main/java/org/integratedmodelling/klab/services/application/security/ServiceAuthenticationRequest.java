package org.integratedmodelling.klab.services.application.security;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;

public class ServiceAuthenticationRequest {

  protected String name;
  protected String email;
  protected String certificate;
  protected String key;
  protected KlabCertificate.Level level;
  protected String idAgreement;

  public ServiceAuthenticationRequest() {
    this.setLevel(KlabCertificate.Level.INSTITUTIONAL);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getCertificate() {
    return certificate;
  }

  public void setCertificate(String certificate) {
    this.certificate = certificate;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public KlabCertificate.Level getLevel() {
    return level;
  }

  public void setLevel(KlabCertificate.Level level) {
    this.level = level;
  }

  public String getIdAgreement() {
    return idAgreement;
  }

  public void setIdAgreement(String idAgreement) {
    this.idAgreement = idAgreement;
  }
}
