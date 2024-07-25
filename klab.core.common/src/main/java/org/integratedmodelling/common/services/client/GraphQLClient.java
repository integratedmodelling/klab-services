package org.integratedmodelling.common.services.client;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Codifies the main queries and provides a client to talk to the GraphQL endpoint.
 */
public class GraphQLClient {

    private final HttpSyncGraphQlClient graphQlClient;

    public GraphQLClient(String baseUrl) {
        RestClient client = RestClient.builder()
                                      .baseUrl(baseUrl)
                                      .build();
        this.graphQlClient = HttpSyncGraphQlClient.builder(client)
                                                  .build();
    }

    /**
     *
     * @param query
     * @param resultClass
     * @param scope
     * @param arguments
     * @return
     * @param <T>
     */
    public <T> T query(String query, String target, Class<T> resultClass, Scope scope, Object... arguments) {
        var scopeToken = scope instanceof SessionScope sessionScope ? sessionScope.getId() : null;
        var client = this.graphQlClient;
        if (scopeToken != null) {
            client = client.mutate().header(ServicesAPI.SCOPE_HEADER,  scopeToken).build();
        }
        var request = client.document(query);
        if (arguments != null && arguments.length > 0) {
            request = request.variables(Utils.Maps.makeKeyMap(arguments));
        }
        return request.retrieveSync(target).toEntity(resultClass);
    }

    public <T> List<T> queryList(String query, Class<T> resultClass, Scope scope, Object... arguments) {
        return null;
    }

    /**
     *
     * @param query
     * @param resultClass
     * @param scope
     * @param arguments
     * @return
     * @param <T>
     */
    public <T> T mutate(String query, Class<T> resultClass, Scope scope, Object... arguments) {
        return null;
    }

    public <T> List<T> mutateList(String query, Class<T> resultClass, Scope scope, Object... arguments) {
        return null;
    }

}
