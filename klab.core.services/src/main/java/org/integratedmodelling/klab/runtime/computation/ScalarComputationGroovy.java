package org.integratedmodelling.klab.runtime.computation;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import gg.jte.resolve.ResourceCodeResolver;
import groovy.lang.Script;
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
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import javax.tools.JavaCompiler;
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
  static KlabGroovyShell groovyShell = new KlabGroovyShell();

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
      codeInfo.setTemplateName("ScalarBufferFiller.jte");
      codeInfo.setClassName("ScalarComputation_" + Utils.Names.shortUUID());

      // constructor arguments
      List<Object> args = new ArrayList<>();
      args.add(scope);
      args.add(target);

      for (var step : steps) {
        if (step.expressionDescriptor
            instanceof GroovyProcessor.GroovyDescriptor groovyDescriptor) {
          for (var field : groovyDescriptor.getTemplateFields()) {
            codeInfo.getFieldDeclarations().add(field);
          }
          codeInfo.getMainCodeBlocks().add(groovyDescriptor.getProcessedCode());
          if (step.expressionDescriptor != null) {
            for (var identifier : step.expressionDescriptor.getIdentifiers().keySet()) {
              var desc = step.expressionDescriptor.getIdentifiers().get(identifier);
              if (desc.nonScalarReferenceCount() > 0) {
                codeInfo.getConstructorArguments().add("Observable " + identifier);
                args.add(groovyDescriptor.getKnownObservables().get(identifier));
                codeInfo
                    .getFieldDeclarations()
                    .add("Observable " + identifier + "Observable"); // TODO wrap
                codeInfo
                    .getFieldDeclarations()
                    .add(
                        "@Lazy Observation "
                            + identifier
                            + "Obs = {scope.getObservation("
                            + identifier
                            + "Observable)}()"); // TODO wrap
                codeInfo
                    .getConstructorInitializationStatements()
                    .add("this." + identifier + "Observable = " + identifier);
              }
              if (desc.scalarReferenceCount() > 0) {

                var observation = scope.getObservation(desc.observable());
                var buffers =
                    scope
                        .getDigitalTwin()
                        .getStateStorage()
                        .getStorage(observation);

                // add loop variables assignments besides self
                System.out.println("SPORP");
              }
            }
          }
        }
      }

      // 1. Get class template for the final class
      // 2. Create class fields as lazy injectors
      // 3. Create wrappers for observations, time, space and whatever else was referenced
      // 3. Create referenced concepts/observables as predefined fields
      // 4. Create main code for each step
      // 5. If scalar code is there, group it for code generation and define intermediate variables
      // 5.1 Create loop based on fill curve and offset where scalar code goes in
      // 6. Finalization

      TemplateOutput output = new StringOutput();
      templateEngine.render(codeInfo.getTemplateName(), codeInfo, output);
      var compiled = groovyShell.compile(output.toString(), Script.class, args.toArray());
      return new ScalarComputationGroovy(compiled, scope, output.toString());
    }
  }

  private Script script;
  private ContextScope scope;
  private String sourceCode;

  private ScalarComputationGroovy(Script groovyScript, ContextScope scope, String sourceCode) {
    this.script = groovyScript;
    this.scope = scope;
    this.sourceCode = sourceCode;
  }

  @Override
  public boolean execute() {
    try {
      var ret = script.run();
      if (ret instanceof Boolean) {
        return (Boolean) ret;
      }
      return true;
    } catch (Throwable t) {
      scope.error(t, sourceCode);
    }
    return false;
  }

  public static Builder builder(Observation target, ContextScope scope, Actuator actuator) {
    return new BuilderImpl(target, scope, actuator);
  }
}
