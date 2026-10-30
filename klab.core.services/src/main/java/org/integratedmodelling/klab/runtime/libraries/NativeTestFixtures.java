package org.integratedmodelling.klab.runtime.libraries;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;

/** Deterministic functions for native canonical tests. Never grant through metadata or transport. */
@Library(name = "klab.tests.fixtures", description = "Permit-gated native testcase discovery and deliberate execution failures")
public final class NativeTestFixtures {
  private static final String KEY = NativeTestFixtures.class.getName() + ".permit";
  private NativeTestFixtures() {}

  /** Deliberately not serializable, with no public constructor or bean accessors. */
  private static final class Permit implements AutoCloseable {
    final TestCaseBase.TestCaseScope owner;
    final Object agent;
    final DigitalTwin twin;
    final String context;
    final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
    final AtomicLong sequence = new AtomicLong();
    final Map<String, Map<String, Long>> classified = new ConcurrentHashMap<>();
    volatile boolean closed;
    Permit(TestCaseBase.TestCaseScope owner, ContextScope context) {
      this.owner = owner;this.agent = owner.getAgent();this.twin = context.getDigitalTwin();this.context = context.getId();
    }
    void add(String name, long value) { counts.computeIfAbsent(name, key -> new AtomicLong()).addAndGet(value); }
    @Override public synchronized void close() { closed = true; }
  }

  static boolean grant(RuntimeAgent.Scope caller, ContextScope context) {
    var test = caller(caller);
    if (!test.ownsContext(context) || !(context.getService(RuntimeService.class) instanceof RuntimeService.Testing)
        || test.getAgent() == null || "stopped".equals(test.getAgent().status()))
      throw new IllegalArgumentException("Native fixtures require a live testcase-owned native digital twin");
    var previous = context.getData().get(KEY);
    if (previous instanceof Permit permit && !permit.closed)
      throw new IllegalStateException("Native fixtures already granted for this context");
    var permit = new Permit(test, context);
    context.getData().put(KEY, permit);
    test.disposeWith(() -> release(context, permit));
    return true;
  }

  static boolean clear(RuntimeAgent.Scope caller, ContextScope context) {
    var test = caller(caller);
    var permit = require(context);
    if (test.getAgent() != permit.agent || !test.ownsContext(context))
      throw new IllegalArgumentException("Native fixture permit belongs to another testcase");
    release(context, permit);return true;
  }

  static long count(RuntimeAgent.Scope caller, ContextScope context, String name) {
    var test = caller(caller);var permit = require(context);
    if (test.getAgent() != permit.agent || !test.ownsContext(context))
      throw new IllegalArgumentException("Native fixture evidence belongs to another testcase");
    if (!java.util.Set.of("empty", "classifier", "prior", "consumer", "consumerMembers",
        "classification", "ordered", "discovery").contains(name))
      throw new IllegalArgumentException("Unknown native fixture counter");
    return permit.counts.getOrDefault(name, new AtomicLong()).get();
  }

  private static TestCaseBase.TestCaseScope caller(RuntimeAgent.Scope caller) {
    if (!(caller instanceof TestCaseBase.TestCaseScope test) || test.isDone())
      throw new IllegalArgumentException("Native fixtures require an actual active testcase scope");
    return test;
  }

  private static Permit require(Scope scope) {
    if (!(scope instanceof ContextScope context) || context.isInterrupted()
        || !(context.getService(RuntimeService.class) instanceof RuntimeService.Testing)
        || !(context.getData().get(KEY) instanceof Permit permit) || permit.closed
        || permit.twin != context.getDigitalTwin() || !Objects.equals(permit.context, context.getId())
        || permit.owner.isDone() || permit.owner.getAgent() != permit.agent
        || !permit.owner.ownsContext(context) || "stopped".equals(permit.owner.getAgent().status()))
      throw new IllegalArgumentException("No live native testcase fixture permit");
    return permit;
  }

  private static void release(ContextScope context, Permit permit) {
    synchronized (permit) {
      permit.close();
      context.getData().remove(KEY, permit);
    }
  }

  /** Explicit testcase SETUP of an existing durable catalog, never an observation operation. */
  static boolean prepareCatalog(RuntimeAgent.Scope caller, ContextScope context, String definition) {
    var owner = caller(caller);
    var permit = require(context);
    synchronized (permit) {
      require(context);
      if (owner.getAgent() != permit.agent || !owner.ownsContext(context))
        throw new IllegalArgumentException("Native catalog setup belongs to another testcase");
      if (context.getCurrentTransaction() != null)
        throw new IllegalStateException("Native catalog setup cannot run inside an observation transaction");
      var reasoner = context.getService(Reasoner.class);
      var concept = reasoner.resolveConcept(definition);
      if (concept == null || concept.isCollective() || concept.getQualifier() != null
          || concept.isAbstract() || concept.is(SemanticType.ABSTRACT) || concept.is(SemanticType.NOTHING)
          || !(concept.is(SemanticType.SUBJECT) || concept.is(SemanticType.AGENT))
          || concept.getUrn() == null || !concept.getUrn().matches("[a-z][a-z0-9_.]*:[A-Z][A-Za-z0-9_]*")
          || !reasoner.satisfiable(concept) || !concept.equals(reasoner.baseSubstantialType(concept, context)))
        throw new IllegalArgumentException("Catalog setup requires a concrete singular native Subject or Agent concept");
      var runtime = context.getService(RuntimeService.class);
      final java.lang.reflect.Method method;
      try {
        // Core cannot depend on the Runtime implementation. This exact method is already public
        // on native Runtime; do not add a service endpoint or override Java accessibility.
        method = runtime.getClass().getMethod("getCohortFor", Semantics.class, ContextScope.class, boolean.class);
        if (method.getReturnType() != Cohort.class) throw new NoSuchMethodException("Unexpected native catalog return type");
      } catch (NoSuchMethodException unavailable) {
        throw new IllegalStateException("The existing public native catalog routine is unavailable", unavailable);
      }
      var before = ObservationInspector.nativeSnapshot(context);
      var existing = before.cohorts().entrySet().stream()
          .filter(entry -> concept.getUrn().equals(entry.getValue().semantics())).toList();
      if (existing.size() > 1 || existing.stream().anyMatch(entry -> !entry.getValue().members().isEmpty()
          || !entry.getValue().contributors().isEmpty()))
        throw new IllegalStateException("Catalog setup requires an absent or already empty native catalog");
      final Cohort catalog;
      try {
        catalog = (Cohort) method.invoke(runtime, concept, context, true);
      } catch (java.lang.reflect.InvocationTargetException failed) {
        if (failed.getCause() instanceof RuntimeException runtimeFailure) throw runtimeFailure;
        throw new IllegalStateException("Native catalog setup failed", failed.getCause());
      } catch (IllegalAccessException inaccessible) {
        throw new IllegalStateException("The public native catalog routine is not accessible", inaccessible);
      }
      if (catalog == null || catalog.getId() <= 0 || catalog.getObservable() == null
          || !concept.equals(catalog.getObservable().getSemantics()))
        throw new IllegalStateException("Native catalog setup returned no valid persisted catalog");
      var graph = context.getDigitalTwin().getKnowledgeGraph();
      var persisted = graph.query(Cohort.class, context).source(RuntimeAsset.CONTEXT_ASSET)
          .along(GraphModel.Relationship.HAS_CHILD)
          .where(GraphModel.Cohort.OBSERVABLE_FIELD, KnowledgeGraph.Query.Operator.EQUALS, concept.getUrn()).run(context);
      if (persisted.size() != 1 || persisted.getFirst().getId() != catalog.getId()
          || !graph.query(Observation.class, context).source(catalog).along(GraphModel.Relationship.HAS_MEMBER).run(context).isEmpty()
          || !graph.query(Observation.class, context).target(catalog).along(GraphModel.Relationship.CONTRIBUTED_TO).run(context).isEmpty())
        throw new IllegalStateException("Native catalog setup did not produce one empty persisted catalog");
      var after = ObservationInspector.nativeSnapshot(context);
      var prepared = after.cohorts().get(catalog.getId());
      boolean existingPreserved = before.cohorts().entrySet().stream()
          .allMatch(entry -> Objects.equals(entry.getValue(), after.cohorts().get(entry.getKey())));
      int expectedSize = before.cohorts().size() + (before.cohorts().containsKey(catalog.getId()) ? 0 : 1);
      if (!before.context().equals(after.context()) || !before.observations().equals(after.observations())
          || !before.contextualizations().equals(after.contextualizations()) || !existingPreserved
          || after.cohorts().size() != expectedSize || prepared == null
          || !concept.getUrn().equals(prepared.semantics()) || !prepared.members().isEmpty() || !prepared.contributors().isEmpty()
          || context.getCurrentTransaction() != null)
        throw new IllegalStateException("Catalog setup changed native observations, work or unrelated catalogs");
      require(context);
      return true;
    }
  }

  @KlabFunction(name = "empty", type = Artifact.Type.OBJECT, geometry = "*",
      description = "Execute a permitted native instantiator without generating any members")
  public static void empty(Data.Builder builder, Geometry geometry, Observable observable, Scope scope) {
    require(scope).add("empty", 1);
    // Normal contextualizer execution owns Data.Builder, transactions and INSTANTIATION evidence.
    // No observation, graph link, completion receipt or provenance activity is fabricated here.
  }

  @KlabFunction(name = "fixed", type = Artifact.Type.OBJECT, geometry = "*",
      description = "Emit one permitted native testcase member with exactly the requested geometry")
  public static void fixed(Data.Builder builder, Geometry geometry, Observable observable, ContextScope scope) {
    var permit = require(scope);
    if (builder == null || geometry == null || geometry.isUniversal() || geometry.isEmpty()
        || geometry.size() == 0 || observable == null || observable.getSemantics() == null
        || !observable.getSemantics().isCollective())
      throw new IllegalArgumentException("Fixed discovery requires a builder, concrete geometry and collective observable");
    var individual = observable.builder(scope).collective(false).buildObservable();
    if (individual == null || individual.getSemantics() == null || individual.getSemantics().isCollective())
      throw new IllegalStateException("Fixed discovery requires the declared singular observable");
    var key = scope.getId() + "|" + observable.getUrn() + "|" + Geometry.forTransport(geometry).encode();
    var identity = Urn.of("native.fixture:" + java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    if (builder.object("fixed_member", individual, geometry, identity) == null)
      throw new IllegalStateException("Fixed discovery did not acknowledge its object builder");
    // Normal execution owns native creation, transactions and INSTANTIATION completion.
    permit.add("discovery", 1);
  }

  @KlabFunction(name = "classify", type = Artifact.Type.CONCEPT, geometry = "*",
      description = "Return a concrete predicate through normal classification and record its actual member invocation",
      parameters = @KlabFunction.Argument(name = "result", type = Artifact.Type.CONCEPT,
          description = "Concrete predicate returned to the normal classifier executor"))
  public static Concept classify(Observable observable, ContextScope scope, ServiceCall call) {
    var permit = require(scope);
    var member = scope.getContextObservation();
    var identity = member == null ? null : org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.logicalUrn(member.getUrn());
    if (identity == null || identity.isBlank()) throw new IllegalStateException("Classifier fixture requires an identified actual member");
    var reasoner = scope.getService(Reasoner.class);
    var result = reasoner.resolveConcept(CoreActorLibrary.Inspector.semanticConceptDefinition(call.getParameters().get("result")));
    if (result == null || !result.is(SemanticType.PREDICATE) || result.is(SemanticType.ABSTRACT)
        || result.isAbstract() || result.is(SemanticType.NOTHING) || !reasoner.satisfiable(result))
      throw new IllegalArgumentException("Classifier fixture requires a valid concrete predicate");
    var family = reasoner.lexicalRoot(result);
    if (family == null || family.getUrn() == null) throw new IllegalArgumentException("Predicate has no lexical family");
    permit.classified.computeIfAbsent(family.getUrn(), ignored -> new ConcurrentHashMap<>())
        .put(identity, permit.sequence.incrementAndGet());
    permit.add("classification", 1);
    // The real MemberClassifierExecutor validates this result and stages the attribution.
    return result;
  }

  @KlabFunction(name = "discoverafter", type = Artifact.Type.OBJECT, geometry = "*",
      description = "Emit one native object only after independently invoked classification is visible on its actual members",
      parameters = {
        @KlabFunction.Argument(name = "members", type = Artifact.Type.CONCEPT, description = "Native substantial type whose completed discovery is required"),
        @KlabFunction.Argument(name = "family", type = Artifact.Type.CONCEPT, description = "Required earlier classification family")})
  public static void discoverAfter(Data.Builder builder, Geometry geometry, Observable observable, ContextScope scope, ServiceCall call) {
    var permit = require(scope);
    if (builder == null || geometry == null || geometry.isUniversal() || geometry.isEmpty()
        || geometry.size() == 0 || observable == null || !observable.getSemantics().isCollective())
      throw new IllegalArgumentException("Ordered discovery requires a real builder, bounded support and a collective target");
    var reasoner = scope.getService(Reasoner.class);
    var family = reasoner.resolveConcept(CoreActorLibrary.Inspector.semanticConceptDefinition(call.getParameters().get("family")));
    var nativeObservable = reasoner.resolveObservable(CoreActorLibrary.Inspector.semanticConceptDefinition(call.getParameters().get("members")));
    if (family == null || !family.is(SemanticType.PREDICATE) || nativeObservable == null)
      throw new IllegalArgumentException("Invalid ordered discovery family or native members");
    nativeObservable = nativeObservable.builder(scope).collective(true).buildObservable();
    var runtime = scope.getService(RuntimeService.class);
    var request = new Observation.NaiveBuilder(nativeObservable, scope).geometry(geometry).query().build();
    var completed = runtime.submit(request, scope).join();
    if (completed == null || completed.isEmpty() || completed.getObservable() == null
        || !completed.getObservable().getSemantics().equals(nativeObservable.getSemantics())
        || !Double.isFinite(completed.getResolvedCoverage()) || Math.abs(completed.getResolvedCoverage() - 1) > 1e-9)
      throw new IllegalStateException("Earlier native discovery has not completed");
    var members = runtime.getMembers(completed, scope);
    var invocations = permit.classified.getOrDefault(family.getUrn(), Map.of());
    long ordinal = permit.sequence.incrementAndGet();
    var identities = new java.util.HashSet<String>();
    if (members == null || members.isEmpty()) throw new IllegalStateException("Ordered discovery requires actual earlier members");
    for (var member : members) {
      var identity = member == null ? null : org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.logicalUrn(member.getUrn());
      if (identity == null) throw new IllegalStateException("Earlier native member has no identity");
      var invoked = invocations.get(identity);
      if (!identities.add(identity) || invoked == null || invoked >= ordinal || member.getObservable() == null)
        throw new IllegalStateException("No earlier classifier invocation for this actual native member");
      var predicates = new java.util.ArrayList<>(reasoner.directTraits(member.getObservable()));
      predicates.addAll(reasoner.directRoles(member.getObservable()));
      if (predicates.stream().noneMatch(p -> !p.is(SemanticType.ABSTRACT) && !p.isAbstract()
          && !p.is(SemanticType.NOTHING) && reasoner.satisfiable(p) && (p.equals(family) || reasoner.is(p, family))))
        throw new IllegalStateException("Earlier classifier result has not been staged on its actual member");
    }
    var individual = observable.builder(scope).collective(false).buildObservable();
    var key = scope.getId() + "|" + observable.getUrn() + "|" + Geometry.forTransport(geometry).encode();
    var identity = Urn.of("native.fixture:" + java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    if (builder.object("ordered_member", individual, geometry, identity) == null)
      throw new IllegalStateException("Native discovery did not acknowledge its object builder");
    permit.add("ordered", 1);permit.add("discovery", 1);
  }

  @KlabFunction(name = "failclassifier", type = Artifact.Type.CONCEPT, geometry = "*",
      description = "Fail classification only after observing a concrete predicate from an earlier family",
      parameters = @KlabFunction.Argument(name = "after", type = Artifact.Type.CONCEPT,
          description = "Earlier family that must already be present on the current member"))
  public static Concept failClassifier(Observable observable, Scope scope, ServiceCall call) {
    var permit = require(scope);
    if (!(scope instanceof ContextScope context) || context.getContextObservation() == null)
      throw new IllegalStateException("Failing classifier requires its actual member scope");
    var reasoner = scope.getService(Reasoner.class);
    var family = reasoner.resolveConcept(CoreActorLibrary.Inspector.semanticConceptDefinition(call.getParameters().get("after")));
    if (family == null || !family.is(SemanticType.PREDICATE)) throw new IllegalArgumentException("Invalid earlier fixture family");
    var member = context.getContextObservation();
    var predicates = new java.util.ArrayList<>(reasoner.directTraits(member.getObservable()));
    predicates.addAll(reasoner.directRoles(member.getObservable()));
    if (predicates.stream().noneMatch(p -> !p.is(SemanticType.ABSTRACT) && (p.equals(family) || reasoner.is(p, family))))
      throw new IllegalStateException("Earlier classification did not reach this member");
    permit.add("prior", 1);permit.add("classifier", 1);
    throw new IllegalStateException("DELIBERATE_NATIVE_TEST_CLASSIFIER_FAILURE");
  }

  @KlabFunction(name = "failconsumer", type = Artifact.Type.NUMBER, geometry = "*",
      description = "Validate the actual selected member input, then deliberately fail its ordinary consumer")
  public static void failConsumer(Data.Builder builder, Observable observable, Geometry geometry, ContextScope scope,
      @KlabFunction.Input(name = "selected", type = Artifact.Type.OBJECT, description = "Actual selected model input") Observation selected) {
    var permit = require(scope);
    if (selected == null || selected.isEmpty() || selected.getId() != Observation.QUERY_ID)
      throw new IllegalStateException("Failing consumer did not receive a virtual selection");
    var members = scope.getService(RuntimeService.class).getMembers(selected, scope);
    permit.add("consumerMembers", members.size());permit.add("consumer", 1);
    throw new IllegalStateException("DELIBERATE_NATIVE_TEST_CONSUMER_FAILURE");
  }
}
