package org.integratedmodelling.klab.runtime.computation;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import gg.jte.resolve.ResourceCodeResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * Scalar computation implementation using Groovy-based expressions and turning the sequence into a
 * compiled Java class for execution. Each computation receives all the scanners pertaining to a
 * single shard.
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
    private final Map<String, Observation> observations;
    private static GroovyProcessor groovyProcessor = new GroovyProcessor();

    // list of th
    //
    // ese to describe the sequence of steps
    class Step {
      String target = Dataflow.SELF_ID;
      boolean scalar = true;
      Expression.Descriptor expressionDescriptor;
      // TODO compiled LUT and the like
      Object constantLiteral;
    }

    public BuilderImpl(
        Observation target,
        ContextScope scope,
        Actuator actuator,
        Map<String, Observation> observations) {
      this.scope = scope;
      this.actuator = actuator;
      this.target = target;
      this.observations = observations;
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
                List.of(actuator.getObservation()),
                actuator.getChildren().stream().map(Actuator::getObservation).toList());
        step.scalar =
            step.expressionDescriptor.getIdentifiers().values().stream()
                .anyMatch(id -> id.observation() != null && id.scalarReferenceCount() > 0);
        if (Utils.Notifications.hasErrors(step.expressionDescriptor.getNotifications())) {
          target.getNotifications().addAll(step.expressionDescriptor.getNotifications());
          return false;
        }

        steps.add(step);

      } else if (RuntimeService.CoreFunctor.LUT_RESOLVER
          .getServiceCallName()
          .equals(contextualizable.getUrn())) {
        // check types
        // LUT, classification or reference to codelist. Should build a LUT object for internal
        // processing and
        // generate the scalar code using it. The result should STILL have a proper GroovyDescriptor
        // with
        // the scalar call.
      } else if (RuntimeService.CoreFunctor.CONSTANT_RESOLVER
          .getServiceCallName()
          .equals(contextualizable.getUrn())) {
        // check types
        // insert streamlined code, same as before (buffer.fill(value) TODO using native methods)
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

      record VarInfo(String name, String type, int index) {}

      // ordering in this one is important
      Map<String, VarInfo> scalarBuffers = new LinkedHashMap<>();
      var codeStatements = new ArrayList<String>();

      for (var step : steps) {
        if (step.expressionDescriptor
            instanceof GroovyProcessor.GroovyDescriptor groovyDescriptor) {

          /*
           * Template fields are variables containing constant concepts and other fields that need
           * initialization.
           */
          for (var field : groovyDescriptor.getTemplateFields()) {
            codeInfo.getFieldDeclarations().add(field);
          }

          if (step.expressionDescriptor != null) {
            int n = 1;
            codeStatements.add(groovyDescriptor.getProcessedCode());

            for (var identifier : step.expressionDescriptor.getIdentifiers().keySet()) {
              var desc = step.expressionDescriptor.getIdentifiers().get(identifier);
              var observation = observations.get(identifier);
              if (desc.nonScalarReferenceCount() + desc.scalarReferenceCount() > 0) {
                args.add(observation);
              }

              /*
               * Initialize the observation fields and the constructor arguments, passing every
               * observation needed besides self.
               */
              codeInfo.getConstructorArguments().add("Observation " + identifier);
              codeInfo.getFieldDeclarations().add("Observation __" + identifier);

              if (desc.scalarReferenceCount() > 0) {
                codeInfo
                    .getConstructorInitializationStatements()
                    .add("this.__" + identifier + " = " + identifier);

                var typeDeclaration = getTypeDeclaration(observation);
                scalarBuffers.put(identifier, new VarInfo(identifier, typeDeclaration, n++));
                codeInfo
                    .getLoopVariableAssignments()
                    .add("def " + identifier + " = " + identifier + "Buffer.get()");
              }

              /*
               * TODO add any other reserved identifiers and bridges to scale, scope and scheduler if
               *  referenced.
               */

              /*
               * Create observation wrappers inline before the main loop
               */
              if (desc.nonScalarReferenceCount() > 0) {
                codeInfo
                    .getBodyInitializationStatements()
                    .add(
                        "def "
                            + identifier
                            + "Obs = new ObservationWrapper(__"
                            + identifier
                            + ", event)");
              }
            }
          }
        }
      }

      // buffer creation
      codeInfo
          .getBodyInitializationStatements()
          .add(
              "def selfBuffer = ("
                  + getScannerType(target, codeInfo)
                  + ") scanners.get(\"self\")\n");

      codeInfo.getMainCodeBlocks().addAll(codeStatements);

      for (String var : scalarBuffers.keySet()) {
        var info = scalarBuffers.get(var);
        codeInfo
            .getBodyInitializationStatements()
            .add(
                "def "
                    + info.name
                    + "Buffer = ("
                    + getScannerType(observations.get(info.name), codeInfo)
                    + ") scanners.get(\""
                    + info.name
                    + "\")\n");
      }

      TemplateOutput output = new StringOutput();
      templateEngine.render(codeInfo.getTemplateName(), codeInfo, output);
      var compiled = groovyShell.compile(output.toString(), ExpressionBase.class, args.toArray());
      return new ScalarComputationGroovy(compiled, scope, output.toString());
    }

    private String getScannerType(Observation observation, TemplateCodeInfo codeInfo) {

      var shardingStrategy =
          observation.getContextualizationData() == null
              ? scope
                  .getService(RuntimeService.class)
                  .getDefaultShardingStrategy(observation, scope)
              : observation.getContextualizationData().getNativeShardingStrategy();

      return switch (shardingStrategy.getDataType()) {
        case DOUBLE -> "Storage.DoubleScanner";
        case FLOAT -> "Storage.ObjectScanner";
        case INTEGER -> "Storage.IntegerScanner";
        case LONG -> "Storage.LongScanner";
        case KEYED -> "Storage.KeyedScanner";
        case BOOLEAN -> "Storage.BooleanScanner";
      };
    }

    private String getTypeDeclaration(Observation observation) {

      var shardingStrategy =
          observation.getContextualizationData() == null
              ? scope
                  .getService(RuntimeService.class)
                  .getDefaultShardingStrategy(observation, scope)
              : observation.getContextualizationData().getNativeShardingStrategy();

      return switch (shardingStrategy.getDataType()) {
        case DOUBLE -> "double";
        case FLOAT -> "float";
        case INTEGER -> "int";
        case LONG -> "long";
        case KEYED -> "Concept";
        case BOOLEAN -> "boolean";
      };
    }
  }

  private ExpressionBase script;
  private ContextScope scope;
  private String sourceCode;

  private ScalarComputationGroovy(
      ExpressionBase groovyScript, ContextScope scope, String sourceCode) {
    this.script = groovyScript;
    this.scope = scope;
    this.sourceCode = sourceCode;
  }

  @Override
  public boolean execute(
      Map<String, Storage.Scanner> scanners, Scheduler.Event event, ContextScope scope) {
    try {
      return script.run(scanners, event, scope);
    } catch (Throwable t) {
      System.out.println("Scalar code fucked up: " + Utils.Exceptions.stackTrace(t));
      scope.error(t, sourceCode);
    }
    return false;
  }

  public static Builder builder(
      Observation target,
      ContextScope scope,
      Actuator actuator,
      Map<String, Observation> observations) {
    return new BuilderImpl(target, scope, actuator, observations);
  }
}
