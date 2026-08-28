package org.integratedmodelling.common.authentication;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.UserAuthenticationRequest;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.rest.EngineAuthenticationRequest;
import org.integratedmodelling.klab.rest.EngineAuthenticationResponse;
import org.integratedmodelling.klab.rest.GroupImpl;
import org.integratedmodelling.klab.rest.ServiceReference;

/**
 * Implements the default certificate-based authentication mechanism for an engine. Also maintains
 * external credentials.
 */
public enum Authentication {
  INSTANCE;

  /**
   * Group property that flags the group as a federation. There can only be zero or more federations
   * in an institution's group set. This applies to institutional certificates and is used to set up
   * federated sessions and digital twins.
   */
  public static final String FEDERATION_FLAG_GROUP_PROPERTY = "federation.id";

  /**
   * Predefined, brings in additional features and "expert mode" in UIs and other applications
   * without changing privileges.
   */
  public static final String DEVELOPERS_GROUP = "DEVELOPERS";

  /**
   * Any group that is a federation must specify the URL of the messaging broker used for
   * communication. The URL must be accessible to every client in the federation and to all the
   * users that can access its DTs.
   */
  public static final String FEDERATION_MESSAGING_BROKER = "federation.broker";

  private final AtomicReference<Collection<String>> sshHosts = new AtomicReference<>();
  private final AtomicReference<EngineAuthenticationResponse> lastEngineAuthenticationResponse =
      new AtomicReference<>();
  private final Set<KlabService.Type> started = EnumSet.noneOf(KlabService.Type.class);

  /** any external credentials taken from the .klab/credentials.json file if any. */
  private Utils.FileCatalog<ExternalAuthenticationCredentials> externalCredentials;

  Authentication() {
    this.externalCredentials =
        new Utils.FileCatalog<>(
            Configuration.INSTANCE.getFileWithTemplate("credentials.json", "{}"),
            ExternalAuthenticationCredentials.class,
            ExternalAuthenticationCredentials.class);

    this.sshHosts.set(Utils.SSH.readHostFile());
  }

  public EngineAuthenticationResponse getLastEngineAuthenticationResponse() {
    return lastEngineAuthenticationResponse.get();
  }

  public String encodeAuthenticationResponse(EngineAuthenticationResponse authentication) {
    if (authentication == null) {
      return null;
    }
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Utils.Json.asString(authentication).getBytes(StandardCharsets.UTF_8));
  }

  public EngineAuthenticationResponse decodeAuthenticationResponse(String authenticationPackage) {
    if (authenticationPackage == null || authenticationPackage.isBlank()) {
      return null;
    }
    try {
      var json =
          new String(Base64.getUrlDecoder().decode(authenticationPackage), StandardCharsets.UTF_8);
      return Utils.Json.newObjectMapper().readValue(json, EngineAuthenticationResponse.class);
    } catch (Throwable urlSafeFailure) {
      try {
        var json =
            new String(Base64.getDecoder().decode(authenticationPackage), StandardCharsets.UTF_8);
        return Utils.Json.newObjectMapper().readValue(json, EngineAuthenticationResponse.class);
      } catch (Throwable failure) {
        Logging.INSTANCE.error(failure, "Could not decode local authentication package");
      }
    }
    return null;
  }

  public Pair<Identity, List<ServiceReference>> authenticate(
      EngineAuthenticationResponse authentication) {
    return authenticate(authentication, "local authentication package");
  }

  /**
   * Authenticate using the default certificate if present on the filesystem, or anonymously if not.
   *
   * @param settings
   * @return
   */
  public Pair<Identity, List<ServiceReference>> authenticate(Settings settings) {
    File certFile = new File(Configuration.INSTANCE.getDataPath() + File.separator + "klab.cert");
    KlabCertificate certificate =
        (certFile.isFile() && !settings.get(Setting.LOGIN_ANONYMOUSLY, Boolean.class))
            ? KlabCertificateImpl.createFromFile(certFile)
            : new AnonymousEngineCertificate();

    if (certificate instanceof AnonymousEngineCertificate) {
      Logging.INSTANCE.info("No valid certificate found: continuing in anonymous offline mode");
    }

    return authenticate(certificate, settings);
  }

  // Obviously this won't work - auth is with VaultWarden, then the token is exchanged for a JWT
  //  public Pair<Identity, ServiceReference> authenticate(
  //      String username, String password, Settings settings) {
  //
  //    UserIdentity identity = null;
  //    ServiceReference serviceReference = null;
  //
  //    try (var client =
  //        Utils.Http.getClient(settings.get(Setting.AUTHENTICATION_HUB, String.class), null)) {
  //
  //      UserAuthenticationRequest request = new UserAuthenticationRequest();
  //      request.setUsername(username);
  //      request.setPassword(password);
  //      request.setRemote(true);
  //
  //      var response = client.post(ServicesAPI.HUB.AUTHENTICATE_USER, request, Map.class);
  //
  //      if (response != null) {}
  //
  //    } catch (Throwable e) {
  //      Logging.INSTANCE.error("authentication failed for user " + username + ": " + e);
  //      if (e instanceof KlabException ke) {
  //        throw ke;
  //      }
  //    }
  //
  //    return Pair.of(identity, serviceReference);
  //  }

  /**
   * Authenticate through a hub using the passed certificate. If the passed certificate is
   * anonymous, just return the anonymous user.
   *
   * @param certificate
   * @param settings
   * @return
   */
  public Pair<Identity, List<ServiceReference>> authenticate(
      KlabCertificate certificate, Settings settings) {

    lastEngineAuthenticationResponse.set(null);

    if (certificate instanceof AnonymousEngineCertificate
        || settings.get(Setting.LOGIN_ANONYMOUSLY, Boolean.class)) {
      // no partner, no node, no token, no nothing. REST calls automatically accept
      // the anonymous user when secured as Roles.PUBLIC.
      if (settings.get(Setting.LOG_EVENTS, Boolean.class)) {
        Logging.INSTANCE.info("No user certificate: continuing in anonymous offline mode");
      }
      return Pair.of(new AnonymousUser(), Collections.emptyList());
    }

    if (certificate.getType() != KlabCertificate.Type.ENGINE) {
      throw new KlabAuthorizationException(
          "wrong certificate for an engine: cannot create identity of type "
              + certificate.getType());
    }

    if (!certificate.isValid()) {
      /*
       * expired or invalid certificate: throw away the identity, continue as anonymous.
       */
      if (settings.get(Setting.LOG_EVENTS, Boolean.class)) {
        Logging.INSTANCE.info(
            "Certificate is invalid or expired: continuing in anonymous offline " + "mode");
      }
      return Pair.of(new AnonymousUser(), Collections.emptyList());
    }

    EngineAuthenticationResponse authentication = null;
    String authenticationServer = certificate.getProperty(KlabCertificate.KEY_PARTNER_HUB);

    if (authenticationServer != null) {

      try (var client = Utils.Http.getClient(authenticationServer, null)) {

        if (settings.get(Setting.LOG_EVENTS, Boolean.class)) {
          Logging.INSTANCE.info(
              "authenticating "
                  + certificate.getProperty(KlabCertificate.KEY_USERNAME)
                  + " with hub "
                  + authenticationServer);
        }
        /*
         * Authenticate with server(s). If authentication fails because of a 403, invalidate the
         * certificate. If no server can be reached, certificate is valid but engine is offline.
         */
        EngineAuthenticationRequest request =
            new EngineAuthenticationRequest(
                certificate.getProperty(KlabCertificate.KEY_USERNAME),
                certificate.getProperty(KlabCertificate.KEY_SIGNATURE),
                certificate.getProperty(KlabCertificate.KEY_CERTIFICATE_TYPE),
                certificate.getProperty(KlabCertificate.KEY_CERTIFICATE),
                certificate.getLevel(),
                certificate.getProperty(KlabCertificate.KEY_AGREEMENT));
        // add email if we have it, so the hub can notify in any case if so configured
        request.setEmail(certificate.getProperty(KlabCertificate.KEY_EMAIL));

        authentication =
            client
                .withTimeout(2)
                .post(
                    ServicesAPI.HUB.AUTHENTICATE_ENGINE,
                    request,
                    EngineAuthenticationResponse.class);

      } catch (Throwable e) {
        Logging.INSTANCE.error(
            "authentication failed for user "
                + certificate.getProperty(KlabCertificate.KEY_USERNAME)
                + ": "
                + e);
        if (e instanceof KlabException ke) {
          throw ke;
        }
      }
    }

    if (authentication != null) {
      return authenticate(authentication, "hub " + authenticationServer);
    }

    Logging.INSTANCE.warn(
        "No authentication response received for user "
            + certificate.getProperty(KlabCertificate.KEY_USERNAME)
            + " from hub "
            + authenticationServer
            + ": continuing anonymously");

    return Pair.of(new AnonymousUser(), Collections.emptyList());
  }

  private Pair<Identity, List<ServiceReference>> authenticate(
      EngineAuthenticationResponse authentication, String sourceDescription) {

    lastEngineAuthenticationResponse.set(null);

    if (authentication == null
        || authentication.getUserData() == null
        || authentication.getUserData().getIdentity() == null) {
      Logging.INSTANCE.warn(
          "Authentication package from "
              + sourceDescription
              + " has no user identity: continuing anonymously");
      return Pair.of(new AnonymousUser(), Collections.emptyList());
    }

    Instant expiry;
    try {
      var expiryText = authentication.getUserData().getExpiry();
      expiry =
          expiryText == null
              ? null
              : Instant.parse(expiryText.endsWith("Z") ? expiryText : expiryText + "Z");
    } catch (Throwable e) {
      Logging.INSTANCE.error(
          e,
          "Bad date or wrong date format in authentication package from %s. Continuing anonymously.",
          sourceDescription);
      return Pair.of(new AnonymousUser(), Collections.emptyList());
    }
    if (expiry == null) {
      Logging.INSTANCE.error(
          "Authentication package from "
              + sourceDescription
              + " has no expiration date. Continuing anonymously.");
      return Pair.of(new AnonymousUser(), Collections.emptyList());
    } else if (expiry.isBefore(Instant.now())) {

      Logging.INSTANCE.error(
          "Authentication package from "
              + sourceDescription
              + " expired on "
              + expiry
              + ". Continuing anonymously.");

      UserIdentityImpl ret = new UserIdentityImpl();
      ret.setAnonymous(true);
      ret.setEmailAddress(authentication.getUserData().getIdentity().getEmail());
      ret.setUsername(authentication.getUserData().getIdentity().getId());
      ret.setAuthenticated(false);
      ret.setOnline(false);

      return Pair.of(ret, Collections.emptyList());
    }

    List<ServiceReference> services = new ArrayList<>();
    var hubNode = authentication.getHub();

    UserIdentityImpl ret = new UserIdentityImpl();
    ret.setId(authentication.getUserData().getToken());
    ret.setEmailAddress(authentication.getUserData().getIdentity().getEmail());
    ret.setUsername(authentication.getUserData().getIdentity().getId());
    ret.setAuthenticated(true);
    ret.setOnline(true);

    if (authentication.getUserData().getGroups() != null) {
      for (Object ogroup : authentication.getUserData().getGroups()) {
        // FIXME these come w/o class info so our deserializer screws up
        Group group = null;
        if (ogroup instanceof Map map) {
          group = Utils.Json.convertMap(map, GroupImpl.class);
        } else if (ogroup instanceof Group g) {
          group = g;
        }
        if (group != null) {
          ret.getGroups().add(group);
        }
      }
    }

    var hubId = hubNode == null ? "unknown hub" : hubNode.getId();
    var partnerId =
        hubNode == null || hubNode.getPartner() == null
            ? "unknown partner"
            : hubNode.getPartner().getId();
    Logging.INSTANCE.info(
        "User "
            + ret.getUsername()
            + " authenticated from "
            + sourceDescription
            + " through hub "
            + hubId
            + " owned by "
            + partnerId);

    /* validate federation data */
    var federationData = Klab.INSTANCE.getFederationData(ret);
    if (federationData != null) {
      Logging.INSTANCE.info(
          "User " + ret.getUsername() + " is part of the " + federationData.getId());
      ret.getData().put(UserIdentity.FEDERATION_DATA_PROPERTY, federationData);
    }

    Logging.INSTANCE.info("The following services are available to " + ret.getUsername() + ":");
    if (authentication.getServices() != null) {
      for (var service : authentication.getServices()) {
        Logging.INSTANCE.info("   " + service.getId() + " online");
        if (service.getPartner() != null) {
          Logging.INSTANCE.info(
              "      "
                  + service.getPartner().getId()
                  + " ("
                  + service.getPartner().getEmail()
                  + ")");
        }
        Logging.INSTANCE.info("      " + "type: " + service.getIdentityType());
        services.add(service);
      }
    }

    lastEngineAuthenticationResponse.set(authentication);
    return Pair.of(ret, services);
  }

  Utils.FileCatalog<ExternalAuthenticationCredentials> getExternalCredentialsCatalog(Scope scope) {
    // TODO use separate catalog for services and user scopes
    return externalCredentials;
  }

  public ExternalAuthenticationCredentials.CredentialInfo addExternalCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    var catalog = getExternalCredentialsCatalog(scope);
    // TODO improve key
    catalog.put(extractHost(host), credentials);
    catalog.write();
    return credentials.info(host);
  }

  public ExternalAuthenticationCredentials getCredentials(String hostUrl, Scope scope) {

    var catalog = getExternalCredentialsCatalog(scope);
    var host = extractHost(hostUrl);

    var candidateKeys = new ArrayList<String>();
    for (var hostKey : catalog.keySet()) {
      if (hostKey.startsWith(host) && hostUrl.contains(hostKey)) {
        candidateKeys.add(hostKey);
      }
    }

    if (!candidateKeys.isEmpty()) {
      // sort longest first
      candidateKeys.sort((s1, s2) -> Integer.compare(s2.length(), s1.length()));
      return catalog.get(candidateKeys.getFirst());
    }

    // FIXME match hostname, then compare all keys that start with hostname, choosing the longest
    // that
    //  is contained in the URL
    var ret = catalog.get(host);

    if (ret == null && sshHosts.get().contains(host)) {
      ret = new ExternalAuthenticationCredentials();
      ret.setScheme("ssh");
      // save with no passkey, if it's needed in an interactive app we'll ask and save it.
      ret.setCredentials(List.of(null));
      externalCredentials.put(hostUrl, ret);
      externalCredentials.write();
    }

    return ret;
  }

  private String extractHost(String hostUrl) {
    if (hostUrl.contains(":/")) {
      /**
       * Only use the host:port part. If there are no credentials for this host but the host is
       * known to the ssh authentication, insert credentials from there.
       */
      try {
        var url = new URI(hostUrl);
        var host = url.getHost();
        if (host != null) {
          if (url.getPort() > 0) {
            host += ":" + url.getPort();
          }
          hostUrl = host;
        }
      } catch (URISyntaxException e) {
        // leave hostUrl as is
      }
    }
    return hostUrl;
  }

  /**
   * Return a new credential provider that knows the credentials saved into the k.LAB database and
   * will log appropriate messages when credentials aren't found.
   *
   * @return
   */
  public CredentialsProvider getCredentialProvider(Scope scope) {

    return new CredentialsProvider() {

      @Override
      public void clear() {}

      @Override
      public Credentials getCredentials(AuthScope arg0) {

        String auth = arg0.getHost() + (arg0.getPort() == 80 ? "" : (":" + arg0.getPort()));

        ExternalAuthenticationCredentials credentials =
            getExternalCredentialsCatalog(scope).get(auth);

        if (credentials == null) {
          throw new KlabAuthorizationException(auth);
        }

        return new UsernamePasswordCredentials(
            credentials.getCredentials().get(0), credentials.getCredentials().get(1));
      }

      @Override
      public void setCredentials(AuthScope arg0, org.apache.http.auth.Credentials arg1) {
        // TODO Auto-generated method stub

      }
    };
  }

  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
    var ret = new ArrayList<ExternalAuthenticationCredentials.CredentialInfo>();
    for (var host : getExternalCredentialsCatalog(scope).keySet()) {
      var credentials = getExternalCredentialsCatalog(scope).get(host);
      if (credentials.getPrivileges().checkAuthorization(scope)) {
        ret.add(credentials.info(host));
      }
    }
    return ret;
  }

  /**
   * Return the default privileges for the passed scope.
   *
   * @param scope
   * @return
   */
  public ResourcePrivileges getDefaultPrivileges(Scope scope) {

    if (scope == null) {
      return ResourcePrivileges.empty();
    }

    ResourcePrivileges ret = new ResourcePrivileges();
    if (scope instanceof UserScope user) {
      ret.getAllowedUsers().add(user.getUser().getUsername());
    } else if (scope instanceof ServiceScope service) {
      ret.getAllowedServices().add(service.getIdentity().getId());
    }

    return ret;
  }

  public boolean removeCredentials(String id) {
    // TODO
    return false;
  }
}
