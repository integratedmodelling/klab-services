package org.integratedmodelling.klab.services.scopes.messaging;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.FileBasedLock;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An embedded broker that only starts once and finds the service after it's started. Checking for an active
 * service is synchronized with a lock file so that different JVMs can use this without interfering with each
 * other and ensure there is only one service URI for all.
 */
public class EmbeddedBroker {

    private static final String EMBEDDED_BROKER_CONFIGURATION = "klab-broker-config.json";
    private static final int EMBEDDED_BROKER_PORT = 20937;

    private ConnectionFactory connectionFactory;
    private Connection connection;
    private SystemLauncher systemLauncher;
    private URI uri;
    private boolean online;

    public EmbeddedBroker() {

        try (FileBasedLock lock = FileBasedLock.getLock(Configuration.INSTANCE.getFile(".broker.lock"))) {

            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                try {
                    this.uri = new URI("amqp://127.0.0.1:" + EMBEDDED_BROKER_PORT);

                    // wait until the file lock is released
                    // establish file lock
                    this.connectionFactory = new ConnectionFactory();
                    try {
                        this.connectionFactory.setUri(this.uri);
                        this.connection = this.connectionFactory.newConnection();
                        this.online = this.connection.isOpen();
                    } catch (Exception e) {
                    }

                    if (!this.online) {
                        this.online = startLocalBroker();
                    }

                } catch (URISyntaxException e) {
                    // just leave this offline
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (online) {
            Logging.INSTANCE.info("Embedded broker online as a " + (systemLauncher == null ? "slave" :
                                                                  "native") + " instance");
        } else {
            Logging.INSTANCE.warn("Embedded broker failed to initialize");
        }
    }

    private boolean startLocalBroker() {

        Map<String, Object> attributes = new HashMap<>();
        URL initialConfig = this.getClass().getClassLoader().getResource(EMBEDDED_BROKER_CONFIGURATION);
        assert initialConfig != null;
        attributes.put("type", "Memory");
        attributes.put("startupLoggedToSystemOut", true);
        attributes.put("initialConfigurationLocation", initialConfig.toExternalForm());
        try {
            this.systemLauncher = new SystemLauncher();
            if (System.getProperty("QPID_WORK") == null) {
                // this works; setting qpid.work_dir in the attributes does not.
                System.setProperty("QPID_WORK", Configuration.INSTANCE.getDataPath("broker").toString());
            }
            systemLauncher.startup(attributes);
            Logging.INSTANCE.info("Embedded broker available for local connections on " + this.uri);
            return true;
        } catch (Exception e) {
            Logging.INSTANCE.error("Error initializing embedded broker: " + e.getMessage());
        }
        return false;
    }

    /**
     * False here is only returned in case of error, because creating the instance already attempts connection
     * and starts the service if needed.
     *
     * @return
     */
    public boolean isOnline() {
        return false;
    }

    /**
     * Get a connection to the service. Must check isOnline first.
     *
     * @return
     */
    public Connection getConnection() {
        return this.connection;
    }

    public URI getURI() {
        return uri;
    }
}