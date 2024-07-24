package org.integratedmodelling.common.services.client.runtime;

import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Codifies the main queries and provides a client to talk to the GraphQL endpoint.
 *
 */
public class GraphQLClient {

    private final HttpSyncGraphQlClient graphQlClient;

    public GraphQLClient(String baseUrl) {
        RestClient client = RestClient.builder()
                                     .baseUrl(baseUrl)
                                     .build();
        this.graphQlClient = HttpSyncGraphQlClient.builder(client).build();
    }

    public <T> T query(String query, Class<T> resultClass, Object... arguments) {
        return null;
    }

    public <T> List<T> queryList(String query, Class<T> resultClass, Object... arguments) {
        return null;
    }

}
