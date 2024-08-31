package org.integratedmodelling.common.services.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpSyncGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Codifies the main queries and provides a client to talk to the GraphQL endpoint.
 */
public class GraphQLClient {

    private final HttpSyncGraphQlClient graphQlClient;
    private ObjectMapper objectMapper = new ObjectMapper();
    String serverKey = null;

    public GraphQLClient(String baseUrl, String serverKey) {
        this.serverKey = serverKey;
        RestClient client = RestClient.builder()
                                      .baseUrl(baseUrl)
                                      .build();
        this.graphQlClient = HttpSyncGraphQlClient.builder(client)
                                                  .build();
    }

    private GraphQlClient.RequestSpec request(String query, Scope scope, Object... arguments) {

        var scopeToken = scope instanceof SessionScope sessionScope ? sessionScope.getId() : null;
        var client = this.graphQlClient;
        var authorization = scope.getIdentity().getId();
        var mutation = client.mutate();

        if (scopeToken != null) {
            mutation = mutation.header(ServicesAPI.SCOPE_HEADER, scopeToken);
        }
        if (serverKey != null) {
            mutation = mutation.header(ServicesAPI.SERVER_KEY_HEADER, serverKey);
        }
        if (authorization != null) {
            mutation = mutation.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        client = mutation.build();

        if (arguments != null) {
            for (int i = 0; i < arguments.length; i++) {
                String key = arguments[i].toString();
                String val = null;
                Object value = arguments[++i];
                if (value == null) {
                    // if this is an argument, it will be left as is and generate a GraphQL server error.
                    continue;
                }
                query = query.replace("$" + key, convertValue(value));
            }
        }
        return client.document(query);
    }

    /**
     * There must be a better way, but this will work.
     *
     * @param o
     * @return
     */
    private String convertValue(Object o) {
        return switch (o) {
            case Boolean b -> b ? "true" : "false";
            case Number n -> n.toString();
            case String s -> "\"" + s + "\"";
            case Collection<?> collection -> {
                StringBuilder buf = new StringBuilder(512);
                buf.append("[");
                for (var obj : collection) {
                    if (buf.length() > 1) {
                        buf.append(", ");
                    }
                    buf.append(convertValue(obj));
                }
                yield buf.append("]").toString();
            }
            default -> {
                var map = objectMapper.convertValue(o, Map.class);
                StringBuilder buf = new StringBuilder(512);
                buf.append("{");
                for (var key : map.keySet()) {
                    var val = map.get(key);
                    if (val != null) {
                        if (!buf.isEmpty()) {
                            buf.append(" ");
                        }
                        buf.append(key.toString()).append(": ").append(convertValue(val));
                    }
                }
                yield buf.append("}").toString();
            }
        };
    }

    /**
     * @param query
     * @param resultClass
     * @param scope
     * @param arguments
     * @param <T>
     * @return
     */
    public <T> T query(String query, String target, Class<T> resultClass, Scope scope, Object... arguments) {
        try {
            return request(query, scope, arguments).retrieveSync(target).toEntity(resultClass);
        } catch (Throwable t) {
            scope.error(t);
        }
        return null;
    }

    public <T> List<T> queryList(String query, String target, Class<T> resultClass, Scope scope,
                                 Object... arguments) {
        try {
            return request(query, scope, arguments).retrieveSync(target).toEntityList(resultClass);
        } catch (Throwable t) {
            scope.error(t);
        }
        return null;
    }

}
