package org.integratedmodelling.klab.runtime.computation;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.CoreLibrary;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;

import java.util.ArrayList;
import java.util.List;

/**
 * Scalar computation implementation using Groovy-based expressions and turning the sequence into a
 * compiled Java class for execution.
 */
public class ScalarComputationGroovy implements ScalarComputation {

  static class BuilderImpl implements Builder {

    // list of these to describe the sequence of steps
    class Step {
      String target = "self";
    }

    public BuilderImpl(Observation target, ContextScope scope, Actuator actuator) {}

    @Override
    public boolean add(ServiceCall contextualizable) {
      if (RuntimeService.CoreFunctor.EXPRESSION_RESOLVER
          .getServiceCallName()
          .equals(contextualizable.getUrn())) {
        // compile expression and check fit. May be vectorial (compile outside the loop) or scalar
      } else if (RuntimeService.CoreFunctor.LUT_RESOLVER
              .getServiceCallName()
              .equals(contextualizable.getUrn())) {
          // LUT, classification or reference to codelist
      } else if (RuntimeService.CoreFunctor.CONSTANT_RESOLVER
              .getServiceCallName()
              .equals(contextualizable.getUrn())) {
        // check types
      } else {
        // scalar contextualizer
      }

//      calls.add(contextualizable);
      return true;
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
