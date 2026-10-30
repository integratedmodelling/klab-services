package org.integratedmodelling.klab.api.authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A named credential set. Secret material is accepted on writes and resolved only inside a service. */
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
    public record CredentialInfo(String host, String description, String schema, String key,
                                 Map<String, String> parameters, List<String> configuredFields,
                                 ResourcePrivileges privileges) {
        public CredentialInfo(String host, String description, String schema, String key) {
            this(host, description, schema, key, Map.of(), List.of(), ResourcePrivileges.empty());
        }
    }

    /**
     * "Legend" for parameter names in the different auth methods
     */
    public static final Map<String, String[]> parameterKeys;

    static {
        parameterKeys = new HashMap<>();
        parameterKeys.put(BASIC, new String[]{"username", "password"});
        parameterKeys.put(OIDC, new String[]{"grant_type", "client_id", "client_secret", "token_endpoint"});
        parameterKeys.put(S3, new String[]{"accessKey", "secretKey", "sessionToken"});
        parameterKeys.put(KEY, new String[]{"key"});
        parameterKeys.put(SSH, new String[]{"username", "privateKey", "passphrase"});
        parameterKeys.put("token", new String[]{"username", "password"});
    }

    private ResourcePrivileges privileges;


    private String id;
    private String name;
    private String endpoint;
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    private Map<String, String> parameters = new HashMap<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }

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
        return name == null ? id : name;
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
        return new CredentialInfo(host, description(), scheme, id);
    }

}
