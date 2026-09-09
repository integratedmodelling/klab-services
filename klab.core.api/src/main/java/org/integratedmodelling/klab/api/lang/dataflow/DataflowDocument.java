package org.integratedmodelling.klab.api.lang.dataflow;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Initial portable semantic model for the observation language's dataflow persuasion. This is a
 * document, not a runtime Dataflow or a certificate of replay completeness. Immutable value records
 * contain no graph handles, database IDs, parser objects or live scopes. Only the finite
 * define/observe/reference subset is represented so far; see docs/DATAFLOW.md. JSON polymorphic
 * transport and parser adaptation are not yet registered for this model.
 */
public record DataflowDocument(
    String name,
    String version,
    Mode mode,
    List<Requirement> requirements,
    List<Declaration> declarations)
    implements Serializable {

  public DataflowDocument {
    requirements = List.copyOf(requirements);
    declarations = List.copyOf(declarations);
  }

  public enum Mode {
    REPLAY,
    ADAPTIVE
  }

  public enum RequirementKind {
    WORLDVIEW,
    RESOURCE,
    COMPONENT,
    NAMESPACE,
    CAPABILITY,
    OBSERVATION
  }

  public record Requirement(RequirementKind kind, String identity, String version, String checksum)
      implements Serializable {}

  public sealed interface Declaration extends Serializable
      permits ObservationDefinition, ObservationStep, ReferenceStep {
    String name();
  }

  /** A submitted input. Observable is the full closed observable source, including units. */
  public record ObservationDefinition(
      String name, String observable, String identity, String within, String geometry, Value value)
      implements Declaration {}

  /**
   * Chosen computations, in execution order. Strategy is provenance only, never a search request.
   */
  public record ObservationStep(
      String name,
      String observable,
      String within,
      String strategy,
      String geometry,
      List<Apply> computation)
      implements Declaration {
    public ObservationStep {
      computation = List.copyOf(computation);
    }
  }

  /** An external observation prerequisite, not a reference to a source database numeric ID. */
  public record ReferenceStep(String name, String identity, String observable)
      implements Declaration {}

  public record Apply(
      String implementation, String version, List<Argument> arguments, String output)
      implements Serializable {
    public Apply {
      arguments = List.copyOf(arguments);
    }
  }

  public record Argument(String name, Value value) implements Serializable {}

  public sealed interface Value extends Serializable
      permits Text, NumberValue, BooleanValue, ObservableValue, PortReference, Payload {}

  public record Text(String value) implements Value {}

  public record NumberValue(BigDecimal value) implements Value {}

  public record BooleanValue(boolean value) implements Value {}

  public record ObservableValue(String observable) implements Value {}

  /** Local symbol and optional named output; references never point to Java declaration objects. */
  public record PortReference(String node, String output) implements Value {}

  public record Payload(String resource, String checksum, String mediaType) implements Value {}
}
