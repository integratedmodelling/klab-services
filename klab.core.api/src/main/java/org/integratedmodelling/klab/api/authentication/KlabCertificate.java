package org.integratedmodelling.klab.api.authentication;

import java.util.Map;
import java.util.Set;

/**
 * A certificate defines a 'root' identity for an engine. It can be personal or institutional. The default
 * location of the k.LAB certificate file is ~/.klab/klab.cert. Any user who runs an engine locally must have
 * a certificate that establishes their privileges and defines their network environment once the engine
 * connects to the k.LAB network.
 * <p>
 * If no certificate file is found, implementations can create a default certificate with anonymous identity,
 * linked to a preferred worldview and enabling basic, local operations with no access to the network. The
 * same certificate may be used for testing.
 * <p>
 * When a certificate begins its lifetime, it should be already authenticated and its validity should have
 * been checked with {@link #isValid()} immediately after creation.
 * <p>
 *
 * @author ferdinando villa
 */
public interface KlabCertificate {

    public static enum Level {

        /**
         * Anonymous certificate can only authenticate with a locally running hub.
         */
        ANONYMOUS,
        /**
         * Legacy certificate can only be user level
         */
        LEGACY,
        /**
         * Individual user owns the engine (default).
         */
        USER,
        /**
         * Institutional engine. Can be linked to security settings.
         */
        INSTITUTIONAL,

        /**
         * Test certificate. Only free to connect to localhost test networks.
         */
        TEST
    }

    public static enum Type {
        /**
         * This certificate authorizes an engine, at one of the levels defined by {@link Level}.
         */
        ENGINE,
        /**
         * This certificate authorizes a node. The only allowed level is {@link Level#INSTITUTIONAL} or
         * {@link Level#TEST}.
         */
        NODE,
        /**
         * This certificate authorizes a hub. The only allowed level is {@link Level#INSTITUTIONAL} or
         * {@link Level#TEST}.
         */
        HUB,
        /**
         * This certificate authorizes a lever. The only allowed level is {@link Level#INSTITUTIONAL} or
         * {@link Level#TEST}.
         */
        LEVER

    }

    public static final String DEFAULT_ENGINE_CERTIFICATE_FILENAME = "klab.cert";
    public static final String DEFAULT_NODE_CERTIFICATE_FILENAME = "node.cert";
    public static final String DEFAULT_SEMANTIC_SERVER_CERTIFICATE_FILENAME = "semantic.cert";
    public static final String DEFAULT_HUB_CERTIFICATE_FILENAME = "hub.cert";
    public static final String DEFAULT_LEVER_CERTIFICATE_FILENAME = "lever.cert";

    /*
     * Keys for user properties in certificates or for set operations.
     */
    public static final String KEY_EMAIL = "klab.user.email";
    public static final String KEY_USERNAME = "klab.username";
    public static final String KEY_AGREEMENT = "klab.agreement";
    public static final String KEY_NODENAME = "klab.nodename";
    public static final String KEY_LEVERNAME = "klab.levername";
    public static final String KEY_HUBNAME = "klab.hubname";
    public static final String KEY_URL = "klab.url";
    public static final String KEY_SIGNATURE = "klab.signature";
    public static final String KEY_PARTNER_HUB = "klab.partner.hub";
    public static final String KEY_PARTNER_NAME = "klab.partner.name";
    public static final String KEY_PARTNER_EMAIL = "klab.partner.email";
    public static final String KEY_CERTIFICATE = "klab.certificate";
    public static final String KEY_CERTIFICATE_TYPE = "klab.certificate.type";
    public static final String KEY_CERTIFICATE_LEVEL = "klab.certificate.level";

    /**
     * @return the name of the worldview associated with the engine. Can only be null in hub certificates.
     */
    String getWorldview();

    /**
     * The type of this certificate.
     *
     * @return the type
     */
    Type getType();

    /**
     * The level of this certificate. Will mandatorily return {@link Level#INSTITUTIONAL} unless in an engine
     * certificate or in a test certificate.
     *
     * @return the level
     */
    Level getLevel();

    /**
     * Validity may depend on expiration date and possibly upstream conditions after authentication, such as
     * having had a certificate invalidated by an administrator.
     * <p>
     * If this returns true, the certificate exists, is readable and properly encrypted, and is current.
     * <p>
     * If this returns false, {@link #getInvalidityCause} will contain the reason why.
     *
     * @return true if everything is OK.
     */
    boolean isValid();

    /**
     * Returns why {@link #isValid()} returned false. Undefined otherwise.
     *
     * @return a description of the cause for invalidity
     */
    String getInvalidityCause();

    /**
     * Return the named property on a valid certificate.
     *
     * @param property
     * @return the value of the property, or null.
     */
    String getProperty(String property);

}
