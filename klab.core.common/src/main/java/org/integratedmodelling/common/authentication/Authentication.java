package org.integratedmodelling.common.authentication;

import org.integratedmodelling.common.distribution.DevelopmentDistributionImpl;
import org.integratedmodelling.common.distribution.DistributionImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.community.CommunityClient;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.common.services.client.scope.ClientScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.rest.EngineAuthenticationRequest;
import org.integratedmodelling.klab.rest.EngineAuthenticationResponse;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.io.File;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Implements the default certificate-based authentication mechanism for an engine. Also maintains external
 * credentials.
 */
public enum Authentication {

    INSTANCE;

    /**
     * Authenticate using the default certificate if present on the filesystem, or anonymously if not.
     *
     * @param logEvents log info messages (errors are logged no matter what)
     * @return
     */
    public Pair<Identity, List<ServiceReference>> authenticate(boolean logEvents) {
        File certFile = new File(Configuration.INSTANCE.getDataPath() + File.separator + "klab.cert");
        KlabCertificate certificate = certFile.isFile() ? KlabCertificateImpl.createFromFile(certFile) :
                                      new AnonymousEngineCertificate();
        return authenticate(certificate, logEvents);
    }

    /**
     * Authenticate through a hub using the passed certificate. If the passed certificate is anonymous, just
     * return the anonymous user.
     *
     * @param certificate
     * @param logEvents   log info messages (errors are logged no matter what)
     * @return
     */
    public Pair<Identity, List<ServiceReference>> authenticate(KlabCertificate certificate,
                                                               boolean logEvents) {

        if (certificate instanceof AnonymousEngineCertificate) {
            // no partner, no node, no token, no nothing. REST calls automatically accept
            // the anonymous user when secured as Roles.PUBLIC.
            if (logEvents) {
                Logging.INSTANCE.info("No user certificate: continuing in anonymous offline mode");
            }
            return Pair.of(new AnonymousUser(), Collections.emptyList());
        }

        if (!certificate.isValid()) {
            /*
             * expired or invalid certificate: throw away the identity, continue as anonymous.
             */
            if (logEvents) {
                Logging.INSTANCE.info("Certificate is invalid or expired: continuing in anonymous offline " +
                        "mode");
            }
            return Pair.of(new AnonymousUser(), Collections.emptyList());
        }

        EngineAuthenticationResponse authentication = null;
        String authenticationServer = certificate.getProperty(KlabCertificate.KEY_PARTNER_HUB);

        if (authenticationServer != null) {

            try (var client = Utils.Http.getClient(authenticationServer)) {

                if (logEvents) {
                    Logging.INSTANCE.info("authenticating " + certificate.getProperty(KlabCertificate.KEY_USERNAME) + " with hub "
                            + authenticationServer);
                }
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
                return Pair.of(new AnonymousUser(), Collections.emptyList());
            }
            if (expiry == null) {
                Logging.INSTANCE.error("certificate has no expiration date. Please obtain a new certificate" +
                        ". Continuing anonymously.");
                return Pair.of(new AnonymousUser(), Collections.emptyList());
            } else if (expiry.isBefore(Instant.now())) {
                Logging.INSTANCE.error("certificate expired on " + expiry + ". Please obtain a new " +
                        "certificate. Continuing anonymously.");
                return Pair.of(new AnonymousUser(), Collections.emptyList());
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
                return Pair.of(ret, services);
            }

        } else {
            throw new KlabAuthorizationException(
                    "wrong certificate for an engine: cannot create identity of type " + certificate.getType());
        }

        return Pair.of(new AnonymousUser(), Collections.emptyList());
    }


    /**
     * Strategy to locate a primary service in all possible ways. If there are primary service URLs for the
     * passed service class in the list of service references obtained through authentication, try them and if
     * one responds return a client to it. Otherwise, try the local URL and if the passed service is running
     * locally, return a client to it. As a last resort, check if we have a source distribution configured or
     * available, and if so, synchronize it if needed and if it provides the required service product, run it
     * and return a service client.
     *
     * @param serviceType       the service we need.
     * @param identity          the identity we represent
     * @param availableServices a list of {@link ServiceReference} objects obtained through certificate
     *                          authentication, or an empty list.
     * @param <T>               the type of service we want to obtain
     * @return a service client or null. The service status should be checked before use.
     */
    public <T extends KlabService> T findService(KlabService.Type serviceType,
                                                 Scope scope,
                                                 Identity identity,
                                                 List<ServiceReference> availableServices,
                                                 boolean logFailures) {

        BiConsumer<Scope, Message>[] listeners = scope instanceof ClientScope clientScope ? clientScope.getListeners() : null;

        for (var service : availableServices) {
            if (service.getServiceType() == serviceType && service.isPrimary()) {
                for (var url : service.getUrls()) {
                    if (ServiceClient.readServiceStatus(url) != null) {
                        scope.info("Using authenticated " + service.getServiceType() + " service from " + service.getPartner().getId());
                        return (T) createLocalServiceClient(serviceType, url, identity, availableServices, listeners);
                    }
                }
            }
        }

        // if we get here, we have no remote services available and we should try a running local one first.
        if (ServiceClient.readServiceStatus(serviceType.localServiceUrl()) != null) {
            scope.info("Using locally running " + serviceType + " service at " + serviceType.localServiceUrl());
            return (T) createLocalServiceClient(serviceType, serviceType.localServiceUrl(), identity,
                    availableServices, listeners);
        }

        // if we got here, we need to launch the service ourselves. We may be using a remote distribution or
        // a development one, which takes priority. TODO use options to influence the priority here.
        var distribution = DistributionImpl.isDevelopmentDistributionAvailable() ?
                           new DevelopmentDistributionImpl() : new DistributionImpl();

        if (distribution.isAvailable()) {
            scope.info("No service available for " + serviceType + ": starting local service from local k" +
                    ".LAB distribution");
            var product = distribution.findProduct(Product.ProductType.forService(serviceType));
            var instance = product.getInstance(scope);
            if (instance.start()) {
                scope.info("Service is starting: will be attempting connection to locally running " + serviceType);
                scope.send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceInitializing, serviceType + " service at " + serviceType.localServiceUrl());
                return (T) createLocalServiceClient(serviceType, serviceType.localServiceUrl(), identity,
                        availableServices, listeners);
            }
        } else if (logFailures) {
            scope.info("No service available for " + serviceType + " and no k.LAB distribution available");

        }

        return null;
    }

    public <T extends KlabService> T createLocalServiceClient(KlabService.Type serviceType, URL url,
                                                              Identity identity,
                                                              List<ServiceReference> services,
                                                              BiConsumer<Scope, Message>... listeners) {
        T ret = switch (serviceType) {
            case REASONER -> {
                yield (T) new ReasonerClient(url, identity, services);
            }
            case RESOURCES -> {
                yield (T) new ResourcesClient(url, identity, services);
            }
            case RESOLVER -> {
                yield (T) new ResolverClient(url, identity, services);
            }
            case RUNTIME -> {
                yield (T) new RuntimeClient(url, identity, services);
            }
            case COMMUNITY -> {
                yield (T) new CommunityClient(url, identity, services);
            }
            default -> throw new IllegalStateException("Unexpected value: " + serviceType);
        };

        if (ret instanceof ServiceClient serviceClient && listeners != null) {
            for (var listener : listeners) {
                serviceClient.addListener(listener);
            }
        }

        return null;
    }

}
