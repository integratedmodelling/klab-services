package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.connectors.HttpConnector;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.io.ByteUnit;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * A local, embedded, persistent k.LAB-instrumented, configurable Neo4j database. To work with the f'ing
 * community edition the database must be a singleton within the service, containing data for all contexts.
 */
public class KnowledgeGraphNeo4JEmbedded  extends KnowledgeGraphNeo4j implements KnowledgeGraph {

    private static final String DEFAULT_DATABASE_NAME = "klab";
    private DatabaseManagementService managementService;
    private GraphDatabaseService graphDb;
//    private SessionFactory sessionFactory;
    private boolean online = true;
    // we use a session per context in normal usage, established through contextualize() which also sets up
    // the root nodes for the context
//    private Session contextSession_ = null;
//    private GraphMapping.ContextMapping rootNode;
//    private Driver driver;

    private KnowledgeGraphNeo4JEmbedded(KnowledgeGraphNeo4JEmbedded parent, ContextScope scope) {
        this.managementService = parent.managementService;
        this.graphDb = parent.graphDb;
//        this.sessionFactory = parent.sessionFactory;
        this.online = parent.online;
//        if (this.online) {
//            this.contextSession_ = sessionFactory.openSession();
            this.scope = scope;
//        }
        this.driver = parent.driver;
    }

    /**
     * @param directory
     */
    public KnowledgeGraphNeo4JEmbedded(Path directory) {

        /*
         * TODO tie the performance parameters to runtime configuration
         */
        try {
            this.managementService = new DatabaseManagementServiceBuilder(directory)
                    .setConfig(GraphDatabaseSettings.initial_default_database, DEFAULT_DATABASE_NAME)
                    .setConfig(GraphDatabaseSettings.pagecache_memory, ByteUnit.mebiBytes(512))
                    .setConfig(GraphDatabaseSettings.transaction_timeout, Duration.ofSeconds(60))
                    .setConfig(GraphDatabaseSettings.preallocate_logical_logs, true)
                    .setConfig(BoltConnector.enabled, true) // for the driver
                    .setConfig(HttpConnector.enabled, true) // for debugging (?)
                    .build();

            this.graphDb = managementService.database(DEFAULT_DATABASE_NAME);

//            this.sessionFactory = new SessionFactory(
//                    new Configuration.Builder()
//                                .encryptionLevel("DISABLED")
//                                .uri("bolt://localhost:7687").build(),
//                    this.getClass().getPackageName());

            // TODO this could just reimplement query() to use the DB directly and not expose the
            //  connectors, losing debugging access outside the application
            this.driver = GraphDatabase.driver("bolt://localhost:7687");


            configureDatabase();

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

    private void configureDatabase() {

        // TODO all the needed indices

//        IndexDefinition usernamesIndex;
//        try ( Transaction tx = graphDb.beginTx() )
//        {
//            Schema schema = tx.schema();
//            usernamesIndex = schema.indexFor(Label.label( "User" ) )
//                                   .on( "username" )
//                                   .withName( "usernames" )
//                                   .create();
//            tx.commit();
//        }
    }

    @Override
    public KnowledgeGraph contextualize(ContextScope scope) {

        if (this.scope != null) {

            // idempotence
            if (this.scope.getId().equals(scope.getId())) {
                return this;
            }

            throw new KlabIllegalStateException("cannot recontextualize a previously contextualized graph " +
                    "database");
        }

//        contextSession_ = this.sessionFactory.openSession();
//
//        var node = session().load(GraphMapping.ContextMapping.class, scope.getId());
//        if (node == null) {
//            node = GraphMapping.adapt(scope);
//            try (var transaction = session().beginTransaction()) {
//                session().save(node);
//                transaction.commit();
//            } catch (Throwable t) {
//                return this;
//            }
//        }

        var ret = new KnowledgeGraphNeo4JEmbedded(this, scope);
//        ret.rootNode = node;

        ret.initializeContext();

        return ret;
    }

    @Override
    public <T extends RuntimeAsset> T get(long id, Class<T> resultClass) {
        return null;
    }

    @Override
    public <T extends RuntimeAsset> List<T> get(RuntimeAsset source, DigitalTwin.Relationship linkType, Class<T> resultClass) {
        return List.of();
    }

    @Override
    public KnowledgeGraph merge(URL remoteDigitalTwinURL) {
        return null;
    }

    @Override
    public boolean isOnline() {
        return this.online;
    }

//    private Session session() {
//        if (contextSession_ == null) {
//            throw new KlabIllegalStateException("DB session is null: KnowledgeGraphNeo4JEmbedded used " +
//                    "without previous " +
//                    "contextualization");
//        }
//        return contextSession_;
//    }

//    /**
//     * @param observation any observation, including relationships
//     * @return
//     */
//    @Override
//    public long add(Observation observation, Object relationshipSource, DigitalTwin.Relationship connection
//            , Metadata relationshipMetadata) {
//
//
//        if (this.scope == null) {
//            throw new KlabIllegalStateException("cannot use a graph database in its non-contextualized " +
//                    "state");
//        }
//
//        var observationMapping = GraphMapping.adapt(observation);
//        try (var transaction = session().beginTransaction()) {
//            session().save(observationMapping);
//            var link = createLink(observationMapping, relationshipSource, connection);
//            if (link != null) {
//                session().save(link, 0);
//            }
//            transaction.commit();
//            return observationMapping.id;
//        } catch (Throwable t) {
//            scope.error(t);
//        }
//
//        return Observation.UNASSIGNED_ID;
//    }
//
//    private GraphMapping.Link createLink(GraphMapping.ObservationMapping source, Object target,
//                                         DigitalTwin.Relationship connection) {
//        GraphMapping.Link ret = null;
//        DigitalTwin.Relationship defaultConnection = null;
//        if (target == null) {
//            var link = new GraphMapping.RootObservationLink();
//            link.context = rootNode;
//            link.observation = source;
//            defaultConnection = DigitalTwin.Relationship.RootObservation;
//            ret = link;
//        } else if (target instanceof Observation observation) {
//            var link = new GraphMapping.ObservationLink();
//            link.observation = source;
//            link.context = session().load(GraphMapping.ObservationMapping.class, observation.getId());
//            defaultConnection = DigitalTwin.Relationship.Parent;
//            ret = link;
//        }
//
//        if (ret != null) {
//            ret.timestamp = System.currentTimeMillis();
//            ret.type = connection == null ? defaultConnection : connection;
//        }
//
//        return ret;
//    }

    @Override
    public void shutdown() {
        managementService.shutdown();
    }

//    @Override
//    protected Driver driver() {
//        return driver;
//    }
}
