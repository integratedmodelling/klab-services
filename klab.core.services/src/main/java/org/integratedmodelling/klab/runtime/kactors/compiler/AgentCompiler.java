package org.integratedmodelling.klab.runtime.kactors.compiler;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.lang.model.element.Modifier;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.exceptions.KlabActorException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Ternary;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.api.utils.Utils.CLI;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;

/**
 * Compiles an analyzed {@link KActorsBehavior} into Java source backed by {@link RuntimeAgentBase}.
 */
public class AgentCompiler {

  /**
   * Resolution boundary for resources and Java actor extensions. The resources-backed behavior
   * lookup is provided by default; component-aware callers can supply actor descriptors and their
   * non-serializable implementations after validation has selected the correct overloads.
   */
  public interface Resolver {

    default KActorsBehavior resolveBehavior(String urn, UserScope scope) {
      if (scope == null) {
        return null;
      }
      var resources = scope.getService(ResourcesService.class);
      return resources == null ? null : resources.retrieve(urn, KActorsBehavior.class, scope);
    }

    default ResolvedActor resolveActor(String urn, UserScope scope) {
      return null;
    }

    /**
     * Assess whether a value described by {@code sourceVariable} can be adapted to an instance of
     * {@code targetBehavior}. Runtime environments may consult component adapters, source runtime
     * types, or behavior-specific construction contracts.
     */
    default List<Notification> validateBehaviorAdaptation(
        KActorsBehavior targetBehavior,
        KActorsVisitor.VariableInfo sourceVariable,
        UserScope scope) {
      return List.of();
    }
  }

  /** Compile-time view of a Java actor selected by validation. */
  public record ResolvedActor(
      Extensions.ActorDescriptor descriptor,
      Map<String, ComponentRegistry.ServiceImplementation> verbs) {

    public ResolvedActor {
      verbs = verbs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(verbs));
    }

    public Class<?> implementationClass() {
      return verbs.values().stream()
          .map(implementation -> implementation == null ? null : implementation.implementation)
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(null);
    }
  }

  /** Stable pair of runtime-aware compiler extension points. */
  public record Environment(KActorsVisitor.Validator validator, Resolver resolver) {}

  private static final Resolver DEFAULT_RESOLVER = new Resolver() {};
  private static final Map<String, Class<? extends RuntimeAgentBase>> compiledActorClasses =
      new ConcurrentHashMap<>();
  private static final Map<String, String> generatedActorSources = new ConcurrentHashMap<>();

  private final KActorsBehavior behavior;
  private final UserScope scope;
  private final KActorsVisitor.Validator validator;
  private final Resolver resolver;
  private final String packageName = "org.integratedmodelling.klab.runtime.kactors.generated";
  private final List<Notification> notifications = new ArrayList<>();
  private final Map<String, String> dependencySources = new LinkedHashMap<>();
  private final IdentityHashMap<KActorsStatement.Verb, KActorsVisitor.CallInfo> calls =
      new IdentityHashMap<>();
  private final IdentityHashMap<KActorsValue, String> expressionFields = new IdentityHashMap<>();
  private final Map<String, KActorsVisitor.ImportInfo> imports = new LinkedHashMap<>();
  private final Map<String, ResolvedActor> resolvedActors = new LinkedHashMap<>();
  private final Set<Class<?>> requiredRuntimeClasses = new LinkedHashSet<>();
  private final Map<String, KActorsBehavior> inheritedBehaviors = new LinkedHashMap<>();
  private final Map<String, String> inheritedFields = new LinkedHashMap<>();
  private final BehaviorAnalyzer analyzer;
  private String sourceCode;
  private String qualifiedClassName;
  private int generatedName;

  /** Build the Java-extension half of a resolver from a live component registry. */
  public static Resolver componentResolver(ComponentRegistry registry) {
    Objects.requireNonNull(registry, "registry");
    return new Resolver() {
      @Override
      public ResolvedActor resolveActor(String urn, UserScope scope) {
        var descriptors = registry.getActorDescriptors(urn, null);
        if (descriptors.isEmpty()) {
          return null;
        }
        var descriptor = descriptors.getFirst();
        var implementations = new LinkedHashMap<String, ComponentRegistry.ServiceImplementation>();
        for (var verb : descriptor.verbs) {
          var implementation = registry.implementation(verb);
          if (implementation != null && verb.serviceInfo != null) {
            implementations.put(verb.serviceInfo.getName(), implementation);
            String name = verb.serviceInfo.getName();
            int separator = name.lastIndexOf('.');
            implementations.put(
                separator < 0 ? name : name.substring(separator + 1), implementation);
          }
        }
        return new ResolvedActor(descriptor, implementations);
      }
    };
  }

  /**
   * Build the compiler environment used by a runtime service. The resolver combines the resources
   * visible in the supplied scope with the live component registry; the validator uses the same
   * view to classify imported k.Actors actions and Java verbs.
   */
  public static Environment runtimeEnvironment(ComponentRegistry registry, UserScope scope) {
    Objects.requireNonNull(registry, "registry");
    return runtimeEnvironment(componentResolver(registry), scope);
  }

  /**
   * Build the runtime validator around a caller-supplied resolver. This overload is useful for
   * services that compose resource and component resolution themselves.
   */
  public static Environment runtimeEnvironment(Resolver resolver, UserScope scope) {
    Objects.requireNonNull(resolver, "resolver");
    var validator =
        new KActorsVisitor.LenientValidator() {
          @Override
          public Verb.Type classifyActionCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            var variable = context.getVariable(verb.getRecipient());
            if (variable != null && variable.agentUrn() != null) {
              try {
                var targetBehavior = resolver.resolveBehavior(variable.agentUrn(), scope);
                if (targetBehavior != null) {
                  return targetBehavior.getStatements().stream()
                      .filter(action -> Objects.equals(action.getUrn(), verb.getMessage()))
                      .map(KActorsAction::getActionType)
                      .filter(Objects::nonNull)
                      .findFirst()
                      .orElse(null);
                }
              } catch (Throwable ignored) {
                // Adaptation validation reports target resolution failures.
              }
            }
            var imported =
                context.getBehavior().getImports().stream()
                    .filter(
                        candidate ->
                            Objects.equals(candidate.getImportedAlias(), verb.getRecipient()))
                    .findFirst()
                    .orElse(null);
            if (imported == null) {
              return null;
            }
            try {
              var importedBehavior =
                  resolver.resolveBehavior(imported.getImportedBehavior(), scope);
              if (importedBehavior != null) {
                return importedBehavior.getStatements().stream()
                    .filter(action -> Objects.equals(action.getUrn(), verb.getMessage()))
                    .map(KActorsAction::getActionType)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
              }
              var actor = resolver.resolveActor(imported.getImportedBehavior(), scope);
              var implementation = actor == null ? null : actor.verbs().get(verb.getMessage());
              if (implementation != null && implementation.method != null) {
                var annotation = implementation.method.getAnnotation(Verb.class);
                return annotation == null ? Verb.Type.FUNCTION : annotation.executionType();
              }
            } catch (Throwable ignored) {
              // Import validation below reports resolution failures with their source context.
            }
            return null;
          }

          @Override
          public List<Notification> validateImport(
              KActorsBehavior.Import imported, KActorsVisitor.KActorsContext context) {
            try {
              if (resolver.resolveBehavior(imported.getImportedBehavior(), scope) != null
                  || resolver.resolveActor(imported.getImportedBehavior(), scope) != null) {
                return List.of();
              }
            } catch (Throwable failure) {
              return List.of(
                  Notification.error(
                      "Cannot resolve imported actor " + imported.getImportedBehavior(),
                      failure,
                      Notification.LexicalContext.of(imported, context.getBehavior())));
            }
            return List.of(
                Notification.error(
                    "Cannot resolve imported actor " + imported.getImportedBehavior(),
                    Notification.LexicalContext.of(imported, context.getBehavior())));
          }

          @Override
          public List<Notification> validateInheritance(
              KActorsBehavior.Import inheritedBehaviorStatement,
              KActorsVisitor.KActorsContext context) {
            var inheritedBehaviorUrn = inheritedBehaviorStatement.getImportedBehavior();
            try {
              var inheritedBehavior = resolver.resolveBehavior(inheritedBehaviorUrn, scope);
              if (inheritedBehavior == null) {
                return List.of(
                    Notification.error(
                        "Cannot resolve inherited behavior " + inheritedBehaviorUrn,
                        Notification.LexicalContext.of(
                            inheritedBehaviorStatement, context.getBehavior())));
              }
              var childType = context.getBehavior().getBehaviorType();
              var inheritedType = inheritedBehavior.getBehaviorType();
              if (childType != null && childType.canInherit(inheritedType)) {
                return List.of();
              }
              return List.of(
                  Notification.error(
                      (childType == null ? "Unclassified" : childType)
                          + " behavior "
                          + context.getBehavior().getUrn()
                          + " cannot inherit "
                          + (inheritedType == null ? "an unclassified" : inheritedType)
                          + " behavior "
                          + inheritedBehaviorUrn,
                      Notification.LexicalContext.of(
                          inheritedBehaviorStatement, context.getBehavior())));
            } catch (Throwable failure) {
              return List.of(
                  Notification.error(
                      "Cannot validate inherited behavior " + inheritedBehaviorUrn,
                      failure,
                      Notification.LexicalContext.of(
                          inheritedBehaviorStatement, context.getBehavior())));
            }
          }

          @Override
          public List<String> getHandledMessageClasses(
              String behaviorUrn, KActorsVisitor.KActorsContext context) {
            try {
              return inheritedMessageClasses(
                  resolver.resolveBehavior(behaviorUrn, scope), resolver, scope, new LinkedHashSet<>());
            } catch (Throwable ignored) {
              // Inheritance validation reports resolution failures with source context.
              return List.of();
            }
          }

          @Override
          public List<Notification> validateAdaptation(
              KActorsStatement.Assignment assignment,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            try {
              var targetBehavior = resolver.resolveBehavior(behaviorUrn, scope);
              if (targetBehavior == null) {
                return List.of(
                    Notification.error(
                        "Cannot resolve adaptation behavior " + behaviorUrn,
                        Notification.LexicalContext.of(assignment, context.getBehavior())));
              }
              return resolver.validateBehaviorAdaptation(targetBehavior, sourceVariable, scope);
            } catch (Throwable failure) {
              return List.of(
                  Notification.error(
                      "Cannot validate adaptation to behavior " + behaviorUrn,
                      failure,
                      Notification.LexicalContext.of(assignment, context.getBehavior())));
            }
          }

          @Override
          public List<Notification> validateVerbCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            var variable = context.getVariable(verb.getRecipient());
            if (variable == null || variable.agentUrn() == null) {
              return List.of();
            }
            try {
              var targetBehavior = resolver.resolveBehavior(variable.agentUrn(), scope);
              if (targetBehavior == null) {
                return List.of(
                    Notification.error(
                        "Cannot resolve adapted behavior " + variable.agentUrn(),
                        Notification.LexicalContext.of(verb, context.getBehavior())));
              }
              boolean actionExists =
                  targetBehavior.getStatements().stream()
                      .anyMatch(action -> Objects.equals(action.getUrn(), verb.getMessage()));
              return actionExists
                  ? List.of()
                  : List.of(
                      Notification.error(
                          "Behavior " + variable.agentUrn() + " has no action " + verb.getMessage(),
                          Notification.LexicalContext.of(verb, context.getBehavior())));
            } catch (Throwable failure) {
              return List.of(
                  Notification.error(
                      "Cannot validate action "
                          + verb.getMessage()
                          + " on adapted behavior "
                          + variable.agentUrn(),
                      failure,
                      Notification.LexicalContext.of(verb, context.getBehavior())));
            }
          }
        };
    return new Environment(validator, resolver);
  }

  private static List<String> inheritedMessageClasses(
      KActorsBehavior behavior, Resolver resolver, UserScope scope, Set<String> resolutionPath) {
    if (behavior == null || !resolutionPath.add(behavior.getUrn())) {
      return List.of();
    }
    var ret = new LinkedHashSet<String>();
    if (behavior.getInheritedBehaviors() != null) {
      for (var inherited : behavior.getInheritedBehaviors()) {
        var inheritedBehavior = resolver.resolveBehavior(inherited.getImportedBehavior(), scope);
        ret.addAll(inheritedMessageClasses(inheritedBehavior, resolver, scope, resolutionPath));
      }
    }
    if (behavior.getStatements() != null) {
      for (var action : behavior.getStatements()) {
        if (action.getAnnotations() == null) {
          continue;
        }
        for (var annotation : action.getAnnotations()) {
          String messageClass = KActorsVisitor.handledMessageClass(annotation);
          if (messageClass != null) {
            ret.add(messageClass);
          }
        }
      }
    }
    resolutionPath.remove(behavior.getUrn());
    return List.copyOf(ret);
  }

  public AgentCompiler(String behaviorUrn, UserScope scope) {
    this(
        Objects.requireNonNull(scope, "scope")
            .getService(ResourcesService.class)
            .retrieve(behaviorUrn, KActorsBehavior.class, scope),
        scope,
        new KActorsVisitor.LenientValidator(),
        DEFAULT_RESOLVER);
  }

  public AgentCompiler(KActorsBehavior behavior, UserScope scope) {
    this(behavior, scope, new KActorsVisitor.LenientValidator(), DEFAULT_RESOLVER);
  }

  public AgentCompiler(
      KActorsBehavior behavior,
      UserScope scope,
      KActorsVisitor.Validator validator,
      Resolver resolver) {
    this.behavior = Objects.requireNonNull(behavior, "behavior");
    this.scope = scope;
    this.validator = Objects.requireNonNullElseGet(validator, KActorsVisitor.LenientValidator::new);
    this.resolver = Objects.requireNonNullElse(resolver, DEFAULT_RESOLVER);
    this.analyzer = new BehaviorAnalyzer(behavior, this.validator);
  }

  /** For source-generation tests that do not have a service scope. */
  public AgentCompiler(KActorsBehavior behavior) {
    this(behavior, null, new KActorsVisitor.LenientValidator(), DEFAULT_RESOLVER);
  }

  public boolean compile() {
    notifications.clear();
    if (!analyzer.analyze()) {
      notifications.addAll(analyzer.getNotifications());
      return false;
    }
    notifications.addAll(analyzer.getNotifications());
    indexAnalysis();
    resolveImportsAndCompileDependencies(new LinkedHashSet<>(Set.of(behavior.getUrn())));
    if (Utils.Notifications.hasErrors(notifications)) {
      return false;
    }

    JavaFile classFile = generateClass(behavior);
    if (classFile == null) {
      return false;
    }
    sourceCode = classFile.toString();
    generatedActorSources.put(behavior.getUrn(), sourceCode);
    dependencySources.put(behavior.getUrn(), sourceCode);
    Logging.INSTANCE.info("Generated class: " + classFile.typeSpec().name());

    // TODO Feed sourceCode to the in-memory Java compiler/classloader and populate
    // compiledActorClasses. Source generation is a successful compilation stage on its own.
    return true;
  }

  private void indexAnalysis() {
    calls.clear();
    analyzer.getCalls().forEach(call -> calls.put(call.statement(), call));
    imports.clear();
    analyzer.getImports().forEach(imported -> imports.put(imported.name(), imported));
    expressionFields.clear();
    int index = 0;
    for (var expression : analyzer.getExpressions()) {
      expressionFields.putIfAbsent(expression.statement(), "expression_" + index++);
    }
  }

  private void resolveImportsAndCompileDependencies(Set<String> path) {
    resolvedActors.clear();
    requiredRuntimeClasses.clear();
    for (var imported : analyzer.getImports()) {
      var actor = resolver.resolveActor(imported.behaviorUrn(), scope);
      if (actor != null) {
        resolvedActors.put(imported.name(), actor);
        actor.verbs().values().stream()
            .map(implementation -> implementation == null ? null : implementation.implementation)
            .filter(Objects::nonNull)
            .forEach(requiredRuntimeClasses::add);
        continue;
      }
      var importedBehavior = resolver.resolveBehavior(imported.behaviorUrn(), scope);
      if (importedBehavior == null || path.contains(importedBehavior.getUrn())) {
        continue;
      }
      var nextPath = new LinkedHashSet<>(path);
      nextPath.add(importedBehavior.getUrn());
      var compiler = new AgentCompiler(importedBehavior, scope, validator, resolver);
      if (compiler.analyzer.analyze()) {
        compiler.indexAnalysis();
        compiler.resolveImportsAndCompileDependencies(nextPath);
        requiredRuntimeClasses.addAll(compiler.requiredRuntimeClasses);
        var javaFile = compiler.generateClass(importedBehavior);
        if (javaFile != null) {
          compiler.sourceCode = javaFile.toString();
          dependencySources.putAll(compiler.dependencySources);
          dependencySources.put(importedBehavior.getUrn(), compiler.sourceCode);
          generatedActorSources.put(importedBehavior.getUrn(), compiler.sourceCode);
        }
      }
      notifications.addAll(compiler.analyzer.getNotifications());
    }
    inheritedBehaviors.clear();
    for (var inheritedUrn : behavior.getInheritedBehaviors()) {
      var inheritedBehavior = resolver.resolveBehavior(inheritedUrn.getImportedBehavior(), scope);
      if (inheritedBehavior == null) {
        notifications.add(
            Notification.error(
                "Cannot resolve inherited behavior " + inheritedUrn.getImportedBehavior(),
                Notification.LexicalContext.of(inheritedUrn, behavior)));
        continue;
      }
      var childType = behavior.getBehaviorType();
      var inheritedType = inheritedBehavior.getBehaviorType();
      if (childType == null || !childType.canInherit(inheritedType)) {
        notifications.add(
            Notification.error(
                childType
                    + " behavior "
                    + behavior.getUrn()
                    + " cannot inherit "
                    + inheritedType
                    + " behavior "
                    + inheritedUrn,
                Notification.LexicalContext.of(inheritedUrn, behavior)));
        continue;
      }
      if (path.contains(inheritedBehavior.getUrn())) {
        continue;
      }
      var nextPath = new LinkedHashSet<>(path);
      nextPath.add(inheritedBehavior.getUrn());
      var compiler = new AgentCompiler(inheritedBehavior, scope, validator, resolver);
      if (compiler.analyzer.analyze()) {
        compiler.indexAnalysis();
        compiler.resolveImportsAndCompileDependencies(nextPath);
        requiredRuntimeClasses.addAll(compiler.requiredRuntimeClasses);
        var javaFile = compiler.generateClass(inheritedBehavior);
        if (javaFile != null) {
          compiler.sourceCode = javaFile.toString();
          dependencySources.putAll(compiler.dependencySources);
          dependencySources.put(inheritedBehavior.getUrn(), compiler.sourceCode);
          generatedActorSources.put(inheritedBehavior.getUrn(), compiler.sourceCode);
          inheritedBehaviors.put(inheritedUrn.getImportedBehavior(), inheritedBehavior);
        }
      }
      notifications.addAll(compiler.analyzer.getNotifications());
    }
  }

  public String getSourceCode() {
    return sourceCode;
  }

  /** Includes recursively generated imported behaviors, keyed by behavior URN. */
  public Map<String, String> getGeneratedSources() {
    return Collections.unmodifiableMap(dependencySources);
  }

  public static String getGeneratedSource(String behaviorUrn) {
    return generatedActorSources.get(behaviorUrn);
  }

  public static Class<? extends RuntimeAgentBase> getCompiledClass(String behaviorUrn) {
    return compiledActorClasses.get(behaviorUrn);
  }

  /**
   * Register a class produced from this compiler's source. Class loading is owned by the runtime
   * registry, but this keeps the legacy behavior-URN lookup usable by compiler clients.
   */
  public static void registerCompiledClass(
      String behaviorUrn, Class<? extends RuntimeAgentBase> compiledClass) {
    if (behaviorUrn != null && compiledClass != null) {
      compiledActorClasses.put(behaviorUrn, compiledClass);
    }
  }

  /** The binary name of the primary generated class, available after {@link #compile()}. */
  public String getQualifiedClassName() {
    return qualifiedClassName;
  }

  /**
   * Runtime extension classes referenced by generated source, including recursively generated
   * imported and inherited behaviors.
   */
  public Set<Class<?>> getRequiredRuntimeClasses() {
    return Set.copyOf(requiredRuntimeClasses);
  }

  private JavaFile generateClass(KActorsBehavior sourceBehavior) {
    if (Utils.Notifications.hasErrors(analyzer.getNotifications())) {
      return null;
    }
    generatedName = 0;
    String className = Utils.CamelCase.toUpperCamelCase(sourceBehavior.getUrn(), '.');
    if (sourceBehavior == behavior) {
      qualifiedClassName = packageName + "." + className;
    }
    var classBuilder =
        TypeSpec.classBuilder(className)
            .superclass(analyzer.getAgentClass())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    addExpressionFields(classBuilder);
    addImportFields(classBuilder);
    addInheritanceFields(classBuilder);
    addConstructors(classBuilder, className);
    for (var action : sourceBehavior.getStatements()) {
      classBuilder.addMethod(compileAction(action));
    }
    classBuilder.addMethod(compileMessageHandlers(sourceBehavior));
    classBuilder.addMethod(compileMain());
    classBuilder.addMethod(compileExecutionMode());
    classBuilder.addMethod(compileCliMain(className));
    return JavaFile.builder(packageName, classBuilder.build()).build();
  }

  private void addExpressionFields(TypeSpec.Builder type) {
    expressionFields.values().stream()
        .distinct()
        .forEach(
            name ->
                type.addField(
                    FieldSpec.builder(Expression.class, name, Modifier.PRIVATE, Modifier.FINAL)
                        .build()));
  }

  private void addImportFields(TypeSpec.Builder type) {
    imports
        .keySet()
        .forEach(
            alias ->
                type.addField(
                    FieldSpec.builder(
                            Object.class,
                            "actor_" + javaIdentifier(alias),
                            Modifier.PRIVATE,
                            Modifier.FINAL)
                        .build()));
  }

  private void addInheritanceFields(TypeSpec.Builder type) {
    inheritedFields.clear();
    int index = 0;
    for (var inheritedUrn : inheritedBehaviors.keySet()) {
      String field = "inherited_" + index++;
      inheritedFields.put(inheritedUrn, field);
      type.addField(
          FieldSpec.builder(RuntimeAgentBase.class, field, Modifier.PRIVATE, Modifier.FINAL)
              .build());
    }
  }

  private void addConstructors(TypeSpec.Builder type, String className) {
    type.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addStatement("this(null, null, null, null, $T.of(), new Object[0])", Map.class)
            .build());
    type.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(SessionScope.class, "scope")
            .addParameter(ArrayTypeName.of(ClassName.get(Object.class)), "initArguments")
            .varargs(true)
            .addStatement("this(null, scope, null, scope, $T.of(), initArguments)", Map.class)
            .build());
    type.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(KActorsBehavior.class, "behavior")
            .addParameter(SessionScope.class, "scope")
            .addParameter(
                ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(Object.class)),
                "importedActors")
            .addParameter(ArrayTypeName.of(ClassName.get(Object.class)), "initArguments")
            .varargs(true)
            .addStatement("this(behavior, scope, null, scope, importedActors, initArguments)")
            .build());
    type.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(KActorsBehavior.class, "behavior")
            .addParameter(SessionScope.class, "scope")
            .addParameter(Observation.class, "observation")
            .addParameter(
                ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(Object.class)),
                "importedActors")
            .addParameter(ArrayTypeName.of(ClassName.get(Object.class)), "initArguments")
            .varargs(true)
            .addStatement(
                "this(behavior, scope, observation, scope, importedActors, initArguments)")
            .build());

    var constructor =
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(KActorsBehavior.class, "behavior")
            .addParameter(SessionScope.class, "scope")
            .addParameter(Observation.class, "observation")
            .addParameter(org.integratedmodelling.klab.api.scope.Scope.class, "creationScope")
            .addParameter(
                ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(Object.class)),
                "importedActors")
            .addParameter(ArrayTypeName.of(ClassName.get(Object.class)), "initArguments")
            .varargs(true)
            .addStatement("super(behavior, scope, observation, creationScope)");

    for (var entry : inheritedBehaviors.entrySet()) {
      constructor.addStatement(
          "this.$L = registerInheritedBehavior(new $T(null, scope, observation, creationScope, importedActors, new Object[0]))",
          inheritedFields.get(entry.getKey()),
          generatedClass(entry.getValue().getUrn()));
    }
    for (var expression : analyzer.getExpressions()) {
      String field = expressionFields.get(expression.statement());
      constructor.addStatement(
          "this.$L = compileExpression($S)", field, expressionSource(expression));
    }
    for (var imported : analyzer.getImports()) {
      String field = "actor_" + javaIdentifier(imported.name());
      var resolved = resolvedActors.get(imported.name());
      Class<?> implementation = resolved == null ? null : resolved.implementationClass();
      if (implementation != null) {
        constructor.addStatement("this.$L = $T.class", field, implementation);
      } else {
        constructor.addStatement(
            "this.$L = resolveImportedActor($S, $S, importedActors)",
            field,
            imported.behaviorUrn(),
            imported.name());
      }
    }
    var init = analyzer.getActions().get("init");
    if (init != null) {
      switch (init.effectiveExecutionType()) {
        case FUNCTION ->
            constructor.addStatement(
                "invokeSelfFunction($S, (AgentScope) rootScope(), initArguments)", "init");
        case SUPPLIER ->
            constructor.addStatement(
                "invokeSelfSupplier($S, (AgentScope) rootScope(), initArguments).join()", "init");
        case EMITTER ->
            constructor.addStatement(
                "invokeSelfEmitter($S, (AgentScope) rootScope(), initArguments)", "init");
      }
    }
    type.addMethod(constructor.build());
  }

  private MethodSpec compileExecutionMode() {
    return MethodSpec.methodBuilder("getAgentExecutionMode")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PUBLIC)
        .returns(Verb.Type.class)
        .addStatement("return $T.$L", Verb.Type.class, analyzer.getAgentExecutionMode().name())
        .build();
  }

  private MethodSpec compileMessageHandlers(KActorsBehavior sourceBehavior) {
    var handlerType = ClassName.get(RuntimeAgentBase.AgentMessageHandler.class);
    var returnType =
        ParameterizedTypeName.get(
            ClassName.get(Map.class), ClassName.get(String.class), handlerType);
    var method =
        MethodSpec.methodBuilder("agentMessageHandlers")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PROTECTED)
            .returns(returnType)
            .addStatement(
                "var handlers = new $T<String, $T>(super.agentMessageHandlers())",
                LinkedHashMap.class,
                handlerType);
    for (var field : inheritedFields.values()) {
      method.addStatement("inheritAgentMessageHandlers(handlers, this.$L)", field);
    }
    for (var action : sourceBehavior.getStatements()) {
      for (var annotation : action.getAnnotations()) {
        boolean standardInput = "stdin".equals(annotation.getName());
        if (!standardInput && !"handle".equals(annotation.getName())) {
          continue;
        }
        String messageClass =
            standardInput
                ? RuntimeAgent.ConsoleMessageType.STDIN.name()
                : KActorsVisitor.handledMessageClass(annotation);
        if (messageClass == null || messageClass.isBlank()) {
          notifications.add(
              Notification.warning(
                  "The @handle annotation requires a CONSTANT as its unnamed parameter or 'class' parameter"));
          continue;
        }
        method.addStatement(
            "handlers.put($S, new $T($S, $T.$L, $L, $L))",
            messageClass,
            handlerType,
            action.getUrn(),
            Verb.Type.class,
            action.getActionType().name(),
            stringList(action.getArgumentNames()),
            !standardInput);
      }
    }
    method.addStatement("return $T.copyOf(handlers)", Map.class);
    return method.build();
  }

  private ClassName generatedClass(String behaviorUrn) {
    return ClassName.get(packageName, Utils.CamelCase.toUpperCamelCase(behaviorUrn, '.'));
  }

  private MethodSpec compileMain() {
    var method =
        MethodSpec.methodBuilder("main")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PROTECTED)
            .returns(RuntimeAgentBase.ExitValue.class)
            .addParameter(AgentScope.class, "rootScope")
            .beginControlFlow("try");
    var main = analyzer.getActions().get("main");
    if (main == null) {
      method.addStatement("return TASK_RUNNING");
    } else {
      switch (main.effectiveExecutionType()) {
        case FUNCTION -> {
          method.addStatement("Object result = invokeSelfFunction($S, rootScope)", "main");
          method.addStatement(
              main.callsUnknownActions()
                  ? "return awaitDynamicCalls(result)"
                  : "return ExitValue.success(result)");
        }
        case SUPPLIER -> {
          method.addStatement(
              "$T<Object> result = invokeSelfSupplier($S, rootScope)",
              CompletableFuture.class,
              "main");
          if (main.callsUnknownActions()) {
            method.addStatement(
                "result.whenComplete((value, error) -> { if (error == null) awaitDynamicCalls(value); else failDynamicCalls(error); })");
          } else {
            method.addStatement(
                "result.whenComplete((value, error) -> { if (error == null) rootScope.done(value); else rootScope.done(error); })");
          }
          method.addStatement("return TASK_RUNNING");
        }
        case EMITTER -> {
          method.addStatement("invokeSelfEmitter($S, rootScope)", "main");
          method.addStatement("return TASK_RUNNING");
        }
      }
    }
    method.nextControlFlow("catch ($T error)", Throwable.class);
    if (main != null && main.callsUnknownActions()) {
      method.addStatement("return failDynamicCalls(error)");
    } else {
      method.addStatement("rootScope.done(error)").addStatement("return ExitValue.failure(error)");
    }
    method.endControlFlow();
    return method.build();
  }

  private MethodSpec compileCliMain(String className) {
    var generatedClass = ClassName.get(packageName, className);
    var method =
        MethodSpec.methodBuilder("main")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(void.class)
            .addParameter(String[].class, "args");
    if (analyzer.getLifecycle() == BehaviorAnalyzer.Lifecycle.PERSISTENT) {
      method
          .addStatement("var agent = new $T()", generatedClass)
          .addStatement(
              "$T.create().with($S, command -> agent.run()).with($S, command -> agent.stop()).with($S, command -> $T.out.println(agent.status())).run()",
              CLI.class,
              "start",
              "stop",
              "status",
              System.class);
    } else {
      method
          .addStatement("var agent = new $T(null, (Object[]) args)", generatedClass)
          .addStatement("var result = agent.run()")
          .beginControlFlow("if (result.getErrorCode() != 0)")
          .addStatement("$T.err.println(result.getErrorMessage())", System.class)
          .endControlFlow();
    }
    return method.build();
  }

  private MethodSpec compileAction(KActorsAction action) {
    Verb.Type type = action.getActionType();
    var method =
        MethodSpec.methodBuilder("action_" + javaIdentifier(action.getUrn()))
            .addModifiers(Modifier.PRIVATE)
            .returns(actionReturnType(type))
            .addParameter(AgentScope.class, "scope")
            .addParameter(ArrayTypeName.of(ClassName.get(Object.class)), "arguments")
            .varargs(true)
            .addJavadoc(
                "Compiled k.Actors action {@code $L}; analyzed as {@link $T#$L}.\n",
                action.getUrn(),
                Verb.Type.class,
                type.name());
    var code = CodeBlock.builder();
    code.addStatement(
        "var frame = bindArguments($L, arguments)", stringList(action.getArgumentNames()));
    String result = null;
    if (type == Verb.Type.SUPPLIER) {
      result = "actionResult";
      code.addStatement("var $L = new $T<Object>()", result, CompletableFuture.class);
    }
    var context = new CompilationContext(type, false, "scope", "frame", result, List.of());
    emitStatements(action.getCode(), code, context);
    if (type == Verb.Type.FUNCTION && !definitelyReturns(action.getCode())) {
      code.addStatement("return VOID_VALUE");
    } else if (type == Verb.Type.SUPPLIER && !definitelyReturns(action.getCode())) {
      code.addStatement("return $L", result);
    }
    method.addCode(code.build());
    return method.build();
  }

  private TypeName actionReturnType(Verb.Type type) {
    return switch (type) {
      case FUNCTION -> ClassName.get(Object.class);
      case SUPPLIER ->
          ParameterizedTypeName.get(
              ClassName.get(CompletableFuture.class), ClassName.get(Object.class));
      case EMITTER -> TypeName.VOID;
    };
  }

  private void emitStatements(
      Collection<? extends KActorsStatement> statements,
      CodeBlock.Builder code,
      CompilationContext context) {
    if (statements == null || statements.isEmpty()) {
      return;
    }
    var sequence = new ArrayList<>(statements);
    for (int i = 0; i < sequence.size(); i++) {
      boolean awaitCompletion = i + 1 < sequence.size() && sequence.get(i + 1).isSequential();
      emitStatement(sequence.get(i), code, context, awaitCompletion);
    }
  }

  private void emitStatement(
      KActorsStatement statement, CodeBlock.Builder code, CompilationContext context) {
    emitStatement(statement, code, context, false);
  }

  private void emitStatement(
      KActorsStatement statement,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    if (statement == null) {
      return;
    }
    switch (statement) {
      case KActorsStatement.Verb verb -> emitVerb(verb, code, context, awaitCompletion);
      case KActorsStatement.Assignment assignment -> emitAssignment(assignment, code, context);
      case KActorsStatement.Fire fire ->
          code.addStatement(
              "$L.doFire($L)",
              context.scope(),
              valueOrCall(fire.getValue(), fire.getFunction(), context));
      case KActorsStatement.Return returned -> emitReturn(returned, code, context);
      case KActorsStatement.Fail failed ->
          code.addStatement(
              "throw new $T(this, $S)",
              KlabActorException.class,
              Objects.requireNonNullElse(failed.getMessage(), "k.Actors action failed"));
      case KActorsStatement.Break ignored -> code.addStatement("break");
      case KActorsStatement.Text text ->
          code.addStatement(
              "handleText($S, $L, $L)", text.getText(), context.scope(), context.frame());
      case KActorsStatement.Group group -> emitGroup(group, code, context, awaitCompletion);
      case KActorsStatement.If conditional -> emitIf(conditional, code, context, awaitCompletion);
      case KActorsStatement.While loop -> emitWhile(loop, code, context, awaitCompletion);
      case KActorsStatement.Do loop -> emitDo(loop, code, context, awaitCompletion);
      case KActorsStatement.For loop -> emitFor(loop, code, context, awaitCompletion);
      case KActorsStatement.Assert assertion -> emitAssert(assertion, code, context);
      case KActorsStatement.Assert.Assertion assertion -> emitAssertion(assertion, code, context);
      default -> code.add("// TODO unsupported statement $L\n", statement.getType());
    }
  }

  private void emitAssignment(
      KActorsStatement.Assignment assignment, CodeBlock.Builder code, CompilationContext context) {
    CodeBlock value = valueOrCall(assignment.getValue(), assignment.getFunction(), context);
    if (assignment.getAdaptedBehaviorUrn() != null
        && !assignment.getAdaptedBehaviorUrn().isBlank()) {
      value =
          CodeBlock.of(
              "adaptToBehavior($L, $S, $L)",
              value,
              assignment.getAdaptedBehaviorUrn().trim(),
              context.scope());
    }
    if (assignment.getAssignmentScope() == KActorsStatement.Assignment.Scope.ACTOR) {
      code.addStatement("setActorState($S, $L)", assignment.getVariable(), value);
    } else {
      code.addStatement("$L.put($S, $L)", context.frame(), assignment.getVariable(), value);
    }
  }

  private void emitReturn(
      KActorsStatement.Return returned, CodeBlock.Builder code, CompilationContext context) {
    CodeBlock value = valueOrCall(returned.getValue(), returned.getFunction(), context);
    if (context.reactive()) {
      if (context.actionType() == Verb.Type.SUPPLIER && context.result() != null) {
        code.addStatement("$L.complete($L)", context.result(), value);
      }
      code.addStatement("$L.done($L)", context.scope(), value);
      code.addStatement("return");
      return;
    }
    switch (context.actionType()) {
      case FUNCTION -> code.addStatement("return $L", value);
      case SUPPLIER -> {
        code.addStatement("$L.complete($L)", context.result(), value);
        code.addStatement("return $L", context.result());
      }
      case EMITTER -> {
        code.addStatement("$L.done($L)", context.scope(), value);
        code.addStatement("return");
      }
    }
  }

  private void emitGroup(
      KActorsStatement.Group group,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    String frame = nextName("frame");
    code.addStatement("var $L = childFrame($L)", frame, context.frame());
    if (awaitCompletion) {
      var completions = new ArrayList<String>();
      emitStatements(
          group.getStatements(), code, context.withFrame(frame).collectingCompletions(completions));
      awaitCompletions(completions, code);
    } else {
      emitStatements(group.getStatements(), code, context.withFrame(frame));
    }
  }

  private void emitIf(
      KActorsStatement.If conditional,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    code.beginControlFlow(
        "if (truthy($L))",
        valueOrCall(conditional.getCondition(), conditional.getFunction(), context));
    emitStatement(
        conditional.getThenBody(), code, context.withoutCompletionCollectors(), awaitCompletion);
    for (var elseIf : conditional.getElseIfs()) {
      code.nextControlFlow(
          "else if (truthy($L))",
          valueOrCall(elseIf.getFirst().getFirst(), elseIf.getFirst().getSecond(), context));
      emitStatement(
          elseIf.getSecond(), code, context.withoutCompletionCollectors(), awaitCompletion);
    }
    if (conditional.getElseBody() != null) {
      code.nextControlFlow("else");
      emitStatement(
          conditional.getElseBody(), code, context.withoutCompletionCollectors(), awaitCompletion);
    }
    code.endControlFlow();
  }

  private void emitWhile(
      KActorsStatement.While loop,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    code.beginControlFlow(
        "while (truthy($L))", valueOrCall(loop.getCondition(), loop.getFunction(), context));
    emitStatement(loop.getBody(), code, context.withoutCompletionCollectors(), awaitCompletion);
    code.endControlFlow();
  }

  private void emitDo(
      KActorsStatement.Do loop,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    code.beginControlFlow("do");
    emitStatement(loop.getBody(), code, context.withoutCompletionCollectors(), awaitCompletion);
    code.endControlFlow(
        "while (truthy($L))", valueOrCall(loop.getCondition(), loop.getFunction(), context));
  }

  private void emitFor(
      KActorsStatement.For loop,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    String item = nextName("item");
    code.beginControlFlow(
        "for (Object $L : asIterable($L))",
        item,
        valueOrCall(loop.getIterable(), loop.getFunction(), context));
    if (loop.getVariable() != null && !loop.getVariable().isBlank()) {
      code.addStatement("$L.put($S, $L)", context.frame(), loop.getVariable(), item);
    }
    emitStatement(loop.getBody(), code, context.withoutCompletionCollectors(), awaitCompletion);
    code.endControlFlow();
  }

  private void emitAssert(
      KActorsStatement.Assert assertion, CodeBlock.Builder code, CompilationContext context) {
    for (var item : assertion.getAssertions()) {
      emitAssertion(item, code, context);
    }
  }

  private void emitAssertion(
      KActorsStatement.Assert.Assertion assertion,
      CodeBlock.Builder code,
      CompilationContext context) {
    CodeBlock actual;
    if (assertion.getExpression() != null) {
      actual = value(assertion.getExpression(), context);
    } else if (assertion.getCalls() != null && !assertion.getCalls().isEmpty()) {
      actual = callValue(assertion.getCalls().getLast(), context);
    } else {
      actual = CodeBlock.of("null");
    }
    CodeBlock expected =
        assertion.getValue() == null ? CodeBlock.of("null") : value(assertion.getValue(), context);
    code.addStatement("assertValue($L, $L)", actual, expected);
  }

  private void emitVerb(
      KActorsStatement.Verb verb,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    var info = calls.get(verb);
    Verb.Type type = info == null ? null : info.executionType();
    if (type == Verb.Type.FUNCTION) {
      code.addStatement("$L", invoke(verb, type, context.scope(), context));
      return;
    }

    boolean trackCompletion = awaitCompletion || context.isCollectingCompletions();
    String completion = trackCompletion ? nextName("reaction") : null;
    if (trackCompletion) {
      code.addStatement("var $L = new $T<Void>()", completion, CompletableFuture.class);
      context.trackCompletion(completion);
    }

    String event = nextName("event");
    String eventScope = nextName("eventScope");
    CodeBlock.Builder handler = CodeBlock.builder();
    if (verb.getActions() == null || verb.getActions().isEmpty()) {
      handler.add("// No match actions: completion is tracked only for scope lifetime.\n");
    } else {
      boolean first = true;
      for (var match : verb.getActions()) {
        CodeBlock criterion = matchCriterion(match, context);
        if (first) {
          handler.beginControlFlow(
              "if (matches($L.payload(), $T.$L, $L, $L))",
              event,
              ValueType.class,
              matchType(match).name(),
              criterion,
              match.getMatchCriterion() != null && match.getMatchCriterion().isExclusive());
          first = false;
        } else {
          handler.nextControlFlow(
              "else if (matches($L.payload(), $T.$L, $L, $L))",
              event,
              ValueType.class,
              matchType(match).name(),
              criterion,
              match.getMatchCriterion() != null && match.getMatchCriterion().isExclusive());
        }
        String matchFrame = nextName("matchFrame");
        handler.addStatement("var $L = childFrame($L)", matchFrame, context.frame());
        handler.addStatement(
            "bindMatch($L, $L.payload(), $L, $S)",
            matchFrame,
            event,
            stringList(match.getVariables()),
            match.getCaptureAs());
        emitStatement(
            match.getActionOnMatch(), handler, context.asReactive(eventScope, matchFrame));
      }
      if (!first) {
        handler
            .nextControlFlow("else if ($L.type() == EventType.EXCEPTION)", event)
            .addStatement("$L.done($L.payload())", eventScope, event)
            .endControlFlow();
      }
    }

    CodeBlock handlerCode = handler.build();
    if (trackCompletion) {
      handler = CodeBlock.builder();
      handler
          .beginControlFlow("try")
          .add(handlerCode)
          .nextControlFlow("catch ($T | $T error)", RuntimeException.class, Error.class)
          .addStatement("$L.completeExceptionally(error)", completion)
          .addStatement("throw error")
          .nextControlFlow("finally")
          .addStatement("completeReaction($L, $L)", completion, event)
          .endControlFlow();
    }

    CodeBlock listener;
    if (type == null) {
      listener =
          CodeBlock.builder()
              .add(
                  "onEvent($L, ($L, $L) -> {\n$L}, EventType.RETURN, EventType.FIRE, EventType.EXCEPTION)",
                  context.scope(),
                  event,
                  eventScope,
                  handler.build())
              .build();
    } else {
      listener =
          CodeBlock.builder()
              .add(
                  "onEvent($L, ($L, $L) -> {\n$L}, EventType.$L, EventType.EXCEPTION)",
                  context.scope(),
                  event,
                  eventScope,
                  handler.build(),
                  type == Verb.Type.SUPPLIER ? "RETURN" : "FIRE")
              .build();
    }
    if (type == null) {
      // The listener contains a handler built with addStatement(), so it already carries
      // JavaPoet's statement markers. Wrapping it in addStatement() would nest $[ ... $] and
      // fail when JavaFile is rendered.
      code.add(
          "runDynamicVerb($L, $S, $L, $L);\n",
          dynamicReceiver(verb, context),
          verb.getMessage(),
          listener,
          arguments(verb, context));
    } else if (type == Verb.Type.SUPPLIER) {
      code.add(
          "runSupplier($L, callScope -> $L);\n",
          listener,
          invoke(verb, type, "callScope", context));
    } else {
      code.add(
          "runEmitter($L, callScope -> $L);\n", listener, invoke(verb, type, "callScope", context));
    }
    if (awaitCompletion) {
      awaitCompletions(List.of(completion), code);
    }
  }

  private void awaitCompletions(List<String> completions, CodeBlock.Builder code) {
    if (completions == null || completions.isEmpty()) {
      return;
    }
    var futures = completions.stream().map(CodeBlock::of).toList();
    code.addStatement("awaitReactions($L)", CodeBlock.join(futures, ", "));
  }

  private CodeBlock valueOrCall(
      KActorsValue value, KActorsStatement.Verb function, CompilationContext context) {
    return value != null ? value(value, context) : callValue(function, context);
  }

  private CodeBlock callValue(KActorsStatement.Verb verb, CompilationContext context) {
    if (verb == null) {
      return CodeBlock.of("null");
    }
    var info = calls.get(verb);
    Verb.Type type = info == null ? null : info.executionType();
    if (type == null) {
      return CodeBlock.of(
          "invokeDynamicValue($L, $S, $L, $L)",
          dynamicReceiver(verb, context),
          verb.getMessage(),
          context.scope(),
          arguments(verb, context));
    }
    CodeBlock invocation = invoke(verb, type, context.scope(), context);
    return type == Verb.Type.SUPPLIER ? CodeBlock.of("$L.join()", invocation) : invocation;
  }

  private CodeBlock invoke(
      KActorsStatement.Verb verb, Verb.Type type, String scopeName, CompilationContext context) {
    CodeBlock arguments = arguments(verb, context);
    boolean self = verb.getRecipient() == null || "self".equals(verb.getRecipient());
    String operation =
        switch (type) {
          case FUNCTION -> self ? "invokeSelfFunction" : "invokeFunction";
          case SUPPLIER -> self ? "invokeSelfSupplier" : "invokeSupplier";
          case EMITTER -> self ? "invokeSelfEmitter" : "invokeEmitter";
        };
    if (self) {
      return CodeBlock.of("$L($S, $L, $L)", operation, verb.getMessage(), scopeName, arguments);
    }
    return CodeBlock.of(
        "$L($L, $S, $L, $L)",
        operation,
        receiver(verb.getRecipient(), context),
        verb.getMessage(),
        scopeName,
        arguments);
  }

  private CodeBlock receiver(String recipient, CompilationContext context) {
    if (imports.containsKey(recipient)) {
      return CodeBlock.of("this.actor_$L", javaIdentifier(recipient));
    }
    return CodeBlock.of("resolveIdentifier($S, $L)", recipient, context.frame());
  }

  private CodeBlock dynamicReceiver(KActorsStatement.Verb verb, CompilationContext context) {
    return verb.getRecipient() == null || "self".equals(verb.getRecipient())
        ? CodeBlock.of("this")
        : receiver(verb.getRecipient(), context);
  }

  private CodeBlock arguments(KActorsStatement.Verb verb, CompilationContext context) {
    var values = new ArrayList<CodeBlock>();
    if (verb.getArguments() != null) {
      verb.getArguments()
          .values()
          .forEach(argument -> values.add(argumentValue(argument, context)));
    }
    return CodeBlock.of("new Object[] {$L}", CodeBlock.join(values, ", "));
  }

  private CodeBlock argumentValue(Object argument, CompilationContext context) {
    return argument instanceof KActorsValue value
        ? value(value, context)
        : literal(argument, ValueType.STRING);
  }

  private CodeBlock value(KActorsValue value, CompilationContext context) {
    if (value == null) {
      return CodeBlock.of("null");
    }
    Object raw = rawValue(value);
    return switch (value.getType()) {
      case EXPRESSION ->
          CodeBlock.of(
              "evaluateExpression(this.$L, $L, $L)",
              expressionFields.get(value),
              context.scope(),
              context.frame());
      case IDENTIFIER ->
          CodeBlock.of("resolveIdentifier($S, $L)", String.valueOf(raw), context.frame());
      case TERNARY_EXPRESSION -> ternary(raw, context);
      case NUMBER, INTEGER, DOUBLE, BOOLEAN, STRING -> literal(raw, value.getType());
      case NODATA, EMPTY -> CodeBlock.of("null");
      case ANYTHING, ANYVALUE, ANYTRUE -> CodeBlock.of("null");
      default ->
          CodeBlock.of(
              "literalValue($T.$L, $S)",
              ValueType.class,
              value.getType().name(),
              String.valueOf(raw));
    };
  }

  private CodeBlock ternary(Object raw, CompilationContext context) {
    if (!(raw instanceof Ternary ternary)
        || !(ternary.getCondition() instanceof KActorsValue condition)
        || !(ternary.getTrueCase() instanceof KActorsValue trueCase)
        || !(ternary.getFalseCase() instanceof KActorsValue falseCase)) {
      throw new IllegalArgumentException("Invalid k.Actors ternary value");
    }
    return CodeBlock.of(
        "(truthy($L) ? $L : $L)",
        value(condition, context),
        value(trueCase, context),
        value(falseCase, context));
  }

  private CodeBlock literal(Object value, ValueType type) {
    if (value == null) {
      return CodeBlock.of("null");
    }
    if (value instanceof String string) {
      return CodeBlock.of("$S", string);
    }
    if (value instanceof Character character) {
      return CodeBlock.of("$S.charAt(0)", character.toString());
    }
    if (value instanceof Boolean
        || value instanceof Byte
        || value instanceof Short
        || value instanceof Integer) {
      return CodeBlock.of("$L", value);
    }
    if (value instanceof Long number) {
      return CodeBlock.of("$LL", number);
    }
    if (value instanceof Float number) {
      return CodeBlock.of("$Lf", number);
    }
    if (value instanceof Double number) {
      return CodeBlock.of("$L", number);
    }
    return CodeBlock.of("literalValue($T.$L, $S)", ValueType.class, type.name(), value.toString());
  }

  private ValueType matchType(KActorsStatement.Verb.MatchAction match) {
    var criterion = match.getMatchCriterion();
    if (criterion == null) {
      return ValueType.ANYTHING;
    }
    if (criterion.getType() == ValueType.IDENTIFIER
        && match.getVariables() != null
        && match.getVariables().contains(String.valueOf(rawValue(criterion)))) {
      return ValueType.ANYTHING;
    }
    return criterion.getType();
  }

  private CodeBlock matchCriterion(
      KActorsStatement.Verb.MatchAction match, CompilationContext context) {
    return matchType(match) == ValueType.ANYTHING
        ? CodeBlock.of("null")
        : value(match.getMatchCriterion(), context);
  }

  private Object rawValue(KActorsValue value) {
    try {
      return value.getValue(Object.class);
    } catch (RuntimeException e) {
      return value.toString();
    }
  }

  private String expressionSource(KActorsVisitor.ExpressionInfo expression) {
    Object raw = rawValue(expression.statement());
    return raw == null ? String.valueOf(expression.expression()) : raw.toString();
  }

  private CodeBlock stringList(Collection<String> strings) {
    if (strings == null || strings.isEmpty()) {
      return CodeBlock.of("$T.of()", List.class);
    }
    var values = strings.stream().map(value -> CodeBlock.of("$S", value)).toList();
    return CodeBlock.of("$T.of($L)", List.class, CodeBlock.join(values, ", "));
  }

  private boolean definitelyReturns(Collection<? extends KActorsStatement> statements) {
    if (statements == null || statements.isEmpty()) {
      return false;
    }
    return definitelyReturns(statements.stream().reduce((first, second) -> second).orElse(null));
  }

  private boolean definitelyReturns(KActorsStatement statement) {
    return switch (statement) {
      case KActorsStatement.Return ignored -> true;
      case KActorsStatement.Fail ignored -> true;
      case KActorsStatement.Group group -> definitelyReturns(group.getStatements());
      case KActorsStatement.If conditional ->
          conditional.getElseBody() != null
              && definitelyReturns(conditional.getThenBody())
              && conditional.getElseIfs().stream()
                  .allMatch(branch -> definitelyReturns(branch.getSecond()))
              && definitelyReturns(conditional.getElseBody());
      default -> false;
    };
  }

  private String javaIdentifier(String identifier) {
    String ret = identifier == null ? "unnamed" : identifier.replaceAll("[^A-Za-z0-9_$]", "_");
    if (ret.isEmpty() || !Character.isJavaIdentifierStart(ret.charAt(0))) {
      ret = "_" + ret;
    }
    return ret;
  }

  private String nextName(String prefix) {
    return prefix + "_" + generatedName++;
  }

  public List<Notification> getNotifications() {
    return List.copyOf(notifications);
  }

  public Verb.Type getAgentExecutionMode() {
    return analyzer.getAgentExecutionMode();
  }

  public BehaviorAnalyzer.Lifecycle getLifecycle() {
    return analyzer.getLifecycle();
  }

  private record CompilationContext(
      Verb.Type actionType,
      boolean reactive,
      String scope,
      String frame,
      String result,
      List<List<String>> completionCollectors) {

    CompilationContext withFrame(String newFrame) {
      return new CompilationContext(
          actionType, reactive, scope, newFrame, result, completionCollectors);
    }

    CompilationContext asReactive(String newScope, String newFrame) {
      return new CompilationContext(actionType, true, newScope, newFrame, result, List.of());
    }

    CompilationContext withoutCompletionCollectors() {
      return completionCollectors.isEmpty()
          ? this
          : new CompilationContext(actionType, reactive, scope, frame, result, List.of());
    }

    CompilationContext collectingCompletions(List<String> collector) {
      var collectors = new ArrayList<>(completionCollectors);
      collectors.add(collector);
      return new CompilationContext(actionType, reactive, scope, frame, result, collectors);
    }

    boolean isCollectingCompletions() {
      return !completionCollectors.isEmpty();
    }

    void trackCompletion(String completion) {
      completionCollectors.forEach(collector -> collector.add(completion));
    }
  }
}
