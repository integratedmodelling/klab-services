package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.data.GraphDatabase;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.io.ByteUnit;
import org.neo4j.ogm.config.Configuration;
import org.neo4j.ogm.session.Session;
import org.neo4j.ogm.session.SessionFactory;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;

/**
 * A local, embedded, persistent k.LAB-instrumented, configurable Neo4j database. To work with the f'ing
 * community edition it must be a singleton within the service, containing data for all contexts.
 */
public class GraphDatabaseNeo4jEmbedded implements GraphDatabase {

    private static final String DEFAULT_DATABASE_NAME = "klab";
    private DatabaseManagementService managementService;
    private GraphDatabaseService graphDb;
    private SessionFactory sessionFactory;
    private boolean online = true;
    // we use a session per context in normal usage, established through contextualize() which also sets up
    // the root nodes for the context
    private Session contextSession_ = null;
    private ContextScope scope_ = null;

    private GraphDatabaseNeo4jEmbedded(GraphDatabaseNeo4jEmbedded parent, ContextScope scope) {
        this.managementService = parent.managementService;
        this.graphDb = parent.graphDb;
        this.sessionFactory = parent.sessionFactory;
        if (this.online = parent.online) {
            this.contextSession_ = sessionFactory.openSession();
            this.scope_ = scope;
        }
    }

    /**
     * @param directory
     */
    public GraphDatabaseNeo4jEmbedded(Path directory) {

        /*
         * TODO tie the performance parameters to runtime configuration
         */
        try {
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

            Logging.INSTANCE.info("Embedded Neo4J database initialized");

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    managementService.shutdown();
                }
            });

        } catch (Throwable t) {
            Logging.INSTANCE.error("Error initializing Neo4J embedded database", t);
            this.online = false;
        }
    }

    @Override
    public GraphDatabase contextualize(ContextScope scope) {

        if (this.scope_ != null) {

            // idempotence
            if (this.scope_.getId().equals(scope.getId())) {
                return this;
            }

            throw new KlabIllegalStateException("cannot recontextualize a previously contextualized graph database");
        }

        contextSession_ = this.sessionFactory.openSession();

        var node = session().load(GraphMapping.ContextMapping.class, scope.getId());
        if (node == null) {
            node = GraphMapping.adapt(scope);
            try (var transaction = session().beginTransaction()) {
                session().save(node);
                transaction.commit();
            } catch (Throwable t) {
                return this;
            }
        }
        return new GraphDatabaseNeo4jEmbedded(this, scope);
    }

    @Override
    public boolean canDistribute() {
        // for now. We should do this through the DT's API, without depending on the backend db.
        return false;
    }

    @Override
    public GraphDatabase merge(URL remoteDigitalTwinURL) {
        return null;
    }

    @Override
    public boolean isOnline() {
        return this.online;
    }

    private Session session() {
        if (contextSession_ == null) {
            throw new KlabIllegalStateException("DB session is null: GraphDatabaseNeo4jEmbedded used without previous " +
                    "contextualization");
        }
        return contextSession_;
    }

    /**
     * @param observation any observation, including relationships
     * @param parent      may be null
     * @return
     */
    @Override
    public long add(Observation observation, Observation parent) {

        if (this.scope_ == null) {
            throw new KlabIllegalStateException("cannot use a graph database in its non-contextualized state");
        }

        return Observation.UNASSIGNED_ID;
    }

    @Override
    public long add(Actuator actuator, Actuator parent) {
        return Observation.UNASSIGNED_ID;
    }

    @Override
    public long add(Provenance.Node node, Provenance.Node parent) {
        return Observation.UNASSIGNED_ID;
    }

    @Override
    public void shutdown() {
        managementService.shutdown();
    }

}
