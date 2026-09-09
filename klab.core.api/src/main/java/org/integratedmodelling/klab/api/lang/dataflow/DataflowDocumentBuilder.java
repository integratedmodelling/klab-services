package org.integratedmodelling.klab.api.lang.dataflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.integratedmodelling.klab.api.lang.dataflow.DataflowDocument.*;

/**
 * Builds a detached document and checks structural symbol/port integrity for the initial subset.
 * Does not extract a graph, validate observable syntax, check installed capabilities or certify
 * replay.
 */
public final class DataflowDocumentBuilder {
  private final String name;
  private final String version;
  private final Mode mode;
  private final List<Requirement> requirements = new ArrayList<>();
  private final List<Declaration> declarations = new ArrayList<>();

  public DataflowDocumentBuilder(String name, String version, Mode mode) {
    this.name = required(name, "document name");
    this.version = required(version, "document version");
    this.mode = Objects.requireNonNull(mode, "mode");
  }

  public DataflowDocumentBuilder require(Requirement requirement) {
    requirements.add(Objects.requireNonNull(requirement));
    return this;
  }

  /**
   * Insertion order must already be dependency-safe; the builder never sorts by timestamp or name.
   */
  public DataflowDocumentBuilder add(Declaration declaration) {
    declarations.add(Objects.requireNonNull(declaration));
    return this;
  }

  public DataflowDocument build() {
    if (declarations.isEmpty()) {
      throw new IllegalArgumentException("A dataflow document requires a declaration");
    }
    for (var requirement : requirements) {
      Objects.requireNonNull(requirement.kind(), "requirement kind");
      required(requirement.identity(), "requirement identity");
    }
    Map<String, Set<String>> symbols = new HashMap<>();
    for (var declaration : declarations) {
      String symbol = required(declaration.name(), "declaration name");
      if (symbols.containsKey(symbol)) {
        throw new IllegalArgumentException("Duplicate dataflow symbol: " + symbol);
      }
      switch (declaration) {
        case ObservationDefinition definition -> {
          required(definition.observable(), "observable");
          required(definition.identity(), "observation identity");
          context(definition.within(), symbols);
          if (definition.value() != null) {
            value(definition.value(), symbols);
          }
          symbols.put(symbol, new HashSet<>());
        }
        case ReferenceStep reference -> {
          required(reference.identity(), "reference identity");
          required(reference.observable(), "observable");
          symbols.put(symbol, new HashSet<>());
        }
        case ObservationStep observation -> {
          required(observation.observable(), "observable");
          context(observation.within(), symbols);
          // A computation may consume a prior output in this same observation, but not itself
          // as a whole before it has been produced.
          Set<String> outputs = new HashSet<>();
          for (var call : observation.computation()) {
            required(call.implementation(), "implementation");
            required(call.version(), "implementation version");
            String output = required(call.output(), "output port");
            Set<String> arguments = new HashSet<>();
            for (var argument : call.arguments()) {
              if (!arguments.add(required(argument.name(), "argument name"))) {
                throw new IllegalArgumentException("Duplicate argument: " + argument.name());
              }
              if (argument.value() instanceof PortReference ref && symbol.equals(ref.node())) {
                if (ref.output() == null || !outputs.contains(ref.output())) {
                  throw new IllegalArgumentException("Unavailable local output: " + ref);
                }
              } else {
                value(argument.value(), symbols);
              }
            }
            if (!outputs.add(output)) {
              throw new IllegalArgumentException("Duplicate output port: " + symbol + "." + output);
            }
          }
          symbols.put(symbol, outputs);
        }
      }
    }
    return new DataflowDocument(name, version, mode, requirements, declarations);
  }

  private static void context(String context, Map<String, Set<String>> symbols) {
    if (context != null && !symbols.containsKey(context)) {
      throw new IllegalArgumentException("Unknown or forward context: " + context);
    }
  }

  private static void value(Value value, Map<String, Set<String>> symbols) {
    Objects.requireNonNull(value, "value");
    switch (value) {
      case PortReference reference -> {
        var ports = symbols.get(reference.node());
        if (ports == null || (reference.output() != null && !ports.contains(reference.output()))) {
          throw new IllegalArgumentException("Unknown or forward port reference: " + reference);
        }
      }
      case Text text -> Objects.requireNonNull(text.value(), "text value");
      case NumberValue number -> Objects.requireNonNull(number.value(), "number value");
      case BooleanValue ignored -> {}
      case ObservableValue observable -> required(observable.observable(), "observable value");
      case Payload payload -> {
        required(payload.resource(), "payload resource");
        required(payload.checksum(), "payload checksum");
        required(payload.mediaType(), "payload media type");
      }
    }
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing " + field);
    }
    return value;
  }
}
