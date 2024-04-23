package org.integratedmodelling.klab.api.authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Credential holder. Still basic and tentative, the key should be host+scheme as there should only be one per
 * such pair in a single-user environment. At the moment it does not support multiple rights for the same
 * hosts, but it should. It could also store an optional serviceId for client environments and a
 * {@link org.integratedmodelling.klab.api.authentication.ResourcePrivileges} object for access.
 *
 * In fact this could have a URN like credentials:/xxxx and use the same resource rights permissions as
 * the rest of the k.LAB assets.
 */
public class ExternalAuthenticationCredentials {

    public final static String BASIC = "basic";
    public final static String OIDC = "oidc";
    public final static String S3 = "s3";
    public final static String KEY = "key";
    public final static String SSH = "ssh";


    /**
     * Credential information for display and choice by users. Each credentials object can produce the correspondent info.
     *
     * @param host
     * @param description
     * @param schema
     * @param key the unique key to the relevant credentials.
     */
    public record CredentialInfo(String host, String description, String schema, String key) {
    }

    /**
     * "Legend" for parameter names in the different auth methods
     */
    public static final Map<String, String[]> parameterKeys;

    static {
        parameterKeys = new HashMap<>();
        parameterKeys.put(BASIC, new String[]{"username", "password"});
        parameterKeys.put(OIDC, new String[]{"grant_type", "client_id", "client_secrets", "provider_id"});
        parameterKeys.put(S3, new String[]{"accessKey", "secretKey"});
        parameterKeys.put(KEY, new String[]{"key"});
        parameterKeys.put(SSH, new String[]{"passkey"});
    }

    private ResourcePrivileges privileges = ResourcePrivileges.empty();


    private String id;

    /**
     * Credentials, depending on scheme
     * <p>
     * for basic: username and password for oidc: grant type, client ID, client secret, provider for s3:
     * endpoint URL, access key, secret key for key: a single key
     */
    private List<String> credentials = new ArrayList<>();

    /**
     * one of basic, oidc, s3, key...
     */
    private String scheme = BASIC;

    public List<String> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<String> credentials) {
        this.credentials = credentials;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String description() {
        // TODO return a shareable descriptive string built according to scheme, with no sensitive details
        //  but informative enough to
        //  select among a set.
        return credentials.getFirst();
    }

    /**
     * The ID must be non-null and unique for reference in the API.
     *
     * @return
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ResourcePrivileges getPrivileges() {
        return privileges;
    }

    public void setPrivileges(ResourcePrivileges privileges) {
        this.privileges = privileges;
    }

    public CredentialInfo info(String host) {
        return new CredentialInfo(host, /* TODO */ credentials.getFirst(), scheme, id);
    }

}
