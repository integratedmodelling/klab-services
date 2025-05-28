package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-side knowledge graph query, serializable to be ingested by the runtime's REST digital twin
 * endpoint. In the end this is less messy than using GraphQL, although we keep the latter
 * configured for possible future use by paired digital twins.
 */
public class KnowledgeGraphQuery<T extends RuntimeAsset> implements KnowledgeGraph.Query<T> {

  public enum QueryType {
    QUERY,
    AND,
    OR,
    NOT
  }

  /* we can only store assets that can be serialized. The others get to the server in other ways and
   * we use this enum to tag source and destination specified through their URNs and/or type when not included in the query. We also use this to encode the result class we expect.
   */
  public enum AssetType {
    SCOPE,
    DATAFLOW,
    PROVENANCE,
    ACTUATOR,
    ACTIVITY,
    OBSERVATION,
    SEMANTICS,
    OBSERVABLE,
    LINK,
    DATA;

    public static AssetType classify(Object asset) {

      if (asset == RuntimeAsset.CONTEXT_ASSET
          || asset instanceof RuntimeAsset runtimeAsset
              && runtimeAsset.getId() == RuntimeAsset.CONTEXT_ASSET.getId()) {
        return AssetType.SCOPE;
      } else if (asset == RuntimeAsset.PROVENANCE_ASSET
          || asset instanceof RuntimeAsset runtimeAsset
              && runtimeAsset.getId() == RuntimeAsset.PROVENANCE_ASSET.getId()) {
        return AssetType.PROVENANCE;
      } else if (asset == RuntimeAsset.DATAFLOW_ASSET
          || asset instanceof RuntimeAsset runtimeAsset
              && runtimeAsset.getId() == RuntimeAsset.DATAFLOW_ASSET.getId()) {
        return AssetType.DATAFLOW;
      }

      return switch (asset) {
        case Observation ignored -> AssetType.OBSERVATION;
        case Actuator ignored -> AssetType.ACTUATOR;
        case Activity ignored -> AssetType.ACTIVITY;
        case Observable ignored -> AssetType.OBSERVABLE;
        case KimObservable ignored -> AssetType.OBSERVABLE;
        case ContextScope ignored ->
            ignored.getContextObservation() == null ? AssetType.SCOPE : AssetType.OBSERVATION;
        case Concept ignored -> AssetType.SEMANTICS;
        case KimConcept ignored -> AssetType.SEMANTICS;
        case KnowledgeGraph.Link ignored -> AssetType.LINK;
        case Storage.Buffer ignored -> AssetType.DATA;
        default ->
            throw new KlabIllegalArgumentException(
                "Can't make a query with a " + asset + " target");
      };
    }

    public static AssetType classify(Class<?> asset) {
      if (Observation.class.isAssignableFrom(asset)) {
        return AssetType.OBSERVATION;
      }
      if (Actuator.class.isAssignableFrom(asset)) {
        return AssetType.ACTUATOR;
      }
      if (Activity.class.isAssignableFrom(asset)) {
        return AssetType.ACTIVITY;
      }
      if (Observable.class.isAssignableFrom(asset)) {
        return AssetType.OBSERVABLE;
      }
      if (KimObservable.class.isAssignableFrom(asset)) {
        return AssetType.OBSERVABLE;
      }
      if (ContextScope.class.isAssignableFrom(asset)) {
        return AssetType.SCOPE;
      }
      if (Concept.class.isAssignableFrom(asset)) {
        return AssetType.SEMANTICS;
      }
      if (KimConcept.class.isAssignableFrom(asset)) {
        return AssetType.SEMANTICS;
      }
      if (KnowledgeGraph.Link.class.isAssignableFrom(asset)) {
        return AssetType.LINK;
      }
      if (Storage.Buffer.class.isAssignableFrom(asset)) {
        return AssetType.DATA;
      }
      throw new KlabIllegalArgumentException("Can't make a query with a " + asset + " target");
    }

    public Class<?> getAssetClass() {
      return switch (this) {
        case SCOPE -> ContextScope.class;
        case DATAFLOW -> Dataflow.class;
        case PROVENANCE -> Provenance.class;
        case ACTUATOR -> Actuator.class;
        case ACTIVITY -> Activity.class;
        case OBSERVATION -> Observation.class;
        case SEMANTICS -> Concept.class;
        case OBSERVABLE -> Observable.class;
        case DATA -> Storage.Buffer.class;
        case LINK -> KnowledgeGraph.Link.class;
      };
    }
  }

  public static class Asset {

    private AssetType type;
    private String urn;

    public AssetType getType() {
      return type;
    }

    public void setType(AssetType type) {
      this.type = type;
    }

    public String getUrn() {
      return urn;
    }

    public void setUrn(String urn) {
      this.urn = urn;
    }
  }

  private AssetType resultType;
  private Asset source;
  private Asset target;
  private QueryType type = QueryType.QUERY;
  private List<KnowledgeGraphQuery<T>> children = new ArrayList<>();
  private GraphModel.Relationship relationship;
  private List<Triple<String, String, String>> assetQueryCriteria = new ArrayList<>();
  private Parameters<String> relationshipQueryCriteria = Parameters.create();
  private int depth = 1;
  private long limit = -1;
  private long offset = 0;
  private long id = -1;
  private Asset relationshipSource;
  private Asset relationshipTarget;

  public KnowledgeGraphQuery() {}

  public KnowledgeGraphQuery(AssetType assetType) {
    this.resultType = assetType;
  }

  @Override
  public KnowledgeGraph.Query<T> id(long id) {
    this.id = id;
    return this;
  }

  public long getId() {
    return this.id;
  }

  @Override
  public KnowledgeGraph.Query<T> source(Object startingPoint) {
    this.source = makeAsset(startingPoint);
    return this;
  }

  private Asset makeAsset(Object startingPoint) {
    var ret = new Asset();
    ret.type = AssetType.classify(startingPoint);
    ret.urn =
        switch (startingPoint) {
          case Observation ignored -> ignored.getUrn();
          case Actuator ignored -> ignored.getId() + "";
          case Activity ignored -> ignored.getUrn();
          case Observable ignored -> ignored.getUrn();
          case KimObservable ignored -> ignored.getUrn();
          case Concept ignored -> ignored.getUrn();
          case KimConcept ignored -> ignored.getUrn();
          case ServiceSideScope ignored ->
              ignored instanceof ContextScope contextScope
                      && contextScope.getContextObservation() != null
                  ? contextScope.getContextObservation().getUrn()
                  : ignored.getId();
          case ClientContextScope ignored ->
              ignored.getContextObservation() == null
                  ? ignored.getId()
                  : ignored.getContextObservation().getUrn();
          case Storage.Buffer ignored -> ignored.getId() + "";
          default -> null;
        };

    if (ret.urn == null) {
      throw new KlabIllegalStateException("Unresolved asset passed to a query");
    }
    return ret;
  }

  @Override
  public KnowledgeGraph.Query<T> target(Object startingPoint) {
    this.target = makeAsset(startingPoint);
    return this;
  }

  @Override
  public KnowledgeGraph.Query<T> along(GraphModel.Relationship relationship, Object... parameters) {
    this.relationship = relationship;
    this.relationshipQueryCriteria = Parameters.create(parameters);
    return this;
  }

  @Override
  public KnowledgeGraph.Query<T> between(
      Object source, Object target, GraphModel.Relationship relationship) {
    this.relationship = relationship;
    this.relationshipSource = makeAsset(source);
    this.relationshipTarget = makeAsset(target);
    return this;
  }

  @Override
  public KnowledgeGraph.Query<T> depth(int depth) {
    this.depth = depth;
    return this;
  }

  @Override
  public KnowledgeGraph.Query<T> limit(long n) {
    this.limit = n;
    return this;
  }

  @Override
  public KnowledgeGraph.Query<T> offset(long n) {
    this.offset = n;
    return this;
  }

  @Override
  public KnowledgeGraph.Query<T> where(String field, Operator operator, Object argument) {
    this.assetQueryCriteria.add(Triple.of(field, operator.name(), Utils.Data.asString(argument)));
    return this;
  }

  @Override
  public KnowledgeGraph.Query<T> order(Object... criteria) {
    // TODO
    return this;
  }

  /**
   * At the client side, override this one to send to the client. At the server side, translate it
   * to a server-side query and run that.
   *
   * @return
   */
  @Override
  public List<T> run(Scope scope) {
    throw new KlabIllegalStateException(
        "The client-side knowledge graph query must be sent to a runtime service to be run");
  }

  @Override
  public Optional<T> peek(Scope scope) {
    var results = run(scope);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
  }

  @Override
  public KnowledgeGraph.Query<T> or(KnowledgeGraph.Query<T> query) {
    var ret = new KnowledgeGraphQuery<>();
    ret.type = QueryType.AND;
    ret.children.add((KnowledgeGraphQuery<RuntimeAsset>) this);
    ret.children.add((KnowledgeGraphQuery<RuntimeAsset>) query);
    return (KnowledgeGraph.Query<T>) ret;
  }

  @Override
  public KnowledgeGraph.Query<T> and(KnowledgeGraph.Query<T> query) {
    var ret = new KnowledgeGraphQuery<>();
    ret.type = QueryType.OR;
    ret.children.add((KnowledgeGraphQuery<RuntimeAsset>) this);
    ret.children.add((KnowledgeGraphQuery<RuntimeAsset>) query);
    return (KnowledgeGraph.Query<T>) ret;
  }

  public Asset getSource() {
    return source;
  }

  public void setSource(Asset source) {
    this.source = source;
  }

  public Asset getTarget() {
    return target;
  }

  public void setTarget(Asset target) {
    this.target = target;
  }

  public QueryType getType() {
    return type;
  }

  public void setType(QueryType type) {
    this.type = type;
  }

  public List<KnowledgeGraphQuery<T>> getChildren() {
    return children;
  }

  public void setChildren(List<KnowledgeGraphQuery<T>> children) {
    this.children = children;
  }

  public GraphModel.Relationship getRelationship() {
    return relationship;
  }

  public void setRelationship(GraphModel.Relationship relationship) {
    this.relationship = relationship;
  }

  public List<Triple<String, String, String>> getAssetQueryCriteria() {
    return assetQueryCriteria;
  }

  public void setAssetQueryCriteria(List<Triple<String, String, String>> assetQueryCriteria) {
    this.assetQueryCriteria = assetQueryCriteria;
  }

  public Parameters<String> getRelationshipQueryCriteria() {
    return relationshipQueryCriteria;
  }

  public void setRelationshipQueryCriteria(Parameters<String> relationshipQueryCriteria) {
    this.relationshipQueryCriteria = relationshipQueryCriteria;
  }

  public int getDepth() {
    return depth;
  }

  public void setDepth(int depth) {
    this.depth = depth;
  }

  public long getLimit() {
    return limit;
  }

  public void setLimit(long limit) {
    this.limit = limit;
  }

  public long getOffset() {
    return offset;
  }

  public void setOffset(long offset) {
    this.offset = offset;
  }

  public Asset getRelationshipSource() {
    return relationshipSource;
  }

  public void setRelationshipSource(Asset relationshipSource) {
    this.relationshipSource = relationshipSource;
  }

  public Asset getRelationshipTarget() {
    return relationshipTarget;
  }

  public void setRelationshipTarget(Asset relationshipTarget) {
    this.relationshipTarget = relationshipTarget;
  }

  public AssetType getResultType() {
    return resultType;
  }

  public void setResultType(AssetType resultType) {
    this.resultType = resultType;
  }
}
