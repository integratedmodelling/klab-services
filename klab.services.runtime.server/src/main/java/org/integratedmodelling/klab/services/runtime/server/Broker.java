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

    public static void main(String args[]) {
        Broker broker = new Broker();
        try {
            broker.start();
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