package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;
import org.integratedmodelling.klab.services.reasoner.ObservationReasoner;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.languages.ObservationStandaloneSetup;
import org.integratedmodelling.languages.ObservationSyntaxAdapter;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real parsing, adaptation, selection, transport and graph compilation; external services are doubles. */
class ObservationPipelineTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  public static KimObservationStrategyDocument document() throws Exception {
    String source;
    try (var input = ObservationPipelineTest.class.getResourceAsStream("/observation/observations-proposed.obs")) {
      source = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
    }
    var parser = new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source));
    assertFalse(parsed.hasSyntaxErrors());
    var root = (org.integratedmodelling.languages.observation.ObservationDocument) parsed.getRootASTElement();
    new ResourceImpl(URI.createURI("memory:/observations.obs")).getContents().add(root);
    var scope = new LanguageValidationScope() {
      public ConceptDescriptor getConceptDescriptor(String urn) {
        var parts = urn.split(":", 2);
        return new ConceptDescriptor(parts[0], parts[1], SemanticSyntax.Type.SUBJECT, urn, "test", false, false);
      }
      public void notifyCoreConcept(String urn, SemanticSyntax.Type type) {}
      public LanguageValidationScope contextualize(EObject context) { return this; }
    };
    var syntax = (ObservationSyntax.StrategyDocument) new ObservationSyntaxAdapter() {
      @Override protected void logWarning(ParsedObject target, EObject object,
          org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
      @Override protected void logError(ParsedObject target, EObject object,
          org.eclipse.emf.ecore.EStructuralFeature feature, String message) { fail(message); }
    }.adapt(root, scope);
    var bean = LanguageAdapter.INSTANCE.adaptStrategies(syntax, "test", List.of(), 1L);
    var mapper = JacksonConfiguration.newObjectMapper();
    return mapper.readValue(mapper.writerFor(KimObservationStrategyDocument.class).writeValueAsString(bean),
        KimObservationStrategyDocument.class);
  }

  private static Observable observable(SemanticType kind, boolean collective) {
    var concept = new ConceptImpl();
    concept.setUrn((collective ? "each " : "") + "test:" + kind.name());
    concept.setType(EnumSet.of(kind));
    concept.setName(kind.name());
    concept.setReferenceName(kind.name().toLowerCase());
    concept.setCollective(collective);
    var observable = new ObservableImpl();
    observable.setSemantics(concept);
    observable.setUrn(concept.getUrn());
    observable.setName(kind.name().toLowerCase());
    observable.setDescriptionType(collective ? Contextualization.INSTANTIATION : Contextualization.ACKNOWLEDGEMENT);
    return observable;
  }

  private static ObservationImpl observation(Observable observable, long id, Geometry geometry) {
    var result = new ObservationImpl();
    result.setId(id); result.setObservable(observable); result.setName(observable.getName()); result.setGeometry(geometry);
    return result;
  }

  @Test void tierZeroCompilesAcrossBothServiceBoundariesToDataflow() throws Exception {
    for (var kind : List.of(SemanticType.SUBJECT, SemanticType.QUALITY, SemanticType.PROCESS,
        SemanticType.AGENT, SemanticType.EVENT, SemanticType.RELATIONSHIP)) {
      boolean collective = kind == SemanticType.AGENT || kind == SemanticType.EVENT || kind == SemanticType.RELATIONSHIP;
      exercise(kind, collective, true);
    }
  }

  @Test void unnamedTerminalProducersCrossBothServiceBoundaries() throws Exception {
    exercise(SemanticType.QUALITY, false, true, true, true);
    exercise(SemanticType.RELATIONSHIP, true, true, true, true);
    exercise(SemanticType.RELATIONSHIP, true, false, true, true);
  }

  @Test void endpointCoverageCannotResolveARelationshipWithoutAModel() throws Exception {
    exercise(SemanticType.RELATIONSHIP, true, false);
  }

  @Test void missingEndpointsRecursivelySelectTheirOwnTierZeroStrategies() throws Exception {
    exercise(SemanticType.RELATIONSHIP, true, true, false);
  }

  @Test void substantialModelDependenciesInheritTheirOwnerAndLexicalConstraints() throws Exception {
    for (boolean collective : List.of(false, true)) {
      for (boolean nested : List.of(false, true)) {
        new DependencyContextFixture(collective, nested).run();
      }
    }
  }

  /** Scope doubles derive immutable context/constraint snapshots, just as service scopes do. */
  private static class DependencyContextFixture {
    final Geometry geometry = Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}");
    final Reasoner reasoner = mock(Reasoner.class);
    final RuntimeService runtime = mock(RuntimeService.class);
    final Parameters<String> data = Parameters.create();
    final java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong(-10);
    final Map<String, Model> models = new HashMap<>();
    final List<String> selected = new ArrayList<>();
    final Observable owner;
    final Observable freshwater = named(SemanticType.SUBJECT, true, "Freshwater");
    final Observable elevation = named(SemanticType.QUALITY, false, "Elevation");
    final Observable slope = named(SemanticType.QUALITY, false, "Slope");
    final Observation requested;
    final Observation outer;
    final ContextScope initial;

    DependencyContextFixture(boolean collective, boolean nested) throws Exception {
      owner = named(SemanticType.SUBJECT, collective, "Region");
      requested = observation(owner, -2, geometry);
      outer = nested ? observation(named(SemanticType.SUBJECT, false, "Outer"), 25, geometry) : null;
      initial = scope(outer, Map.of(ResolutionConstraint.Type.Scenarios,
          ResolutionConstraint.of(ResolutionConstraint.Type.Scenarios, "baseline")));
      var key = ResolverService.class.getDeclaredField("RESOLUTION_GRAPH_KEY");
      key.setAccessible(true);
      data.put((String) key.get(null), ResolutionGraph.create(initial));
      var strategyReasoner = new ObservationReasoner(reasoner, ignored -> { throw new AssertionError(); });
      document().getStatements().stream().filter(s -> s.getRank() == 0).forEach(strategyReasoner::registerStrategy);
      strategyReasoner.initializeStrategies();
      var mapper = JacksonConfiguration.newObjectMapper();
      var listType = mapper.getTypeFactory().constructCollectionType(List.class, ObservationStrategy.class);
      when(reasoner.computeObservationStrategies(any(), any())).thenAnswer(invocation -> {
        Observation target = invocation.getArgument(0);
        ContextScope current = invocation.getArgument(1);
        if (target.getObservable().is(SemanticType.QUALITY)) {
          assertSame(requested, current.getContextObservation());
          assertConstraint(current, ResolutionConstraint.Type.ResolutionNamespace, "private.example");
          assertConstraint(current, ResolutionConstraint.Type.ResolutionProject, "example.project");
        }
        var strategies = strategyReasoner.computeMatchingStrategies(target, current, true);
        assertEquals(1, strategies.size(), target.getObservable().getUrn());
        selected.add(strategies.getFirst().getUrn());
        return mapper.readValue(mapper.writerFor(listType).writeValueAsString(strategies), listType);
      });
      var requirements = new ResourceSet(); requirements.setEmpty(false);
      when(runtime.resolveContextualizables(any(), any())).thenAnswer(i -> {
        ContextScope current = i.getArgument(1);
        assertNotNull(current.getContextObservation());
        assertConstraint(current, ResolutionConstraint.Type.Scenarios, "baseline");
        assertTrue(current.getResolutionConstraints().stream().anyMatch(c -> c.getType() == ResolutionConstraint.Type.Geometry));
        return requirements;
      });
      for (var observable : List.of(owner, freshwater, elevation, slope)) {
        var model = mock(Model.class);
        when(model.getNamespace()).thenReturn(observable == freshwater ? "freshwater.example" : "private.example");
        when(model.getProjectName()).thenReturn(observable == freshwater ? "freshwater.project" : "example.project");
        when(model.getCoverage()).thenReturn(geometry);
        when(model.getAnnotations()).thenReturn(List.of());
        when(model.getDependencies()).thenReturn(observable == owner ? List.of(freshwater, elevation, slope) : List.of());
        var computation = new ContextualizableImpl();
        computation.setServiceCall(new ServiceCallImpl("test.compute"));
        when(model.getComputation()).thenReturn(List.of(computation));
        models.put(observable.getUrn(), model);
      }
    }

    private static Observable named(SemanticType kind, boolean collective, String name) {
      var result = (ObservableImpl) observable(kind, collective);
      result.setUrn((collective ? "each " : "") + "test:" + name);
      ((ConceptImpl) result.getSemantics()).setUrn(result.getUrn());
      result.setName(name.toLowerCase());
      return result;
    }

    private ContextScope scope(Observation context, Map<ResolutionConstraint.Type, ResolutionConstraint> constraints) {
      var result = mock(ContextScope.class);
      when(result.getId()).thenReturn("dependency-context");
      when(result.getData()).thenReturn(data);
      when(result.getContextObservation()).thenReturn(context);
      when(result.getResolutionConstraints()).thenReturn(new ArrayList<>(constraints.values()));
      when(result.getService(Reasoner.class)).thenReturn(reasoner);
      when(result.getService(RuntimeService.class)).thenReturn(runtime);
      when(result.within(nullable(Observation.class))).thenAnswer(i -> scope(i.getArgument(0), constraints));
      when(result.withResolutionConstraints(any(ResolutionConstraint[].class))).thenAnswer(i -> {
        var merged = new EnumMap<ResolutionConstraint.Type, ResolutionConstraint>(ResolutionConstraint.Type.class);
        merged.putAll(constraints);
        for (var constraint : (ResolutionConstraint[]) i.getRawArguments()[0]) {
          if (constraint != null && !constraint.empty()) merged.put(constraint.getType(), constraint);
        }
        return scope(context, merged);
      });
      when(result.observation(any(Observable.class))).thenAnswer(i -> {
        Observable target = i.getArgument(0);
        assertSame(target.getSemantics().isCollective() ? null : requested, context);
        assertConstraint(result, ResolutionConstraint.Type.ResolutionNamespace, "private.example");
        return new Observation.NaiveBuilder(target, result) {
          @Override public Observation register() { return observation(target, ids.getAndDecrement(), geometry); }
        };
      });
      return result;
    }

    private static void assertConstraint(ContextScope scope, ResolutionConstraint.Type type, String expected) {
      var constraint = scope.getResolutionConstraints().stream().filter(c -> c.getType() == type).findFirst().orElseThrow();
      assertEquals(List.of(expected), constraint.payload(String.class));
    }

    void run() {
      var compiler = new ResolutionCompiler(mock(ResolverService.class)) {
        @Override public List<Model> queryModels(Observable o, Concept context, ContextScope s, Scale scale) {
          assertConstraint(s, ResolutionConstraint.Type.Scenarios, "baseline");
          assertTrue(s.getResolutionConstraints().stream().anyMatch(c -> c.getType() == ResolutionConstraint.Type.Geometry));
          if (o.getUrn().equals(owner.getUrn())) assertSame(outer == null ? null : outer.getObservable().getSemantics(), context);
          else {
            assertConstraint(s, ResolutionConstraint.Type.ResolutionNamespace, "private.example");
            assertConstraint(s, ResolutionConstraint.Type.ResolutionProject, "example.project");
            if (o.is(SemanticType.QUALITY)) assertSame(owner.getSemantics(), context);
          }
          return List.of(models.get(o.getUrn()));
        }
        @Override QueryMatch query(Observable o, Scale scale, ContextScope s) {
          return new QueryMatch(null, null, scale, null, Coverage.create(scale, 0));
        }
      };
      var graph = compiler.resolve(requested, initial);
      assertFalse(graph.isEmpty());
      assertTrue(graph.getCoverage().isComplete());
      assertEquals(2, Collections.frequency(selected, "dependent.direct"));
      var flow = new DataflowCompiler(requested, graph, initial).compile();
      assertEquals(3, flow.getComputation().getFirst().getChildren().size());
      assertSame(outer, initial.getContextObservation());
      assertEquals(1, initial.getResolutionConstraints().size(), "Derived model scopes must not mutate their parent");
    }
  }

  private void exercise(SemanticType kind, boolean collective, boolean modelAvailable) throws Exception {
    exercise(kind, collective, modelAvailable, true);
  }

  private void exercise(SemanticType kind, boolean collective, boolean modelAvailable, boolean endpointsExist) throws Exception {
    exercise(kind, collective, modelAvailable, endpointsExist, false);
  }

  private void exercise(SemanticType kind, boolean collective, boolean modelAvailable,
      boolean endpointsExist, boolean unnamed) throws Exception {
    var geometry = Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}");
    var observable = observable(kind, collective);
    var requested = observation(observable, -2L, geometry);
    var scope = mock(ContextScope.class);
    when(scope.getId()).thenReturn("test-context");
    when(scope.within(isNull())).thenReturn(scope);
    when(scope.within(any(Observation.class))).thenReturn(scope);
    var nextId = new java.util.concurrent.atomic.AtomicLong(-10);
    when(scope.observation(any(Observable.class))).thenAnswer(invocation -> {
      Observable target = invocation.getArgument(0);
      return new Observation.NaiveBuilder(target, scope) {
        @Override public Observation register() { return observation(target, nextId.getAndDecrement(), geometry); }
      };
    });
    when(scope.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(scope);
    if (kind == SemanticType.QUALITY || kind == SemanticType.PROCESS)
      when(scope.getContextObservation()).thenReturn(observation(observable(SemanticType.SUBJECT, false), 20L, geometry));
    var reasoner = mock(Reasoner.class);
    var endpoint = observable(SemanticType.SUBJECT, false).getSemantics();
    when(reasoner.relationshipSource(any())).thenReturn(endpoint);
    when(reasoner.relationshipTarget(any())).thenReturn(endpoint);
    when(reasoner.resolveObservable("each test:SUBJECT")).thenReturn(observable(SemanticType.SUBJECT, true));
    var strategyReasoner = new ObservationReasoner(reasoner, ignored -> { throw new AssertionError("No closed observables in tier zero"); });
    var strategies = document();
    if (unnamed) {
      for (var strategy : strategies.getStatements()) {
        if (strategy.getRank() != 0) continue;
        var terminal = (org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.GraphProducerImpl)
            strategy.getPlan().getSteps().getLast();
        terminal.setName(null);
      }
      var transport = JacksonConfiguration.newObjectMapper();
      strategies = transport.readValue(transport.writerFor(KimObservationStrategyDocument.class)
          .writeValueAsString(strategies), KimObservationStrategyDocument.class);
    }
    strategies.getStatements().stream().filter(s -> s.getRank() == 0).forEach(strategyReasoner::registerStrategy);
    strategyReasoner.initializeStrategies();
    var matching = strategyReasoner.computeMatchingStrategies(requested, scope, true);
    assertEquals(1, matching.size(), kind.toString());
    var mapper = JacksonConfiguration.newObjectMapper();
    var compiled = mapper.readValue(mapper.writerFor(ObservationStrategy.class).writeValueAsString(matching.getFirst()), ObservationStrategy.class);
    var listType = mapper.getTypeFactory().constructCollectionType(List.class, ObservationStrategy.class);
    List<ObservationStrategy> restoredList = mapper.readValue(mapper.writerFor(listType).writeValueAsString(matching), listType);
    assertEquals(compiled.getUrn(), restoredList.getFirst().getUrn());
    assertEquals(0, compiled.getRank());
    assertEquals("observations", compiled.getNamespace());
    for (var operation : compiled.getOperations()) {
      var restored = mapper.readValue(mapper.writerFor(ObservationStrategy.Operation.class).writeValueAsString(operation), ObservationStrategy.Operation.class);
      assertEquals(operation.getType(), restored.getType());
      assertEquals(operation.getObservable().getUrn(), restored.getObservable().getUrn());
      assertEquals(operation.getId(), restored.getId());
      assertEquals(operation.getInputs(), restored.getInputs());
    }
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.computeObservationStrategies(any(), eq(scope))).thenAnswer(invocation ->
        mapper.readValue(mapper.writerFor(listType).writeValueAsString(
            strategyReasoner.computeMatchingStrategies(invocation.getArgument(0), scope, true)), listType));
    var root = ResolutionGraph.create(scope);
    // Bind by the actual key used by the service, without assuming its private string value.
    var key = ResolverService.class.getDeclaredField("RESOLUTION_GRAPH_KEY");
    key.setAccessible(true);
    when(scope.getData()).thenReturn(Parameters.create((String) key.get(null), root));
    var runtime = mock(RuntimeService.class);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var requirements = new ResourceSet(); requirements.setEmpty(false);
    when(runtime.resolveContextualizables(any(), eq(scope))).thenReturn(requirements);
    var model = mock(Model.class);
    when(model.getCoverage()).thenReturn(geometry);
    when(model.getDependencies()).thenReturn(List.of());
    when(model.getAnnotations()).thenReturn(List.of());
    var computation = new ContextualizableImpl();
    computation.setServiceCall(new ServiceCallImpl("test.compute"));
    when(model.getComputation()).thenReturn(List.of(computation));
    var endpointModel = mock(Model.class);
    when(endpointModel.getCoverage()).thenReturn(geometry);
    when(endpointModel.getDependencies()).thenReturn(List.of());
    when(endpointModel.getAnnotations()).thenReturn(List.of());
    when(endpointModel.getComputation()).thenReturn(List.of(computation));
    var compiler = new ResolutionCompiler(mock(ResolverService.class)) {
      @Override public List<Model> queryModels(Observable o, Concept context, ContextScope s, Scale requestedScale) {
        return modelAvailable ? List.of(o.getUrn().equals(observable.getUrn()) ? model : endpointModel) : List.of();
      }
      @Override QueryMatch query(Observable o, Scale requestedScale, ContextScope s) {
        if (o.getUrn().equals(observable.getUrn()) || !endpointsExist)
          return new QueryMatch(null, null, requestedScale, null, Coverage.create(requestedScale, 0));
        var existing = observation(o, 42L, geometry);
        return new QueryMatch(existing, existing, requestedScale, requestedScale, Coverage.create(requestedScale, 1));
      }
    };
    var graph = compiler.resolve(requested, scope);
    assertEquals(!modelAvailable, graph.isEmpty());
    if (!modelAvailable) return;
    assertTrue(graph.getCoverage().isComplete());
    Dataflow dataflow = new DataflowCompiler(requested, graph, scope).compile();
    var actuator = dataflow.getComputation().getFirst();
    assertEquals(1, actuator.getComputation().size());
    if (kind == SemanticType.RELATIONSHIP) {
      assertEquals(3, compiled.getOperations().size());
      assertEquals(Map.of("source", "sources", "target", "targets"), compiled.getOperations().getLast().getInputs());
      assertEquals(Set.of("source", "target"), new HashSet<>(actuator.getChildren().stream().map(a -> a.getName()).toList()));
      assertEquals(2, actuator.getChildren().size(), "The same endpoint may feed two distinct ports");
      assertTrue(actuator.getComputation().getFirst().getParameters().containsKey("source"));
      assertTrue(actuator.getComputation().getFirst().getParameters().containsKey("target"));
      if (!endpointsExist) {
        assertTrue(actuator.getChildren().stream().allMatch(a -> !a.getComputation().isEmpty()));
        verify(reasoner, atLeast(3)).computeObservationStrategies(any(), eq(scope));
      }
    }
  }
}
