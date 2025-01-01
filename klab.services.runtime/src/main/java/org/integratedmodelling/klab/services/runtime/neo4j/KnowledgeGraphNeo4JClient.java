package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.neo4j.driver.Driver;
import org.neo4j.driver.ExecutableQuery;

import java.net.URL;
import java.util.List;

public class KnowledgeGraphNeo4JClient extends KnowledgeGraphNeo4j implements KnowledgeGraph {

  // TODO connect to a DB and run a driver

  @Override
  public KnowledgeGraph contextualize(ContextScope scope) {
    return null;
  }

  @Override
  public <T extends RuntimeAsset> T get(long id, Class<T> resultClass) {
    return null;
  }

  @Override
  public <T extends RuntimeAsset> List<T> get(
      RuntimeAsset source, DigitalTwin.Relationship linkType, Class<T> resultClass) {
    return List.of();
  }

  @Override
  public KnowledgeGraph merge(URL remoteDigitalTwinURL) {
    return null;
  }

  @Override
  public boolean isOnline() {
    return false;
  }

  @Override
  public void shutdown() {}
}
