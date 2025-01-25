package org.integratedmodelling.common.services.client.digitaltwin;

import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;

import java.net.URL;
import java.util.List;

/** At the client side, the knowledge graph can only be queried and rejects any operation. */
public class ClientKnowledgeGraph implements KnowledgeGraph {

  private final ClientContextScope scope;

  public ClientKnowledgeGraph(ClientContextScope scope) {
    this.scope = scope;
  }

  @Override
  public Operation operation(
      Agent agent, Activity parentActivity, Activity.Type activityType, Object... data) {
    throw new KlabIllegalStateException("Modifying operations not allowed on the client-side knowledge graph");
  }

  @Override
  public <T extends RuntimeAsset> Query<T> query(Class<T> resultClass) {
    return new KnowledgeGraphQuery<>(KnowledgeGraphQuery.AssetType.classify(resultClass)) {
      @Override
      public List<T> run() {
        return scope.queryKnowledgeGraph(this);
      }
    };
  }

  @Override
  public <T extends RuntimeAsset> List<T> query(Query<T> knowledgeGraphQuery, Class<T> resultClass) {
    if (knowledgeGraphQuery instanceof KnowledgeGraphQuery<T> kc) {
      return scope.queryKnowledgeGraph(kc);
    }
    throw new KlabUnimplementedException("Not ready to compile arbitrary KG query implementations");
  }

  @Override
  public void deleteContext() {}

  @Override
  public Agent user() {
    return null;
  }

  @Override
  public Agent klab() {
    return null;
  }

  @Override
  public List<ContextInfo> getExistingContexts(UserScope scope) {
    return List.of();
  }

  @Override
  public void clear() {}

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
  public void update(RuntimeAsset observation, ContextScope scope, Object... arguments) {
    throw new KlabIllegalStateException("Modifying operations not allowed on the client-side knowledge graph");
  }

  @Override
  public <T extends RuntimeAsset> List<T> get(
      ContextScope scope, Class<T> resultClass, Object... queryParameters) {
    return List.of();
  }

  @Override
  public Agent requireAgent(String agentName) {
    return null;
  }

  @Override
  public KnowledgeGraph merge(URL remoteDigitalTwinURL) {
    return null;
  }

  @Override
  public boolean isOnline() {
    return true;
  }

  @Override
  public void shutdown() {}

  @Override
  public List<SessionInfo> getSessionInfo(Scope scope) {
    return List.of();
  }
}
