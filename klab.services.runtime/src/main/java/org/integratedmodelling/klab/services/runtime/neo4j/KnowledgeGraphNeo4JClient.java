package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.configuration.RuntimeConfiguration;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.ExecutableQuery;
import org.neo4j.driver.GraphDatabase;

import java.net.URL;
import java.util.List;

public class KnowledgeGraphNeo4JClient extends KnowledgeGraphNeo4j implements KnowledgeGraph {

  boolean online = false;

  public KnowledgeGraphNeo4JClient(String url) {
    if (url == null) throw new KlabIllegalArgumentException("Database url is mandatory");
    this.driver = GraphDatabase.driver(url, AuthTokens.none());
    try {
      // TODO launch a timed something to verify connectivity periodically
      this.driver.verifyConnectivity();
      online = true;
      Logging.INSTANCE.info("Connected to Neo4J at " + url);
      configureDatabase();
    } catch (Exception e) {
      Logging.INSTANCE.error("Failed to connect to Neo4J at " + url, e);
      online = false;
    }
  }

  private KnowledgeGraphNeo4JClient(
      KnowledgeGraphNeo4JClient parent, String scopeId, UserIdentity user) {
    this.online = parent.online;
    this.driver = parent.driver;
    this.klab = getOrCreateAgent("k.LAB", "AI");
    this.user = getOrCreateAgent(user.getUsername(), "USER");
    this.rootContextId = scopeId;
    this.serviceId = parent.serviceId;
  }

  private void configureDatabase() {

    // TODO if the DB is new, add all the indices! So far the DB is unindexed.

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
  public KnowledgeGraph contextualize(
      DigitalTwin.Configuration digitalTwinConfig, UserScope userScope) {

    // idempotence
    if (digitalTwinConfig.getId().equals(rootContextId)) {
      return this;
    }

    var ret = new KnowledgeGraphNeo4JClient(this, digitalTwinConfig.getId(), userScope.getUser());

    // TODO pass the
    ret.initializeContext(digitalTwinConfig, userScope);

    return ret;
  }

  @Override
  public KnowledgeGraph merge(URL remoteDigitalTwinURL) {
    return null;
  }

  @Override
  public boolean isOnline() {
    return this.online;
  }

  @Override
  public void shutdown() {}
}
