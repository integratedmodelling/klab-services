package org.integratedmodelling.klab.services.runtime.neo4j;

import java.util.HashMap;
import java.util.Map;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.runtime.storage.ShardImpl;
import org.integratedmodelling.klab.utilities.Utils;

public abstract class AbstractKnowledgeGraph implements KnowledgeGraph {

  protected UserScope userScope;

  protected abstract long nextKey();

  /**
   * Retrieve the asset with the passed key.
   *
   * @param key
   * @param assetClass
   * @param <T>
   * @return
   */
  protected abstract <T extends RuntimeAsset> T retrieve(
      Object key, Class<T> assetClass, Scope scope);

  /**
   * Store the passed asset, return its unique long ID.
   *
   * @param asset
   * @param additionalProperties any pair of properties we want overridden. Pass pairs and do it
   *     right or you'll get an exception.
   * @return
   */
  protected abstract long store(RuntimeAsset asset, Scope scope, Object... additionalProperties);

  /**
   * Link the two passed assets.
   *
   * @param source
   * @param destination
   * @param additionalProperties any pair of properties we want overridden. Pass pairs and do it
   *     right or you'll get an exception.
   */
  protected abstract void link(
      RuntimeAsset source,
      RuntimeAsset destination,
      GraphModel.Relationship relationship,
      Scope scope,
      Object... additionalProperties);

  @Override
  public <T extends RuntimeAsset> T getAsset(long id, Scope scope, Class<T> resultClass) {
    return retrieve(id, resultClass, scope);
  }

  /**
   * Define all properties for the passed asset.
   *
   * @param asset
   * @param additionalParameters any pair of additional parameters to add
   * @return
   */
  protected Map<String, Object> asParameters(Object asset, Object... additionalParameters) {
    Map<String, Object> ret = new HashMap<>();
    if (asset != null) {
      switch (asset) {
        case Observation observation -> {
          ret.putAll(sanitizeMetadata(observation.getMetadata()));
          ret.put(
              "name",
              observation.getName() == null
                  ? observation.getObservable().codeName()
                  : observation.getName());
          ret.put("type", observation.getType().name());
          ret.put("urn", observation.getUrn());
          ret.put("childrenCount", observation.getChildrenCount());
          ret.put(
              "semantictype",
              SemanticType.fundamentalType(observation.getObservable().getSemantics().getType())
                  .name());
          ret.put("semantics", observation.getObservable().getSemantics().getUrn());
          ret.put("observable", observation.getObservable().getUrn());
          ret.put("id", observation.getId());
          ret.put("parentId", observation.getParentId());
          ret.put("eventTimestamps", observation.getEventTimestamps());
          if (observation instanceof ObservationImpl observation1) {
            ret.put("substantial", observation1.isSubstantialQuality());
          }
          var instanceUrn = observation.getMetadata().get(Metadata.IM_FEATURE_URN);
          if (instanceUrn != null) {
            ret.put("instanceUrn", instanceUrn);
          }
          if (observation.getContextualizationData()
              instanceof ObservationImpl.ContextualizationDataImpl data) {
            ret.put("adapterId", data.getAdapterId());
            ret.put(
                "adapterParameters",
                data.getParameters() == null ? null : Utils.Json.asString(data.getParameters()));

            var shardingStrategy =
                observation.getContextualizationData().getNativeShardingStrategy();
            if (shardingStrategy != null) {
              ret.put("fillCurve", shardingStrategy.getCurve().name());
              ret.put("suggestedSplits", shardingStrategy.getSuggestedSplits());
              ret.put("maxBufferSize", shardingStrategy.getMaxBufferSize());
              ret.put("minSplitSize", shardingStrategy.getMinSplitSize());
              ret.put("dataType", shardingStrategy.getDataType().name());
            }
          }
        }
        case Agent agent -> {
          ret.putAll(sanitizeMetadata(agent.getMetadata()));
          ret.put("name", agent.getName());
          // TODO
        }
        case ActuatorImpl actuator -> {
          ret.put("semantics", actuator.getObservation().getObservable().getUrn());
          ret.put(
              "computation",
              // TODO skip any recursive resolution calls and prepare for linking later
              actuator.getComputation().stream()
                  .map(call -> call.encode(KlabLanguage.KLAB_EXPRESSION_LANGUAGE))
                  .toList());
          ret.put("parentId", actuator.getParentId());
          ret.put("strategy", actuator.getStrategyUrn());
        }
        case Activity activity -> {
          ret.putAll(sanitizeMetadata(activity.getMetadata()));
          ret.put("credits", activity.getCredits());
          ret.put("description", activity.getDescription());
          ret.put("end", activity.getEnd());
          ret.put("start", activity.getStart());
          ret.put("parentId", activity.getParentId());
          ret.put("schedulerTime", activity.getSchedulerTime());
          ret.put("size", activity.getSize());
          ret.put("type", activity.getType().name());
          ret.put("name", activity.getName());
          ret.put("id", activity.getId());
          ret.put("parentId", activity.getParentId());
          ret.put("urn", activity.getUrn());
          ret.put("observationUrn", activity.getObservationUrn());
          ret.put("serviceName", activity.getServiceName());
          ret.put(
              "serviceType",
              activity.getServiceType() == null ? null : activity.getServiceType().name());
          ret.put("dataflow", activity.getDataflow());
          ret.put("outcome", activity.getOutcome() == null ? null : activity.getOutcome().name());
          ret.put("stackTrace", activity.getStackTrace());
        }
        case ShardImpl buffer -> {
          ret.put("id", buffer.getId());
          ret.put("persistence", buffer.getPersistence().name());
          ret.put("nativeType", buffer.getNativeType().name());
          ret.put("fillCurve", buffer.getShardingStrategy().getCurve().name());
          ret.put("size", buffer.getGeometry().size());
          ret.put("shardIndex", buffer.getShardIndex());
          ret.put("timestamp", buffer.getTimestamp());
          ret.put("shardCount", buffer.getShardCount());
          ret.put("urn", buffer.getUrn());
          if (buffer.getHistogram() != null) {
            ret.put("histogram", Utils.Json.asString(buffer.getHistogram()));
          }
         ret.put("fillCurve", buffer.getShardingStrategy().getCurve().name());
          ret.put("suggestedSplits", buffer.getShardingStrategy().getSuggestedSplits());
          ret.put("maxBufferSize", buffer.getShardingStrategy().getMaxBufferSize());
          ret.put("minSplitSize", buffer.getShardingStrategy().getMinSplitSize());
          ret.put("dataType", buffer.getShardingStrategy().getDataType().name());
        }
        default ->
            throw new KlabInternalErrorException(
                "unexpected value for asParameters: " + asset.getClass().getCanonicalName());
      }
    }

    if (additionalParameters != null) {
      for (int i = 0; i < additionalParameters.length; i++) {
        ret.put(additionalParameters[i].toString(), additionalParameters[++i]);
      }
    }

    return Utils.Maps.removeNullValues(ret);
  }

  private Map<String, ?> sanitizeMetadata(Metadata metadata) {
    if (metadata == null) {
      return Map.of();
    }
    Map<String, Object> ret = new HashMap<>();
    metadata.forEach(
        (k, v) -> {
          if (Utils.Data.isPOD(v)) {
            ret.put(k, v);
          }
        });
    return ret;
  }
}
