package org.integratedmodelling.common.authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExternalAuthenticationCredentials {

    public final static String BASIC = "basic";
    public final static String OIDC = "oidc";
    public final static String S3 = "s3";
    public final static String KEY = "key";
    public final static String SSH = "ssh";

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

    /**
     * Credentials, depending on scheme
     *
     * for basic: username and password
     * for oidc: grant type, client ID, client secret, provider
     * for s3: endpoint URL, access key, secret key
     * for key: a single key
     */
    private List<String> credentials = new ArrayList<>();

    /**
     * one of basic, oidc, s3, key...
     */
    private String scheme = "basic";

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

}
