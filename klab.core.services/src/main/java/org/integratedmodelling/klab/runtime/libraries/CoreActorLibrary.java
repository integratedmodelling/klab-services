package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;

@Library(name = "core")
public class CoreActorLibrary {

  /**
   * Universal Java behavior inherited implicitly by every k.Actors behavior.
   *
   * <p>The compiler binds an instance of this class to the recipient of a self/agent call. This
   * keeps the base contract in the ordinary Java actor catalog while ensuring that messages are
   * sent by the calling runtime agent rather than by a detached serialized handle.
   *
   * <p>Because the validator runs before compilation, any new verb should be declared in
   * RuntimeAgent.java explicitly for them to be recognized. TODO we should use the agent descriptor
   * or reflection once to retrieve them.
   */
  @Actor(
      name = "agent",
      description =
          "The universal agent contract. Every k.Actors behavior implicitly inherits these verbs.")
  public static final class Agent {

    private final Object target;

    public Agent(Object target) {
      this.target = target;
    }

    @Verb(
        name = "new",
        executionType = Verb.Type.FUNCTION,
        description =
            "Construction contract implemented by behavior and Java actor specifications.")
    public Object newAgent(RuntimeAgent.Scope scope, Object... arguments) {
      throw new IllegalStateException(
          "The core.agent new verb requires a behavior or Java actor specification");
    }

    @Verb(
        name = "tell",
        executionType = Verb.Type.FUNCTION,
        returns = Void.class,
        description = "Send one custom message to this agent.")
    public void tell(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "class", description = "Custom message class", constant = true)
            Constant messageClass,
        @Verb.Argument(name = "payload", description = "Serializable message payload")
            Object payload) {
      runtime(scope).tellAgentValue(target, messageClass, payload);
    }

    @Verb(name = "duration", executionType = Verb.Type.FUNCTION, returns = TimeDuration.class)
    public static TimeDuration duration(Quantity time) {
      Objects.requireNonNull(time, "quantity");
      return TimeDuration.of(time);
    }

    @Verb(
        name = "ask",
        executionType = Verb.Type.SUPPLIER,
        description = "Send a correlated custom message and supply its response.")
    public CompletableFuture<Object> ask(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "class", description = "Custom message class", constant = true)
            Constant messageClass,
        @Verb.Argument(name = "payload", description = "Serializable request payload")
            Object payload,
        @Verb.Argument(
                name = "timeout",
                description = "Temporal timeout, null for the runtime default, or false to disable",
                optional = true)
            Object timeout) {
      return runtime(scope).askAgentValue(target, messageClass, payload, timeout);
    }

    @Verb(
        name = "name",
        executionType = Verb.Type.FUNCTION,
        returns = String.class,
        description = "Return this agent's non-unique display name.")
    public String name(RuntimeAgent.Scope scope) {
      return runtime(scope).agentName(target);
    }

    @Verb(
        name = "urn",
        executionType = Verb.Type.FUNCTION,
        returns = String.class,
        description = "Return this agent's runtime-wide unique URN.")
    public String urn(RuntimeAgent.Scope scope) {
      return runtime(scope).agentUrn(target);
    }

    private RuntimeAgentBase runtime(RuntimeAgent.Scope scope) {
      if (scope == null || !(scope.getAgent() instanceof RuntimeAgentBase runtime)) {
        throw new IllegalStateException("core.agent requires a generated runtime agent scope");
      }
      return runtime;
    }
  }

  /**
   * Static actor class methods map to static actors. They must be declared (although these core
   * ones may be automatically linked, TBD). It should be illegal to use a constructor if there are
   * only static methods annotated with @Verb.
   */
  @Actor(
      name = "console",
      description =
          "A static actor that prints to whatever console was configured for the agent. All methods are static and can be called directly without instantiating the actor.")
  public static class Console {

    @Verb(name = "println", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void println(RuntimeAgent.Scope scope, Object... messages) {
      write(
          scope, RuntimeAgent.ConsoleMessageType.STDOUT, render(messages) + System.lineSeparator());
    }

    @Verb(name = "print", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void print(RuntimeAgent.Scope scope, Object... messages) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDOUT, render(messages));
    }

    @Verb(name = "format", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void format(RuntimeAgent.Scope scope, String format, Object... args) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDOUT, String.format(format, args));
    }

    @Verb(name = "printf", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void printf(RuntimeAgent.Scope scope, String format, Object... args) {
      format(scope, format, args);
    }

    @Verb(name = "error", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void error(RuntimeAgent.Scope scope, Object... messages) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDERR, render(messages));
    }

    @Verb(name = "errorln", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void errorln(RuntimeAgent.Scope scope, Object... messages) {
      write(
          scope, RuntimeAgent.ConsoleMessageType.STDERR, render(messages) + System.lineSeparator());
    }

    @Verb(name = "errorf", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void errorf(RuntimeAgent.Scope scope, String format, Object... args) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDERR, String.format(format, args));
    }

    @Verb(name = "flush", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void flush(RuntimeAgent.Scope scope) {
      scope.getPrintWriter().flush();
    }

    private static String render(Object... messages) {
      if (messages == null || messages.length == 0) {
        return "";
      }
      var builder = new StringBuilder();
      for (var message : messages) {
        builder.append(String.valueOf(message));
      }
      return builder.toString();
    }

    private static void write(
        RuntimeAgent.Scope scope, RuntimeAgent.ConsoleMessageType stream, String text) {
      boolean delivered =
          scope.getAgent() instanceof RuntimeAgentBase runtime
              ? runtime.sendToConsole(scope, stream, text)
              : scope.getAgent().sendToConsole(stream, text);
      if (!delivered) {
        if (stream == RuntimeAgent.ConsoleMessageType.STDERR) {
          System.err.print(text);
          System.err.flush();
        } else {
          scope.getPrintWriter().print(text);
          scope.getPrintWriter().flush();
        }
      }
    }
  }

  public static class File {}

  /** ContextScope proxy; query contracts and proposed event emitters are in docs/DIGITALTWINS.md. */
  @Actor(name = "context", description = "Digital twin actor")
  public static class Context {

    private final ContextScope context;

    public Context() {
      this.context = null;
    }

    public Context(ContextScope context) {
      this.context = Objects.requireNonNull(context, "context");
    }

    private ContextScope requireContext() {
      if (context == null) throw new KlabIllegalStateException("Use context.new, context.current or context.wrap first");
      return context;
    }

    @Verb(name = "wrap", executionType = Verb.Type.FUNCTION, producesAgent = "core.context",
        description = "Borrow an existing ContextScope without creating a twin or taking cleanup ownership")
    public static Context wrap(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Existing ContextScope") ContextScope context) {
      return new Context(context);
    }

    @Verb(name = "current", executionType = Verb.Type.FUNCTION, producesAgent = "core.context",
        description = "Borrow the calling agent's current context; fails if the agent has no context")
    public static Context current(RuntimeAgent.Scope scope) {
      if (scope == null || scope.getContext() == null) throw new KlabIllegalStateException("The calling agent has no context");
      return new Context(scope.getContext());
    }

    @Verb(name = "focus", executionType = Verb.Type.FUNCTION, producesAgent = "core.context",
        description = "Return a proxy focused by :within observation or :source observation :target observation")
    public Context focus(RuntimeAgent.Scope scope, Metadata options) {
      for (String key : options.keySet()) if (!Set.of("within", "source", "target").contains(key))
        throw new IllegalArgumentException("Unknown focus option: " + key);
      return new Context(ContextActorSupport.focus(requireContext(), options));
    }

    @Verb(name = "scope", executionType = Verb.Type.FUNCTION, returns = ContextScope.class)
    public ContextScope scope(RuntimeAgent.Scope scope) { return requireContext(); }

    @Verb(name = "twin", executionType = Verb.Type.FUNCTION, returns = DigitalTwin.class)
    public DigitalTwin twin(RuntimeAgent.Scope scope) {
      var twin = requireContext().getDigitalTwin();
      if (twin == null) throw new KlabIllegalStateException("No digital twin is available in this context");
      return twin;
    }

    @Verb(name = "graph", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.data.KnowledgeGraph.class)
    public org.integratedmodelling.klab.api.data.KnowledgeGraph graph(RuntimeAgent.Scope scope) { return twin(scope).getKnowledgeGraph(); }

    @Verb(name = "scheduler", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.digitaltwin.Scheduler.class)
    public org.integratedmodelling.klab.api.digitaltwin.Scheduler scheduler(RuntimeAgent.Scope scope) { return twin(scope).getScheduler(); }

    @Verb(name = "timeline", executionType = Verb.Type.FUNCTION, returns = Map.class,
        description = "Read scheduler epochStart, epochEnd and resolution without advancing time")
    public Map<String, Object> timeline(RuntimeAgent.Scope scope) {
      var scheduler = Objects.requireNonNull(scheduler(scope), "No scheduler available");
      var snapshot = new LinkedHashMap<String, Object>();
      snapshot.put("epochStart", scheduler.epochStart());
      snapshot.put("epochEnd", scheduler.epochEnd());
      snapshot.put("resolution", scheduler.resolution());
      return Collections.unmodifiableMap(snapshot);
    }

    @Verb(name = "close", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Explicitly close the underlying context according to its persistence policy; affects all proxies")
    public void close(RuntimeAgent.Scope scope) { requireContext().close(); }

    @Verb(name = "storagemanager", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.digitaltwin.StorageManager.class)
    public org.integratedmodelling.klab.api.digitaltwin.StorageManager storageManager(RuntimeAgent.Scope scope) { return twin(scope).getStorageManager(); }

    @Verb(name = "storage", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.data.Storage.class,
        description = "Retrieve existing observation storage; absent or inaccessible storage raises a backend error")
    public org.integratedmodelling.klab.api.data.Storage storage(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "observation", description = "Observation in this twin") Observation observation) {
      return storageManager(scope).getStorage(Objects.requireNonNull(observation, "observation"));
    }

    @Verb(name = "members", executionType = Verb.Type.FUNCTION, returns = List.class,
        description = "Return an iterable snapshot of a cohort's direct HAS_MEMBER observations; accepts limit and offset")
    public List<?> members(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "cohort", description = "Cohort in this twin") org.integratedmodelling.klab.api.knowledge.Cohort cohort,
        Metadata options) {
      for (String key : options.keySet()) if (!Set.of("limit", "offset").contains(key))
        throw new IllegalArgumentException("Unknown members option: " + key);
      var queryOptions = Metadata.create();
      queryOptions.putAll(options);
      queryOptions.put("source", Objects.requireNonNull(cohort, "cohort"));
      queryOptions.put("along", org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_MEMBER);
      queryOptions.put("all", true);
      return (List<?>) ContextActorSupport.query(requireContext(), queryOptions);
    }

    /**
     * Create a session-owned context. Context composition remains a documented proposal.
     *
     * @param agentScope
     * @return
     */
    @Verb(name = "new", executionType = Verb.Type.FUNCTION, producesAgent = "core.context", description = "Create a new context")
    public static Context createContext(AgentScope agentScope, Object... args) {

      var options = ContextActorSupport.metadata(args);
      for (String key : options.keySet()) if (!Set.of("name", "description", "persistence").contains(key))
        throw new IllegalArgumentException("Unknown context creation option: " + key);
      if (args != null) for (Object argument : args)
        if (!(argument instanceof String || argument instanceof Persistence || argument instanceof Metadata))
          throw new IllegalArgumentException("Context.new accepts a name, Persistence and creation metadata; use wrap for an existing scope");
      for (String key : List.of("name", "description"))
        if (options.containsKey(key) && !(options.get(key) instanceof String))
          throw new IllegalArgumentException(key + " must be a string");
      var persistence = options.containsKey("persistence")
          ? (Persistence) org.integratedmodelling.klab.runtime.kactors.JavaArgumentConversions.enumValue(options.get("persistence"), Persistence.class)
          : Utils.Collections.findElement(args, Persistence.ONE_OFF);

      var aScope = agentScope.getAgent().getCreationScope();
      if (aScope instanceof SessionScope sessionScope) {

        var builder =
            DigitalTwin.Configuration.builder()
                .name(options.containsKey("name") ? (String) options.get("name") : Utils.Collections.findElement(args, "Unnamed context"))
                .persistence(persistence)
                .serviceId(aScope.getService(RuntimeService.class).serviceId())
                .serverUrl(aScope.getService(RuntimeService.class).getUrl())
                .owner(sessionScope.getUser().getUsername())
                .description(
                    options.containsKey("description") ? (String) options.get("description") : "Created by agent "
                        + agentScope.getAgent().getName()
                        + " on "
                        + TimeInstant.create())
                .accessRights(ResourcePrivileges.create(sessionScope));

        var context = sessionScope.createContext(builder.build());

        // register for disposal if we're running a test
        if (agentScope instanceof TestCaseBase.TestCaseScope testScope) {
          testScope.registerContext(context);
        }

        return new Context(context);
      }

      throw new KlabIllegalStateException("Context creation is only supported in a session scope");
    }

    @Verb(name = "query", executionType = Verb.Type.FUNCTION,
        description = "Select typed RuntimeAssets by ID, URN or exact observation semantics. "
            + "Use :within for focused children, :source/:target for directed traversal, "
            + "both endpoints for LINK results, :along for the edge type, and +all for a list. "
            + "Supports :limit, :offset and :depth. See docs/DIGITALTWINS.md.")
    public Object query(AgentScope scope, Object... arguments) {
      return ContextActorSupport.query(requireContext(), arguments);
    }

    @Verb(
        name = "submit",
        executionType = Verb.Type.SUPPLIER,
        returns = Observation.class,
        description =
            """
            Submit an observation to the digital twin""")
    public CompletableFuture<Observation> submit(AgentScope scope, Object... arguments) {

      var metadata = ContextActorSupport.metadata(arguments);
      var selectedContext = ContextActorSupport.focus(requireContext(), metadata);
      var runtimeService = selectedContext.getService(RuntimeService.class);

      var provenanceAgent = selectedContext.getDigitalTwin().getKnowledgeGraph()
          .requireAgent(scope.getAgent().getName());

      var builder = Observation.builder(selectedContext);
      var definition = Utils.Collections.findElement(arguments, Map.class, metadata);
      var observable = Utils.Collections.findElement(arguments, KimObservable.class);
      var concept = Utils.Collections.findElement(arguments, KimConcept.class);
      var urn = Utils.Collections.findElement(arguments, Urn.class);
      var geometry = Utils.Collections.findElement(arguments, Geometry.class);

      String semanticDef =
          concept == null ? (observable == null ? null : observable.getUrn()) : concept.getUrn();
      Observable semantics = Utils.Collections.findElement(arguments, Observable.class);
      if (semanticDef != null) {
        semantics =
            scope
                .getAgent()
                .getCreationScope()
                .getService(Reasoner.class)
                .resolveObservable(semanticDef);
      }

      // definition MUST remain last
      var target =
          builder
              .observable(semantics)
              .identity(urn)
              .geometry(geometry)
              .definition(definition)
              .build();

      var submissionScope =
          selectedContext.withResolutionConstraints(
              ResolutionConstraint.of(ResolutionConstraint.Type.Provenance, provenanceAgent));

      if (metadata.get("namespace") instanceof String namespace) {
        submissionScope =
            submissionScope.withResolutionConstraints(
                ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace, namespace));
      }

      if (metadata.get("project") instanceof String project) {
        submissionScope =
            submissionScope.withResolutionConstraints(
                ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionProject, project));
      }

      return runtimeService.submit(target, submissionScope);
    }
  }

  /** Snapshot assertion functions; see docs/TESTING.md for policies and capture boundaries. */
  @Actor(name = "inspector", description = "Read-only asset and graph assertion factory")
  public static class Inspector {

    @Verb(
        name = "viable",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check all assets. +data requires some data, +nodata all no-data, !nodata complete data. "
                + "!resolved requires substantial resolution; :mincoverage sets a coverage threshold.")
    public static boolean checkViable(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "assets",
                description = "Assets to inspect and inline policy metadata")
            Object... arguments) {
      var problems = problems(scope, arguments);
      if (!problems.isEmpty() && scope != null) Console.println(scope, String.join("; ", problems));
      return problems.isEmpty();
    }

    @Verb(
        name = "problems",
        executionType = Verb.Type.FUNCTION,
        returns = List.class,
        description = "Return viability failure reasons without recording assertions or printing.")
    public static List<String> problems(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "assets",
                description = "Assets to inspect and inline policy metadata")
            Object... arguments) {
      Object[] normalized =
          arguments == null
              ? null
              : Arrays.stream(arguments)
                  .map(a -> a instanceof Context context ? context.context : a)
                  .toArray();
      return InspectorSupport.problems(normalized);
    }

    @Verb(
        name = "present",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Test whether a captured value is non-null, without interpreting viability.")
    public static boolean present(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Captured value", optional = true)
            Object asset) {
      return asset != null;
    }

    @Verb(
        name = "resolved",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require positive resolution coverage, optionally at least minimum (0..1).")
    public static boolean resolved(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "observation", description = "Observation to inspect")
            Observation observation,
        @Verb.Argument(
                name = "minimum",
                description = "Minimum coverage; default zero means any positive coverage",
                optional = true)
            Number minimum) {
      return InspectorSupport.resolved(observation, minimum == null ? 0 : minimum.doubleValue());
    }

    @Verb(
        name = "hasdata",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require at least one valid value in scalar data or histogram snapshots.")
    public static boolean hasData(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset) {
      return InspectorSupport.hasData(asset, false);
    }

    @Verb(
        name = "nodata",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require nonempty evidence consisting entirely of no-data.")
    public static boolean noData(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset) {
      return InspectorSupport.allNoData(asset);
    }

    @Verb(
        name = "complete",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require valid data and zero missing values in every captured slice.")
    public static boolean complete(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset) {
      return InspectorSupport.hasData(asset, true);
    }

    @Verb(
        name = "inrange",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check inclusive numeric bounds across all captured histogram slices or a scalar value. Missing values are ignored; combine with complete.")
    public static boolean inRange(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset,
        @Verb.Argument(name = "minimum", description = "Inclusive finite lower bound")
            double minimum,
        @Verb.Argument(name = "maximum", description = "Inclusive finite upper bound")
            double maximum) {
      return InspectorSupport.inRange(asset, minimum, maximum);
    }

    @Verb(
        name = "metadata",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check that an observation metadata key exists and equals the expected value.")
    public static boolean metadata(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "observation", description = "Observation") Observation observation,
        @Verb.Argument(name = "key", description = "Metadata key") String key,
        @Verb.Argument(name = "expected", description = "Expected value", optional = true)
            Object expected) {
      return observation != null
          && observation.getMetadata() != null
          && observation.getMetadata().containsKey(key)
          && Objects.deepEquals(observation.getMetadata().get(key), expected);
    }

    @Verb(
        name = "graph",
        executionType = Verb.Type.FUNCTION,
        returns = org.integratedmodelling.klab.api.data.KnowledgeGraph.class,
        description =
            "Get the knowledge graph from a context actor, context scope, digital twin or graph.")
    public static org.integratedmodelling.klab.api.data.KnowledgeGraph graph(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor, scope, twin or graph")
            Object context) {
      if (context instanceof Context actor) context = actor.context;
      if (context instanceof ContextScope contextScope) context = contextScope.getDigitalTwin();
      if (context instanceof DigitalTwin twin) context = twin.getKnowledgeGraph();
      if (context instanceof org.integratedmodelling.klab.api.data.KnowledgeGraph graph)
        return graph;
      throw new IllegalArgumentException("Expected a context, digital twin or knowledge graph");
    }

    @Verb(
        name = "contains",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check committed knowledge-graph membership by asset ID, numeric ID, or canonical URN.")
    public static boolean contains(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope")
            Object context,
        @Verb.Argument(
                name = "asset",
                description = "Runtime asset, Long/Integer ID, String or URN")
            Object asset) {
      ContextScope ctx = contextScope(context);
      var graph = graph(scope, ctx);
      if (asset instanceof RuntimeAsset runtimeAsset) asset = runtimeAsset.getId();
      if (asset instanceof Long || asset instanceof Integer) {
        long id = ((Number) asset).longValue();
        return id > 0 && graph.getAsset(id, ctx, RuntimeAsset.class) != null;
      }
      if (asset instanceof Urn urn) asset = urn.toString();
      if (asset instanceof String urn) return graph.getAsset(urn, ctx, RuntimeAsset.class) != null;
      if (asset == null) return false;
      throw new IllegalArgumentException("Expected an asset, integer ID or canonical URN");
    }

    @Verb(
        name = "linked",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check a directed knowledge-graph relationship, including transaction-local links.")
    public static boolean linked(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope")
            Object context,
        @Verb.Argument(name = "source", description = "Source asset") RuntimeAsset source,
        @Verb.Argument(name = "target", description = "Target asset") RuntimeAsset target,
        @Verb.Argument(name = "relationship", description = "Graph relationship")
            org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship relationship) {
      if (source == null || target == null) return false;
      Objects.requireNonNull(relationship, "relationship");
      var ctx = contextScope(context);
      return graph(scope, ctx)
          .getLinks(
              source,
              org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.Direction
                  .OUTGOING,
              ctx,
              relationship)
          .stream()
          .anyMatch(link -> link.type() == relationship && sameAsset(link.target(), target));
    }

    @Verb(
        name = "acyclic",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Check a captured directed JGraphT graph for cycles. Empty graphs pass.")
    public static boolean acyclic(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "graph", description = "Captured directed graph")
            org.jgrapht.Graph<?, ?> graph) {
      if (graph == null) return false;
      if (!graph.getType().isDirected())
        throw new IllegalArgumentException("Expected a directed graph");
      return !new org.jgrapht.alg.cycle.CycleDetector<>(graph).detectCycles();
    }

    private static ContextScope contextScope(Object context) {
      if (context instanceof Context actor) context = actor.context;
      if (context instanceof ContextScope ctx) return ctx;
      throw new IllegalArgumentException("Expected a context actor or context scope");
    }

    @Verb(
        name = "storage",
        executionType = Verb.Type.FUNCTION,
        returns = org.integratedmodelling.klab.api.data.Storage.class,
        description =
            "Retrieve existing observation storage from the supplied context; backend errors, including absent storage, propagate.")
    public static org.integratedmodelling.klab.api.data.Storage storage(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope")
            Object context,
        @Verb.Argument(name = "observation", description = "Observation owned by this context")
            Observation observation) {
      if (observation == null) return null;
      return contextScope(context).getDigitalTwin().getStorageManager().getStorage(observation);
    }

    @Verb(
        name = "vertices",
        executionType = Verb.Type.FUNCTION,
        returns = Integer.class,
        description = "Count vertices of a captured JGraphT graph.")
    public static int vertices(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "graph", description = "Captured graph")
            org.jgrapht.Graph<?, ?> graph) {
      return Objects.requireNonNull(graph, "graph").vertexSet().size();
    }

    @Verb(
        name = "edges",
        executionType = Verb.Type.FUNCTION,
        returns = Integer.class,
        description = "Count edges of a captured JGraphT graph.")
    public static int edges(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "graph", description = "Captured graph")
            org.jgrapht.Graph<?, ?> graph) {
      return Objects.requireNonNull(graph, "graph").edgeSet().size();
    }

    private static boolean sameAsset(RuntimeAsset left, RuntimeAsset right) {
      return left != null
          && (left == right
              || (left.getId() != -1 && right.getId() != -1 && left.getId() == right.getId())
              || (left.getId() == -1
                  && right.getId() == -1
                  && left.getTransientId() != 0
                  && left.getTransientId() == right.getTransientId()));
    }
  }

  @Actor(name = "log", description = "Logging actor")
  public static class Logger {

    @Verb(name = "info", executionType = Verb.Type.FUNCTION)
    public static void info(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.info(messages);
      } else {
        Logging.INSTANCE.info(messages);
      }
    }

    @Verb(name = "error", executionType = Verb.Type.FUNCTION)
    public static void error(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.error(messages);
      } else {
        Logging.INSTANCE.error(messages);
      }
    }

    @Verb(name = "warning", executionType = Verb.Type.FUNCTION)
    public static void warning(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.warn(messages);
      } else {
        Logging.INSTANCE.warn(messages);
      }
    }

    @Verb(name = "debug", executionType = Verb.Type.FUNCTION)
    public static void debug(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.debug(messages);
      } else {
        Logging.INSTANCE.debug(messages);
      }
    }

    // TODO emitter that catches log entries from the code with pattern

  }

  @Actor(
      name = "strings",
      description =
          "Null-safe string conversion, inspection, searching, splitting, joining and formatting functions.")
  public static class Strings {

    @Verb(name = "lowercase", executionType = Verb.Type.FUNCTION)
    public static String lowercase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to convert") String text) {
      return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    @Verb(name = "uppercase", executionType = Verb.Type.FUNCTION)
    public static String uppercase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to convert") String text) {
      return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    @Verb(name = "capitalize", executionType = Verb.Type.FUNCTION)
    public static String capitalize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text whose first character is capitalized")
            String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.capitalize(text);
    }

    @Verb(name = "labelize", executionType = Verb.Type.FUNCTION)
    public static String labelize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "identifier", description = "Identifier to turn into a label")
            String identifier) {
      return identifier == null
          ? null
          : org.integratedmodelling.klab.api.utils.Utils.Strings.labelizeIdentifier(identifier);
    }

    @Verb(name = "trim", executionType = Verb.Type.FUNCTION)
    public static String trim(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to strip at both ends") String text) {
      return text == null ? null : text.strip();
    }

    @Verb(name = "normalize", executionType = Verb.Type.FUNCTION)
    public static String normalize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "text",
                description = "Text to trim and normalize to single internal spaces")
            String text) {
      return text == null
          ? null
          : org.integratedmodelling.klab.api.utils.Utils.Strings.replaceWhitespace(
              text.strip(), " ");
    }

    @Verb(name = "length", executionType = Verb.Type.FUNCTION)
    public static int length(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text whose length is returned") String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.length(text);
    }

    @Verb(name = "isempty", executionType = Verb.Type.FUNCTION)
    public static boolean isEmpty(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to test", optional = true) String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.isEmpty(text);
    }

    @Verb(name = "contains", executionType = Verb.Type.FUNCTION)
    public static boolean contains(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to find")
            String fragment) {
      return text != null && fragment != null && text.contains(fragment);
    }

    @Verb(name = "startswith", executionType = Verb.Type.FUNCTION)
    public static boolean startsWith(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to inspect") String text,
        @Verb.Argument(name = "prefix", description = "Literal prefix") String prefix) {
      return text != null && prefix != null && text.startsWith(prefix);
    }

    @Verb(name = "endswith", executionType = Verb.Type.FUNCTION)
    public static boolean endsWith(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to inspect") String text,
        @Verb.Argument(name = "suffix", description = "Literal suffix") String suffix) {
      return text != null && suffix != null && text.endsWith(suffix);
    }

    @Verb(name = "equalsignorecase", executionType = Verb.Type.FUNCTION)
    public static boolean equalsIgnoreCase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "First text") String text,
        @Verb.Argument(name = "other", description = "Text to compare") String other) {
      return text == null ? other == null : other != null && text.equalsIgnoreCase(other);
    }

    @Verb(name = "indexof", executionType = Verb.Type.FUNCTION)
    public static int indexOf(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to find")
            String fragment) {
      return text == null || fragment == null ? -1 : text.indexOf(fragment);
    }

    @Verb(name = "count", executionType = Verb.Type.FUNCTION)
    public static int count(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to count")
            String fragment) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.countMatches(text, fragment);
    }

    @Verb(name = "matches", executionType = Verb.Type.FUNCTION)
    public static boolean matches(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to test") String text,
        @Verb.Argument(name = "regex", description = "Java regular expression") String regex) {
      return text != null && regex != null && Pattern.matches(regex, text);
    }

    @Verb(name = "replace", executionType = Verb.Type.FUNCTION)
    public static String replace(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to modify") String text,
        @Verb.Argument(name = "target", description = "Literal text to replace") String target,
        @Verb.Argument(name = "replacement", description = "Replacement text") String replacement) {
      return text == null || target == null
          ? text
          : text.replace(target, replacement == null ? "" : replacement);
    }

    @Verb(name = "substring", executionType = Verb.Type.FUNCTION)
    public static String substring(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Source text") String text,
        @Verb.Argument(name = "start", description = "Inclusive start index") int start,
        @Verb.Argument(name = "end", description = "Exclusive end index") int end) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.substring(text, start, end);
    }

    @Verb(name = "split", executionType = Verb.Type.FUNCTION)
    public static List<String> split(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to split") String text,
        @Verb.Argument(name = "separator", description = "Literal separator") String separator) {
      if (text == null) {
        return List.of();
      }
      if (separator == null || separator.isEmpty()) {
        return text.codePoints().mapToObj(Character::toString).toList();
      }
      return List.of(text.split(Pattern.quote(separator), -1));
    }

    @Verb(name = "tokenize", executionType = Verb.Type.FUNCTION)
    public static List<String> tokenize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "text",
                description = "Text to split on whitespace while preserving quoted phrases")
            String text) {
      return text == null
          ? List.of()
          : List.copyOf(org.integratedmodelling.klab.api.utils.Utils.Strings.tokenize(text));
    }

    @Verb(name = "join", executionType = Verb.Type.FUNCTION)
    public static String join(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "values", description = "Values to join") Iterable<?> values,
        @Verb.Argument(name = "separator", description = "Separator placed between values")
            String separator) {
      if (values == null) {
        return "";
      }
      var builder = new StringBuilder();
      for (var value : values) {
        if (!builder.isEmpty()) {
          builder.append(separator == null ? "" : separator);
        }
        builder.append(String.valueOf(value));
      }
      return builder.toString();
    }

    @Verb(name = "concat", executionType = Verb.Type.FUNCTION)
    public static String concat(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "values", description = "Values to concatenate") Object... values) {
      if (values == null) {
        return "";
      }
      var builder = new StringBuilder();
      for (var value : values) {
        builder.append(String.valueOf(value));
      }
      return builder.toString();
    }

    @Verb(name = "repeat", executionType = Verb.Type.FUNCTION)
    public static String repeat(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to repeat") String text,
        @Verb.Argument(name = "times", description = "Number of repetitions") int times) {
      return text == null ? null : text.repeat(Math.max(0, times));
    }

    @Verb(name = "abbreviate", executionType = Verb.Type.FUNCTION)
    public static String abbreviate(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to abbreviate") String text,
        @Verb.Argument(name = "width", description = "Maximum result width") int width) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.abbreviate(text, width);
    }
  }

  /** TODO: timer.at(datetime-string) timer.in(duration-string, quantity) */
  @Actor(name = "timer", description = "Time event generator")
  public static class Timer {

    /**
     * Supplier of an object at given time. Objects that are not constants must be dereferenced when
     * supplied.
     *
     * @param time
     * @param object
     * @return
     */
    @Verb(name = "at", executionType = Verb.Type.SUPPLIER)
    public static CompletableFuture<Object> at(
        RuntimeAgent.Scope scope, TimeInstant time, Object object) {
      Objects.requireNonNull(time, "time");

      return completeAfter(time.getMilliseconds() - System.currentTimeMillis(), object);
    }

    /**
     * Supplier of an object after a given interval from method call. Objects that are not constants
     * must be dereferenced when supplied.
     *
     * @param time
     * @param optionalObject
     * @return
     */
    @Verb(name = "in", executionType = Verb.Type.SUPPLIER)
    public static CompletableFuture<Object> in(
        RuntimeAgent.Scope scope, Quantity time, Object... optionalObject) {

      Objects.requireNonNull(time, "quantity");
      TimeUnit unit = extractTimeUnit(time);
      long amount = time.getValue().longValue();
      var millis = unit.toMillis(amount);

      if (optionalObject == null || optionalObject.length == 0) {
        return completeAfter(millis, null);
      }

      return completeAfter(millis, optionalObject[0]);
    }

    private static CompletableFuture<Object> completeAfter(long delayMilliseconds, Object object) {

      if (delayMilliseconds <= 0) {
        return CompletableFuture.completedFuture(object == null ? TimeInstant.create() : object);
      }

      var future = new CompletableFuture<Object>();
      var timer = new java.util.Timer(true);
      var task =
          new TimerTask() {
            @Override
            public void run() {
              future.complete(object == null ? TimeInstant.create() : object);
            }
          };
      timer.schedule(task, delayMilliseconds);
      future.whenComplete((value, throwable) -> timer.cancel());
      return future;
    }

    @Verb(name = "tick", executionType = Verb.Type.EMITTER, fires = TimeInstant.class)
    public static void tick(RuntimeAgent.Scope scope, Quantity quantity) {

      Objects.requireNonNull(quantity, "quantity");

      TimeUnit unit = extractTimeUnit(quantity);
      long amount = quantity.getValue().longValue();

      var timer = new java.util.Timer();
      TimerTask task =
          new TimerTask() {
            @Override
            public void run() {
              scope.doFire(TimeInstant.create());
            }
          };

      timer.scheduleAtFixedRate(task, 0, unit.toMillis(amount));

      // Wait until scope signals completion
      try {
        synchronized (scope) {
          while (!scope.isDone()) {
            scope.wait();
          }
        }
      } catch (InterruptedException e) {
        scope.done(e);
      }
      timer.cancel();
    }

    @Verb(name = "random", executionType = Verb.Type.EMITTER, fires = TimeInstant.class)
    public static void random(RuntimeAgent.Scope scope, Quantity quantity) {

      Objects.requireNonNull(quantity, "quantity");

      TimeUnit unit = extractTimeUnit(quantity);
      long amount = quantity.getValue().longValue();

      var timer = new java.util.Timer();
      scheduleRandomTick(scope, timer, unit.toMillis(amount));

      // Wait until scope signals completion
      try {
        synchronized (scope) {
          while (!scope.isDone()) {
            scope.wait();
          }
        }
      } catch (InterruptedException e) {
        scope.done(e);
      }
      timer.cancel();
    }

    private static void scheduleRandomTick(
        RuntimeAgent.Scope scope, java.util.Timer timer, long averageDelayMilliseconds) {

      if (scope.isDone()) {
        return;
      }

      TimerTask task =
          new TimerTask() {
            @Override
            public void run() {
              if (!scope.isDone()) {
                scope.doFire(TimeInstant.create());
              }
              scheduleRandomTick(scope, timer, averageDelayMilliseconds);
            }
          };

      timer.schedule(task, randomDelayMilliseconds(averageDelayMilliseconds));
    }

    private static long randomDelayMilliseconds(long averageDelayMilliseconds) {
      if (averageDelayMilliseconds <= 1) {
        return Math.max(0, averageDelayMilliseconds);
      }

      var minimumDelayMilliseconds = Math.max(1, averageDelayMilliseconds / 2);
      var maximumDelayMilliseconds =
          Math.max(
              minimumDelayMilliseconds + 1, averageDelayMilliseconds + minimumDelayMilliseconds);
      return ThreadLocalRandom.current()
          .nextLong(minimumDelayMilliseconds, maximumDelayMilliseconds);
    }
  }

  private static TimeUnit extractTimeUnit(Quantity quantity) {
    return switch (quantity.getUnit()) {
      case "ms" -> TimeUnit.MILLISECONDS;
      case "s", "sec" -> TimeUnit.SECONDS;
      case "d" -> TimeUnit.DAYS;
      case "min" -> TimeUnit.MINUTES;
      case "h", "hr" -> TimeUnit.HOURS;
      default ->
          throw new KlabIllegalArgumentException("Invalid time unit for quantity: " + quantity);
    };
  }
}
