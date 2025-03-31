package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.neo4j.driver.Driver;
import org.neo4j.driver.ExecutableQuery;

import java.net.URL;
import java.util.List;

public class KnowledgeGraphNeo4JRAM extends KnowledgeGraphNeo4j implements KnowledgeGraph {

  @Override
  public KnowledgeGraph contextualize(ContextScope scope) {
    return null;
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
