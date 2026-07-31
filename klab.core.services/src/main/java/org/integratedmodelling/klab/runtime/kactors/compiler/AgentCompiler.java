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
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
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
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.exceptions.KlabActorException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Ternary;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsCodeStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.api.utils.Utils.CLI;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;

/**
 * Compiles an analyzed {@link KActorsBehavior} into Java source backed by {@link RuntimeAgentBase}.
 */
public class AgentCompiler {

  public static final String CORE_AGENT_URN = RuntimeAgent.CORE_BEHAVIOR_URN;

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
     * Negotiate a non-positional match between Java parameter types and supplied k.Actors values.
     * Implementations may split or combine values and return them in Java declaration order.
     * Returning {@code null} rejects the match.
     */
    default List<Object> negotiateParameterMatch(
        List<Class<?>> unmatchedParameterTypes, List<?> suppliedParameters) {
      return null;
    }

    /** Runtime counterpart of agent-parameter behavior conformance validation. */
    default boolean implementsBehavior(
        String actualBehaviorUrn, String requiredBehaviorUrn, UserScope scope) {
      if (actualBehaviorUrn == null || requiredBehaviorUrn == null) {
        return false;
      }
      if (Objects.equals(actualBehaviorUrn, requiredBehaviorUrn)) {
        return true;
      }
      return implementsBehavior(
          resolveBehavior(actualBehaviorUrn, scope),
          requiredBehaviorUrn,
          scope,
          new LinkedHashSet<>());
    }

    private boolean implementsBehavior(
        KActorsBehavior actual, String requiredBehaviorUrn, UserScope scope, Set<String> visited) {
      if (actual == null || actual.getUrn() == null || !visited.add(actual.getUrn())) {
        return false;
      }
      if (Objects.equals(actual.getUrn(), requiredBehaviorUrn)) {
        return true;
      }
      return actual.getInheritedBehaviors().stream()
          .map(parent -> resolveBehavior(parent.getImportedBehavior(), scope))
          .anyMatch(parent -> implementsBehavior(parent, requiredBehaviorUrn, scope, visited));
    }

    /**
     * Perform a component-provided adaptation at runtime. Generated agents call this only after a
     * Java actor descriptor with an adapter has been validated for the requested URN.
     */
    default Object adaptToBehavior(
        String behaviorUrn, Object source, RuntimeAgent.Scope runtimeScope) {
      throw new UnsupportedOperationException(
          "No component adapter is available for " + behaviorUrn);
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

    /** Assess whether an adapted behavior instance can be consumed as a boolean condition. */
    default List<Notification> validateBooleanAdaptation(
        KActorsBehavior targetBehavior, UserScope scope) {
      return List.of();
    }

    /** Assess whether an adapted behavior instance can be consumed as an iterable. */
    default List<Notification> validateIterableAdaptation(
        KActorsBehavior targetBehavior, UserScope scope) {
      return List.of();
    }
  }

  /** Compile-time view of a Java actor selected by validation. */
  public record ResolvedActor(
      Extensions.ActorDescriptor descriptor,
      Map<String, ComponentRegistry.ServiceImplementation> verbs,
      ComponentRegistry.ServiceImplementation adapter) {

    public ResolvedActor {
      verbs = verbs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(verbs));
    }

    public ResolvedActor(
        Extensions.ActorDescriptor descriptor,
        Map<String, ComponentRegistry.ServiceImplementation> verbs) {
      this(descriptor, verbs, null);
    }

    public Class<?> implementationClass() {
      var implementationClass =
          verbs.values().stream()
              .map(
                  serviceImplementation ->
                      serviceImplementation == null
                          ? null
                          : serviceImplementation.implementation)
              .filter(Objects::nonNull)
              .findFirst()
              .orElse(null);
      if (implementationClass != null
          || descriptor == null
          || descriptor.javaClassName == null
          || descriptor.javaClassName.isBlank()) {
        return implementationClass;
      }
      try {
        return Class.forName(
            descriptor.javaClassName, false, Thread.currentThread().getContextClassLoader());
      } catch (ClassNotFoundException | LinkageError ignored) {
        return null;
      }
    }
  }

  /** Stable pair of runtime-aware compiler extension points. */
  public record Environment(KActorsVisitor.Validator validator, Resolver resolver) {}

  private record ResolvedCall(
      Verb.Type executionType,
      Boolean staticAction,
      Method javaMethod,
      String producedAgentUrn,
      KActorsAction kActorsAction,
      Class<?> javaActorClass) {

    private ResolvedCall(
        Verb.Type executionType,
        Boolean staticAction,
        Method javaMethod,
        String producedAgentUrn,
        KActorsAction kActorsAction) {
      this(executionType, staticAction, javaMethod, producedAgentUrn, kActorsAction, null);
    }
  }

  private record JavaArgumentConstraint(
      String name,
      boolean optional,
      boolean observation,
      boolean actor,
      boolean constant,
      Class<?> javaType,
      String requiredAgentUrn) {}

  private static final Resolver DEFAULT_RESOLVER = new Resolver() {};
  private static final ResolvedActor CORE_AGENT = createCoreAgentDescriptor();
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
  private final Map<String, KActorsBehavior> resolvedBehaviors = new LinkedHashMap<>();
  private final Map<String, KActorsBehavior> adaptationBehaviors = new LinkedHashMap<>();
  private final Set<Class<?>> requiredRuntimeClasses = new LinkedHashSet<>();
  private final Map<String, KActorsBehavior> inheritedBehaviors = new LinkedHashMap<>();
  private final Map<String, String> inheritedFields = new LinkedHashMap<>();
  private final BehaviorAnalyzer analyzer;
  private String sourceCode;
  private String qualifiedClassName;
  private int generatedName;

  private static ResolvedActor createCoreAgentDescriptor() {
    var descriptor = new Extensions.ActorDescriptor();
    descriptor.urn = CORE_AGENT_URN;
    descriptor.description = "Universal agent behavior";
    descriptor.javaClassName = CoreActorLibrary.Agent.class.getName();
    var implementations = new LinkedHashMap<String, ComponentRegistry.ServiceImplementation>();
    for (Method method : CoreActorLibrary.Agent.class.getDeclaredMethods()) {
      var verb = method.getAnnotation(Verb.class);
      if (verb == null) {
        continue;
      }
      String name = verb.name().isBlank() ? method.getName() : verb.name();
      var service = new ServiceInfoImpl();
      service.setName(CORE_AGENT_URN + "." + name);
      var function = new Extensions.FunctionDescriptor();
      function.serviceInfo = service;
      function.staticMethod = java.lang.reflect.Modifier.isStatic(method.getModifiers());
      function.behaviorUrn =
          verb.producesAgent().isBlank() ? null : verb.producesAgent().trim();
      descriptor.verbs.add(function);
      var implementation = new ComponentRegistry.ServiceImplementation();
      implementation.implementation = CoreActorLibrary.Agent.class;
      implementation.method = method;
      implementations.put(name, implementation);
      implementations.put(service.getName(), implementation);
    }
    return new ResolvedActor(descriptor, implementations);
  }

  private static ResolvedActor resolveActor(
      Resolver resolver, String urn, UserScope scope) {
    var resolved = resolver.resolveActor(urn, scope);
    return resolved == null && CORE_AGENT_URN.equals(urn) ? CORE_AGENT : resolved;
  }

  private static KActorsVisitor.Validator defaultValidator(UserScope scope) {
    return new KActorsVisitor.LenientValidator() {
      @Override
      public Verb.Type classifyActionCall(
          KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
        var resolved = resolveCall(verb, context, DEFAULT_RESOLVER, scope);
        return resolved == null ? null : resolved.executionType();
      }

      @Override
      public Boolean classifyActionStaticity(
          KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
        var resolved = resolveCall(verb, context, DEFAULT_RESOLVER, scope);
        return resolved == null ? null : resolved.staticAction();
      }

      @Override
      public String classifyActionResultBehavior(
          KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
        var resolved = resolveCall(verb, context, DEFAULT_RESOLVER, scope);
        return resolved == null ? null : resolved.producedAgentUrn();
      }

      @Override
      public Class<?> classifyActionResultJavaClass(
          KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
        var resolved = resolveCall(verb, context, DEFAULT_RESOLVER, scope);
        return resolved == null ? null : declaredJavaReturnType(resolved.javaMethod());
      }
    };
  }

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
        return new ResolvedActor(
            descriptor,
            implementations,
            descriptor.adapter == null ? null : registry.implementation(descriptor.adapter));
      }

      @Override
      public Object adaptToBehavior(
          String behaviorUrn, Object source, RuntimeAgent.Scope runtimeScope) {
        var descriptors = registry.getActorDescriptors(behaviorUrn, null);
        if (descriptors.isEmpty()) {
          throw new IllegalArgumentException("Unknown Java actor " + behaviorUrn);
        }
        return registry.invokeAgentAdapter(descriptors.getFirst(), source, runtimeScope);
      }

      @Override
      public List<Object> negotiateParameterMatch(
          List<Class<?>> unmatchedParameterTypes, List<?> suppliedParameters) {
        return registry.negotiateAgentParameters(unmatchedParameterTypes, suppliedParameters);
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
            try {
              var resolved = resolveCall(verb, context, resolver, scope);
              return resolved == null ? null : resolved.executionType();
            } catch (Throwable ignored) {
              // Import validation below reports resolution failures with their source context.
            }
            return null;
          }

          @Override
          public Boolean classifyActionStaticity(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            try {
              var resolved = resolveCall(verb, context, resolver, scope);
              return resolved == null ? null : resolved.staticAction();
            } catch (Throwable ignored) {
              // Resolution diagnostics are produced by import/call validation.
              return null;
            }
          }

          @Override
          public String classifyActionResultBehavior(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            try {
              var resolved = resolveCall(verb, context, resolver, scope);
              return resolved == null ? null : resolved.producedAgentUrn();
            } catch (Throwable ignored) {
              // Import and call validation report resolution failures with lexical context.
              return null;
            }
          }

          @Override
          public Class<?> classifyActionResultJavaClass(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            try {
              var resolved = resolveCall(verb, context, resolver, scope);
              return resolved == null ? null : declaredJavaReturnType(resolved.javaMethod());
            } catch (Throwable ignored) {
              return null;
            }
          }

          @Override
          public List<Notification> validateAction(
              KActorsAction action, KActorsVisitor.KActorsContext context) {
            var ret = new ArrayList<Notification>();
            boolean acknowledgesOverride =
                action.getAnnotations() != null
                    && action.getAnnotations().stream()
                        .anyMatch(annotation -> "override".equals(annotation.getName()));
            String inheritedFrom =
                inheritedActionOwner(
                    context.getBehavior(), action.getUrn(), resolver, scope);
            if (!acknowledgesOverride && inheritedFrom != null) {
              ret.add(
                  Notification.warning(
                      "Action "
                          + action.getUrn()
                          + " overrides "
                          + inheritedFrom
                          + "; add @override to acknowledge it",
                      Notification.LexicalContext.of(action, context.getBehavior())));
            }
            var returnedBehaviorUrn = KActorsVisitor.returnedBehaviorUrn(action);
            if (returnedBehaviorUrn == null
                || Objects.equals(returnedBehaviorUrn, context.getBehavior().getUrn())) {
              return List.copyOf(ret);
            }
            try {
              if (resolver.resolveBehavior(returnedBehaviorUrn, scope) != null
                  || resolveActor(resolver, returnedBehaviorUrn, scope) != null) {
                return List.copyOf(ret);
              }
              ret.add(
                  Notification.error(
                      "Cannot resolve behavior declared by @return: " + returnedBehaviorUrn,
                      Notification.LexicalContext.of(action, context.getBehavior())));
              return List.copyOf(ret);
            } catch (Throwable failure) {
              ret.add(
                  Notification.error(
                      "Cannot resolve behavior declared by @return: " + returnedBehaviorUrn,
                      failure,
                      Notification.LexicalContext.of(action, context.getBehavior())));
              return List.copyOf(ret);
            }
          }

          @Override
          public List<Notification> validateImport(
              KActorsBehavior.Import imported, KActorsVisitor.KActorsContext context) {
            try {
              if (resolver.resolveBehavior(imported.getImportedBehavior(), scope) != null
                  || resolveActor(resolver, imported.getImportedBehavior(), scope) != null) {
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
                if (CORE_AGENT_URN.equals(inheritedBehaviorUrn)
                    && resolveActor(resolver, inheritedBehaviorUrn, scope) != null) {
                  return List.of();
                }
                if (resolveActor(resolver, inheritedBehaviorUrn, scope) != null) {
                  return List.of(
                      Notification.error(
                          "Only the universal Java behavior "
                              + CORE_AGENT_URN
                              + " can be inherited directly",
                          Notification.LexicalContext.of(
                              inheritedBehaviorStatement, context.getBehavior())));
                }
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
                  resolver.resolveBehavior(behaviorUrn, scope),
                  resolver,
                  scope,
                  new LinkedHashSet<>());
            } catch (Throwable ignored) {
              // Inheritance validation reports resolution failures with source context.
              return List.of();
            }
          }

          @Override
          public List<Notification> validateAdaptation(
              KActorsCodeStatement statement,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            try {
              var targetBehavior = resolver.resolveBehavior(behaviorUrn, scope);
              if (targetBehavior != null) {
                var targetAnalyzer = new BehaviorAnalyzer(targetBehavior, this);
                if (!targetAnalyzer.analyze()) {
                  return List.copyOf(targetAnalyzer.getNotifications());
                }
                boolean hasAdapter =
                    targetAnalyzer.getActions().values().stream()
                        .anyMatch(
                            action ->
                                action.statement().getAnnotations().stream()
                                    .anyMatch(annotation -> "adapt".equals(annotation.getName())));
                if (!hasAdapter) {
                  return List.of(
                      Notification.error(
                          "Behavior " + behaviorUrn + " does not declare an @adapt action",
                          Notification.LexicalContext.of(statement, context.getBehavior())));
                }
                return resolver.validateBehaviorAdaptation(targetBehavior, sourceVariable, scope);
              }
              var targetActor = resolver.resolveActor(behaviorUrn, scope);
              if (targetActor != null) {
                if (targetActor.descriptor().adapter == null
                    || targetActor.descriptor().adapter.error
                    || targetActor.adapter() == null
                    || targetActor.adapter().method == null) {
                  return List.of(
                      Notification.error(
                          "Java actor " + behaviorUrn + " does not provide a usable @AgentAdapter",
                          Notification.LexicalContext.of(statement, context.getBehavior())));
                }
                return List.of();
              }
              return List.of(
                  Notification.error(
                      "Cannot resolve adaptation behavior or Java actor " + behaviorUrn,
                      Notification.LexicalContext.of(statement, context.getBehavior())));
            } catch (Throwable failure) {
              return List.of(
                  Notification.error(
                      "Cannot validate adaptation to behavior " + behaviorUrn,
                      failure,
                      Notification.LexicalContext.of(statement, context.getBehavior())));
            }
          }

          @Override
          public List<Notification> validateBooleanAdaptation(
              KActorsCodeStatement statement,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            return validateAdaptedUse(statement, behaviorUrn, context, true, resolver, scope);
          }

          @Override
          public List<Notification> validateIterableAdaptation(
              KActorsCodeStatement statement,
              String behaviorUrn,
              KActorsVisitor.VariableInfo sourceVariable,
              KActorsVisitor.KActorsContext context) {
            return validateAdaptedUse(statement, behaviorUrn, context, false, resolver, scope);
          }

          @Override
          public List<Notification> validateVerbCall(
              KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
            var resolvedCore = resolveCall(verb, context, resolver, scope);
            if (isCoreAgentCall(resolvedCore)) {
              return validateCoreAgentCall(verb, context);
            }
            var imported = findImport(context.getBehavior(), verb.getRecipient());
            if (imported != null) {
              try {
                var targetBehavior =
                    resolver.resolveBehavior(imported.getImportedBehavior(), scope);
                if (targetBehavior != null) {
                  if ("new".equals(verb.getMessage())
                      || targetBehavior.getStatements().stream()
                          .anyMatch(action -> Objects.equals(action.getUrn(), verb.getMessage()))) {
                    return List.of();
                  }
                  return List.of(
                      Notification.error(
                          "Behavior "
                              + imported.getImportedBehavior()
                              + " has no action "
                              + verb.getMessage(),
                          Notification.LexicalContext.of(verb, context.getBehavior())));
                }
                var actor = resolver.resolveActor(imported.getImportedBehavior(), scope);
                if (actor != null
                    && ("new".equals(verb.getMessage())
                        || actor.verbs().containsKey(verb.getMessage()))) {
                  return List.of();
                }
                return List.of(
                    Notification.error(
                        "Actor "
                            + imported.getImportedBehavior()
                            + " has no verb "
                            + verb.getMessage(),
                        Notification.LexicalContext.of(verb, context.getBehavior())));
              } catch (Throwable failure) {
                return List.of(
                    Notification.error(
                        "Cannot validate action "
                            + verb.getMessage()
                            + " on imported actor "
                            + imported.getImportedBehavior(),
                        failure,
                        Notification.LexicalContext.of(verb, context.getBehavior())));
              }
            }
            var variable = context.getVariable(verb.getRecipient());
            if (variable == null || variable.agentUrn() == null) {
              return List.of();
            }
            try {
              var producerImport = findImport(context.getBehavior(), variable.agentUrn());
              var targetUrn =
                  producerImport == null
                      ? variable.agentUrn()
                      : producerImport.getImportedBehavior();
              var targetBehavior = resolver.resolveBehavior(targetUrn, scope);
              if (targetBehavior != null) {
                boolean actionExists =
                    targetBehavior.getStatements().stream()
                        .anyMatch(action -> Objects.equals(action.getUrn(), verb.getMessage()));
                return actionExists
                    ? List.of()
                    : List.of(
                        Notification.error(
                            "Behavior " + targetUrn + " has no action " + verb.getMessage(),
                            Notification.LexicalContext.of(verb, context.getBehavior())));
              }
              var targetActor = resolver.resolveActor(targetUrn, scope);
              if (targetActor != null) {
                return targetActor.verbs().containsKey(verb.getMessage())
                    ? List.of()
                    : List.of(
                        Notification.error(
                            "Java actor " + targetUrn + " has no verb " + verb.getMessage(),
                            Notification.LexicalContext.of(verb, context.getBehavior())));
              }
              return List.of(
                  Notification.error(
                      "Cannot resolve actor behavior " + targetUrn,
                      Notification.LexicalContext.of(verb, context.getBehavior())));
            } catch (Throwable failure) {
              return List.of(
                  Notification.error(
                      "Cannot validate action "
                          + verb.getMessage()
                          + " on actor behavior "
                          + variable.agentUrn(),
                      failure,
                      Notification.LexicalContext.of(verb, context.getBehavior())));
            }
          }

          @Override
          public List<Notification> validateArguments(
              KActorsStatement.Verb verb,
              KActorsStatement.Arguments arguments,
              KActorsVisitor.KActorsContext context) {
            try {
              var resolved = resolveCall(verb, context, resolver, scope);
              if (resolved == null) {
                return List.of();
              }
              if (resolved.kActorsAction() != null) {
                return validateKActorsActionArguments(
                    verb, arguments, resolved.kActorsAction(), context, resolver, scope);
              }
              if ("new".equals(verb.getMessage())
                  && resolved.javaMethod() == null
                  && resolved.javaActorClass() == null) {
                var supplied = ordinaryArgumentValues(arguments);
                return supplied.isEmpty()
                    ? List.of()
                    : List.of(
                        Notification.error(
                            "Behavior "
                                + resolved.producedAgentUrn()
                                + " has no init action and new accepts no arguments",
                            Notification.LexicalContext.of(verb, context.getBehavior())));
              }
              if ("new".equals(verb.getMessage()) && resolved.javaActorClass() != null) {
                return validateJavaConstructorArguments(
                    verb, arguments, resolved.javaActorClass(), context, resolver);
              }
              if (resolved.javaMethod() == null) {
                return List.of();
              }
              var supplied = ordinaryArgumentValues(arguments);
              var constrained =
                  validateJavaVerbArguments(verb, arguments, resolved, context, resolver, scope);
              if (!constrained.isEmpty()) {
                return constrained;
              }
              if (annotatedArityMatches(resolved, arguments)) {
                return List.of();
              }
              var expected = negotiableParameterTypes(resolved.javaMethod());
              var negotiated = resolver.negotiateParameterMatch(expected, supplied);
              if (negotiated != null
                  && directArityMatches(resolved.javaMethod(), negotiated.size())) {
                return List.of();
              }
              return List.of(
                  Notification.error(
                      parameterMismatchMessage(resolved.javaMethod(), expected, supplied),
                      Notification.LexicalContext.of(verb, context.getBehavior())));
            } catch (Throwable failure) {
              return List.of(
                  Notification.error(
                      "Cannot validate parameters for "
                          + verb.getRecipient()
                          + "."
                          + verb.getMessage(),
                      failure,
                      Notification.LexicalContext.of(verb, context.getBehavior())));
            }
          }
        };
    return new Environment(validator, resolver);
  }

  private static String inheritedActionOwner(
      KActorsBehavior behavior,
      String actionName,
      Resolver resolver,
      UserScope scope) {
    String explicit =
        inheritedActionOwner(
            behavior, actionName, resolver, scope, new LinkedHashSet<>());
    if (explicit != null) {
      return explicit;
    }
    return CORE_AGENT.verbs().containsKey(actionName) ? CORE_AGENT_URN : null;
  }

  private static String inheritedActionOwner(
      KActorsBehavior behavior,
      String actionName,
      Resolver resolver,
      UserScope scope,
      Set<String> visited) {
    if (behavior == null || behavior.getUrn() == null || !visited.add(behavior.getUrn())) {
      return null;
    }
    for (var inherited : behavior.getInheritedBehaviors()) {
      String urn = inherited.getImportedBehavior();
      var inheritedBehavior = resolver.resolveBehavior(urn, scope);
      if (inheritedBehavior != null) {
        if (inheritedBehavior.getStatements().stream()
            .anyMatch(action -> Objects.equals(action.getUrn(), actionName))) {
          return urn;
        }
        String nested =
            inheritedActionOwner(
                inheritedBehavior, actionName, resolver, scope, visited);
        if (nested != null) {
          return nested;
        }
      } else {
        var actor = resolveActor(resolver, urn, scope);
        if (actor != null && actor.verbs().containsKey(actionName)) {
          return urn;
        }
      }
    }
    return null;
  }

  private static boolean isCoreAgentCall(ResolvedCall call) {
    return call != null
        && call.javaMethod() != null
        && call.javaMethod().getDeclaringClass() == CoreActorLibrary.Agent.class;
  }

  private static List<Notification> validateCoreAgentCall(
      KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context) {
    var ret = new ArrayList<Notification>();
    var metadata =
        verb.getArguments() == null || verb.getArguments().getMetadataKeys() == null
            ? List.<String>of()
            : verb.getArguments().getMetadataKeys();
    for (String key : metadata) {
      if (!"ask".equals(verb.getMessage()) || !"timeout".equals(key)) {
        ret.add(
            Notification.error(
                "Unsupported metadata :" + key + " for core.agent." + verb.getMessage(),
                Notification.LexicalContext.of(verb, context.getBehavior())));
      }
    }
    if ("ask".equals(verb.getMessage()) && metadata.contains("timeout")) {
      Object timeout = verb.getArguments().get("timeout");
      boolean disabled = Boolean.FALSE.equals(timeout);
      if (timeout instanceof KActorsValue value && value.getType() == ValueType.BOOLEAN) {
        disabled = Boolean.FALSE.equals(value.getValue(Boolean.class));
      }
      if (!disabled
          && (!(timeout instanceof KActorsValue value)
              || value.getType() != ValueType.QUANTITY)) {
        ret.add(
            Notification.error(
                "The ask timeout must be a temporal :timeout Quantity or disabled with !timeout",
                Notification.LexicalContext.of(verb, context.getBehavior())));
      }
    }
    return List.copyOf(ret);
  }

  private static List<Notification> validateAdaptedUse(
      KActorsCodeStatement statement,
      String behaviorUrn,
      KActorsVisitor.KActorsContext context,
      boolean booleanUse,
      Resolver resolver,
      UserScope scope) {
    try {
      var targetBehavior = resolver.resolveBehavior(behaviorUrn, scope);
      if (targetBehavior == null) {
        return resolver.resolveActor(behaviorUrn, scope) == null
            ? List.of(
                Notification.error(
                    "Cannot resolve adaptation behavior or Java actor " + behaviorUrn,
                    Notification.LexicalContext.of(statement, context.getBehavior())))
            : List.of();
      }
      return booleanUse
          ? resolver.validateBooleanAdaptation(targetBehavior, scope)
          : resolver.validateIterableAdaptation(targetBehavior, scope);
    } catch (Throwable failure) {
      return List.of(
          Notification.error(
              "Cannot validate "
                  + (booleanUse ? "boolean" : "iterable")
                  + " use of adapted behavior "
                  + behaviorUrn,
              failure,
              Notification.LexicalContext.of(statement, context.getBehavior())));
    }
  }

  private static ResolvedCall resolveCall(
      KActorsStatement.Verb verb,
      KActorsVisitor.KActorsContext context,
      Resolver resolver,
      UserScope scope) {
    String actorUrn = null;
    var recipient = verb.getRecipient();
    var imported = findImport(context.getBehavior(), recipient);
    if (imported != null) {
      actorUrn = imported.getImportedBehavior();
    } else if (recipient == null || recipient.isBlank() || "self".equals(recipient)) {
      actorUrn = context.getBehavior().getUrn();
    } else {
      var variable = context.getVariable(recipient);
      if (variable != null && variable.agentUrn() != null) {
        var producerImport = findImport(context.getBehavior(), variable.agentUrn());
        actorUrn =
            producerImport == null ? variable.agentUrn() : producerImport.getImportedBehavior();
      }
    }
    if (actorUrn == null) {
      var variable = recipient == null ? null : context.getVariable(recipient);
      if (variable != null
          && variable.agentUrn() == null
          && (variable.javaClass() == null
              || org.integratedmodelling.klab.api.actors.Agent.class.isAssignableFrom(
                  variable.javaClass()))) {
        return resolveJavaActorCall(CORE_AGENT, verb.getMessage());
      }
      return null;
    }
    var targetBehavior = resolver.resolveBehavior(actorUrn, scope);
    if (targetBehavior != null) {
      if ("new".equals(verb.getMessage()) && imported != null) {
        var init =
            targetBehavior.getStatements().stream()
                .filter(action -> Objects.equals(action.getUrn(), "init"))
                .findFirst()
                .orElse(null);
        return new ResolvedCall(Verb.Type.FUNCTION, true, null, actorUrn, init);
      }
      var resolved =
          resolveBehaviorCall(
              targetBehavior,
              verb.getMessage(),
              resolver,
              scope,
              new LinkedHashSet<>());
      if (resolved != null || imported != null) {
        return resolved;
      }
      return resolveJavaActorCall(CORE_AGENT, verb.getMessage());
    }
    var actor = resolveActor(resolver, actorUrn, scope);
    var implementation = actor == null ? null : actor.verbs().get(verb.getMessage());
    if (actor != null && "new".equals(verb.getMessage()) && imported != null) {
      if (implementation != null && implementation.method != null) {
        var annotation = implementation.method.getAnnotation(Verb.class);
        return new ResolvedCall(
            annotation == null ? Verb.Type.FUNCTION : annotation.executionType(),
            java.lang.reflect.Modifier.isStatic(implementation.method.getModifiers()),
            implementation.method,
            actorUrn,
            null);
      }
      return new ResolvedCall(
          Verb.Type.FUNCTION, true, null, actorUrn, null, actor.implementationClass());
    }
    if (implementation == null || implementation.method == null) {
      return imported == null ? resolveJavaActorCall(CORE_AGENT, verb.getMessage()) : null;
    }
    return resolveJavaActorCall(actor, verb.getMessage());
  }

  private static ResolvedCall resolveBehaviorCall(
      KActorsBehavior behavior,
      String actionName,
      Resolver resolver,
      UserScope scope,
      Set<String> visited) {
    if (behavior == null || behavior.getUrn() == null || !visited.add(behavior.getUrn())) {
      return null;
    }
    var local =
        behavior.getStatements().stream()
            .filter(action -> Objects.equals(action.getUrn(), actionName))
            .findFirst()
            .orElse(null);
    if (local != null) {
      return new ResolvedCall(
          local.getActionType(),
          local.isStatic(),
          null,
          KActorsVisitor.returnedBehaviorUrn(local),
          local);
    }
    for (var inherited : behavior.getInheritedBehaviors()) {
      var inheritedBehavior = resolver.resolveBehavior(inherited.getImportedBehavior(), scope);
      var resolved =
          inheritedBehavior == null
              ? resolveJavaActorCall(
                  resolveActor(resolver, inherited.getImportedBehavior(), scope), actionName)
              : resolveBehaviorCall(inheritedBehavior, actionName, resolver, scope, visited);
      if (resolved != null) {
        return resolved;
      }
    }
    return null;
  }

  private static ResolvedCall resolveJavaActorCall(ResolvedActor actor, String actionName) {
    var implementation = actor == null ? null : actor.verbs().get(actionName);
    if (implementation == null || implementation.method == null) {
      return null;
    }
    var annotation = implementation.method.getAnnotation(Verb.class);
    var type = annotation == null ? Verb.Type.FUNCTION : annotation.executionType();
    var descriptor =
        actor.descriptor().verbs.stream()
            .filter(
                candidate ->
                    candidate.serviceInfo != null
                        && Objects.equals(
                            simpleName(candidate.serviceInfo.getName()), actionName))
            .findFirst()
            .orElse(null);
    return new ResolvedCall(
        type,
        descriptor == null ? null : descriptor.staticMethod,
        implementation.method,
        descriptor == null || descriptor.behaviorUrn == null || descriptor.behaviorUrn.isBlank()
            ? null
            : descriptor.behaviorUrn.trim(),
        null);
  }

  private static List<Notification> validateKActorsActionArguments(
      KActorsStatement.Verb verb,
      KActorsStatement.Arguments arguments,
      KActorsAction action,
      KActorsVisitor.KActorsContext context,
      Resolver resolver,
      UserScope scope) {
    var formals =
        action.getArguments() == null ? List.<KActorsAction.Argument>of() : action.getArguments();
    var bound = new Object[formals.size()];
    var notifications = new ArrayList<Notification>();
    if (arguments != null) {
      for (var key : arguments.getNamedKeys()) {
        if (isMetadataArgument(arguments, key)) {
          continue;
        }
        int index = kActorsArgumentIndex(formals, key);
        if (index < 0) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  "Unknown named argument '" + key + "' for action " + action.getUrn()));
        } else {
          bound[index] = arguments.get(key);
        }
      }
      int next = 0;
      for (var value : arguments.getUnnamedArguments()) {
        while (next < bound.length && bound[next] != null) {
          next++;
        }
        if (next == bound.length) {
          notifications.add(
              argumentError(
                  verb, context, "Too many positional arguments for action " + action.getUrn()));
          break;
        }
        bound[next++] = value;
      }
    }
    for (int index = 0; index < formals.size(); index++) {
      var formal = formals.get(index);
      Object supplied = bound[index];
      var required = KActorsVisitor.actionArgumentType(formal);
      if (supplied == null) {
        notifications.add(
            argumentError(
                verb,
                context,
                "Missing required argument '"
                    + formal.getName()
                    + "' for action "
                    + action.getUrn()));
        continue;
      }
      if (required == null) {
        continue;
      }
      if (required.behaviorUrn() != null) {
        String actualAgent = agentUrnOf(supplied, context, resolver, scope);
        if (actualAgent != null
            && !behaviorIsOrInherits(
                actualAgent, required.behaviorUrn(), resolver, scope, new LinkedHashSet<>())) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  "Argument '"
                      + formal.getName()
                      + "' for action "
                      + action.getUrn()
                      + " requires an agent implementing "
                      + required.behaviorUrn()
                      + ", but "
                      + actualAgent
                      + " was supplied"));
        }
      } else if (required.javaClassName() != null) {
        var actualType = knownJavaType(supplied, context, resolver, scope);
        if (actualType != null
            && actualType.definitive()
            && !matchesJavaType(actualType, required.javaClassName())) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  "Argument '"
                      + formal.getName()
                      + "' for action "
                      + action.getUrn()
                      + " must be "
                      + required.javaClassName()
                      + ", but "
                      + actualType.name()
                      + " was supplied"));
        }
      }
    }
    return List.copyOf(notifications);
  }

  private static int kActorsArgumentIndex(List<KActorsAction.Argument> arguments, String name) {
    for (int index = 0; index < arguments.size(); index++) {
      if (Objects.equals(arguments.get(index).getName(), name)) {
        return index;
      }
    }
    return -1;
  }

  private record KnownJavaType(Class<?> type, String name, boolean definitive) {}

  private static KnownJavaType knownJavaType(
      Object supplied, KActorsVisitor.KActorsContext context, Resolver resolver, UserScope scope) {
    KActorsStatement.Verb suppliedFunction =
        supplied instanceof KActorsStatement.CallArgument argument
            ? argument.getFunction()
            : supplied instanceof KActorsStatement.Verb verb ? verb : null;
    if (suppliedFunction != null) {
      var produced = resolveCall(suppliedFunction, context, resolver, scope);
      Class<?> returnType = produced == null ? null : declaredJavaReturnType(produced.javaMethod());
      if (returnType != null && returnType != Void.class && returnType != void.class) {
        return new KnownJavaType(
            returnType,
            returnType.getTypeName(),
            returnType.isPrimitive()
                || returnType.isEnum()
                || java.lang.reflect.Modifier.isFinal(returnType.getModifiers()));
      }
    }
    Object literal = literalValue(supplied);
    if (literal != UNKNOWN_LITERAL) {
      return literal == null
          ? null
          : new KnownJavaType(literal.getClass(), literal.getClass().getTypeName(), true);
    }
    if (!(supplied instanceof KActorsValue value) || value.getType() != ValueType.IDENTIFIER) {
      return null;
    }
    var variable = context.getVariable(value.getValue(String.class));
    if (variable == null) {
      return null;
    }
    if (variable.producerCall() != null) {
      var producer = resolveCall(variable.producerCall(), context, resolver, scope);
      Class<?> produced = producer == null ? null : declaredJavaReturnType(producer.javaMethod());
      if (produced != null && produced != Void.class && produced != void.class) {
        return new KnownJavaType(
            produced,
            produced.getTypeName(),
            produced.isPrimitive()
                || produced.isEnum()
                || java.lang.reflect.Modifier.isFinal(produced.getModifiers()));
      }
    }
    if (variable.statement() instanceof KActorsAction declaringAction) {
      var formal =
          declaringAction.getArguments().stream()
              .filter(argument -> Objects.equals(argument.getName(), variable.name()))
              .findFirst()
              .orElse(null);
      var declared = KActorsVisitor.actionArgumentType(formal);
      if (declared != null && declared.javaClassName() != null) {
        Class<?> type = loadJavaType(declared.javaClassName(), null);
        return new KnownJavaType(
            type,
            declared.javaClassName(),
            type != null && java.lang.reflect.Modifier.isFinal(type.getModifiers()));
      }
    }
    return null;
  }

  private static List<Notification> validateJavaConstructorArguments(
      KActorsStatement.Verb verb,
      KActorsStatement.Arguments arguments,
      Class<?> actorClass,
      KActorsVisitor.KActorsContext context,
      Resolver resolver) {
    var supplied = ordinaryArgumentValues(arguments);
    for (var constructor : actorClass.getConstructors()) {
      if (directArityMatches(constructor, supplied.size())) {
        return List.of();
      }
      var expected = negotiableParameterTypes(constructor);
      var negotiated = resolver.negotiateParameterMatch(expected, supplied);
      if (negotiated != null && directArityMatches(constructor, negotiated.size())) {
        return List.of();
      }
    }
    return List.of(
        Notification.error(
            "No public constructor of "
                + actorClass.getName()
                + " accepts "
                + supplied.size()
                + " argument(s)",
            Notification.LexicalContext.of(verb, context.getBehavior())));
  }

  private static Class<?> declaredJavaReturnType(Method method) {
    if (method == null) {
      return null;
    }
    var verb = method.getAnnotation(Verb.class);
    if (verb != null && verb.returns() != Void.class) {
      return boxed(verb.returns());
    }
    if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
      var generic = method.getGenericReturnType();
      if (generic instanceof java.lang.reflect.ParameterizedType parameterized
          && parameterized.getActualTypeArguments().length == 1
          && parameterized.getActualTypeArguments()[0] instanceof Class<?> resultType) {
        return boxed(resultType);
      }
      return null;
    }
    return boxed(method.getReturnType());
  }

  private static boolean matchesJavaType(KnownJavaType actual, String requiredName) {
    if (!requiredName.contains(".")) {
      String simple =
          actual.type() == null ? simpleName(actual.name()) : actual.type().getSimpleName();
      return simple != null && simple.equalsIgnoreCase(requiredName);
    }
    if (Objects.equals(actual.name(), requiredName)) {
      return true;
    }
    Class<?> required = loadJavaType(requiredName, actual.type());
    return required != null && actual.type() != null && required.isAssignableFrom(actual.type());
  }

  private static Class<?> loadJavaType(String name, Class<?> relatedType) {
    if (name == null || !name.contains(".")) {
      return null;
    }
    try {
      ClassLoader loader =
          relatedType == null
              ? Thread.currentThread().getContextClassLoader()
              : relatedType.getClassLoader();
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException | LinkageError ignored) {
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException | LinkageError alsoIgnored) {
        return null;
      }
    }
  }

  private static List<Notification> validateJavaVerbArguments(
      KActorsStatement.Verb verb,
      KActorsStatement.Arguments arguments,
      ResolvedCall resolved,
      KActorsVisitor.KActorsContext context,
      Resolver resolver,
      UserScope scope) {
    var constraints = javaArgumentConstraints(resolved.javaMethod());
    if (constraints.isEmpty()) {
      return List.of();
    }
    var bound = new Object[constraints.size()];
    var notifications = new ArrayList<Notification>();
    if (arguments != null) {
      for (var key : arguments.getNamedKeys()) {
        if (isMetadataArgument(arguments, key)) {
          continue;
        }
        int index = argumentIndex(constraints, key);
        if (index < 0) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  "Unknown named argument '" + key + "' for Java verb " + verb.getMessage()));
        } else {
          bound[index] = arguments.get(key);
        }
      }
      int next = 0;
      for (var value : arguments.getUnnamedArguments()) {
        while (next < bound.length && bound[next] != null) {
          next++;
        }
        if (next == bound.length) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  "Too many positional arguments for Java verb " + verb.getMessage()));
          break;
        }
        bound[next++] = value;
      }
    }
    for (int index = 0; index < constraints.size(); index++) {
      var constraint = constraints.get(index);
      Object supplied = bound[index];
      String label =
          constraint.name() == null || constraint.name().isBlank()
              ? "argument " + (index + 1)
              : "argument '" + constraint.name() + "'";
      if (supplied == null) {
        if (!constraint.optional()) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  "Missing required " + label + " for Java verb " + verb.getMessage()));
        }
        continue;
      }
      if (constraint.constant()
          && supplied instanceof KActorsValue value
          && !isPodLiteral(value.getType())) {
        notifications.add(
            argumentError(
                verb,
                context,
                label + " for Java verb " + verb.getMessage() + " must be a literal value"));
        continue;
      }
      String requiredAgent = normalized(constraint.requiredAgentUrn());
      if (requiredAgent != null) {
        String actualAgent = agentUrnOf(supplied, context, resolver, scope);
        if (actualAgent != null
            && !behaviorIsOrInherits(
                actualAgent, requiredAgent, resolver, scope, new LinkedHashSet<>())) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  label
                      + " for Java verb "
                      + verb.getMessage()
                      + " requires an agent implementing "
                      + requiredAgent
                      + ", but "
                      + actualAgent
                      + " was supplied"));
        }
        continue;
      }
      Object literal = literalValue(supplied);
      if (literal != UNKNOWN_LITERAL) {
        Class<?> expected = constraint.javaType();
        if (expected != null && literal != null && !boxed(expected).isInstance(literal)) {
          notifications.add(
              argumentError(
                  verb,
                  context,
                  label
                      + " for Java verb "
                      + verb.getMessage()
                      + " must be "
                      + boxed(expected).getTypeName()
                      + ", but a "
                      + literal.getClass().getTypeName()
                      + " literal was supplied"));
        }
      }
    }
    return List.copyOf(notifications);
  }

  private static final Object UNKNOWN_LITERAL = new Object();

  private static Object literalValue(Object supplied) {
    if (supplied instanceof KActorsStatement.CallArgument
        || supplied instanceof KActorsStatement.Verb
        || supplied instanceof KActorsStatement.Switch) {
      return UNKNOWN_LITERAL;
    }
    if (!(supplied instanceof KActorsValue value)) {
      return supplied;
    }
    if (!isPodLiteral(value.getType())) {
      return UNKNOWN_LITERAL;
    }
    return value.getValue(Object.class);
  }

  private static boolean isPodLiteral(ValueType type) {
    return type == ValueType.STRING
        || type == ValueType.NUMBER
        || type == ValueType.INTEGER
        || type == ValueType.DOUBLE
        || type == ValueType.BOOLEAN
        || type == ValueType.CONSTANT
        || type == ValueType.QUANTITY
        || type == ValueType.DATE
        || type == ValueType.RANGE
        || type == ValueType.URN;
  }

  private static String agentUrnOf(
      Object supplied,
      KActorsVisitor.KActorsContext context,
      Resolver resolver,
      UserScope scope) {
    KActorsStatement.Verb function =
        supplied instanceof KActorsStatement.CallArgument argument
            ? argument.getFunction()
            : supplied instanceof KActorsStatement.Verb verb ? verb : null;
    if (function != null) {
      var resolved = resolveCall(function, context, resolver, scope);
      return resolved == null ? null : normalized(resolved.producedAgentUrn());
    }
    if (supplied instanceof KActorsValue value && value.getType() == ValueType.IDENTIFIER) {
      var variable = context.getVariable(value.getValue(String.class));
      return variable == null ? null : normalized(variable.agentUrn());
    }
    return null;
  }

  private static boolean behaviorIsOrInherits(
      String actualUrn,
      String requiredUrn,
      Resolver resolver,
      UserScope scope,
      Set<String> visited) {
    if (Objects.equals(actualUrn, requiredUrn)) {
      return true;
    }
    if (actualUrn == null || !visited.add(actualUrn)) {
      return false;
    }
    var behavior = resolver.resolveBehavior(actualUrn, scope);
    if (behavior == null || behavior.getInheritedBehaviors() == null) {
      return false;
    }
    return behavior.getInheritedBehaviors().stream()
        .map(KActorsBehavior.Import::getImportedBehavior)
        .anyMatch(parent -> behaviorIsOrInherits(parent, requiredUrn, resolver, scope, visited));
  }

  private static Notification argumentError(
      KActorsStatement.Verb verb, KActorsVisitor.KActorsContext context, String message) {
    return Notification.error(message, Notification.LexicalContext.of(verb, context.getBehavior()));
  }

  private static int argumentIndex(List<JavaArgumentConstraint> constraints, String name) {
    for (int index = 0; index < constraints.size(); index++) {
      if (Objects.equals(name, constraints.get(index).name())) {
        return index;
      }
    }
    return -1;
  }

  private static boolean annotatedArityMatches(
      ResolvedCall resolved, KActorsStatement.Arguments arguments) {
    var constraints = javaArgumentConstraints(resolved.javaMethod());
    if (constraints.isEmpty()) {
      return directArityMatches(resolved.javaMethod(), ordinaryArgumentValues(arguments).size());
    }
    int supplied = ordinaryArgumentValues(arguments).size();
    long required = constraints.stream().filter(argument -> !argument.optional()).count();
    return supplied >= required && supplied <= constraints.size();
  }

  private static List<JavaArgumentConstraint> javaArgumentConstraints(Method method) {
    if (method == null
        || java.util.Arrays.stream(method.getParameters())
            .noneMatch(parameter -> parameter.isAnnotationPresent(Verb.Argument.class))) {
      return List.of();
    }
    var ret = new ArrayList<JavaArgumentConstraint>();
    for (var parameter : method.getParameters()) {
      if (RuntimeAgent.Scope.class.isAssignableFrom(parameter.getType())) {
        continue;
      }
      var argument = parameter.getAnnotation(Verb.Argument.class);
      ret.add(
          new JavaArgumentConstraint(
              argument == null || argument.name().isBlank() ? null : argument.name().trim(),
              argument != null && argument.optional(),
              argument != null && argument.observation(),
              argument != null && argument.actor(),
              argument != null && argument.constant(),
              argument == null || argument.type() == Object.class
                  ? parameter.getType()
                  : argument.type(),
              argument == null || argument.requiresAgent().isBlank()
                  ? null
                  : argument.requiresAgent().trim()));
    }
    return List.copyOf(ret);
  }

  private static Class<?> boxed(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == boolean.class) return Boolean.class;
    if (type == byte.class) return Byte.class;
    if (type == short.class) return Short.class;
    if (type == int.class) return Integer.class;
    if (type == long.class) return Long.class;
    if (type == float.class) return Float.class;
    if (type == double.class) return Double.class;
    if (type == char.class) return Character.class;
    return type;
  }

  private static String normalized(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static List<Object> ordinaryArgumentValues(KActorsStatement.Arguments arguments) {
    return KActorsVisitor.argumentValues(arguments);
  }

  private static boolean isMetadataArgument(
      KActorsStatement.Arguments arguments, String key) {
    return arguments != null
        && arguments.getMetadataKeys() != null
        && arguments.getMetadataKeys().contains(key);
  }

  private static boolean directArityMatches(Executable method, int suppliedCount) {
    int required = 0;
    for (int index = 0; index < method.getParameterCount(); index++) {
      if (RuntimeAgent.Scope.class.isAssignableFrom(method.getParameterTypes()[index])) {
        continue;
      }
      if (method.isVarArgs() && index == method.getParameterCount() - 1) {
        return suppliedCount >= required;
      }
      required++;
    }
    return suppliedCount == required;
  }

  private static List<Class<?>> negotiableParameterTypes(Executable method) {
    var ret = new ArrayList<Class<?>>();
    for (int index = 0; index < method.getParameterCount(); index++) {
      var parameter = method.getParameterTypes()[index];
      if (RuntimeAgent.Scope.class.isAssignableFrom(parameter)) {
        continue;
      }
      ret.add(
          method.isVarArgs() && index == method.getParameterCount() - 1
              ? parameter.getComponentType()
              : parameter);
    }
    return List.copyOf(ret);
  }

  private static String parameterMismatchMessage(
      Method method, List<Class<?>> expected, List<?> supplied) {
    return "Cannot match parameters for Java verb "
        + method.getDeclaringClass().getName()
        + "."
        + method.getName()
        + ": expected "
        + expected.stream().map(Class::getTypeName).toList()
        + " but received "
        + supplied.size()
        + " argument(s); parameter negotiation did not produce a compatible match";
  }

  private static KActorsBehavior.Import findImport(KActorsBehavior behavior, String alias) {
    if (behavior == null || alias == null) {
      return null;
    }
    return behavior.getImports().stream()
        .filter(candidate -> Objects.equals(candidate.getImportedAlias(), alias))
        .findFirst()
        .orElse(null);
  }

  private static String simpleName(String name) {
    int separator = name == null ? -1 : name.lastIndexOf('.');
    return separator < 0 ? name : name.substring(separator + 1);
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
        defaultValidator(scope),
        DEFAULT_RESOLVER);
  }

  public AgentCompiler(KActorsBehavior behavior, UserScope scope) {
    this(
        behavior,
        scope,
        defaultValidator(scope),
        DEFAULT_RESOLVER);
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
    this(
        behavior,
        null,
        defaultValidator(null),
        DEFAULT_RESOLVER);
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
    resolvedBehaviors.clear();
    adaptationBehaviors.clear();
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
      resolvedBehaviors.put(imported.name(), importedBehavior);
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
        if (CORE_AGENT_URN.equals(inheritedUrn.getImportedBehavior())
            && resolveActor(resolver, CORE_AGENT_URN, scope) != null) {
          requiredRuntimeClasses.add(CoreActorLibrary.Agent.class);
          continue;
        }
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

    for (String adaptationUrn : adaptationUrns(behavior)) {
      var actor = resolver.resolveActor(adaptationUrn, scope);
      if (actor != null) {
        if (actor.adapter() != null && actor.adapter().implementation != null) {
          requiredRuntimeClasses.add(actor.adapter().implementation);
        }
        continue;
      }
      var targetBehavior = resolver.resolveBehavior(adaptationUrn, scope);
      if (targetBehavior == null) {
        continue;
      }
      adaptationBehaviors.put(adaptationUrn, targetBehavior);
      if (Objects.equals(targetBehavior.getUrn(), behavior.getUrn())
          || dependencySources.containsKey(targetBehavior.getUrn())
          || path.contains(targetBehavior.getUrn())) {
        continue;
      }
      var nextPath = new LinkedHashSet<>(path);
      nextPath.add(targetBehavior.getUrn());
      var compiler = new AgentCompiler(targetBehavior, scope, validator, resolver);
      if (compiler.analyzer.analyze()) {
        compiler.indexAnalysis();
        compiler.resolveImportsAndCompileDependencies(nextPath);
        requiredRuntimeClasses.addAll(compiler.requiredRuntimeClasses);
        var javaFile = compiler.generateClass(targetBehavior);
        if (javaFile != null) {
          compiler.sourceCode = javaFile.toString();
          dependencySources.putAll(compiler.dependencySources);
          dependencySources.put(targetBehavior.getUrn(), compiler.sourceCode);
          generatedActorSources.put(targetBehavior.getUrn(), compiler.sourceCode);
        }
      }
      notifications.addAll(compiler.analyzer.getNotifications());
    }
  }

  private Set<String> adaptationUrns(KActorsBehavior target) {
    var ret = new LinkedHashSet<String>();
    if (target != null && target.getStatements() != null) {
      for (var action : target.getStatements()) {
        if (action.getCode() != null) {
          action.getCode().forEach(statement -> collectAdaptationUrns(statement, ret));
        }
      }
    }
    return ret;
  }

  private void collectAdaptationUrns(KActorsStatement statement, Set<String> urns) {
    if (statement == null) {
      return;
    }
    String adapted =
        switch (statement) {
          case KActorsStatement.Assignment value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.Return value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.Fire value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.Yield value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.Switch value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.If value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.While value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.Do value -> value.getAdaptedBehaviorUrn();
          case KActorsStatement.For value -> value.getAdaptedBehaviorUrn();
          default -> null;
        };
    if (adapted != null && !adapted.isBlank()) {
      urns.add(adapted.trim());
    }
    switch (statement) {
      case KActorsStatement.Group group ->
          group.getStatements().forEach(child -> collectAdaptationUrns(child, urns));
      case KActorsStatement.Verb verb -> {
        if (verb.getArguments() != null) {
          for (var argument : verb.getArguments().values()) {
            if (argument instanceof KActorsStatement.CallArgument executable) {
              collectAdaptationUrns(executable.getFunction(), urns);
              collectAdaptationUrns(executable.getSwitch(), urns);
              if (executable.getAdaptedBehaviorUrn() != null
                  && !executable.getAdaptedBehaviorUrn().isBlank()) {
                urns.add(executable.getAdaptedBehaviorUrn().trim());
              }
            }
          }
        }
        verb.getActions().forEach(match -> collectAdaptationUrns(match.getActionOnMatch(), urns));
      }
      case KActorsStatement.Verb.MatchAction match ->
          collectAdaptationUrns(match.getActionOnMatch(), urns);
      case KActorsStatement.Switch switched ->
          switched
              .getCases()
              .forEach(match -> collectAdaptationUrns(match.getActionOnMatch(), urns));
      case KActorsStatement.Assignment value -> collectAdaptationUrns(value.getSwitch(), urns);
      case KActorsStatement.Return value -> collectAdaptationUrns(value.getSwitch(), urns);
      case KActorsStatement.Fire value -> collectAdaptationUrns(value.getSwitch(), urns);
      case KActorsStatement.Yield value -> collectAdaptationUrns(value.getSwitch(), urns);
      case KActorsStatement.If conditional -> {
        collectAdaptationUrns(conditional.getThenBody(), urns);
        conditional.getElseIfs().forEach(branch -> collectAdaptationUrns(branch.getSecond(), urns));
        collectAdaptationUrns(conditional.getElseBody(), urns);
      }
      case KActorsStatement.While loop -> collectAdaptationUrns(loop.getBody(), urns);
      case KActorsStatement.Do loop -> collectAdaptationUrns(loop.getBody(), urns);
      case KActorsStatement.For loop -> collectAdaptationUrns(loop.getBody(), urns);
      default -> {
        // Leaf statement.
      }
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
    classBuilder.addMethod(compileImplementedBehaviorUrns(sourceBehavior));
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
      } else if (resolvedBehaviors.containsKey(imported.name())) {
        constructor.addStatement(
            "this.$L = resolveImportedBehavior($S, $S, importedActors, importedInitArguments -> registerImportedBehavior(new $T(null, scope, observation, creationScope, importedActors, importedInitArguments)))",
            field,
            imported.behaviorUrn(),
            imported.name(),
            generatedClass(resolvedBehaviors.get(imported.name()).getUrn()));
      } else {
        constructor.addStatement(
            "this.$L = resolveImportedActor($S, $S, importedActors)",
            field,
            imported.behaviorUrn(),
            imported.name());
      }
    }
    for (var entry : adaptationBehaviors.entrySet()) {
      var adapterAction = adaptationAction(entry.getValue());
      if (adapterAction == null || adapterAction.getActionType() == Verb.Type.EMITTER) {
        continue;
      }
      if (Objects.equals(entry.getKey(), behavior.getUrn())) {
        constructor.addStatement(
            "registerBehaviorAdapter($S, this, $S, $T.$L)",
            entry.getKey(),
            adapterAction.getUrn(),
            Verb.Type.class,
            adapterAction.getActionType().name());
      } else {
        constructor.addStatement(
            "registerBehaviorAdapter($S, new $T(null, scope, observation, creationScope, importedActors, new Object[0]), $S, $T.$L)",
            entry.getKey(),
            generatedClass(entry.getValue().getUrn()),
            adapterAction.getUrn(),
            Verb.Type.class,
            adapterAction.getActionType().name());
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

  private KActorsAction adaptationAction(KActorsBehavior target) {
    if (target == null || target.getStatements() == null) {
      return null;
    }
    return target.getStatements().stream()
        .filter(
            action ->
                action.getAnnotations() != null
                    && action.getAnnotations().stream()
                        .anyMatch(annotation -> "adapt".equals(annotation.getName())))
        .findFirst()
        .orElse(null);
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
                  "The @handle annotation requires a CONSTANT as its unnamed parameter or 'class' parameter",
                  lexicalContext(action, sourceBehavior)));
          continue;
        }
        method.addStatement(
            "handlers.put($S, new $T($S, $T.$L, $L, $L))",
            messageClass,
            handlerType,
            action.getUrn(),
            Verb.Type.class,
            action.getActionType().name(),
            stringList(
                action.getArguments().stream().map(KActorsAction.Argument::getName).toList()),
            !standardInput);
      }
    }
    method.addStatement("return $T.copyOf(handlers)", Map.class);
    return method.build();
  }

  private Notification.LexicalContext lexicalContext(
      KActorsStatement statement, KActorsBehavior sourceBehavior) {
    var ret = new NotificationImpl.LexicalContextImpl();
    int offset = statement.getOffsetInDocument();
    int length = statement.getLength();
    if (offset == 0
        && length == 0
        && statement instanceof KActorsAction action
        && sourceBehavior.getSourceCode() != null) {
      /*
       * Older or generic JSON transports may retain the behavior source while dropping statement
       * parsing coordinates. Recover the narrow @handle/action header so a genuine diagnostic is
       * still useful instead of being reported at 0,0.
       */
      var matcher =
          Pattern.compile(
                  "@handle\\s*(?:\\([^\\r\\n]*\\))?\\s*(?:\\R\\s*)*"
                      + "(?:static\\s+)?action\\s+"
                      + Pattern.quote(action.getUrn())
                      + "\\b")
              .matcher(sourceBehavior.getSourceCode());
      if (matcher.find()) {
        offset = matcher.start();
        length = matcher.end() - matcher.start();
      }
    }
    ret.setOffsetInDocument(offset);
    ret.setLength(length);
    ret.setDocumentUrn(sourceBehavior.getUrn());
    ret.setProjectUrn(sourceBehavior.getProjectName());
    return ret;
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
    var requiredBehaviors =
        action.getArguments().stream()
            .map(KActorsVisitor::actionArgumentType)
            .map(contract -> contract == null ? null : contract.behaviorUrn())
            .toList();
    var requiredJavaTypes =
        action.getArguments().stream()
            .map(KActorsVisitor::actionArgumentType)
            .map(contract -> contract == null ? null : contract.javaClassName())
            .toList();
    if (requiredBehaviors.stream().anyMatch(Objects::nonNull)
        || requiredJavaTypes.stream().anyMatch(Objects::nonNull)) {
      code.addStatement(
          "arguments = validateActionArguments($S, $L, $L, $L, arguments)",
          action.getUrn(),
          stringList(action.getArguments().stream().map(KActorsAction.Argument::getName).toList()),
          nullableStringList(requiredBehaviors),
          nullableStringList(requiredJavaTypes));
    }
    code.addStatement(
        "var frame = bindArguments($L, arguments)",
        stringList(action.getArguments().stream().map(KActorsAction.Argument::getName).toList()));
    String result = null;
    if (type == Verb.Type.SUPPLIER) {
      result = "actionResult";
      code.addStatement("var $L = new $T<Object>()", result, CompletableFuture.class);
    }
    var context =
        new CompilationContext(
            type,
            false,
            false,
            "main".equals(action.getUrn())
                && analyzer.getLifecycle() == BehaviorAnalyzer.Lifecycle.PERSISTENT,
            "scope",
            "frame",
            result,
            List.of());
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
              adaptedValue(
                  valueSource(fire.getValue(), fire.getFunction(), fire.getSwitch(), code, context),
                  fire.getAdaptedBehaviorUrn(),
                  context));
      case KActorsStatement.Switch switchStatement ->
          emitSwitch(switchStatement, code, context, null);
      case KActorsStatement.Yield yielded -> emitYield(yielded, code, context);
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
    CodeBlock value =
        adaptedValue(
            valueSource(
                assignment.getValue(),
                assignment.getFunction(),
                assignment.getSwitch(),
                code,
                context),
            assignment.getAdaptedBehaviorUrn(),
            context);
    if (assignment.getAssignmentScope() == KActorsStatement.Assignment.Scope.ACTOR) {
      code.addStatement("setActorState($S, $L)", assignment.getVariable(), value);
    } else {
      code.addStatement("$L.put($S, $L)", context.frame(), assignment.getVariable(), value);
    }
  }

  private void emitReturn(
      KActorsStatement.Return returned, CodeBlock.Builder code, CompilationContext context) {
    CodeBlock value =
        adaptedValue(
            valueSource(
                returned.getValue(), returned.getFunction(), returned.getSwitch(), code, context),
            returned.getAdaptedBehaviorUrn(),
            context);
    if (context.terminatesAgentOnReturn()) {
      code.addStatement("terminateAgent($L)", value);
    }
    if (context.reactive()) {
      if (context.actionType() == Verb.Type.SUPPLIER && context.result() != null) {
        code.addStatement("$L.complete($L)", context.result(), value);
      }
      if (!context.terminatesAgentOnReturn()) {
        code.addStatement("$L.done($L)", context.scope(), value);
      }
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
        if (!context.terminatesAgentOnReturn()) {
          code.addStatement("$L.done($L)", context.scope(), value);
        }
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
        "if ($L)",
        conditionValue(
            conditional.getCondition(),
            conditional.getFunction(),
            conditional.getAdaptedBehaviorUrn(),
            context));
    emitStatement(
        conditional.getThenBody(), code, context.withoutCompletionCollectors(), awaitCompletion);
    for (var elseIf : conditional.getElseIfs()) {
      code.nextControlFlow(
          "else if ($L)",
          conditionValue(
              elseIf.getFirst().getFirst(),
              elseIf.getFirst().getSecond(),
              elseIf.getFirst().getThird(),
              context));
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
        "while ($L)",
        conditionValue(
            loop.getCondition(), loop.getFunction(), loop.getAdaptedBehaviorUrn(), context));
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
        "while ($L)",
        conditionValue(
            loop.getCondition(), loop.getFunction(), loop.getAdaptedBehaviorUrn(), context));
  }

  private void emitFor(
      KActorsStatement.For loop,
      CodeBlock.Builder code,
      CompilationContext context,
      boolean awaitCompletion) {
    String item = nextName("item");
    CodeBlock iterable = valueOrCall(loop.getIterable(), loop.getFunction(), context);
    iterable =
        loop.getAdaptedBehaviorUrn() == null || loop.getAdaptedBehaviorUrn().isBlank()
            ? CodeBlock.of("asIterable($L)", iterable)
            : CodeBlock.of(
                "adaptToIterable(adaptToBehavior($L, $S, $L), $L)",
                iterable,
                loop.getAdaptedBehaviorUrn().trim(),
                context.scope(),
                context.scope());
    code.beginControlFlow("for (Object $L : $L)", item, iterable);
    if (loop.getVariable() != null && !loop.getVariable().isBlank()) {
      code.addStatement("$L.put($S, $L)", context.frame(), loop.getVariable(), item);
    }
    emitStatement(loop.getBody(), code, context.withoutCompletionCollectors(), awaitCompletion);
    code.endControlFlow();
  }

  private void emitYield(
      KActorsStatement.Yield yielded, CodeBlock.Builder code, CompilationContext context) {
    CodeBlock value =
        adaptedValue(
            valueSource(
                yielded.getValue(), yielded.getFunction(), yielded.getSwitch(), code, context),
            yielded.getAdaptedBehaviorUrn(),
            context);
    if (context.reactive() && !context.synchronous()) {
      if (context.actionType() == Verb.Type.SUPPLIER && context.result() != null) {
        code.addStatement("$L.complete($L)", context.result(), value);
      }
      code.addStatement("$L.done($L)", context.scope(), value);
      code.addStatement("return");
    } else {
      code.addStatement("throw new $T($L)", RuntimeAgentBase.SwitchYield.class, value);
    }
  }

  /**
   * Emit a switch synchronously. When {@code requestedResult} is non-null it receives the yielded
   * value; branches that match without yielding leave it null.
   */
  private String emitSwitch(
      KActorsStatement.Switch switchStatement,
      CodeBlock.Builder code,
      CompilationContext context,
      String requestedResult) {
    String target = nextName("switchValue");
    CodeBlock selector =
        adaptedValue(
            valueOrCall(switchStatement.getValue(), switchStatement.getFunction(), context),
            switchStatement.getAdaptedBehaviorUrn(),
            context);
    code.addStatement("Object $L = $L", target, selector);

    boolean functional = switchContainsYield(switchStatement);
    String result = requestedResult;
    if (functional) {
      if (result == null) {
        result = nextName("switchResult");
      }
      code.addStatement("Object $L = null", result);
      code.beginControlFlow("try");
    }

    boolean first = true;
    for (var match : switchStatement.getCases()) {
      CodeBlock criterion = matchCriterion(match, context);
      if (first) {
        code.beginControlFlow(
            "if (matches($L, $T.$L, $L, $L))",
            target,
            ValueType.class,
            matchType(match).name(),
            criterion,
            match.getMatchCriterion() != null && match.getMatchCriterion().isExclusive());
        first = false;
      } else {
        code.nextControlFlow(
            "else if (matches($L, $T.$L, $L, $L))",
            target,
            ValueType.class,
            matchType(match).name(),
            criterion,
            match.getMatchCriterion() != null && match.getMatchCriterion().isExclusive());
      }
      String matchFrame = nextName("switchFrame");
      code.addStatement("var $L = childFrame($L)", matchFrame, context.frame());
      code.addStatement(
          "bindMatch($L, $L, $L, $S)",
          matchFrame,
          target,
          stringList(match.getVariables()),
          match.getCaptureAs());
      emitStatement(
          match.getActionOnMatch(),
          code,
          context.withFrame(matchFrame).withoutCompletionCollectors().asSynchronous());
    }
    if (!first) {
      code.endControlFlow();
    }

    if (functional) {
      code.nextControlFlow("catch ($T yielded)", RuntimeAgentBase.SwitchYield.class);
      code.addStatement("$L = yielded.value()", result);
      code.endControlFlow();
    }
    return result;
  }

  private boolean switchContainsYield(KActorsStatement.Switch switchStatement) {
    return switchStatement.getCases().stream()
        .map(KActorsStatement.Verb.MatchAction::getActionOnMatch)
        .anyMatch(this::containsSwitchYield);
  }

  private boolean containsSwitchYield(KActorsStatement statement) {
    if (statement == null) {
      return false;
    }
    if (statement instanceof KActorsStatement.Yield) {
      return true;
    }
    if (statement instanceof KActorsStatement.Switch) {
      return false;
    }
    if (statement instanceof KActorsStatement.Group group) {
      return group.getStatements().stream().anyMatch(this::containsSwitchYield);
    }
    if (statement instanceof KActorsStatement.If conditional) {
      return containsSwitchYield(conditional.getThenBody())
          || conditional.getElseIfs().stream()
              .anyMatch(branch -> containsSwitchYield(branch.getSecond()))
          || containsSwitchYield(conditional.getElseBody());
    }
    if (statement instanceof KActorsStatement.While loop) {
      return containsSwitchYield(loop.getBody());
    }
    if (statement instanceof KActorsStatement.Do loop) {
      return containsSwitchYield(loop.getBody());
    }
    if (statement instanceof KActorsStatement.For loop) {
      return containsSwitchYield(loop.getBody());
    }
    return false;
  }

  private CodeBlock valueSource(
      KActorsValue value,
      KActorsStatement.Verb function,
      KActorsStatement.Switch switchStatement,
      CodeBlock.Builder code,
      CompilationContext context) {
    if (switchStatement != null) {
      String result = nextName("switchResult");
      emitSwitch(switchStatement, code, context, result);
      return CodeBlock.of("$L", result);
    }
    return valueOrCall(value, function, context);
  }

  private CodeBlock adaptedValue(CodeBlock value, String behaviorUrn, CompilationContext context) {
    return behaviorUrn == null || behaviorUrn.isBlank()
        ? value
        : CodeBlock.of("adaptToBehavior($L, $S, $L)", value, behaviorUrn.trim(), context.scope());
  }

  private CodeBlock conditionValue(
      KActorsValue value,
      KActorsStatement.Verb function,
      String behaviorUrn,
      CompilationContext context) {
    CodeBlock raw = valueOrCall(value, function, context);
    return behaviorUrn == null || behaviorUrn.isBlank()
        ? CodeBlock.of("truthy($L)", raw)
        : CodeBlock.of(
            "adaptToBoolean(adaptToBehavior($L, $S, $L), $L)",
            raw,
            behaviorUrn.trim(),
            context.scope(),
            context.scope());
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
    if (context.synchronous() && (type == null || type == Verb.Type.SUPPLIER)) {
      emitSynchronousValueVerb(verb, code, context);
      return;
    }
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

  private void emitSynchronousValueVerb(
      KActorsStatement.Verb verb, CodeBlock.Builder code, CompilationContext context) {
    String supplied = nextName("supplied");
    code.addStatement("Object $L = $L", supplied, callValue(verb, context));
    emitSynchronousMatches(verb, supplied, code, context);
  }

  private void emitSynchronousMatches(
      KActorsStatement.Verb verb,
      String supplied,
      CodeBlock.Builder code,
      CompilationContext context) {
    if (verb.getActions() == null || verb.getActions().isEmpty()) {
      return;
    }
    boolean first = true;
    for (var match : verb.getActions()) {
      CodeBlock criterion = matchCriterion(match, context);
      if (first) {
        code.beginControlFlow(
            "if (matches($L, $T.$L, $L, $L))",
            supplied,
            ValueType.class,
            matchType(match).name(),
            criterion,
            match.getMatchCriterion() != null && match.getMatchCriterion().isExclusive());
        first = false;
      } else {
        code.nextControlFlow(
            "else if (matches($L, $T.$L, $L, $L))",
            supplied,
            ValueType.class,
            matchType(match).name(),
            criterion,
            match.getMatchCriterion() != null && match.getMatchCriterion().isExclusive());
      }
      String matchFrame = nextName("matchFrame");
      code.addStatement("var $L = childFrame($L)", matchFrame, context.frame());
      code.addStatement(
          "bindMatch($L, $L, $L, $S)",
          matchFrame,
          supplied,
          stringList(match.getVariables()),
          match.getCaptureAs());
      emitStatement(
          match.getActionOnMatch(),
          code,
          context.withFrame(matchFrame).withoutCompletionCollectors().asSynchronous());
    }
    if (!first) {
      code.endControlFlow();
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
    var callInfo = calls.get(verb);
    if (isCoreAgentInvocation(verb, callInfo)) {
      return invokeCoreAgentVerb(verb, type, context);
    }
    if (callInfo != null
        && callInfo.javaMethod() != null
        && java.lang.reflect.Modifier.isPublic(
            callInfo.javaMethod().getDeclaringClass().getModifiers())) {
      return invokeJavaObjectMethod(verb, callInfo.javaMethod(), context);
    }
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

  private boolean isCoreAgentInvocation(
      KActorsStatement.Verb verb, KActorsVisitor.CallInfo call) {
    if (verb == null || !CORE_AGENT.verbs().containsKey(verb.getMessage())) {
      return false;
    }
    String recipient =
        verb.getRecipient() == null || verb.getRecipient().isBlank()
            ? "self"
            : verb.getRecipient();
    if (findImport(behavior, recipient) != null) {
      return false;
    }
    if ("self".equals(recipient)) {
      return resolveBehaviorCall(
              behavior, verb.getMessage(), resolver, scope, new LinkedHashSet<>())
          == null;
    }
    var variable = call == null ? null : call.knownVariables().get(recipient);
    if (variable == null) {
      return false;
    }
    if (variable.agentUrn() != null) {
      var targetBehavior = resolver.resolveBehavior(variable.agentUrn(), scope);
      if (targetBehavior != null) {
        return resolveBehaviorCall(
                targetBehavior, verb.getMessage(), resolver, scope, new LinkedHashSet<>())
            == null;
      }
      var targetActor = resolveActor(resolver, variable.agentUrn(), scope);
      return targetActor == null || !targetActor.verbs().containsKey(verb.getMessage());
    }
    return variable.javaClass() == null
        || org.integratedmodelling.klab.api.actors.Agent.class.isAssignableFrom(
            variable.javaClass());
  }

  private CodeBlock invokeCoreAgentVerb(
      KActorsStatement.Verb verb, Verb.Type type, CompilationContext context) {
    CodeBlock recipient =
        verb.getRecipient() == null || "self".equals(verb.getRecipient())
            ? CodeBlock.of("this")
            : receiver(verb.getRecipient(), context);
    var supplied = new ArrayList<CodeBlock>();
    for (Object argument : ordinaryArgumentValues(verb.getArguments())) {
      supplied.add(CodeBlock.of("resolveDeferred($L)", argumentValue(argument, context)));
    }
    if ("ask".equals(verb.getMessage())) {
      Object timeout =
          verb.getArguments() == null ? null : verb.getArguments().get("timeout");
      supplied.add(
          timeout == null
              ? CodeBlock.of("null")
              : CodeBlock.of("resolveDeferred($L)", argumentValue(timeout, context)));
    }
    String operation =
        switch (type) {
          case FUNCTION -> "invokeFunction";
          case SUPPLIER -> "invokeSupplier";
          case EMITTER -> "invokeEmitter";
        };
    return CodeBlock.of(
        "$L(coreAgent($L), $S, $L, $L)",
        operation,
        recipient,
        verb.getMessage(),
        context.scope(),
        CodeBlock.of("new Object[] {$L}", CodeBlock.join(supplied, ", ")));
  }

  private CodeBlock invokeJavaObjectMethod(
      KActorsStatement.Verb verb, Method method, CompilationContext context) {
    requiredRuntimeClasses.add(method.getDeclaringClass());
    var supplied = ordinaryArgumentValues(verb.getArguments());
    var compiledArguments = new ArrayList<CodeBlock>();
    var parameterTypes = method.getParameterTypes();
    for (int index = 0; index < supplied.size(); index++) {
      Class<?> expected =
          method.isVarArgs() && index >= parameterTypes.length - 1
              ? parameterTypes[parameterTypes.length - 1].getComponentType()
              : parameterTypes[index];
      compiledArguments.add(
          adaptedJavaArgument(argumentValue(supplied.get(index), context), expected));
    }
    return CodeBlock.of(
        "(($T) $L).$L($L)",
        method.getDeclaringClass(),
        receiver(verb.getRecipient(), context),
        method.getName(),
        CodeBlock.join(compiledArguments, ", "));
  }

  private CodeBlock adaptedJavaArgument(CodeBlock argument, Class<?> expected) {
    if (expected == Object.class) {
      return CodeBlock.of("resolveDeferred($L)", argument);
    }
    Class<?> target = boxed(expected);
    return CodeBlock.of(
        "(($T) adaptJavaArgument($L, $T.class))", target, argument, target);
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
    ordinaryArgumentValues(verb.getArguments())
        .forEach(argument -> values.add(argumentValue(argument, context)));
    return CodeBlock.of("new Object[] {$L}", CodeBlock.join(values, ", "));
  }

  private CodeBlock argumentValue(Object argument, CompilationContext context) {
    if (argument instanceof KActorsValue value) {
      return value(value, context);
    }
    if (argument instanceof KActorsStatement.CallArgument executable) {
      CodeBlock result =
          executable.getFunction() != null
              ? callValue(executable.getFunction(), context)
              : functionalSwitchValue(executable.getSwitch(), context);
      return adaptedValue(result, executable.getAdaptedBehaviorUrn(), context);
    }
    // Compatibility with semantic models that carried the executable statement directly.
    if (argument instanceof KActorsStatement.Verb function) {
      return callValue(function, context);
    }
    if (argument instanceof KActorsStatement.Switch switchStatement) {
      return functionalSwitchValue(switchStatement, context);
    }
    return literal(argument, ValueType.STRING);
  }

  private CodeBlock functionalSwitchValue(
      KActorsStatement.Switch switchStatement, CompilationContext context) {
    if (switchStatement == null) {
      return CodeBlock.of("null");
    }
    var body = CodeBlock.builder();
    String result = emitSwitch(switchStatement, body, context.asSynchronous(), null);
    body.addStatement(
        "return $L", result == null ? CodeBlock.of("null") : CodeBlock.of("$L", result));
    // A CodeBlock containing statement markers cannot itself be nested inside the outer invocation
    // statement. Render this self-contained lambda body first, then insert the resulting Java
    // source as one literal expression.
    String bodySource = body.build().toString();
    return CodeBlock.of(
        "(($T<Object>) () -> {\n$L}).get()", java.util.function.Supplier.class, bodySource);
  }

  private CodeBlock value(KActorsValue value, CompilationContext context) {
    if (value == null) {
      return CodeBlock.of("null");
    }
    if (value.isDeferred()) {
      return CodeBlock.of("defer(() -> $L)", immediateValue(value, context));
    }
    return immediateValue(value, context);
  }

  private CodeBlock immediateValue(KActorsValue value, CompilationContext context) {
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
      case CONSTANT -> CodeBlock.of("$T.create($S)", Constant.class, String.valueOf(raw));
      case NUMBER, INTEGER, DOUBLE, BOOLEAN, STRING -> literal(raw, value.getType());
      case LIST -> collectionLiteral(raw, context, false);
      case SET -> collectionLiteral(raw, context, true);
      case MAP -> mapLiteral(raw, context);
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

  private CodeBlock collectionLiteral(
      Object raw, CompilationContext context, boolean asSet) {
    if (!(raw instanceof Collection<?> collection)) {
      return asSet
          ? CodeBlock.of("new $T<>()", LinkedHashSet.class)
          : CodeBlock.of("new $T<>()", ArrayList.class);
    }
    if (collection.isEmpty()) {
      return asSet
          ? CodeBlock.of("new $T<>()", LinkedHashSet.class)
          : CodeBlock.of("new $T<>()", ArrayList.class);
    }
    var elements = collection.stream().map(value -> objectLiteral(value, context)).toList();
    return CodeBlock.of(
        "new $T<>($T.asList($L))",
        asSet ? LinkedHashSet.class : ArrayList.class,
        java.util.Arrays.class,
        CodeBlock.join(elements, ", "));
  }

  private CodeBlock mapLiteral(Object raw, CompilationContext context) {
    if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
      return CodeBlock.of("new $T<>()", LinkedHashMap.class);
    }
    var keys = map.keySet().stream().map(value -> objectLiteral(value, context)).toList();
    var values = map.values().stream().map(value -> objectLiteral(value, context)).toList();
    return CodeBlock.of(
        "mutableMap(new Object[] {$L}, new Object[] {$L})",
        CodeBlock.join(keys, ", "),
        CodeBlock.join(values, ", "));
  }

  private CodeBlock objectLiteral(Object raw, CompilationContext context) {
    if (raw == null) {
      return CodeBlock.of("null");
    }
    if (raw instanceof KActorsValue value) {
      return value(value, context);
    }
    if (raw instanceof Identifier identifier) {
      return CodeBlock.of(
          "resolveIdentifier($S, $L)", identifier.getValue(), context.frame());
    }
    if (raw instanceof Constant constant) {
      return CodeBlock.of("$T.create($S)", Constant.class, constant.getValue());
    }
    if (raw instanceof Map<?, ?>) {
      return mapLiteral(raw, context);
    }
    if (raw instanceof Set<?>) {
      return collectionLiteral(raw, context, true);
    }
    if (raw instanceof Collection<?>) {
      return collectionLiteral(raw, context, false);
    }
    if (raw instanceof String
        || raw instanceof Character
        || raw instanceof Boolean
        || raw instanceof Number) {
      return literal(raw, raw instanceof String ? ValueType.STRING : ValueType.NUMBER);
    }
    return CodeBlock.of(
        "literalValue($T.$L, $S)", ValueType.class, ValueType.STRING.name(), raw.toString());
  }

  private CodeBlock ternary(Object raw, CompilationContext context) {
    if (!(raw instanceof Ternary ternary)) {
      throw new IllegalArgumentException(
          "Invalid k.Actors ternary value: expected Ternary, found " + valueClass(raw));
    }
    if (!(ternary.getCondition() instanceof KActorsValue condition)
        || ternary.getTrueCase() == null
        || ternary.getFalseCase() == null) {
      throw new IllegalArgumentException(
          "Invalid k.Actors ternary components: condition="
              + valueClass(ternary.getCondition())
              + ", true="
              + valueClass(ternary.getTrueCase())
              + ", false="
              + valueClass(ternary.getFalseCase()));
    }
    return CodeBlock.of(
        "(truthy($L) ? $L : $L)",
        value(condition, context),
        ternaryBranch(ternary.getTrueCase(), context),
        ternaryBranch(ternary.getFalseCase(), context));
  }

  private String valueClass(Object value) {
    return value == null ? "null" : value.getClass().getName();
  }

  private CodeBlock ternaryBranch(Object branch, CompilationContext context) {
    return switch (branch) {
      case KActorsValue value -> value(value, context);
      case KActorsStatement.Verb function -> callValue(function, context);
      case KActorsStatement.Switch switchStatement ->
          functionalSwitchValue(switchStatement, context);
      default ->
          throw new IllegalArgumentException(
              "Unsupported k.Actors ternary branch " + branch.getClass().getName());
    };
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

  private CodeBlock nullableStringList(Collection<String> strings) {
    if (strings == null || strings.isEmpty()) {
      return CodeBlock.of("$T.of()", List.class);
    }
    var values =
        strings.stream()
            .map(value -> value == null ? CodeBlock.of("null") : CodeBlock.of("$S", value))
            .toList();
    return CodeBlock.of("$T.asList($L)", java.util.Arrays.class, CodeBlock.join(values, ", "));
  }

  private MethodSpec compileImplementedBehaviorUrns(KActorsBehavior sourceBehavior) {
    var urns = new LinkedHashSet<String>();
    collectImplementedBehaviorUrns(sourceBehavior, urns);
    return MethodSpec.methodBuilder("implementedBehaviorUrns")
        .addAnnotation(Override.class)
        .addModifiers(Modifier.PROTECTED)
        .returns(ParameterizedTypeName.get(ClassName.get(Set.class), ClassName.get(String.class)))
        .addStatement("return $L", stringSet(urns))
        .build();
  }

  private void collectImplementedBehaviorUrns(KActorsBehavior sourceBehavior, Set<String> urns) {
    if (sourceBehavior == null
        || sourceBehavior.getUrn() == null
        || !urns.add(sourceBehavior.getUrn())) {
      return;
    }
    for (var inherited : sourceBehavior.getInheritedBehaviors()) {
      try {
        collectImplementedBehaviorUrns(
            resolver.resolveBehavior(inherited.getImportedBehavior(), scope), urns);
      } catch (Throwable ignored) {
        // Resolution diagnostics are already reported by behavior analysis.
      }
    }
  }

  private CodeBlock stringSet(Collection<String> strings) {
    if (strings == null || strings.isEmpty()) {
      return CodeBlock.of("$T.of()", Set.class);
    }
    var values = strings.stream().map(value -> CodeBlock.of("$S", value)).toList();
    return CodeBlock.of("$T.of($L)", Set.class, CodeBlock.join(values, ", "));
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
      boolean synchronous,
      boolean terminatesAgentOnReturn,
      String scope,
      String frame,
      String result,
      List<List<String>> completionCollectors) {

    CompilationContext withFrame(String newFrame) {
      return new CompilationContext(
          actionType,
          reactive,
          synchronous,
          terminatesAgentOnReturn,
          scope,
          newFrame,
          result,
          completionCollectors);
    }

    CompilationContext asReactive(String newScope, String newFrame) {
      return new CompilationContext(
          actionType, true, false, terminatesAgentOnReturn, newScope, newFrame, result, List.of());
    }

    CompilationContext asSynchronous() {
      return synchronous
          ? this
          : new CompilationContext(
              actionType, reactive, true, terminatesAgentOnReturn, scope, frame, result, List.of());
    }

    CompilationContext withoutCompletionCollectors() {
      return completionCollectors.isEmpty()
          ? this
          : new CompilationContext(
              actionType,
              reactive,
              synchronous,
              terminatesAgentOnReturn,
              scope,
              frame,
              result,
              List.of());
    }

    CompilationContext collectingCompletions(List<String> collector) {
      var collectors = new ArrayList<>(completionCollectors);
      collectors.add(collector);
      return new CompilationContext(
          actionType,
          reactive,
          synchronous,
          terminatesAgentOnReturn,
          scope,
          frame,
          result,
          collectors);
    }

    boolean isCollectingCompletions() {
      return !completionCollectors.isEmpty();
    }

    void trackCompletion(String completion) {
      completionCollectors.forEach(collector -> collector.add(completion));
    }
  }
}
