package org.integratedmodelling.common.authentication;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CustomProperty;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.Engine;
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

  public static record FederationData(String id, String broker) {}

  /**
   * Group property that flags the group as a federation. There can only be zero or more federations
   * in an institution's group set. This applies to institutional certificates and is used to set up
   * federated sessions and digital twins.
   */
  public static final String FEDERATION_FLAG_GROUP_PROPERTY = "federation.id";

  /**
   * Any group that is a federation must specify the URL of the messaging broker used for
   * communication. The URL must be accessible to every client in the federation and to all the
   * users that can access its DTs.
   */
  public static final String FEDERATION_MESSAGING_BROKER = "federation.broker";

  private final AtomicReference<Collection<String>> sshHosts = new AtomicReference<>();
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

  /**
   * Authenticate using the default certificate if present on the filesystem, or anonymously if not.
   *
   * @param settings
   * @return
   */
  public Pair<Identity, List<ServiceReference>> authenticate(Parameters<Engine.Setting> settings) {
    File certFile = new File(Configuration.INSTANCE.getDataPath() + File.separator + "klab.cert");
    KlabCertificate certificate =
        certFile.isFile()
            ? KlabCertificateImpl.createFromFile(certFile)
            : new AnonymousEngineCertificate();
    return authenticate(certificate, settings);
  }

  /**
   * Authenticate through a hub using the passed certificate. If the passed certificate is
   * anonymous, just return the anonymous user.
   *
   * @param certificate
   * @param settings
   * @return
   */
  public Pair<Identity, List<ServiceReference>> authenticate(
      KlabCertificate certificate, Parameters<Engine.Setting> settings) {

    if (certificate instanceof AnonymousEngineCertificate) {
      // no partner, no node, no token, no nothing. REST calls automatically accept
      // the anonymous user when secured as Roles.PUBLIC.
      if (settings.get(Engine.Setting.LOG_EVENTS, Boolean.class)) {
        Logging.INSTANCE.info("No user certificate: continuing in anonymous offline mode");
      }
      return Pair.of(new AnonymousUser(), Collections.emptyList());
    }

    if (!certificate.isValid()) {
      /*
       * expired or invalid certificate: throw away the identity, continue as anonymous.
       */
      if (settings.get(Engine.Setting.LOG_EVENTS, Boolean.class)) {
        Logging.INSTANCE.info(
            "Certificate is invalid or expired: continuing in anonymous offline " + "mode");
      }
      return Pair.of(new AnonymousUser(), Collections.emptyList());
    }

    EngineAuthenticationResponse authentication = null;
    String authenticationServer = certificate.getProperty(KlabCertificate.KEY_PARTNER_HUB);

    if (authenticationServer != null) {

      try (var client = Utils.Http.getClient(authenticationServer, null)) {

        if (settings.get(Engine.Setting.LOG_EVENTS, Boolean.class)) {
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
            client.post(
                ServicesAPI.HUB.AUTHENTICATE_ENGINE, request, EngineAuthenticationResponse.class);

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

      Instant expiry = null;
      /*
       * check expiration
       */
      try {
        expiry = Instant.parse(authentication.getUserData().getExpiry() + "Z");
      } catch (Throwable e) {
        Logging.INSTANCE.error(
            "bad date or wrong date format in certificate. Please use latest "
                + "version of software. Continuing anonymously.");
        return Pair.of(new AnonymousUser(), Collections.emptyList());
      }
      if (expiry == null) {
        Logging.INSTANCE.error(
            "certificate has no expiration date. Please obtain a new certificate"
                + ". Continuing anonymously.");
        return Pair.of(new AnonymousUser(), Collections.emptyList());
      } else if (expiry.isBefore(Instant.now())) {

        Logging.INSTANCE.error(
            "certificate expired on "
                + expiry
                + ". Please obtain a new "
                + "certificate. Continuing anonymously.");

        UserIdentityImpl ret = new UserIdentityImpl();
        ret.setAnonymous(true);
        ret.setEmailAddress(authentication.getUserData().getIdentity().getEmail());
        ret.setUsername(authentication.getUserData().getIdentity().getId());
        ret.setAuthenticated(false);
        ret.setOnline(false);

        return Pair.of(ret, Collections.emptyList());
      }
    }

    /*
     * build the identity
     */
    if (certificate.getType() == KlabCertificate.Type.ENGINE) {

      // if we have connected, insert network session identity
      if (authentication != null) {

        List<ServiceReference> services = new ArrayList<>();
        var hubNode = authentication.getHub();
        HubImpl hub = new HubImpl();

        UserIdentityImpl ret = new UserIdentityImpl();
        ret.setId(authentication.getUserData().getToken());
        //        ret.setParentIdentity(hub);
        ret.setEmailAddress(authentication.getUserData().getIdentity().getEmail());
        ret.setUsername(authentication.getUserData().getIdentity().getId());
        ret.setAuthenticated(true);
        ret.setOnline(true);

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

        Logging.INSTANCE.info(
            "User "
                + ret.getUsername()
                + " logged in through hub "
                + hubNode.getId()
                + " owned by "
                + hubNode.getPartner().getId());

        /* validate federation data */
        var federationData = getFederationData(ret);
        if (federationData != null) {
          Logging.INSTANCE.info("User " + ret.getUsername() + " is part of the " + federationData);
        }

        Map<KlabService.Type, AtomicInteger> serviceCount = new HashMap<>();
        Logging.INSTANCE.info("The following services are available to " + ret.getUsername() + ":");
        for (var service : authentication.getServices()) {
          Logging.INSTANCE.info("   " + service.getId() + " online");
          Logging.INSTANCE.info(
              "      "
                  + service.getPartner().getId()
                  + " ("
                  + service.getPartner().getEmail()
                  + ")");
          Logging.INSTANCE.info("      " + "type: " + service.getIdentityType());
          services.add(service);
          serviceCount
              .computeIfAbsent(service.getIdentityType(), s -> new AtomicInteger(0))
              .incrementAndGet();
        }

        return Pair.of(ret, services);
      }

    } else {
      throw new KlabAuthorizationException(
          "wrong certificate for an engine: cannot create identity of type "
              + certificate.getType());
    }

    return Pair.of(new AnonymousUser(), Collections.emptyList());
  }

  private FederationData getFederationData(UserIdentity identity) {

    var federations =
        identity.getGroups().stream()
            .filter(
                g ->
                    g.getCustomProperties().stream()
                        .anyMatch(
                            customProperty ->
                                ("federation.id".equals(customProperty.getKey()))
                                    && "true".equals(customProperty.getValue())))
            .toList();

    if (federations.size() > 1) {
      throw new KlabAuthorizationException(
          "multiple federations found for user " + identity.getUsername());
    } else if (federations.size() == 1) {
      return new FederationData(
          federations.getFirst().getName(),
          federations.getFirst().getCustomProperties().stream()
              .filter(cp -> "federation.broker".equals(cp.getKey()))
              .map(CustomProperty::getValue)
              .findFirst()
              .orElse(null));
    }

    return null;
  }

  //  /**
  //   * Strategy to locate a primary service in all possible ways. If there are primary service
  // URLs
  //   * for the passed service class in the list of service references obtained through
  // authentication,
  //   * try them and if one responds return a client to it. Otherwise, try the local URL and if the
  //   * passed service is running locally, return a client to it. As a last resort, check if we
  // have a
  //   * source distribution configured or available, and if so, synchronize it if needed and if it
  //   * provides the required service product, run it and return a service client.
  //   *
  //   * @param serviceType the service we need.
  //   * @param identity the identity we represent
  //   * @param availableServices a list of {@link ServiceReference} objects obtained through
  //   *     certificate authentication, or an empty list.
  //   * @param <T> the type of service we want to obtain
  //   * @return a service client or null. The service status should be checked before use.
  //   */
  //  public <T extends KlabService> T fuckService(
  //      KlabService.Type serviceType,
  //      Scope scope,
  //      Identity identity,
  //      List<ServiceReference> availableServices,
  //      Parameters<Engine.Setting> settings) {
  //
  //    BiConsumer<Channel, Message>[] listeners =
  //        scope instanceof ChannelImpl clientScope
  //            ? clientScope.listeners().toArray(BiConsumer[]::new)
  //            : (scope instanceof AbstractDelegatingScope ascope ? ascope.listeners() : null);
  //
  //
  //
  //    // if we get here, we have no remote services available and we should try a running local
  // one
  //    // first.
  //    var status = ServiceClient.readServiceStatus(serviceType.localServiceUrl(), null);
  //    if (status != null) {
  //      scope.info(
  //          "Using locally running "
  //              + status.getServiceType()
  //              + " service at "
  //              + serviceType.localServiceUrl());
  //      return (T)
  //          createLocalServiceClient(
  //              serviceType,
  //              serviceType.localServiceUrl(),
  //              scope,
  //              identity,
  //              availableServices,
  //              settings,
  //              listeners);
  //    }
  //
  //
  //    return null;
  //  }
  //
  //  @SafeVarargs
  //  public final <T extends KlabService> T createLocalServiceClient(
  //      KlabService.Type serviceType,
  //      URL url,
  //      Scope scope,
  //      Identity identity,
  //      List<ServiceReference> services,
  //      Parameters<Engine.Setting> settings,
  //      BiConsumer<Channel, Message>... listeners) {
  //    T ret =
  //        switch (serviceType) {
  //          case REASONER -> {
  //            yield (T) new ReasonerClient(url, identity, services, settings, listeners);
  //          }
  //          case RESOURCES -> {
  //            yield (T) new ResourcesClient(url, identity, services, settings, listeners);
  //          }
  //          case RESOLVER -> {
  //            yield (T) new ResolverClient(url, identity, services, settings, listeners);
  //          }
  //          case RUNTIME -> {
  //            yield (T) new RuntimeClient(url, identity, services, settings, listeners);
  //          }
  //          case COMMUNITY -> {
  //            yield (T) new CommunityClient(url, identity, services, settings, listeners);
  //          }
  //          default -> throw new IllegalStateException("Unexpected value: " + serviceType);
  //        };
  //
  //    scope.send(
  //        Message.MessageClass.ServiceLifecycle,
  //        Message.MessageType.ServiceInitializing,
  //        serviceType + " service at " + serviceType.localServiceUrl());
  //
  //    return ret;
  //  }

  /**
   * This will only return a non-null distribution after authentication if a distribution was used.
   *
   * @return
   */
  //  public Distribution getDistribution() {
  //    return this.distribution;
  //  }

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
