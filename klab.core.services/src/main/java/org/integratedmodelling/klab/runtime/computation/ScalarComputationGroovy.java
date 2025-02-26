package org.integratedmodelling.klab.runtime.computation;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.ResourceCodeResolver;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.CoreLibrary;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.language.LanguageService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Scalar computation implementation using Groovy-based expressions and turning the sequence into a
 * compiled Java class for execution.
 */
public class ScalarComputationGroovy implements ScalarComputation {

  /*
  TODO use createPrecompiled
   */
  static TemplateEngine templateEngine =
      TemplateEngine.create(new ResourceCodeResolver("code/templates"), ContentType.Plain);


  static class BuilderImpl implements Builder {

    private final List<Step> steps = new ArrayList<>();
    private final ContextScope scope;
    private final Actuator actuator;
    private final Observation target;
    private static GroovyProcessor groovyProcessor = new GroovyProcessor();

    // list of these to describe the sequence of steps
    class Step {
      String target = "self";
      boolean scalar = true;
      Expression.Descriptor expressionDescriptor;
      // TODO compiled LUT and the like
      Object constantLiteral;
    }

    public BuilderImpl(Observation target, ContextScope scope, Actuator actuator) {
      this.scope = scope;
      this.actuator = actuator;
      this.target = target;
    }

    @Override
    public boolean add(ServiceCall contextualizable) {
      if (RuntimeService.CoreFunctor.EXPRESSION_RESOLVER
          .getServiceCallName()
          .equals(contextualizable.getUrn())) {
        // compile expression and check fit. May be vectorial (compile outside the loop) or scalar
        Step step = new Step();
        var expressionCode =
            contextualizable.getParameters().get("expression", ExpressionCode.class);
        if (contextualizable.getParameters().contains("target")) {
          step.target = contextualizable.getParameters().get("target", String.class);
        }

        step.expressionDescriptor =
            groovyProcessor.analyze(
                expressionCode,
                scope,
                List.of(actuator.getObservable()),
                actuator.getChildren().stream().map(Actuator::getObservable).toList());
        step.scalar =
            step.expressionDescriptor.getIdentifiers().values().stream()
                .anyMatch(id -> id.observable() != null && id.scalarReferenceCount() > 0);

        if (Utils.Notifications.hasErrors(step.expressionDescriptor.getNotifications())) {
          return false;
        }

        steps.add(step);

      } else if (RuntimeService.CoreFunctor.LUT_RESOLVER
          .getServiceCallName()
          .equals(contextualizable.getUrn())) {
        // check types
        // LUT, classification or reference to codelist. Should build a LUT object for internal
        // processing and
        // generate the scalar code using it.
      } else if (RuntimeService.CoreFunctor.CONSTANT_RESOLVER
          .getServiceCallName()
          .equals(contextualizable.getUrn())) {
        // check types
        // insert streamlined code
      } else {
        // non-scalar contextualizer
      }

      return true;
    }

    @Override
    public ScalarComputation build() {

      var codeInfo = new TemplateCodeInfo();

      for (var step : steps) {
        System.out.println("popopo");
      }

      // 1. Get class template for the final class
      // 2. Create class fields as lazy injectors
      // 3. Create wrappers for observations, time, space and whatever else was referenced
      // 3. Create referenced concepts/observables as predefined fields
      // 4. Create main code for each step
      // 5. If scalar code is there, group it for code generation and define intermediate variables
      // 5.1 Create loop based on fill curve and offset where scalar code goes in
      // 6. Finalization



      return null;
    }
  }

  @Override
  public boolean execute() {
    return false;
  }

  public static Builder builder(Observation target, ContextScope scope, Actuator actuator) {
    return new BuilderImpl(target, scope, actuator);
  }
}
