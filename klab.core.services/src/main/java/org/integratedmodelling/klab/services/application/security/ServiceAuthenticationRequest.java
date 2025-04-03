package org.integratedmodelling.klab.services.application.security;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.rest.AuthenticationRequest;

public class ServiceAuthenticationRequest extends AuthenticationRequest {

  KlabService.Type type;

  public ServiceAuthenticationRequest(KlabService.Type type) {
    this.setLevel(KlabCertificate.Level.INSTITUTIONAL);
    this.type = type;
  }

  public KlabService.Type getType() {
    return type;
  }

}
