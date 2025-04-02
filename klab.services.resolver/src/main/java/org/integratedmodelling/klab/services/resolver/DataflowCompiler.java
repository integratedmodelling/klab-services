package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.common.services.client.resolver.DataflowEncoder;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * A Compiler is instantiated in context. TODO should also take a parent Dataflow and fill the
 * catalog in from it.
 */
public class DataflowCompiler {

  private final ResolutionGraph resolutionGraph;
  private final ContextScope scope;
  private final Observation observation;
  private Map<Observable, Observation> catalog = new HashMap<>();

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
      return Dataflow.empty();
    }

    System.out.println(Utils.Graphs.dump(resolutionGraph.graph()));

    Map<Observable, String> catalog = new HashMap<>();
    var ret = new DataflowImpl();
//    ret.setTarget(observation);
    ret.setResolvedCoverage(resolutionGraph.getResolvedCoverage());
    for (var node : resolutionGraph.rootNodes()) {
      /*
      These MUST be observations. We check for now but it shouldn't happen.
       */
      if (!(node instanceof Observation)) {
        throw new KlabIllegalStateException("Resolution root is not an observation");
      }
      ret.getComputation()
          .addAll(compileObservation(observation, GeometryRepository.INSTANCE.scale(observation.getGeometry()), null));
    }

    // TODO remove
    System.out.println(Utils.Dataflows.encode(ret, scope));

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
      Observation observation, Geometry coverage, ObservationStrategy strategy) {

    if (catalog.containsKey(observation.getObservable())) {
      var ret = new ActuatorImpl();
      ret.setObservable(observation.getObservable());
      ret.setId(observation.getId());
      ret.setCoverage(coverage.as(Geometry.class));
      ret.setActuatorType(Actuator.Type.REFERENCE);
      return List.of(ret);
    }

    catalog.put(observation.getObservable(), observation);

    var ret = new ArrayList<Actuator>();
    for (var edge : resolutionGraph.graph().outgoingEdgesOf(observation)) {

      var child = resolutionGraph.graph().getEdgeTarget(edge);
      var childCoverage = edge.coverage;

      if (child instanceof ObservationStrategy observationStrategy) {
        var actuator = new ActuatorImpl();
        actuator.setObservable(observation.getObservable());
        actuator.setId(observation.getId());
        actuator.setActuatorType(Actuator.Type.OBSERVE);
        actuator.setCoverage(childCoverage == null ? null : childCoverage.as(Geometry.class));
        actuator.setResolvedGeometry(observation.getGeometry());
        actuator.setStrategyUrn(observationStrategy.getUrn());
        compileStrategy(actuator, observation, childCoverage, observationStrategy);
        ret.add(actuator);
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

      if (child instanceof Model model) {
        compileModel(observationActuator, observation, coverage, observationStrategy, model);
      }
    }
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
      Model model) {

    for (var edge : resolutionGraph.graph().outgoingEdgesOf(model)) {

      var child = resolutionGraph.graph().getEdgeTarget(edge);
      var coverage = edge.coverage;

      if (child instanceof Observation dependentObservation) {
        observationActuator
            .getChildren()
            .addAll(compileObservation(dependentObservation, coverage, observationStrategy));
      } else if (child instanceof Observable observable) {
        observationActuator
            .getChildren()
            .add(compileReference(observable, coverage, edge.observationId));
      }
    }

    for (var contextualizer : model.getComputation()) {
      observationActuator.getComputation().add(adaptContextualizer(contextualizer));
    }
  }

  private Actuator compileReference(Observable observable, Coverage coverage, long observationId) {
    var ret = new ActuatorImpl();
    ret.setObservable(observable);
    ret.setId(observationId);
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
  private ServiceCall adaptContextualizer(Contextualizable contextualizer) {

    ServiceCall ret = null;

    if (contextualizer.getServiceCall() != null) {
      ret = contextualizer.getServiceCall();
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
