package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Typed JSON values include output arguments such as match's binding map. */
public record SemanticCycleResult(String result, List<String> arguments) implements Serializable {
  public SemanticCycleResult {
    Objects.requireNonNull(result);
    arguments = List.copyOf(arguments);
  }
}
