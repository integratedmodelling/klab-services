package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.integratedmodelling.common.diagnostics.SemanticOperationTrace;
import org.integratedmodelling.common.services.client.SemanticCycleCodec;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** One accepted-actor operation, emitted after releasing its monitor. */
final class SemanticExecutionProfile implements AutoCloseable {
  private final String operation;
  private final String id;
  private final String cycle;
  private final String session;
  private final long revision;
  private final long started;
  long preparedAt;
  long enteredAt;
  long boundAt;
  long computedAt;
  private long finishedAt;
  private long refinement;
  private long finalRefinement;
  private Object input;
  private Scope scope;
  private Object result;
  private String wireArgument;

  static SemanticExecutionProfile start(
      String operation, String session, long revision, String cycle, long started) {
    return SemanticOperationTrace.enabled(operation)
        ? new SemanticExecutionProfile(operation, session, revision, cycle, started) : null;
  }

  private SemanticExecutionProfile(
      String operation, String session, long revision, String cycle, long started) {
    this.operation = operation;
    var inbound = SemanticOperationTrace.currentId(operation);
    this.id = inbound == null ? UUID.randomUUID().toString() : inbound;
    this.session = session;
    this.revision = revision;
    this.cycle = cycle;
    this.started = started;
  }

  void input(Object input, Scope scope, String wireArgument, ReasonerService actor, long bound) {
    this.input = input;
    this.scope = scope;
    this.wireArgument = wireArgument;
    this.refinement = actor.semanticRefinementGeneration();
    this.boundAt = bound;
  }

  void computed(Object result, ReasonerService actor, long computed) {
    this.result = result;
    this.finalRefinement = actor.semanticRefinementGeneration();
    this.computedAt = computed;
  }

  void completed(long finished) {
    finishedAt = finished;
  }

  @Override
  public void close() {
    try {
      var fields = new LinkedHashMap<String, Object>();
      fields.put("id", id);
      fields.put("operation", operation);
      fields.put("cycle", cycle);
      fields.put("semanticSession", session);
      fields.put("knowledgeRevision", revision);
      fields.put("refinement", boundAt == 0 ? null : refinement);
      fields.put("finalRefinement", computedAt == 0 ? null : finalRefinement);
      fields.put("completed", finishedAt != 0);
      long finished = finishedAt == 0 ? System.nanoTime() : finishedAt;
      fields.put("elapsedNanos", finished - started);
      fields.put("preparationNanos", phase(started, preparedAt));
      fields.put("actorWaitNanos", phase(preparedAt, enteredAt));
      fields.put("bindingNanos", phase(enteredAt, boundAt));
      fields.put("executionNanos", phase(boundAt, computedAt));
      fields.put("encodingNanos", phase(computedAt, finishedAt));
      fields.put("result", result instanceof Collection<?> list ? list.size()
          : result instanceof Observable observable ? observable.getUrn() : null);
      if (input != null) {
        try {
          describeInput(fields);
        } catch (RuntimeException unavailable) {
          fields.put("inputUnavailable", true);
        }
      }
      SemanticOperationTrace.emit("SEMANTIC_OPERATION_REASONER", fields);
    } catch (RuntimeException ignored) {
      // A profiling snapshot cannot change the semantic result.
    }
  }

  private void describeInput(LinkedHashMap<String, Object> fields) {
    var shape = new LinkedHashMap<String, Object>();
    if (input instanceof ObservableBuildStrategy builder) {
      var base = builder.getBaseObservable() == null
          ? builder.getBaseConcept() : builder.getBaseObservable();
      shape.put("base", meaning(base));
      shape.put("operationTypes",
          builder.getOperations().stream().map(op -> op.getType().name()).toList());
      shape.put("operationsHash", hash(SemanticCycleCodec.encode(builder.getOperations(), java.util.List.class)));
    } else if (input instanceof Observation observation) {
      var requested = observation.getObservable();
      shape.put("requested", meaning(requested));
      shape.put("contextualization",
          requested == null || requested.getContextualization() == null
              ? null : requested.getContextualization().name());
      fields.put("supportHash", geometryHash(observation));
    }
    if (scope instanceof ContextScope context) {
      var focus = context.getContextObservation();
      shape.put("focus", meaning(focus));
      shape.put("observer", meaning(context.getObserver()));
      shape.put("source", meaning(context.getSourceObservation()));
      shape.put("target", meaning(context.getTargetObservation()));
      fields.put("focusSupportHash", geometryHash(focus));
    }
    fields.put("shape", shape);
    fields.put("shapeHash", SemanticContentFingerprint.sha256(SemanticContentFingerprint.canonical(shape)));
    fields.put("argumentHash", hash(wireArgument == null
        ? SemanticCycleCodec.encode(input, input instanceof Observation ? Observation.class
            : ObservableBuildStrategy.class)
        : wireArgument));
  }

  private static String meaning(Observation observation) {
    return observation == null ? null : meaning(observation.getObservable());
  }

  private static String meaning(Semantics semantics) {
    return semantics == null ? null : semantics.getUrn();
  }

  private static String geometryHash(Observation observation) {
    var geometry = observation == null ? null : observation.getGeometry();
    return geometry == null ? null : hash(Geometry.forTransport(geometry).encode());
  }

  private static String hash(String value) {
    return SemanticValidationRequest.sourceHash(value);
  }

  private static Long phase(long from, long to) {
    return from == 0 || to == 0 ? null : to - from;
  }
}
