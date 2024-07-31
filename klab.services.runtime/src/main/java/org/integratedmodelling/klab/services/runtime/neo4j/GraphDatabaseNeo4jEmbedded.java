package org.integratedmodelling.klab.services.runtime.neo4j;

import org.eclipse.collections.api.factory.set.primitive.ImmutableIntSetFactory;
import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.io.ByteUnit;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.SessionFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;

/**
 * A local, embedded, persistent k.LAB-instrumented, configurable Neo4j database. To work with the f'ing
 * community edition it must be a singleton within the service, containing data for all contexts.
 */
public class GraphDatabaseNeo4jEmbedded implements GraphDatabase {

    private static final String DEFAULT_DATABASE_NAME = "klab";
    private final DatabaseManagementService managementService;
    private final GraphDatabaseService graphDb;
    private final SessionFactory sessionFactory;


    /**
     * @param directory
     */
    public GraphDatabaseNeo4jEmbedded(Path directory) {

        /*
         * TODO tie these parameters to runtime configuration
         */
        this.managementService = new DatabaseManagementServiceBuilder(directory)
                .setConfig(GraphDatabaseSettings.initial_default_database, DEFAULT_DATABASE_NAME)
                .setConfig(GraphDatabaseSettings.pagecache_memory, ByteUnit.mebiBytes(512))
                .setConfig(GraphDatabaseSettings.transaction_timeout, Duration.ofSeconds(60))
                .setConfig(GraphDatabaseSettings.preallocate_logical_logs, true)
                .setConfig(BoltConnector.enabled, true)
                .setConfig(HttpConnector.enabled, true)
                .build();

        this.graphDb = managementService.database(DEFAULT_DATABASE_NAME);
        this.sessionFactory = new SessionFactory(new Configuration.Builder()
                .uri("neo4j://localhost:7687").build(), this.getClass().getPackageName());

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                managementService.shutdown();
            }
        });
    }

    @Override
    public boolean canDistribute() {
        return false;
    }

    @Override
    public GraphDatabase merge(URL remoteDigitalTwinURL) {
        return null;
    }

    public long recordObservation(Observation observation) {
        return 0L;
    }

}
