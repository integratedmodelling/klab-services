package org.integratedmodelling.common.authentication;

import org.integratedmodelling.klab.api.authentication.KlabCertificate;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class KlabCertificateImpl implements KlabCertificate {

    // just for info
    public static final String KEY_EXPIRATION = "klab.validuntil";

    // TODO just store a URL and handle uniformly
    private File file = null;
    private String resource = null;
    private Properties properties = null;
    private String cause = null;
    private Instant expiry;
    private String worldview = DEFAULT_WORLDVIEW;
    private Map<String, Set<String>> worldview_repositories = null;
    private Type type = Type.ENGINE;
    private Level level = Level.USER;

    /**
     * Property key for username
     */
    public static final String USER_KEY = "user";
    /**
     * Property key for primary node.
     */
    public static final String PRIMARY_NODE_KEY = "primary.server";

    static final String DEFAULT_WORLDVIEW = "im";

    // static final String DEFAULT_WORLDVIEW_REPOSITORIES =
    // "https://bitbucket.org/integratedmodelling/im.git,https://bitbucket.org/integratedmodelling/im.aries.git";

    /**
     * Create a new certificate from a file. Check isValid() on the resulting certificate.
     *
     * @param file the certificate file
     * @return a certificate read from the passed file
     */
    public static KlabCertificate createFromFile(File file) {
        if (file == null) {
            return createDefault();
        }
        return new KlabCertificateImpl(file);
    }

    public static KlabCertificate createFromClasspath(String resource) {
        return new KlabCertificateImpl(resource);
    }

    public static KlabCertificate createFromProperties(Properties props) {
        return new KlabCertificateImpl(props);
    }

    /**
     * Create a new certificate from a string. Check isValid() on the resulting certificate in this block
     * because the file is deleted after.
     *
     * @param string is the certificate as a complete string
     * @return a certificate read from the passed string
     */
    public static KlabCertificate createFromString(String string) {
        KlabCertificate cert;
        try {
            File temp = File.createTempFile(Utils.Names.newName(), ".cert");
            BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
            bw.write(string);
            bw.close();
            cert = KlabCertificateImpl.createFromFile(temp);
            cert.isValid();
            temp.delete();
        } catch (IOException e) {
            throw new KlabIllegalStateException("certificate string could not be turned into a file");
        }
        return cert;

    }

    /**
     * Get the file from its configured locations and open it. If there is no certificate and the
     * configuration allows anonymous users, return an anonymous certificate. Check {@link #isValid()} after
     * construction.
     *
     * @return the default certificate
     */
    public static KlabCertificate createDefault() {

        File certfile = getCertificateFile();
        if (certfile.exists()) {
            return new KlabCertificateImpl(certfile);
        }
        if (Configuration.INSTANCE.allowAnonymousUsage()) {
            return new AnonymousEngineCertificate();
        }
        throw new KlabIllegalStateException("certificate file not found and anonymous usage not allowed");
    }

    /**
     * Get the file for the certificate according to configuration. Does not check that the file exists.
     *
     * @return the configured certificate file location
     */
    public static File getCertificateFile() {
        if (System.getProperty(Configuration.CERTFILE_PROPERTY) != null) {
            return new File(System.getProperty(Configuration.CERTFILE_PROPERTY));
        }
        return new File(Configuration.INSTANCE.getDataPath() + File.separator + DEFAULT_ENGINE_CERTIFICATE_FILENAME);
    }

    /**
     * Create from a certificate file, downloading the public keyring from the net if not already installed.
     * Check {@link #isValid()} after construction.
     *
     * @param file
     */
    private KlabCertificateImpl(File file) {
        this.file = file;
        // for (String w : StringUtil
        // .splitOnCommas(KlabCertificate.DEFAULT_WORLDVIEW_REPOSITORIES)) {
        // worldview_repositories.put(w, new HashSet<>());
        // }
    }

    private KlabCertificateImpl(String resource) {
        this.resource = resource;
        // for (String w : StringUtil
        // .splitOnCommas(KlabCertificate.DEFAULT_WORLDVIEW_REPOSITORIES)) {
        // worldview_repositories.put(w, new HashSet<>());
        // }
    }

    public KlabCertificateImpl(Properties props) {
        this.properties = props;
        // for (String w : StringUtil
        // .splitOnCommas(KlabCertificate.DEFAULT_WORLDVIEW_REPOSITORIES)) {
        // worldview_repositories.put(w, new HashSet<>());
        // }
    }

    public boolean isValid() {

        if (cause != null) {
            return false;
        }

        if (properties == null) {

            if (file != null && (!file.exists() || !file.isFile() || !file.canRead())) {
                cause = "certificate file cannot be read";
                return false;
            }

            properties = new Properties();
            try (InputStream inp = (resource == null
                                    ? new FileInputStream(file)
                                    : getClass().getClassLoader().getResource(resource).openStream())) {
                properties.load(inp);
            } catch (IOException e) {
                cause = e.getMessage();
                return false;
            }

            this.type = Type.valueOf(properties.getProperty(KEY_CERTIFICATE_TYPE));
            this.level = Level.valueOf(properties.getProperty(KEY_CERTIFICATE_LEVEL, Level.USER.name()));

            if (this.type == Type.NODE || this.type == Type.HUB) {
                if (properties.getProperty(KEY_URL) == null) {
                    cause = "node or hub certificate must define the public URL for the service (klab.url)";
                    return false;
                }
            }

            return true;
        }

        return true;
    }

    public Instant getExpiryDate() {
        return expiry;
    }

    /**
     * If {@link #isValid()} returns false, return the reason why.
     *
     * @return cause of invalidity
     */
    @Override
    public String getInvalidityCause() {
        return cause;
    }

    /**
     * Return the file we've been read from.
     *
     * @return the certificate file
     */
    public File getFile() {
        return file;
    }

    /**
     * Descriptive string with user name, email, and expiry.
     *
     * @return printable description.
     */
    public String getUserDescription() {
        if (properties != null && properties.containsKey(KEY_USERNAME)) {
            return properties.getProperty(KEY_USERNAME) + " (" + properties.getProperty(KEY_USERNAME) + ")," +
                    " valid until "
                    + DateTimeFormatter.ofPattern("MM/dd/YYYY").format(expiry);
        }
        return "anonymous user";
    }

    @Override
    public String getWorldview() {
        return worldview;
    }

//    public Map<String, Set<String>> getWorldviewRepositories(String worldviewIds) {
//        if (worldview_repositories == null) {
//            worldview_repositories = new HashMap<>();
//            UserIdentity user = Authentication.INSTANCE.getAuthenticatedIdentity(IUserIdentity.class);
//            this.worldview = Authentication.INSTANCE.getWorldview(user);
//            for (String repository : Authentication.INSTANCE.getWorldviewRepositories(user)) {
//                worldview_repositories.put(repository, new HashSet<>());
//            }
//        }
//        return worldview_repositories;
//    }

    @Override
    public String getProperty(String property) {
        return properties == null ? null : properties.getProperty(property);
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Level getLevel() {
        return level;
    }
}
