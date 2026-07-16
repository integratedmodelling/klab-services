package org.integratedmodelling.klab.services.resolver;

import java.util.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

/**
 * A Compiler is instantiated in context. TODO should also take a parent Dataflow and fill the
 * catalog in from it.
 */
public class DataflowCompiler {

  private final ResolutionGraph resolutionGraph;
  private final ContextScope scope;
  private final Observation observation;
  // NO this must be a graph as in the RG
  private Set<Long> catalog = new HashSet<>();

  /**
   * TODO add the context dataflow.
   *
   * @param resolutionGraph
   * @param scope
   */
  public DataflowCompiler(
      Observation observation, ResolutionGraph resolutionGraph, ContextScope scope) {
    this.resolutionGraph = resolutionGraph;
    this.scope = scope;
    this.observation = observation;
  }

  /**
   * Main entry point. When we resolve an ObservationStrategy from the runtime we should use the
   * correspondent worker below, after locating the context actuator.
   *
   * @return
   */
  public Dataflow compile() {

    if (resolutionGraph.isEmpty()) {
      var ret = Dataflow.empty();
      ret.getNotifications().addAll(resolutionGraph.getNotifications());
      return ret;
    }

    // TODO remove eventually, or make it debug-level
    var dump = Utils.Graphs.dump(resolutionGraph.graph());
    Logging.INSTANCE.info("Resolution graph for " + observation + ":\n\n" + dump);

    Map<Observable, String> catalog = new HashMap<>();
    var ret = new DataflowImpl();
    ret.setName(observation.getName() + "_" + scope.getId());
    ret.setResolvedCoverage(resolutionGraph.getResolvedCoverage());
    for (var node : resolutionGraph.rootNodes()) {
      /*
      These MUST be observations. We check for now but it shouldn't happen.
       */
      if (!(node instanceof Observation)) {
        throw new KlabIllegalStateException("Resolution root is not an observation");
      }
      ret.getComputation()
          .addAll(
              compileObservation(
                  observation,
                  GeometryRepository.INSTANCE.scale(observation.getGeometry()),
                  null,
                  null));
    }

    ret.getNotifications().addAll(resolutionGraph.getNotifications());

    return ret;
  }

  /**
   * The entry point is calling this with a null strategy for all root observation nodes. Otherwise
   * locate and contextualize the entry point and call one of the others on the correspondent
   * actuator.
   *
   * @param observation
   * @param strategy
   * @return
   */
  List<Actuator> compileObservation(
      Observation observation, Geometry coverage, ObservationStrategy strategy, String localName) {

    // compile references for any obs with ID > 0 (coming from the remote KG) or already compiled
    if (observation.getId() > 0 || catalog.contains(observation.getId())) {
      var ret = new ActuatorImpl();
      ret.setObservation(observation);
      ret.setId(observation.getId());
      ret.setName(
          localName == null
              ? (observation.getObservable().is(SemanticType.COUNTABLE)
                  ? observation.getName()
                  : observation.getObservable().getName())
              : localName);
      ret.setCoverage(coverage.as(Geometry.class));
      ret.setActuatorType(Actuator.Type.REFERENCE);
      return List.of(ret);
    }

    catalog.add(observation.getId());

    var ret = new ArrayList<Actuator>();
    var references = new ArrayList<Actuator>();
    for (var edge : resolutionGraph.graph().outgoingEdgesOf(observation)) {

      var child = resolutionGraph.graph().getEdgeTarget(edge);
      var childCoverage = edge.coverage;

      if (child instanceof ObservationStrategy observationStrategy) {
        var actuator = new ActuatorImpl();
        actuator.setObservation(observation);
        actuator.setName(localName == null ? observation.getObservable().getName() : localName);
        actuator.setId(observation.getId());
        actuator.setActuatorType(Actuator.Type.OBSERVE);
        actuator.setCoverage(childCoverage == null ? null : childCoverage.as(Geometry.class));
        actuator.setResolvedGeometry(observation.getGeometry());
        actuator.setStrategyUrn(observationStrategy.getUrn());
        compileStrategy(actuator, observation, childCoverage, observationStrategy);
        ret.add(actuator);
      } else if (child instanceof Observable) {
        references.add(
            compileReference(
                resolutionGraph.getResolved(edge.observationId), childCoverage, edge.localName));
      }
    }

    if (ret.isEmpty()) {
      ret.addAll(references);
    } else {
      for (var actuator : ret) {
        actuator.getChildren().addAll(references);
      }
    }

    return ret;
  }

  /**
   * The strategy produces model actuators within the observation's
   *
   * @param observationActuator
   * @param observation
   * @param scale
   * @param observationStrategy
   * @return
   */
  void compileStrategy(
      Actuator observationActuator,
      Observation observation,
      Geometry scale,
      ObservationStrategy observationStrategy) {

    for (var edge : resolutionGraph.graph().outgoingEdgesOf(observationStrategy)) {

      var child = resolutionGraph.graph().getEdgeTarget(edge);
      var coverage = edge.coverage;

      // There can be 1+ nodes: if OBS it's the result of a RESOLVE, otherwise a MODEL.
      if (child instanceof Model model) {
        /**
         * Result of OBSERVE in the strategy. Depending on the model's description type, the result
         * may be transforming the dependencies and must be appropriately linked. This happens using
         * the transformation ID that brought along from the strategy's operation, which is the
         * localName in the edge connecting to the strategy.
         */
        compileModel(
            observationActuator, observation, coverage, observationStrategy, model, edge.localName);

      } else if (child instanceof Observation childObservation) {
        // new dependencies brought in by the strategy
        observationActuator
            .getChildren()
            .addAll(
                compileObservation(
                    childObservation, coverage, observationStrategy, edge.localName));
        // TODO if this observation is the target of a transformation, it must carry the internal ID
        // from the strategy
        //   so that we can link it to the transformation
      }
    }

    // HERE we must link the actuators depending on the observation
    System.out.println("VEDIAMO UN PO'RCODIO");

    // THEN any APPLY must be added to the actuator
  }

  /**
   * Compile a model's actuators within the observation's under a strategy
   *
   * @param observationActuator
   * @param observation
   * @param scale
   * @param observationStrategy
   * @param model
   */
  void compileModel(
      Actuator observationActuator,
      Observation observation,
      Geometry scale,
      ObservationStrategy observationStrategy,
      Model model,
      String localName) {

    for (var edge : resolutionGraph.graph().outgoingEdgesOf(model)) {

      var child = resolutionGraph.graph().getEdgeTarget(edge);
      var coverage = edge.coverage;

      if (child instanceof Observation dependentObservation) {
        observationActuator
            .getChildren()
            .addAll(
                compileObservation(dependentObservation, coverage, observationStrategy, localName));
      } else if (child instanceof Observable observable) {
        observationActuator
            .getChildren()
            .add(
                compileReference(
                    resolutionGraph.getResolved(edge.observationId), coverage, localName));
      }
    }

    for (var contextualizer : model.getComputation()) {

      Map<String, Object> overriddenParameters = new HashMap<>();
      // If there is a link from the strategy, the contextualizer carries the transformation
      //  localName to be matched with any input tags from the prototype
      if (contextualizer.getServiceCall() != null) {
        var prototype = resolutionGraph.getServiceInfo(contextualizer.getServiceCall().getUrn());
        if (prototype != null) {
          prototype.listInputs().stream()
              .filter(
                  a -> a.getTags().contains(ServiceInfo.Tag.INPUT) || a.getName().equals(localName))
              .findFirst()
              .ifPresent(
                  input -> overriddenParameters.put(input.getName(), Identifier.create(localName)));
        }
      }
      observationActuator
          .getComputation()
          .add(adaptContextualizer(contextualizer, overriddenParameters));
    }

    if (observationActuator.getObservation().getObservable().is(SemanticType.QUALITY)) {
      var shardingStrategy = new Data.ShardingStrategy();
      Utils.Annotations.getAnnotations(model, true)
          .forEach(
              annotation -> {
                switch (annotation.getName()) {
                  case "type" ->
                      shardingStrategy.setDataType(
                          Storage.Type.valueOf(
                              annotation
                                  .get(Annotation.VALUE_PARAMETER_KEY)
                                  .toString()
                                  .toUpperCase()));
                  case "split" ->
                      shardingStrategy.setSuggestedSplits(
                          Integer.parseInt(
                              annotation.get(Annotation.VALUE_PARAMETER_KEY).toString()));
                  case "maxSize" ->
                      shardingStrategy.setMaxBufferSize(
                          Long.parseLong(
                              annotation.get(Annotation.VALUE_PARAMETER_KEY).toString()));
                  case "minSplitSize" ->
                      shardingStrategy.setMinSplitSize(
                          Long.parseLong(
                              annotation.get(Annotation.VALUE_PARAMETER_KEY).toString()));
                  case "fillCurve" ->
                      shardingStrategy.setCurve(
                          Data.FillCurve.valueOf(
                              annotation
                                  .get(Annotation.VALUE_PARAMETER_KEY)
                                  .toString()
                                  .toUpperCase()));
                }
              });
      ((ActuatorImpl) observationActuator).setShardingStrategy(shardingStrategy);
    }
  }

  private Actuator compileReference(Observation observation, Coverage coverage, String localName) {
    var ret = new ActuatorImpl();
    ret.setObservation(observation);
    ret.setId(observation.getId());
    ret.setName(localName == null ? observation.getObservable().getName() : localName);
    ret.setCoverage(coverage.as(Geometry.class));
    ret.setActuatorType(Actuator.Type.REFERENCE);
    return ret;
  }

  /**
   * Turn each contextualizer into a runtime-supported call and return the call.
   *
   * @param contextualizer
   * @return
   */
  private ServiceCall adaptContextualizer(
      Contextualizable contextualizer, Map<String, Object> parameters) {

    ServiceCall ret = null;

    if (contextualizer.getServiceCall() != null) {
      ret =
          parameters.isEmpty()
              ? contextualizer.getServiceCall()
              : new ServiceCallImpl(contextualizer.getServiceCall(), parameters);
    } else if (!contextualizer.getResourceUrns().isEmpty()) {
      ret =
          new ServiceCallImpl(
              RuntimeService.CoreFunctor.URN_RESOLVER.getServiceCallName(),
              "urns",
              contextualizer.getResourceUrns());
    } else if (contextualizer.getAccordingTo() != null) {
      ret =
          new ServiceCallImpl(
              RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
              "accordingTo",
              contextualizer.getAccordingTo());
    } else if (contextualizer.getClassification() != null) {
      ret =
          new ServiceCallImpl(
              RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
              "classification",
              contextualizer.getClassification());
    } else if (contextualizer.getLookupTable() != null) {
      ret =
          new ServiceCallImpl(
              RuntimeService.CoreFunctor.LUT_RESOLVER.getServiceCallName(),
              "lookupTable",
              contextualizer.getLookupTable());
    } else if (contextualizer.getExpression() != null) {
      // TODO distinguish integrators and take methods from annotations, pass with the parameters
      ret =
          new ServiceCallImpl(
              RuntimeService.CoreFunctor.EXPRESSION_RESOLVER.getServiceCallName(),
              "expression",
              contextualizer.getExpression());
    } else if (contextualizer.getLiteral() != null) {
      ret =
          new ServiceCallImpl(
              RuntimeService.CoreFunctor.CONSTANT_RESOLVER.getServiceCallName(),
              "value",
              contextualizer.getLiteral());
    }

    // TODO add remaining info from the contextualizable in the call's metadata
    // TODO more?
    if (ret != null && contextualizer.getTarget() != null) {
      ret.getParameters().put("_target", contextualizer.getTarget());
      ret.getParameters().put("_targetId", contextualizer.getTargetId());
    }

    return ret;
  }
}
