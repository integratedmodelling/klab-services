package org.integratedmodelling.klab.runtime.computation;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;

/**
 * Scalar computation implementation using Groovy-based expressions and turning the sequence into a
 * compiled Java class for execution.
 */
public class ScalarComputationGroovy implements ScalarComputation {

  static class BuilderImpl implements Builder {

    public BuilderImpl(Observation target, ContextScope scope, Actuator actuator) {}

    @Override
    public boolean add(ServiceCall contextualizable) {

      return false;
    }

    @Override
    public ScalarComputation build() {
      return null;
    }
  }

  @Override
  public boolean run(Storage<?> storage) {
    return false;
  }

  public static Builder builder(Observation target, ContextScope scope, Actuator actuator) {
    return new BuilderImpl(target, scope, actuator);
  }
}
