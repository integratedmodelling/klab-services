package org.integratedmodelling.klab.services.application.security;

import org.integratedmodelling.common.authentication.PartnerIdentityImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.PartnerIdentity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.rest.ServiceReference;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.base.BaseService;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwt.consumer.JwtContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.Principal;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * Singleton containing all the JWT management and hanshaking with the hub that every k.LAB service uses.
 * Should also include a safe strategy to authorize the service and handle the JWT token w/o configuration
 * when the services are available (authenticated or anonymously) in a local configuration.
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
    private boolean initialized;

    /**
     * This is set explicitly after the scope is created. Without the root scope, no authentication is
     * possible
     */
    private BaseService klabService;

    /**
     * Validate a SERVICE-level certificate and return the valid partner identity with the token to talk to
     * the hub. The service may also run on User authority or even anonymously if allowed, with different
     * {@link org.integratedmodelling.klab.api.scope.ServiceScope.Locality} privileges in its
     * {@link org.integratedmodelling.klab.api.scope.ServiceScope} and
     * {@link org.integratedmodelling.klab.api.services.KlabService.ServiceCapabilities}.
     * <p>
     * This is only called if a certificate file is found in the service configuration area.
     *
     * @param certificate //     * @param options
     * @return the partner identity that owns this node.
     */
    public Pair<Identity, List<ServiceReference>> authenticateService(KlabCertificate certificate,
                                                                      StartupOptions options) {

        String serverHub = authenticatingHub;
        if (serverHub == null) {
            serverHub = certificate.getProperty(KlabCertificate.KEY_PARTNER_HUB);
        }

        if (serverHub == null) {
            throw new KlabAuthorizationException("a node cannot be started without a valid authenticating " +
                    "hub");
        }

        this.authenticatingHub = serverHub;
        this.nodeName = options.getServiceName() == null ?
                        certificate.getProperty(KlabCertificate.KEY_NODENAME) : options.getServiceName();

        ServiceAuthenticationRequest request = new ServiceAuthenticationRequest();

        request.setCertificate(certificate.getProperty(KlabCertificate.KEY_CERTIFICATE));
        request.setName(nodeName);
        request.setKey(certificate.getProperty(KlabCertificate.KEY_SIGNATURE));
        request.setLevel(certificate.getLevel());
        request.setEmail(certificate.getProperty(KlabCertificate.KEY_PARTNER_EMAIL));
        initialized = true;

        /*
         * response contains the groupset for validation and the Base64-encoded public
         * key for the JWT tokens. We throw away the public key after building it; if
         * the first token decryption fails and we have authenticated some time before,
         * we can try re-authenticating once to update it before refusing authorization.
         */
        PublicKey publicKey = null;
        ServiceAuthenticationResponse response = null;// client.authenticateNode(serverHub, request);
        this.hubName = response.getAuthenticatingHub();

        try {
            byte publicKeyData[] = Base64.getDecoder().decode(response.getPublicKey());
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
                new JwtConsumerBuilder().setSkipAllValidators().setDisableRequireSignature()
                                        .setSkipSignatureVerification().build();

        JwtConsumer jwtVerifier = new JwtConsumerBuilder().setSkipDefaultAudienceValidation()
                                                          .setAllowedClockSkewInSeconds(ALLOWED_CLOCK_SKEW_MS / 1000).setVerificationKey(publicKey).build();

        jwksVerifiers.put(response.getAuthenticatingHub(), jwtVerifier);


        // TODO fill in services from hub response, which at the moment contains no provision for that
        List<ServiceReference> services = new ArrayList<>();

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

        return Pair.of(ret, services);
    }

    /**
     * Given a JWT token that has previously been generated by a login event, validate its payload &
     * signature. If it passes all checks and its payload can be extracted properly, then return an
     * EngineAuthorization representing it. Transparently, link or retrieve the corresponding scope for the
     * request and attach it to the response for perusal by the service.
     * <p>
     * The token may have additional components appended to it to specify the specific child scope for the
     * request. These will be the remaining dot-separated parts after the JWT three components, or after the
     * service secret in case (2) below. A token without additional components corresponds to a UserScope or a
     * ServiceScope. The scope hierarchy is maintained here and is disposed of (with correspondent resource
     * cleanup) upon timeout.
     * <p>
     * The call may be succeed with a token that is not JWT in the following cases:
     * <p>
     * 1. If the service is running under a
     * {@link org.integratedmodelling.klab.api.identities.UserIdentity}-level scope, i.e. it has been started
     * with a local engine certificate, the only token admitted is the same identity that's running the
     * engine, i.e. a user may access exclusively a service it owns. In this case the JWT is not processed but
     * just matched to the service's identity.
     * <p>
     * 2. The service is running on localhost and the authorization portion (1st dot-separated part) of the
     * token is the secret token saved on the filesystem and read back by the client. In that case the user is
     * authorized for local requests and the rest of the token is used for the scope.
     * <p>
     * In both situations above, the authorization is given with full powers and all roles. Otherwise the hub
     * makes the decision and the JWT is parsed to obtain username, groups and roles as expected.
     */
    public EngineAuthorization validateToken(String token) {

        EngineAuthorization result = null;

        if (token.startsWith(klabService.getServiceSecret())) {

            /*
            local connection on same machine, authorize service scope with all privileges.
             */
            return EngineAuthorization.create(klabService.scope(), true);

        } else if (token.equals(klabService.scope().getIdentity().getId())) {
            /*
            authorized user is same user who runs the service; authorization is entire
            service scope and service is run by user-level certificate, not institution.
             */
            return EngineAuthorization.create(klabService.scope(), false);
        }

        if (!initialized) {
            return null;
        }

        try {
            // first extract the partnerId so that we know which public key to use for
            // validating the signature
            JwtContext jwtContext = preValidationExtractor.process(token);
            String hubId = jwtContext.getJwtClaims().getIssuer().trim();
            JwtConsumer jwtVerifier = jwksVerifiers.get(hubId);

            if (jwtVerifier == null) {
                String msg = String.format("Couldn't find JWT verifier for partnerId %s. I only know about " +
                                "%s.", hubId,
                        jwksVerifiers.keySet().toString());
                // Exception e = new JwksNotFoundException(msg);
                // Logging.INSTANCE.error(msg, e);
                // throw e;
            }

            JwtClaims claims = jwtVerifier.processToClaims(token);
            String username = claims.getSubject();
            List<String> groupStrings = claims.getStringListClaimValue(JWT_CLAIM_KEY_PERMISSIONS);
            List<String> roleStrings = claims.getStringListClaimValue(JWT_CLAIM_KEY_ROLES);

            // didn't throw an exception, so token is valid. Update the result and validate
            // claims. This is an engine-only entry point so the role is obvious.
            result = new EngineAuthorization(hubId, username,
                    Collections.unmodifiableList(filterRoles(roleStrings)));

            /*
             * Audience (aud) - The "aud" (audience) claim identifies the recipients that
             * the JWT is intended for. Each principal intended to process the JWT must
             * identify itself with a value in the audience claim. If the principal
             * processing the claim does not identify itself with a value in the aud claim
             * // when this claim is present, then the JWT must be rejected.
             */
            if (!claims.getAudience().contains(ENGINE_AUDIENCE)) {

            }

            /*
             * Expiration time (exp) - The "exp" (expiration time) claim identifies the
             * expiration time on or after which the JWT must not be accepted for
             * processing. The value should be in NumericDate[10][11] format.
             */
            NumericDate expirationTime = claims.getExpirationTime();
            long now = System.currentTimeMillis();
            if (expirationTime.isBefore(NumericDate.fromMilliseconds(now - ALLOWED_CLOCK_SKEW_MS))) {
                throw new KlabAuthorizationException("user " + username + " is using an expired " +
                        "authorization");
            }

            long issuedAtUtcMs = claims.getIssuedAt().getValueInMillis();
            Instant issuedAt = Instant.ofEpochMilli(issuedAtUtcMs);
            result.setIssuedAt(issuedAt);
            //            result.getGroups().addAll(filterGroups(groupStrings));
            result.setAuthenticated(true);

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

        return result;
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

    public Scope resolveScope(Principal principal) {
        if (principal instanceof EngineAuthorization authorization) {
            return authorization.getScope();
        }
        return null;
    }

    public <T extends Scope> T resolveScope(Principal principal, Class<T> scopeClass) {
        if (principal instanceof EngineAuthorization authorization
                && authorization.getScope() != null
                && scopeClass.isAssignableFrom(authorization.getScope().getClass())) {
            return (T) authorization.getScope();
        }
        return null;
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

    public void setKlabService(BaseService klabService) {
        this.klabService = klabService;
    }
}
