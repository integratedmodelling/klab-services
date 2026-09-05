package org.integratedmodelling.klab.services.runtime.neo4j;

import java.util.HashMap;
import java.util.Map;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.utilities.Utils;

public abstract class AbstractKnowledgeGraph implements KnowledgeGraph {

  protected UserScope userScope;

  public abstract long nextKey();

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

  @Override
  public <T extends RuntimeAsset> T getAsset(String urn, Scope scope, Class<T> resultClass) {
    return retrieve(urn, resultClass, scope);
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
          var metadata = sanitizeMetadata(observation.getMetadata());
          ret.putAll(metadata);
          ret.put(GraphModel.Fields.METADATA, Utils.Json.asString(metadata));
          ret.put(
              GraphModel.Fields.NAME,
              observation.getName() == null
                  ? observation.getObservable().codeName()
                  : observation.getName());
          ret.put(GraphModel.Fields.TYPE, observation.getObservable().getArtifactType().name());
          ret.put(GraphModel.Fields.URN, observation.getUrn());
          ret.put(GraphModel.Fields.CHILDREN_COUNT, observation.getChildrenCount());
          ret.put(
              GraphModel.Fields.SEMANTICTYPE,
              SemanticType.fundamentalType(observation.getObservable().getSemantics().getType())
                  .name());
          ret.put(GraphModel.Fields.SEMANTICS, observation.getObservable().getSemantics().getUrn());
          ret.put(GraphModel.Fields.OBSERVABLE, observation.getObservable().getUrn());
          ret.put(GraphModel.Fields.ID, observation.getId());
          ret.put(GraphModel.Fields.PARENT_ID, observation.getParentId());
          ret.put(GraphModel.Fields.EVENT_TIMESTAMPS, observation.getEventTimestamps());
          if (!observation.getHistograms().isEmpty()) {
            ret.put(
                GraphModel.Fields.HISTOGRAMS, Utils.Data.serializeHistogramMap(observation.getHistograms()));
          }
          if (observation instanceof ObservationImpl observation1) {
            ret.put(GraphModel.Fields.SUBSTANTIAL, observation1.isSubstantialQuality());
          }
          if (observation.getContextualizationData()
              instanceof ObservationImpl.ContextualizationDataImpl data) {
            ret.put(GraphModel.Fields.ADAPTER_ID, data.getAdapterId());
            ret.put(
                GraphModel.Fields.ADAPTER_PARAMETERS,
                data.getParameters() == null ? null : Utils.Json.asString(data.getParameters()));

            var shardingStrategy =
                observation.getContextualizationData().getNativeShardingStrategy();
            if (shardingStrategy != null) {
              ret.put(GraphModel.Fields.FILL_CURVE, shardingStrategy.getCurve().name());
              ret.put(GraphModel.Fields.SUGGESTED_SPLITS, shardingStrategy.getSuggestedSplits());
              ret.put(GraphModel.Fields.MAX_BUFFER_SIZE, shardingStrategy.getMaxBufferSize());
              ret.put(GraphModel.Fields.MIN_SPLIT_SIZE, shardingStrategy.getMinSplitSize());
              ret.put(GraphModel.Fields.DATA_TYPE, shardingStrategy.getDataType().name());
            }
          }
        }
        case Agent agent -> {
          var metadata = sanitizeMetadata(agent.getMetadata());
          ret.putAll(metadata);
          ret.put(GraphModel.Fields.METADATA, Utils.Json.asString(metadata));
          ret.put(GraphModel.Fields.NAME, agent.getName());
          // TODO
        }
        case Cohort cohort -> {
          var metadata = sanitizeMetadata(cohort.getMetadata());
          ret.putAll(metadata);
          ret.put(GraphModel.Fields.METADATA, Utils.Json.asString(metadata));
          ret.put(GraphModel.Fields.OBSERVABLE, cohort.getObservable().getUrn());
          ret.put(GraphModel.Fields.CHILDREN_COUNT, cohort.getChildrenCount());
          ret.put(GraphModel.Fields.URN, cohort.getObservable().getUrn() + "_cohort");
          ret.put(GraphModel.Fields.ID, cohort.getId());
          ret.put(GraphModel.Fields.GEOMETRY, cohort.getGeometry().encode());
          // TODO
        }
        case ActuatorImpl actuator -> {
          ret.put(GraphModel.Fields.ACTUATOR_SCHEMA_VERSION, 1);
          ret.put(GraphModel.Fields.ID, actuator.getId());
          ret.put(GraphModel.Fields.NAME, actuator.getName());
          ret.put(GraphModel.Fields.TYPE, actuator.getType() == null ? null : actuator.getType().name());
          ret.put(GraphModel.Fields.ACTUATOR_TYPE, actuator.getActuatorType() == null ? null : actuator.getActuatorType().name());
          ret.put(GraphModel.Fields.CHILDREN_COUNT, Math.max(actuator.getChildrenCount(), actuator.getChildren().size()));
          ret.put(GraphModel.Fields.COVERAGE, actuator.getCoverage() == null ? null : actuator.getCoverage().encode());
          ret.put(GraphModel.Fields.DATA_JSON, Utils.Json.asString(actuator.getData()));
          ret.put(GraphModel.Fields.COMPUTATION_JSON, actuator.getComputation().stream().map(Utils.Json::asString).toList());
          ret.put(GraphModel.Fields.ANNOTATIONS_JSON, actuator.getAnnotations().stream().map(Utils.Json::asString).toList());
          ret.put(GraphModel.Fields.SHARDING_STRATEGY_JSON, actuator.getShardingStrategy() == null ? null : Utils.Json.asString(actuator.getShardingStrategy()));
          ret.put(GraphModel.Fields.RESOLVED_GEOMETRY, actuator.getResolvedGeometry() == null ? null : actuator.getResolvedGeometry().encode());
          ret.put(GraphModel.Fields.RESOLVED_COVERAGE, actuator.getResolvedCoverage());
          ret.put(GraphModel.Fields.SEMANTICS, actuator.getObservation().getObservable().getUrn());
          ret.put(
              GraphModel.Fields.COMPUTATION,
              // TODO skip any recursive resolution calls and prepare for linking later
              actuator.getComputation().stream()
                  .map(call -> call.encode(KlabLanguage.KLAB_EXPRESSION_LANGUAGE))
                  .toList());
          ret.put(GraphModel.Fields.PARENT_ID, actuator.getParentId());
          ret.put(GraphModel.Fields.STRATEGY, actuator.getStrategyUrn());
        }
        case Activity activity -> {
          var metadata = sanitizeMetadata(activity.getMetadata());
          ret.putAll(metadata);
          ret.put(GraphModel.Fields.METADATA, Utils.Json.asString(metadata));
          ret.put(GraphModel.Fields.CREDITS, activity.getCredits());
          ret.put(GraphModel.Fields.DESCRIPTION, activity.getDescription());
          ret.put(GraphModel.Fields.END, activity.getEnd());
          ret.put(GraphModel.Fields.START, activity.getStart());
          ret.put(GraphModel.Fields.SCHEDULER_TIME, activity.getSchedulerTime());
          ret.put(GraphModel.Fields.SIZE, activity.getSize());
          ret.put(GraphModel.Fields.TYPE, activity.getType().name());
          ret.put(GraphModel.Fields.NAME, activity.getName());
          ret.put(GraphModel.Fields.ID, activity.getId());
          ret.put(GraphModel.Fields.PARENT_ID, activity.getParentId());
          ret.put(GraphModel.Fields.URN, activity.getUrn());
          ret.put(GraphModel.Fields.OBSERVATION_URN, activity.getObservationUrn());
          ret.put(GraphModel.Fields.SERVICE_NAME, activity.getServiceName());
          ret.put(GraphModel.Fields.SERVICE_ID, activity.getServiceId());
          ret.put(
              GraphModel.Fields.SERVICE_TYPE,
              activity.getServiceType() == null ? null : activity.getServiceType().name());
          ret.put(GraphModel.Fields.DATAFLOW, activity.getDataflow());
          ret.put("outcome", activity.getOutcome() == null ? null : activity.getOutcome().name());
          ret.put(GraphModel.Fields.STACK_TRACE, activity.getStackTrace());
          ret.put(GraphModel.Fields.TRIGGERING_ACTIVITY_URN, activity.getTriggeringActivityUrn());
        }
        case ShardImpl buffer -> {
          ret.put(GraphModel.Fields.ID, buffer.getId());
          ret.put(GraphModel.Fields.PERSISTENCE, buffer.getPersistence().name());
          ret.put(GraphModel.Fields.NATIVE_TYPE, buffer.getNativeType().name());
          ret.put(GraphModel.Fields.FILL_CURVE, buffer.getShardingStrategy().getCurve().name());
          ret.put(GraphModel.Fields.SIZE, buffer.getGeometry().size());
          ret.put(GraphModel.Fields.SHARD_INDEX, buffer.getShardIndex());
          ret.put(GraphModel.Fields.TIMESTAMP, buffer.getTimestamp());
          ret.put(GraphModel.Fields.SHARD_COUNT, buffer.getShardCount());
          ret.put(GraphModel.Fields.URN, buffer.getUrn());
          if (buffer.getHistogram() != null) {
            ret.put(GraphModel.Fields.HISTOGRAM, Utils.Json.asString(buffer.getHistogram()));
          }
          ret.put(GraphModel.Fields.SUGGESTED_SPLITS, buffer.getShardingStrategy().getSuggestedSplits());
          ret.put(GraphModel.Fields.MAX_BUFFER_SIZE, buffer.getShardingStrategy().getMaxBufferSize());
          ret.put(GraphModel.Fields.MIN_SPLIT_SIZE, buffer.getShardingStrategy().getMinSplitSize());
          ret.put(GraphModel.Fields.DATA_TYPE, buffer.getShardingStrategy().getDataType().name());
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
