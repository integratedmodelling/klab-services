package org.integratedmodelling.common.authentication;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.rest.EngineAuthenticationRequest;
import org.integratedmodelling.klab.rest.EngineAuthenticationResponse;
import org.integratedmodelling.klab.rest.HubReference;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements the default certificate-based authentication mechanism for an engine. Also maintains external
 * credentials.
 */
public enum Authentication {

    INSTANCE;

    /**
     * Authenticate using the default certificate if present on the filesystem, or anonymously if not.
     *
     * @return
     */
    public Pair<Identity, List<ServiceReference>> authenticate() {
        File certFile = new File(Configuration.INSTANCE.getDataPath() + File.separator + "klab.cert");
        KlabCertificate certificate = certFile.isFile() ? KlabCertificateImpl.createFromFile(certFile) :
                                      new AnonymousEngineCertificate();
        return authenticate(certificate);
    }

    /**
     * Authenticate through a hub using the passed certificate. If the passed certificate is anonymous, just
     * return the anonymous user.
     *
     * @param certificate
     * @return
     */
    public Pair<Identity, List<ServiceReference>> authenticate(KlabCertificate certificate) {

        if (certificate instanceof AnonymousEngineCertificate) {
            // no partner, no node, no token, no nothing. REST calls automatically accept
            // the anonymous user when secured as Roles.PUBLIC.
            Logging.INSTANCE.info("No user certificate: continuing in anonymous offline mode");
            return Pair.of(new AnonymousUser(), getLocalServices());
        }

        if (!certificate.isValid()) {
            /*
             * expired or invalid certificate: throw away the identity, continue as anonymous.
             */
            Logging.INSTANCE.info("Certificate is invalid or expired: continuing in anonymous offline mode");
            return Pair.of(new AnonymousUser(), Collections.emptyList());
        }

        EngineAuthenticationResponse authentication = null;
        String authenticationServer = certificate.getProperty(KlabCertificate.KEY_PARTNER_HUB);

        if (authenticationServer != null) {

            try (var client = Utils.Http.getClient(authenticationServer)) {

                Logging.INSTANCE.info("authenticating " + certificate.getProperty(KlabCertificate.KEY_USERNAME) + " with hub "
                        + authenticationServer);

                /*
                 * Authenticate with server(s). If authentication fails because of a 403, invalidate the
                 * certificate. If no server can be reached, certificate is valid but engine is offline.
                 */
                EngineAuthenticationRequest request = new EngineAuthenticationRequest(
                        certificate.getProperty(KlabCertificate.KEY_USERNAME),
                        certificate.getProperty(KlabCertificate.KEY_SIGNATURE),
                        certificate.getProperty(KlabCertificate.KEY_CERTIFICATE_TYPE),
                        certificate.getProperty(KlabCertificate.KEY_CERTIFICATE), certificate.getLevel(),
                        certificate.getProperty(KlabCertificate.KEY_AGREEMENT));
                // add email if we have it, so the hub can notify in any case if so configured
                request.setEmail(certificate.getProperty(KlabCertificate.KEY_EMAIL));

                authentication = client.post(ServicesAPI.HUB.AUTHENTICATE_ENGINE, request,
                        EngineAuthenticationResponse.class);

            } catch (Throwable e) {
                Logging.INSTANCE.error("authentication failed for user " + certificate.getProperty(KlabCertificate.KEY_USERNAME)
                        + ": " + e);
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
                Logging.INSTANCE.error("bad date or wrong date format in certificate. Please use latest " +
                        "version of software. Continuing anonymously.");
                return Pair.of(new AnonymousUser(), getLocalServices());
            }
            if (expiry == null) {
                Logging.INSTANCE.error("certificate has no expiration date. Please obtain a new certificate" +
                        ". Continuing anonymously.");
                return Pair.of(new AnonymousUser(), getLocalServices());
            } else if (expiry.isBefore(Instant.now())) {
                Logging.INSTANCE.error("certificate expired on " + expiry + ". Please obtain a new " +
                        "certificate. Continuing anonymously.");
                return Pair.of(new AnonymousUser(), getLocalServices());
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
                ret.setParentIdentity(hub);
                ret.setEmailAddress(authentication.getUserData().getIdentity().getEmail());
                ret.setUsername(authentication.getUserData().getIdentity().getId());

                Logging.INSTANCE.info("User " + ret.getUsername() + " logged in through hub " + hubNode.getId()
                        + " owned by " + hubNode.getPartner().getId());

                // TODO services
                Logging.INSTANCE.info("The following services are available to " + ret.getUsername() + ":");

                for (var service : authentication.getNodes()) {
                    if (service.getServiceType() == KlabService.Type.LEGACY_NODE) {
                        // TODO see if we need to adapt
                        Logging.INSTANCE.info("Legacy service " + service.getId() + " from hub " + hubNode.getId()
                                + " authorized, ignored");
                    } else {
                        services.add(service);
                    }
                }
                return Pair.of(ret, addLocalServices(services));
            }

        } else {
            throw new KlabAuthorizationException(
                    "wrong certificate for an engine: cannot create identity of type " + certificate.getType());
        }

        return Pair.of(new AnonymousUser(), getLocalServices());
    }

    /**
     * If a specific service type is missing and the correspondent service is available locally, add the local
     * service as primary; otherwise add it as secondary. If any of the 4 essential services is missing, throw
     * an exception.
     *
     * @param services a list of services retrieved from the authentication response
     * @return the patched list of services
     * @throws org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException if any essential
     *                                                                                 service is missing
     */
    private List<ServiceReference> addLocalServices(List<ServiceReference> services) {
        return services;
    }


    /**
     * TODO create and return clients for any services running locally. If so configured, start embedded
     *  services for each service type.
     */
    public List<ServiceReference> getLocalServices() {

        List<ServiceReference> ret = new ArrayList<>();

        if (Utils.Network.isAlive("http://127.0.0.1:" + KlabService.Type.RESOURCES.defaultPort + " " +
                "/resources" +
                "/actuator")) {
        }

        if (Utils.Network.isAlive("http://127.0.0.1:" + KlabService.Type.REASONER.defaultPort + " /reasoner" +
                "/actuator")) {
        }

        if (Utils.Network.isAlive("http://127.0.0.1:" + KlabService.Type.RESOLVER.defaultPort + " /resolver" +
                "/actuator")) {
        }

        if (Utils.Network.isAlive("http://127.0.0.1:" + KlabService.Type.RUNTIME.defaultPort + " /runtime" +
                "/actuator")) {
        }

        return ret;
    }


}
