package org.integratedmodelling.common.authentication;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;

import java.util.Base64;
import java.util.Collection;
import java.util.Map;

/**
 * This is returned after authentication from a service using ExternalCredentials. Handles any refresh
 * automatically according to the scheme.
 * <p>
 * {@link #getAuthorization()} returns the token complete with token type ("Bearer", "Basic") and any prefix
 * passed to the constructor, ready to be used as an HTTP Authorization header.
 *
 * @author Ferd
 */
public class Authorization {

    private ExternalAuthenticationCredentials credentials;
    private String token;
    private long expiry = -1;
    private String prefix;
    private String tokenType;
    private String endpoint;

    /**
     * Create a new authorization. {@link #isOnline()} should be called after creation.
     *
     * @param credentials
     */
    public Authorization(ExternalAuthenticationCredentials credentials) {
        this(credentials, null);
    }

    /**
     * Create a new authorization. {@link #isOnline()} should be called after creation.
     *
     * @param credentials
     * @param endpoint
     */
    public Authorization(ExternalAuthenticationCredentials credentials, String endpoint) {
        if (credentials == null) {
            throw new KlabIllegalArgumentException("attempted authorization with null credentials");
        }

        this.credentials = credentials;

        if ("basic".equals(credentials.getScheme())) {
            this.tokenType = "Basic";
            byte[] encodedBytes = Base64.getEncoder()
                                        .encode((credentials.getCredentials().get(0) + ":" + credentials.getCredentials().get(1)).getBytes());
            this.token = new String(encodedBytes);
        } else if ("oidc".equals(credentials.getScheme())) {
            if (endpoint == null) {
                throw new KlabIllegalArgumentException("Attempted to start an OIDC authoritation workflow " +
                        "without an endpoint");
            }
            this.endpoint = endpoint;
            refreshToken();
        }
    }

    /**
     * Check if the last authentication attempt went well.
     *
     * @return
     */
    public boolean isOnline() {
        return token != null;
    }

    // TODO pass scope to client
    private Pair<String, String> parseProvider() {

        try (var client = Utils.Http.getClient(endpoint, /* TODO */ null)) {

            var providers = client.getCollection("/credentials/oidc", Map.class);
            var provider =
                    providers.stream().filter(prov -> prov.get("id").equals(credentials.getCredentials().get(3))).findFirst()
                             .orElseThrow(() -> new KlabAuthorizationException("No known provider '" + credentials.getCredentials().get(3) + "' at " + endpoint));

            if (provider.get("scopes") instanceof Collection<?> scopes) {
                var scope = String.join(" ", ((Collection<String>) scopes));
                return Pair.of(provider.get("issuer").toString(), scope);
            }

        } catch (Throwable t) {
            // TODO scope.error(t)
        }

        return null;
        //        HttpResponse<JsonNode> response = Unirest.get(endpoint + "/credentials/oidc").asJson();
        //        if (!response.isSuccess()) {
        //            throw new KlabAuthorizationException("Cannot access " + endpoint + " for OIDC
        //            authentication");
        //        }
        //        List<JSONObject> providers = response.getBody().getObject().getJSONArray("providers")
        //        .toList();
        //        JSONObject provider =
        //                providers.stream().filter(prov -> prov.getString("id").equals(credentials
        //                .getCredentials().get(3))).findFirst()
        //                                       .orElseThrow(() -> new KlabAuthorizationException("No
        //                                       known provider" +
        //                                               " '" + credentials.getCredentials().get(3) + "' at
        //                                               " + endpoint));
        //        List<String> scopes = provider.getJSONArray("scopes").toList();
        //        String scope = scopes.stream().collect(Collectors.joining(" "));
        //        return new Pair<>(provider.getString("issuer"), scope);
    }

    private String parseIssuer(String issuerUrl) {

        try (var client = Utils.Http.getClient(issuerUrl, /* TODO */ null)) {
            return client.get("/.well-known/openid-configuration", Map.class).get("token_endpoint").toString();
        } catch (Throwable t) {
            throw new KlabAuthorizationException("Cannot access " + issuerUrl + " for OIDC authentication");
        }
        //
        //            HttpResponse<JsonNode> response =
        //                Unirest.get(issuerUrl + "/.well-known/openid-configuration").asJson();
        //        if (!response.isSuccess()) {
        //            throw new KlabAuthorizationException("Cannot access " + issuerUrl + " for OIDC
        //            authentication");
        //        }
        //        return response.getBody().getObject().getString("token_endpoint");
    }

    /**
     * OIDC-style token
     */
    private void refreshToken() {
        /*
         * authenticate and get the first token. Credentials should contain:
         * 0. grant type 1. client ID 2. client secret 3. provider
         */
        Pair<String, String> issuerAndScope = parseProvider();
        String issuer = issuerAndScope.getFirst();
        String scope = issuerAndScope.getSecond();
        String tokenServiceUrl = parseIssuer(issuer);

        try (var client = Utils.Http.getClient(tokenServiceUrl, /* TODO */ null)) {
            // TODO see below
        } catch (Throwable t) {
            throw new KlabAuthorizationException("Cannot access " + tokenServiceUrl + " for OIDC authentication");
        }
        //
        //        MultipartBody query = Unirest.post(tokenServiceUrl)
        //                                     .field("grant_type", credentials.getCredentials().get(0))
        //                                     .field("client_id", credentials.getCredentials().get(1))
        //                                     .field("client_secret", credentials.getCredentials().get(2))
        //                                     .field("scope", scope);
        //
        //        if (this.token != null) {
        //            query = query.header("Authorization:",
        //                    (tokenType == null ? "" : (tokenType + " ")) + (prefix == null ? "" : prefix)
        //                    + token);
        //        }
        //
        //        this.expiry = System.currentTimeMillis();
        //        HttpResponse<JsonNode> result = query.asJson();
        //        if (result.isSuccess()) {
        //            JSONObject response = result.getBody().getObject();
        //            long duration = -1;
        //            if (response.has("token_type")) {
        //                this.tokenType = response.getString("token_type");
        //            }
        //            if (response.has("refresh_token")) {
        //                this.token = response.getString("refresh_token");
        //                this.expiry = this.expiry + response.getLong("");
        //                duration = response.has("refresh_token_expires_in") ? response.getLong(
        //                        "refresh_token_expires_in") : -1;
        //            } else {
        //                this.token = response.has("access_token") ? response.getString("access_token") :
        //                null;
        //            }
        //
        //            if (token != null && duration < 0) {
        //                duration = response.has("expires_in") ? response.getLong("expires_in") : 0;
        //            }
        //            this.prefix = "oidc/" + credentials.getCredentials().get(3) + "/";
        //            this.expiry += (duration * 1000l);
        //        }
    }

    /**
     * The raw authorization token with no auth method or prefix. May be null if {@link #isOnline()} returns
     * false.
     *
     * @return
     */
    public String getToken() {
        return this.token;
    }

    /**
     * Return the authorization token for the Authorization: header. Includes the auth method (e.g. Basic,
     * Bearer) and any prefix passed at construction.
     *
     * @return
     */
    public String getAuthorization() {

        if (token == null) {
            throw new KlabIOException("Authorization failed");
        }

        if ("oidc".equals(credentials.getScheme())) {
            if (this.expiry <= System.currentTimeMillis()) {
                refreshToken();
            }
            // repeat
            if (token == null) {
                throw new KlabIOException("Authorization failed");
            }
        }

        return (tokenType == null ? "" : (tokenType + " ")) + (prefix == null ? token : (prefix + token));
    }

    public static void main(String[] args) throws InterruptedException {
//        String endpoint = "https://openeo.vito.be/openeo/1.1.0";
//        ExternalAuthenticationCredentials crds = Authentication.INSTANCE.getCredentials(endpoint);
//        if (crds != null) {
//            Authorization authorization = new Authorization(crds, endpoint);
//            System.out.println(authorization.getAuthorization());
//            System.out.println("Sleeping 300 seconds: don't change the channel");
//            Thread.sleep(300000l);
//            System.out.println(authorization.getAuthorization());
//        } else {
//            throw new KlabAuthorizationException("no stored credentials for https://openeo.vito.be");
//        }
    }

}
