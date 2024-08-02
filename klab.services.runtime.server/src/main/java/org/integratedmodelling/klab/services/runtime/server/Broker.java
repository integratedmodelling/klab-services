package org.integratedmodelling.klab.services.runtime.server;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.SystemLauncher;

/**
 * Local QPid broker for local use when there is no broker URL in configuration and we need
 * messaging.
 */
public class Broker {

    /**
     * FIXME this starts the broker with admin/admin password. Subst with an auth proxy that uses the
     *  context ID.
     */
    private static final String INITIAL_CONFIGURATION = "local-broker-config.json";

    // TODO creating a queue. Should have queues with context-id topics, or a queue per context
//    ConnectionFactory factory;
//
//            ....
//                    factory.setHost("localhost");
//            factory.setPort(qpid_server_port);
//            try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
//        String queue = "queue-x";
//        channel.queueDeclare(queue, true, false, false, null);
//        //channel.queueBind(queue, "exchange-x" , "routing-key-x");
//
//    } catch (Exception e) {
//        e.printStackTrace();
//    }


    // with REST it would be:
//    private fun recreateQueue(queueName: String) {
//        val client = WebClient.create("http://localhost:${EmbeddedAMQPBroker.httpPort}");
//        try {
//            client.method(HttpMethod.DELETE)
//                  .uri("/api/latest/queue/default/$queueName")
//                  .retrieve()
//                  .toBodilessEntity()
//                  .block()
//                    .statusCode
//        } catch (e: WebClientResponseException) {
//            if (e.statusCode != HttpStatus.NOT_FOUND) { // queue might not yet exist so 404 is acceptable
//                throw e
//            }
//        }
//
//        client.method(HttpMethod.PUT)
//              .uri("/api/latest/queue/default/default/$queueName")
//              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//              .body(BodyInserters.fromValue(mapOf("name" to queueName, "type" to "standard")))
//              .retrieve()
//              .toBodilessEntity()
//              .block()
//                .statusCode
//    }


//  REST API docs at  https://qpid.apache.org/releases/qpid-broker-j-9.2.0/book/Java-Broker-Management-Channel-REST-API.html#d0e2147

    public static void main(String args[]) {
        Broker broker = new Broker();
        try {
            broker.start();
            while (true) {
                Thread.sleep(1000);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void start() throws Exception {
        final SystemLauncher systemLauncher = new SystemLauncher();
        try {
            systemLauncher.startup(createSystemConfig());
            // performMessagingOperations();
        } finally {
            systemLauncher.shutdown();
        }
    }

    private Map<String, Object> createSystemConfig() {
        Map<String, Object> attributes = new HashMap<>();
        URL initialConfig = Broker.class.getClassLoader().getResource(INITIAL_CONFIGURATION);
        attributes.put("type", "Memory");
        attributes.put("initialConfigurationLocation", initialConfig.toExternalForm());
        attributes.put("startupLoggedToSystemOut", true);
        return attributes;
    }
}