package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Method signatures are matched against the server's explicit semantic-operation allowlist. */
public record SemanticCycleInvocation(SemanticCycleRequest cycle,String method,
    List<String> parameterTypes,List<String> arguments) implements Serializable {
  public SemanticCycleInvocation {
    Objects.requireNonNull(cycle);Objects.requireNonNull(method);
    parameterTypes=List.copyOf(parameterTypes);arguments=List.copyOf(arguments);
    if(parameterTypes.size()!=arguments.size())throw new IllegalArgumentException("Semantic call arity mismatch");
  }
}
