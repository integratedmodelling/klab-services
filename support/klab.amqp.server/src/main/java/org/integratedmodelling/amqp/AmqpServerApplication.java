package org.integratedmodelling.amqp;

import com.sun.net.httpserver.HttpServer;
import org.apache.qpid.server.SystemLauncher;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.configuration.Configuration;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Main application class for the AMQP server.
 * Starts an embedded Qpid broker and provides a shutdown URL.
 */
public class AmqpServerApplication {

    private static final String EMBEDDED_BROKER_CONFIGURATION = "klab-broker-config.json";
    private static final int SHUTDOWN_PORT = 20938; 

    private final SystemLauncher systemLauncher = new SystemLauncher();
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        new AmqpServerApplication().run();
    }

    public void run() {
        boolean scoped = System.getProperty(ScopedBrokerProfile.PROPERTY) != null;
        try {
            startBroker();
            if (scoped) {
                // Scoped mode exposes no unauthenticated HTTP management/shutdown listener.
                Runtime.getRuntime().addShutdownHook(new Thread(this::stopBroker, "scoped-broker-shutdown"));
                Logging.INSTANCE.info("Scoped loopback broker running; shutdown is controlled by its owning process");
            } else {
                startShutdownService();
                Logging.INSTANCE.info("AMQP Server is running. Use http://localhost:" + SHUTDOWN_PORT + "/shutdown to stop.");
            }
            
            // Wait until shutdown is triggered
            shutdownLatch.await();
            
            stopBroker();
            Logging.INSTANCE.info("AMQP Server stopped.");
            System.exit(0);
        } catch (Exception e) {
            if (scoped) {
                try { stopBroker(); } catch (Exception cleanup) {
                    Logging.INSTANCE.error("Scoped broker shutdown was not confirmed");
                }
            }
            Logging.INSTANCE.error(scoped ? "Scoped broker startup failed; profile content is not logged"
                    : "Error running AMQP Server: " + e.getMessage());
            System.exit(1);
        }
    }

    private void startBroker() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        String profile = System.getProperty(ScopedBrokerProfile.PROPERTY);
        String initialLocation;
        if (profile != null) {
            if (System.getProperty("QPID_WORK") == null || System.getProperty("QPID_WORK").isBlank()) {
                throw new IllegalArgumentException("Scoped broker requires an explicit private QPID_WORK directory");
            }
            initialLocation = ScopedBrokerProfile.load(Path.of(profile));
        } else {
            URL initialConfig = getClass().getClassLoader().getResource(EMBEDDED_BROKER_CONFIGURATION);
            if (initialConfig == null) {
                throw new IllegalStateException("Configuration file " + EMBEDDED_BROKER_CONFIGURATION + " not found");
            }
            initialLocation = initialConfig.toExternalForm();
        }
        
        attributes.put("type", "Memory");
        attributes.put("startupLoggedToSystemOut", true);
        attributes.put("initialConfigurationLocation", initialLocation);
        
        if (System.getProperty("QPID_WORK") == null) {
            System.setProperty("QPID_WORK", Configuration.INSTANCE.getDataPath("broker").toString());
        }
        
        systemLauncher.startup(attributes);
    }

    private void stopBroker() {
        systemLauncher.shutdown();
    }

    private void startShutdownService() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(SHUTDOWN_PORT), 0);
        server.createContext("/shutdown", exchange -> {
            String response = "Shutting down AMQP server...";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            Logging.INSTANCE.info("Shutdown requested via URL");
            shutdownLatch.countDown();
            
            // Give some time for the response to be sent before stopping the HTTP server
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                server.stop(0);
            }).start();
        });
        server.setExecutor(null);
        server.start();
    }
}
