package org.integratedmodelling.klab.services.runtime.neo4j;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

/**
 * A local, embedded, persistent k.LAB-instrumented, configurable Neo4j database. To work with the
 * f'ing community edition the database must be a singleton within the service, containing data for
 * all contexts.
 */
public class KnowledgeGraphNeo4JEmbedded extends KnowledgeGraphNeo4j implements KnowledgeGraph {

  private static final String DEFAULT_DATABASE_NAME = "klab";
  private DatabaseManagementService managementService;
  private GraphDatabaseService graphDb;
  private boolean online = true;

  private KnowledgeGraphNeo4JEmbedded(KnowledgeGraphNeo4JEmbedded parent, ContextScope scope) {
    this.managementService = parent.managementService;
    this.graphDb = parent.graphDb;
    this.online = parent.online;
    this.scope = scope;
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
      this.managementService =
          new DatabaseManagementServiceBuilder(directory)
              .setConfig(GraphDatabaseSettings.initial_default_database, DEFAULT_DATABASE_NAME)
              .setConfig(GraphDatabaseSettings.pagecache_memory, ByteUnit.mebiBytes(512))
              .setConfig(GraphDatabaseSettings.transaction_timeout, Duration.ofSeconds(60))
              .setConfig(GraphDatabaseSettings.preallocate_logical_logs, true)
              .setConfig(BoltConnector.enabled, true) // for the driver
              .setConfig(HttpConnector.enabled, true) // for debugging (?)
              .build();

      this.graphDb = managementService.database(DEFAULT_DATABASE_NAME);

      // TODO this could just reimplement query() to use the DB directly and not expose the
      //  connectors, losing debugging access outside the application
      this.driver = GraphDatabase.driver("bolt://localhost:7687");

      this.driver.verifyConnectivity();

      configureDatabase();

      Logging.INSTANCE.info("Embedded Neo4J database initialized");

      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {
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

      throw new KlabIllegalStateException(
          "cannot recontextualize a previously contextualized graph " + "database");
    }

    var ret = new KnowledgeGraphNeo4JEmbedded(this, scope);

    ret.initializeContext();

    return ret;
  }

//  @Override
//  public <T extends RuntimeAsset> List<T> get(
//      RuntimeAsset source, DigitalTwin.Relationship linkType, Class<T> resultClass) {
//    return List.of();
//  }

  @Override
  public KnowledgeGraph merge(URL remoteDigitalTwinURL) {
    return null;
  }

  @Override
  public boolean isOnline() {
    return this.online;
  }

  @Override
  public void shutdown() {
    managementService.shutdown();
  }
}
