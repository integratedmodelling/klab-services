package org.integratedmodelling.klab.services.application.security;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.common.authentication.PartnerIdentityImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.rest.ServiceReference;
import org.integratedmodelling.klab.services.ServiceInstance;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.springframework.stereotype.Component;

/**
 * Singleton containing all the JWT management and handshaking with the hub that every k.LAB service
 * uses. Should also include a safe strategy to authorize the service and handle the JWT token w/o
 * configuration when the services are available (authenticated or anonymously) in a local
 * configuration.
 */
@Component
public class ServiceAuthorizationManager {

  private static final String TOKEN_CLASS_PACKAGE = "org.integratedmodelling.node.resource.token";
  private static final int ALLOWED_CLOCK_SKEW_MS = 30000;
  private static final String DEFAULT_TOKEN_CLASS = EngineAuthorization.class.getSimpleName();
  private static final long JWKS_UPDATE_INTERVAL_MS = 10 * 60 * 1000; // every 10 minutes
  private static final String JWT_CLAIM_KEY_PERMISSIONS = "perms";
  private static final String JWT_CLAIM_TOKEN_TYPE = "cls";
  private static final String ENGINE_AUDIENCE = "engine";
  private static final String JWT_CLAIM_KEY_ROLES = "roles";
  private JwtConsumer preValidationExtractor;
  private Map<String, JwtConsumer> jwksVerifiers = new HashMap<>();
  private int wtfErrors;
  private String authenticatingHub;
  private String nodeName;
  private String hubName;
  private boolean serviceAuthenticated;
  private KlabService.Type type;

  /**
   * This is set explicitly after the scope is created. Without the root scope, no authentication is
   * possible
   */
  private Supplier<ServiceInstance<?>> klabService;

  public void setKlabService(Supplier<ServiceInstance<?>> klabService) {
    this.klabService = klabService;
  }

  /**
   * Validate a SERVICE-level certificate and return the valid partner identity with the token to
   * talk to the hub. The service may also run on User authority or even anonymously if allowed,
   * with different {@link org.integratedmodelling.klab.api.scope.ServiceScope.Locality} privileges
   * in its {@link org.integratedmodelling.klab.api.scope.ServiceScope} and {@link
   * org.integratedmodelling.klab.api.services.KlabService.ServiceCapabilities}.
   *
   * <p>This is only called if a certificate file is found in the service configuration area.
   *
   * @param certificate // * @param options
   * @return the partner identity that owns this node.
   */
  public Pair<Identity, List<ServiceReference>> authenticateService(
      KlabCertificate certificate, StartupOptions options) {

    String serverHub = authenticatingHub;
    if (serverHub == null) {
      serverHub = certificate.getProperty(KlabCertificate.KEY_PARTNER_HUB);
    }

    if (serverHub == null) {
      throw new KlabAuthorizationException(
          "a node cannot be started without a valid authenticating " + "hub");
    }

    this.authenticatingHub = serverHub;
    this.nodeName =
        certificate.getProperty(KlabCertificate.KEY_NODENAME) != null
            ? certificate.getProperty(KlabCertificate.KEY_NODENAME)
            : options.getServiceName();
    this.type =
        KlabService.Type.valueOf(certificate.getProperty(KlabCertificate.KEY_CERTIFICATE_TYPE));
    ServiceAuthenticationRequest request = new ServiceAuthenticationRequest(type);

    request.setCertificate(certificate.getProperty(KlabCertificate.KEY_CERTIFICATE));
    request.setName(nodeName);
    request.setKey(certificate.getProperty(KlabCertificate.KEY_SIGNATURE));
    request.setLevel(certificate.getLevel());
    request.setEmail(certificate.getProperty(KlabCertificate.KEY_PARTNER_EMAIL));
    serviceAuthenticated = true;

    /*
     * response contains the groupset for validation and the Base64-encoded public
     * key for the JWT tokens. We throw away the public key after building it; if
     * the first token decryption fails and we have authenticated some time before,
     * we can try re-authenticating once to update it before refusing authorization.
     */
    PublicKey publicKey;
    ServiceAuthenticationResponse response;
    try (var client = Utils.Http.getClient(this.authenticatingHub, null)) {
      Logging.INSTANCE.info(
              "authenticating "
                      + certificate.getProperty(KlabCertificate.KEY_NODENAME)
                      + " with hub "
                      + authenticatingHub);
      response =
          client.post(
              ServicesAPI.HUB.AUTHENTICATE_SERVICE, request, ServiceAuthenticationResponse.class);
    } catch (Exception e) {
      throw new KlabAuthorizationException("error authenticating: " + e);
    }

    if (response == null) {
      throw new KlabAuthorizationException("authentication response is null");
    }
    this.hubName = response.getAuthenticatingHub();

    try {
      byte[] publicKeyData = Base64.getDecoder().decode(response.getPublicKey());
      X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyData);
      KeyFactory kf = KeyFactory.getInstance("RSA");
      publicKey = kf.generatePublic(spec);
    } catch (Exception e) {
      throw new KlabAuthorizationException("invalid public key sent by hub");
    }

    /*
     * build a verifier for the token coming from any engine that has validated with
     * the authenticating hub.
     */
    this.preValidationExtractor =
        new JwtConsumerBuilder()
            .setSkipAllValidators()
            .setDisableRequireSignature()
            .setSkipSignatureVerification()
            .build();

    JwtConsumer jwtVerifier =
        new JwtConsumerBuilder()
            .setSkipDefaultAudienceValidation()
            .setAllowedClockSkewInSeconds(ALLOWED_CLOCK_SKEW_MS / 1000)
            .setVerificationKey(publicKey)
            .build();

    jwksVerifiers.put(response.getAuthenticatingHub(), jwtVerifier);

    // TODO fill in services from hub response, which at the moment contains no provision for that
    /*
    ServiceReference service = new ServiceReference();
    service.setId(this.nodeName);
    service.setIdentityType(this.type);
    try {
        service.setUrls(List.of(new URI(certificate.getProperty(KlabCertificate.KEY_URL)).toURL()));
    } catch (MalformedURLException e) {
        throw new RuntimeException(e);
    } catch (URISyntaxException e) {
        throw new RuntimeException(e);
    }
    List<ServiceReference> services = new ArrayList<>();
    service.setPrimary(true);
    services.add(service);
    */
    /*
     * return the institutional identity this certificate belongs to.
     */
    var ret = new PartnerIdentityImpl();
    ret.setId(response.getUserData().getIdentity().getId());
    // ret.setContactPerson(UserIdentity.create(response.getUserData()); // TODO
    ret.setName(certificate.getProperty(KlabCertificate.KEY_NODENAME));
    ret.setAuthenticatingHub(response.getAuthenticatingHub());
    ret.setPublicKey(response.getPublicKey());
    for (Group group : response.getGroups()) {
      ret.getGroups().add(group);
    }
    ret.setToken((response.getUserData().getToken()));
    return Pair.of(ret, response.getServices());
  }

  /**
   * Given a JWT token that has previously been generated by a login event, validate its payload &
   * signature. If it passes all checks and its payload can be extracted properly, then return an
   * EngineAuthorization representing it. Transparently, link or retrieve the corresponding scope
   * for the request and attach it to the response for perusal by the service.
   *
   * <p>The token may have additional components appended to it to specify the specific child scope
   * for the request. These will be the remaining dot-separated parts after the JWT three
   * components, or after the service secret in case (2) below. A token without additional
   * components corresponds to a UserScope or a ServiceScope. The scope hierarchy is maintained here
   * and is disposed of (with correspondent resource cleanup) upon timeout.
   *
   * <p>The call may be succeed with a token that is not JWT in the following cases:
   *
   * <p>1. If the service is running under a {@link
   * org.integratedmodelling.klab.api.identities.UserIdentity}-level scope, i.e. it has been started
   * with a local engine certificate, the only token admitted is the same identity that's running
   * the engine, i.e. a user may access exclusively a service it owns. In this case the JWT is not
   * processed but just matched to the service's identity. FIXME this is currently disabled - may be
   * removed.
   *
   * <p>2. The service is running on localhost and the authorization portion (1st dot-separated
   * part) of the token is the secret token saved on the filesystem and read back by the client. In
   * that case the user is fully authorized for local requests and the rest of the token is used for
   * the scope.
   *
   * <p>In both situations above, the authorization is given with full powers and all client-level
   * roles. Otherwise the hub makes the decision and the JWT is parsed to obtain username, groups
   * and roles as expected.
   */
  public EngineAuthorization validateToken(
      String token, String serverKey, String scopeHeader, String brokerUrl, String federationId) {

    EngineAuthorization ret = null;

    var privilegedLocalService =
        serverKey != null && serverKey.equals(klabService.get().klabService().getServiceSecret());

    /*
    we move on to JWT parsing only if the service is authenticated with the hub and the user is not
    anonymous.
     */
    if (!ServicesAPI.ANONYMOUS_TOKEN.equals(token) && serviceAuthenticated) {

      try {
        // first extract the partnerId so that we know which public key to use for
        // validating the signature
        JwtContext jwtContext = preValidationExtractor.process(token);
        String hubId = jwtContext.getJwtClaims().getIssuer().trim();
        JwtConsumer jwtVerifier = jwksVerifiers.get(hubId);

        if (jwtVerifier == null) {
          String msg =
              String.format(
                  "Couldn't find JWT verifier for partnerId %s. I only know " + "about " + "%s.",
                  hubId, jwksVerifiers.keySet().toString());
          Exception e = new KlabAuthorizationException(msg);
          Logging.INSTANCE.error(msg, e);
          // throw e;
        } else {

          JwtClaims claims = jwtVerifier.processToClaims(token);
          String username = claims.getSubject();
          List<String> groupStrings = claims.getStringListClaimValue(JWT_CLAIM_KEY_PERMISSIONS);
          List<String> roleStrings = claims.getStringListClaimValue(JWT_CLAIM_KEY_ROLES);

          // didn't throw an exception, so token is valid. Update the result and validate
          // claims. This is an engine-only entry point so the role is obvious.
          ret =
              new EngineAuthorization(
                  hubId,
                  username,
                  brokerUrl,
                  federationId,
                  token,
                  groupStrings,
                  Collections.unmodifiableList(filterRoles(roleStrings)));

          /*
           * Audience (aud) - The "aud" (audience) claim identifies the recipients that
           * the JWT is intended for. Each principal intended to process the JWT must
           * identify itself with a value in the audience claim. If the principal
           * processing the claim does not identify itself with a value in the aud claim
           * // when this claim is present, then the JWT must be rejected.
           */
          if (!claims.getAudience().contains(ENGINE_AUDIENCE)) {}

          /*
           * Expiration time (exp) - The "exp" (expiration time) claim identifies the
           * expiration time on or after which the JWT must not be accepted for
           * processing. The value should be in NumericDate[10][11] format.
           */
          NumericDate expirationTime = claims.getExpirationTime();
          long now = System.currentTimeMillis();
          if (expirationTime.isBefore(NumericDate.fromMilliseconds(now - ALLOWED_CLOCK_SKEW_MS))) {
            throw new KlabAuthorizationException(
                "user " + username + " is using an expired " + "authorization");
          }

          long issuedAtUtcMs = claims.getIssuedAt().getValueInMillis();
          Instant issuedAt = Instant.ofEpochMilli(issuedAtUtcMs);
          ret.setIssuedAt(issuedAt);
          //            result.getGroups().addAll(filterGroups(groupStrings));
          ret.setAuthenticated(true);
        }

      } catch (MalformedClaimException | InvalidJwtException e) {
        // TODO see if we should reauthenticate and if so, try that before throwing an
        // authorization exception
        if ((wtfErrors % 100) == 0) {
          Logging.INSTANCE.error("WTF (" + wtfErrors + " errors)", e);
        }
        wtfErrors++;
      } catch (Exception e) {
        // it was a JWT token, but some other exception happened.
        if ((wtfErrors % 100) == 0) {
          Logging.INSTANCE.error("WTF (" + wtfErrors + " errors)", e);
        }
        wtfErrors++;
      }
    }

    if (ret == null) {
      /*
      anonymous user case also intercepts JWT token failure
       */
      ret = new EngineAuthorization("nohub", "anonymous", null, null, null, List.of(), null);
      ret.setTokenString(ServicesAPI.ANONYMOUS_TOKEN);
    }

    if (privilegedLocalService) {
      // any user with local authority (including anonymous) gets all privileges and the user data
      // from the authenticating user
      ret.setLocal(true);
      ret.setRoles(
          EnumSet.of(
              Role.ROLE_ENGINE, Role.ROLE_ADMINISTRATOR, Role.ROLE_USER, Role.ROLE_DATA_MANAGER));
      ret.setAuthenticated(true);
      // ret.setTokenString(serverKey);

      var owner = klabService.get().getServiceOwner();
      if (owner instanceof UserIdentity user && "anonymous".equals(ret.getUsername())) {
        ret.setUsername(user.getUsername());
        ret.setGroups(user.getGroups());
        ret.setEmailAddress(user.getEmailAddress());
      }

    }

    /** User scope is created anyway. */
    Scope scope = klabService.get().klabService().getScopeManager().getOrCreateUserScope(ret);

    if (scopeHeader != null) {
      // ...then contextualized as needed
      var scopeData = ContextScope.parseScopeId(scopeHeader);
      scope =
          klabService
              .get()
              .klabService()
              .getScopeManager()
              .getScope(ret, scopeData.type().classify(), scopeData.scopeId());
      if (scope instanceof ServiceContextScope) {
        scope =
            klabService
                .get()
                .klabService()
                .getScopeManager()
                .contextualizeScope((ServiceContextScope) scope, scopeData);
      }
    }

    if (scope == null) {
      Logging.INSTANCE.error(
          "Internal error: user "
              + ret.getUsername()
              + " was authorized with null"
              + " scope using scope ID "
              + scopeHeader);
    }

    ret.setInfo("scopeHeader=" + scopeHeader + "; serverKey=" + serverKey);
    ret.setScope(scope);

    return ret;
  }

  // As of now the node and hub have different roles. It maybe best to unify this.
  // I add the Role.ROLE_ENGINE because they hub does not give this to users. In
  // the future
  // we may need to create engines with no user, and as of now the hub would not
  // know how to
  // do that...
  private List<Role> filterRoles(List<String> roleStrings) {
    List<Role> ret = new ArrayList<>();
    for (String roleString : roleStrings) {
      for (Role r : Role.values()) {
        if (r.getAuthority().equals(roleString)) {
          ret.add(r);
        }
      }
    }
    ret.add(Role.ROLE_ENGINE);
    return ret;
  }

  private Set<Group> filterGroups(List<String> groupStrings) {
    Set<Group> ret = new HashSet<>();
    List<String> authenticated = new ArrayList<>();
    for (String groupId : groupStrings) {
      //            Group group = groups.get(groupId);
      //            if (group != null) {
      //                authenticated.add(groupId);
      //                ret.add(group);
      //            }
    }

    //		Logging.INSTANCE.info("Received groups " + groupStrings + "; authenticated " +
    //		authenticated);

    return ret;
  }
}
