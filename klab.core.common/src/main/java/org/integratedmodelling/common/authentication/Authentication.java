package org.integratedmodelling.common.authentication;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.rest.EngineAuthenticationRequest;
import org.integratedmodelling.klab.rest.EngineAuthenticationResponse;

import java.time.Instant;

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
    public UserIdentity authenticate() {
        return null;
    }

    /**
     * Authenticate through a hub using the passed certificate. If the passed certificate is anonymous, just
     * return the anonymous user.
     *
     * @param certificate
     * @return
     */
    public UserIdentity authenticate(KlabCertificate certificate) {

        UserIdentity ret = null;
        EngineAuthenticationResponse authentication = null;

        if (certificate instanceof AnonymousEngineCertificate) {
            // no partner, no node, no token, no nothing. REST calls automatically accept
            // the
            // anonymous user when secured as Roles.PUBLIC.
            Logging.INSTANCE.info("No user certificate: continuing in anonymous offline mode");

            return new AnonymousUser();
        }

        if (!certificate.isValid()) {
            /*
             * expired or invalid certificate: throw away the identity, continue as anonymous.
             */
            Logging.INSTANCE.info("Certificate is invalid or expired: continuing in anonymous offline mode");
            return new AnonymousUser();
        }

        //        if (certificate.getType() == Type.NODE && getAuthenticatedIdentity(INodeIdentity.class)
        //        != null) {
        //            ret = new KlabUser(certificate.getProperty(ICertificate.KEY_NODENAME),
        //            getAuthenticatedIdentity(INodeIdentity.class));
        //            registerIdentity(ret);
        //            return ret;
        //        }

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
                request.setEmail(certificate.getProperty(KlabCertificate.KEY_USERNAME));

                var response = client.post(authenticationServer, request, EngineAuthenticationResponse.class);
                if (response.statusCode() == 200) {
                    authentication = response.body();
                }

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
                expiry = Instant.parse(authentication.getUserData().getExpiry());
            } catch (Throwable e) {
                Logging.INSTANCE.error("bad date or wrong date format in certificate. Please use latest " +
                        "version of software.");
                return null;
            }
            if (expiry == null) {
                Logging.INSTANCE.error("certificate has no expiration date. Please obtain a new certificate" +
                        ".");
                return null;
            } else if (expiry.isBefore(Instant.now())) {
                Logging.INSTANCE.error("certificate expired on " + expiry + ". Please obtain a new " +
                        "certificate.");
                return null;
            }
        }

        /*
         * build the identity
         */
        if (certificate.getType() == KlabCertificate.Type.ENGINE) {

            // if we have connected, insert network session identity
            if (authentication != null) {
                //
                //                HubReference hubNode = authentication.getHub();
                //                Hub hub = new Hub(hubNode);
                //                hub.setOnline(true);
                //                NetworkSession networkSession = new NetworkSession(authentication
                //                .getUserData().getToken(), hub);
                //
                //                ret = new KlabUser(authentication.getUserData(), networkSession);
                //
                //                Network.INSTANCE.buildNetwork(authentication);
                //
                //                Logging.INSTANCE.info("User " + ((IUserIdentity) ret).getUsername() + "
                //                logged in through hub " + hubNode.getId()
                //                        + " owned by " + hubNode.getPartner().getId());
                //
                //                Logging.INSTANCE.info("The following nodes are available:");
                //                for (INodeIdentity n : Network.INSTANCE.getNodes()) {
                //                    Duration uptime = new Duration(n.getUptime());
                //                    DateTime boottime = DateTime.now(DateTimeZone.UTC).minus(uptime
                //                    .toPeriod());
                //                    IPartnerIdentity partner = n.getParentIdentity();
                //                    Logging.INSTANCE.info("   " + n.getName() + " online since " +
                //                    boottime);
                //                    Logging.INSTANCE.info("      " + partner.getName() + " (" + partner
                //                    .getEmailAddress() + ")");
                //                    Logging.INSTANCE.info("      " + "online " + PeriodFormat.getDefault
                //                    ().print(uptime.toPeriod()));
                //                }

            } else {

                //                // offline node with no partner
                //                Node node = new Node(certificate.getProperty(KlabCertificate
                //                .KEY_NODENAME), null);
                //                ((Node) node).setOnline(false);
                //                ret = new KlabUser(certificate.getProperty(KlabCertificate.KEY_USERNAME),
                //                node);
                //
                //                Logging.INSTANCE.info("User " + ((IUserIdentity) ret).getUsername() + "
                //                activated in offline mode");
            }

            //            ((KlabUser) ret).setOnline(authentication != null);

        } else {
            throw new KlabAuthorizationException(
                    "wrong certificate for an engine: cannot create identity of type " + certificate.getType());
        }

        return ret;
    }
}
