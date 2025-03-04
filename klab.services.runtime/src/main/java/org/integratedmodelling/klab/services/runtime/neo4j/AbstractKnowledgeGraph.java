package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.runtime.storage.BufferImpl;
import org.integratedmodelling.klab.utilities.Utils;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractKnowledgeGraph implements KnowledgeGraph {

  protected static int MAX_CACHED_OBSERVATIONS = 400;

  protected ContextScope scope;

  /**
   * Return a RuntimeAsset representing the overall dataflow related to the scope, so that it can be
   * used for linking using the other CRUD methods.
   *
   * @return the dataflow root node, unique for the context.
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException if the graph is
   *     not contextualized.
   */
  protected abstract RuntimeAsset getDataflowNode();

  protected abstract long nextKey();

  /**
   * Return a RuntimeAsset representing the overall provenance related to the scope, so that it can
   * be used for linking using the other CRUD methods.
   *
   * @return the dataflow root node, unique for the context.
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException if the graph is
   *     not contextualized.
   */
  protected abstract RuntimeAsset getProvenanceNode();

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
      DigitalTwin.Relationship relationship,
      Scope scope,
      Object... additionalProperties);

  @Override
  public <T extends RuntimeAsset> T get(long id, Class<T> resultClass) {
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
          ret.putAll(observation.getMetadata());
          ret.put(
              "name",
              observation.getName() == null
                  ? observation.getObservable().codeName()
                  : observation.getName());
          ret.put("updated", observation.getLastUpdate());
          ret.put("resolved", observation.isResolved());
          ret.put("type", observation.getType().name());
          ret.put("urn", observation.getUrn());
          ret.put(
              "semantictype",
              SemanticType.fundamentalType(observation.getObservable().getSemantics().getType())
                  .name());
          ret.put("semantics", observation.getObservable().getSemantics().getUrn());
          ret.put("observable", observation.getObservable().getUrn());
          ret.put("id", observation.getId());
        }
        case Agent agent -> {
          // TODO
        }
        case ActuatorImpl actuator -> {
          ret.put("observationId", actuator.getId());
          ret.put("id", actuator.getInternalId());
          StringBuilder code = new StringBuilder();
          for (var call : actuator.getComputation()) {
            // TODO skip any recursive resolution calls and prepare for linking later
            code.append(call.encode(Language.DEFAULT_EXPRESSION_LANGUAGE)).append("\n");
          }
          ret.put("semantics", actuator.getObservable().getUrn());
          ret.put("computation", code.toString());
          ret.put("strategy", actuator.getStrategyUrn());
        }
        case Activity activity -> {
          ret.putAll(activity.getMetadata());
          ret.put("credits", activity.getCredits());
          ret.put("description", activity.getDescription());
          ret.put("end", activity.getEnd());
          ret.put("start", activity.getStart());
          ret.put("schedulerTime", activity.getSchedulerTime());
          ret.put("size", activity.getSize());
          ret.put("type", activity.getType().name());
          ret.put("name", activity.getName());
          ret.put("id", activity.getId());
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
        case BufferImpl buffer -> {
          ret.put("id", buffer.getId());
          ret.put("persistence", buffer.getPersistence().name());
          ret.put("type", buffer.getDataType().name());
          ret.put("fillCurve", buffer.getFillingCurve().name());
          ret.put("size", buffer.size());
          ret.put(
              "histogram",
              org.integratedmodelling.common.utils.Utils.Json.asString(buffer.histogram()));
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
}
