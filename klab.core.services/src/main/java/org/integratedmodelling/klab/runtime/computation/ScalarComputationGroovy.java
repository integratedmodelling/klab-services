package org.integratedmodelling.klab.runtime.computation;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;
import gg.jte.resolve.ResourceCodeResolver;
import groovy.lang.Script;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // list of th
    //
    // ese to describe the sequence of steps
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

      /**
       * TODO all steps must generate merged local variables (using a map) and individual scalar
       * code blocks. These must be generated inside the main loop within the concurrent buffer
       * mapper function. The main loop iterates the selfBuffer[n] which is the last parameter in
       * the constructor and is at location 0 - others follow the numbering in the LinkedHashMap
       * describing the additional scalar dependencies.
       *
       * <p>Add selfBuffers + all others to the fields and constructor
       */
      record VarInfo(String name, String type, int index) {}

      // ordering in this one is important
      Map<String, VarInfo> scalarBuffers = new LinkedHashMap<>();
      var selfStorage = scope.getDigitalTwin().getStorageManager().getStorage(target);
      codeInfo.getFieldDeclarations().add("Observation __self");
      var codeStatements = new ArrayList<String>();

      for (var step : steps) {
        if (step.expressionDescriptor
            instanceof GroovyProcessor.GroovyDescriptor groovyDescriptor) {
          for (var field : groovyDescriptor.getTemplateFields()) {
            codeInfo.getFieldDeclarations().add(field);
          }
          codeInfo.getMainCodeBlocks().add(groovyDescriptor.getProcessedCode());
          if (step.expressionDescriptor != null) {
            int n = 1;
            for (var identifier : step.expressionDescriptor.getIdentifiers().keySet()) {
              var desc = step.expressionDescriptor.getIdentifiers().get(identifier);
              if (desc.nonScalarReferenceCount() + desc.scalarReferenceCount() > 0) {
                args.add(groovyDescriptor.getKnownObservables().get(identifier));
                codeInfo.getFieldDeclarations().add("Observation __" + identifier);
                codeInfo
                    .getFieldDeclarations()
                    .add(
                        "@Lazy ObservationWrapper "
                            + identifier
                            + "Obs = {new ObservationWrapper(__"
                            + identifier
                            + ")}()");
                codeInfo
                    .getConstructorInitializationStatements()
                    .add("this.__" + identifier + " = " + identifier);

                codeStatements.add(groovyDescriptor.getProcessedCode());
              }
              if (desc.scalarReferenceCount() > 0) {

                var observation = scope.getObservation(desc.observable());
                var storage = scope.getDigitalTwin().getStorageManager().getStorage(observation);

                var typeDeclaration = getTypeDeclaration(storage);
                scalarBuffers.put(identifier, new VarInfo(identifier, typeDeclaration, n++));
              }
            }
          }
        }
      }

      scalarBuffers.put("self", new VarInfo("self", getTypeDeclaration(selfStorage), 0));
      codeInfo.getConstructorInitializationStatements().add("this.__self = self");

      // buffer creation
      StringBuilder bufferDeclaration =
          new StringBuilder("def bufferArray = Utils.Collections.transpose(selfBuffers");
      for (String var : scalarBuffers.keySet()) {
        var info = scalarBuffers.get(var);
        codeInfo
            .getBodyInitializationStatements()
            .add(
                "def "
                    + info.name
                    + "Buffers = scope.getDigitalTwin().getStorage(__"
                    + info.name
                    + ").buffers(geometry)");

        if (info.index > 0) {
          bufferDeclaration.append(", ").append(info.name).append("Buffers");
          codeInfo
              .getMainCodeBlocks()
              .add(info.type + " " + info.name + " = bufferArray[" + info.index + "].get()");
        }
      }

      bufferDeclaration.append(")");

      if (codeStatements.size() == 1) {
        codeInfo.getMainCodeBlocks().add("bufferArray[0] = " + codeStatements.getFirst());
      } else {
        for (var statement : codeStatements) {
          // TODO first statement declares self = statement, last statements sets bufferArray[0] to
          //  the computed value
        }
      }

      codeInfo.getBodyInitializationStatements().add(bufferDeclaration.toString());
      TemplateOutput output = new StringOutput();
      templateEngine.render(codeInfo.getTemplateName(), codeInfo, output);
      var compiled = groovyShell.compile(output.toString(), ExpressionBase.class, args.toArray());
      return new ScalarComputationGroovy(compiled, scope, output.toString());
    }

    private String getTypeDeclaration(Storage storage) {
      return switch (storage.getType()) {
        case BOXING -> "Object";
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
  public boolean execute(Geometry geometry) {
    try {
      return script.run(geometry);
    } catch (Throwable t) {
      scope.error(t, sourceCode);
    }
    return false;
  }

  public static Builder builder(Observation target, ContextScope scope, Actuator actuator) {
    return new BuilderImpl(target, scope, actuator);
  }
}
