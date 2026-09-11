package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.*;
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
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.ObservationReasoner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual corpus, lowering and compilation, with controlled model/coverage service boundaries. */
class ClassificationPipelineTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void cachedCohort() throws Exception { exercise(1, true); }
  @Test void newCohort() throws Exception { exercise(0, true); }
  @Test void partialCohortRequiresMissingSupport() throws Exception { exercise(.5, true); }
  @Test void incompleteCohortCannotClaimClassification() throws Exception { exercise(.5, false); }
  @Test void classificationDependencyNeverRegistersDirective() throws Exception { exercise(1, true, true); }
  @Test void characterizationBindsExistingContext() throws Exception { exercise(1, true, false, true); }
  @Test void optionalDependencySurvivesTransport() throws Exception { exercise(1, true, true, false, true); }
  @Test void optionalRootDoesNotConferDependencyOptionality() throws Exception { exercise(1, true, false, false, true); }

  private void exercise(double fraction, boolean instantiatorAvailable) throws Exception {
    exercise(fraction, instantiatorAvailable, false);
  }

  private void exercise(double fraction, boolean instantiatorAvailable, boolean nested) throws Exception {
    exercise(fraction, instantiatorAvailable, nested, false);
  }

  private void exercise(double fraction, boolean instantiatorAvailable, boolean nested, boolean characterization) throws Exception {
    exercise(fraction, instantiatorAvailable, nested, characterization, false);
  }

  private void exercise(double fraction, boolean instantiatorAvailable, boolean nested, boolean characterization, boolean optional) throws Exception {
    var geometry = Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}");
    var members = observable("each test:Region", SemanticType.SUBJECT, Contextualization.INSTANTIATION, true);
    var activity = characterization ? Contextualization.CHARACTERIZATION : Contextualization.CLASSIFICATION;
    var directive = observable(characterization ? "test:Forest of test:Region" : "test:Environment of each test:Region", SemanticType.ATTRIBUTE, activity, false);
    ((ObservableImpl) directive).setOptional(optional);
    var context = observation(observable("test:Region", SemanticType.SUBJECT, Contextualization.ACKNOWLEDGEMENT, false), 10, geometry);
    var request = observation(nested ? context.getObservable() : directive, nested ? -3 : 0, geometry);
    var scope = mock(ContextScope.class);
    when(scope.getId()).thenReturn("classification-test");
    when(scope.getContextObservation()).thenReturn(context);
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
    when(scope.within(any(Observation.class))).thenReturn(scope);
    when(scope.within(isNull())).thenReturn(scope);
    var nextId = new java.util.concurrent.atomic.AtomicLong(-20);
    when(scope.observation(any(Observable.class))).thenAnswer(inv -> {
      Observable target = inv.getArgument(0);
      assertNotEquals(directive.getUrn(), target.getUrn(), "Directive must never be registered");
      return new Observation.NaiveBuilder(target, scope) {
        @Override public Observation register() { return observation(target, nextId.getAndDecrement(), geometry); }
      };
    });
    var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.directInherent(any())).thenAnswer(inv ->
        ((Semantics) inv.getArgument(0)).getUrn().equals(directive.getUrn())
            ? (characterization ? context.getObservable().asConcept() : members.asConcept()) : null);
    when(reasoner.resolveObservable(members.getUrn())).thenReturn(members);
    var matcher = new ObservationReasoner(reasoner, ignored -> { throw new AssertionError(); });
    ObservationPipelineTest.document().getStatements().stream().filter(s -> s.getRank() == 0).forEach(matcher::registerStrategy);
    matcher.initializeStrategies();
    var matching = matcher.computeMatchingStrategies(request, scope, true);
    assertEquals(1, matching.size());
    assertEquals(0, matching.getFirst().getRank());
    var mapper = JacksonConfiguration.newObjectMapper();
    var strategiesType = mapper.getTypeFactory().constructCollectionType(List.class, ObservationStrategy.class);
    when(reasoner.computeObservationStrategies(any(), any())).thenAnswer(inv ->
        mapper.readValue(mapper.writerFor(strategiesType).writeValueAsString(
            matcher.computeMatchingStrategies(inv.getArgument(0), inv.getArgument(1), true)), strategiesType));
    var root = ResolutionGraph.create(scope);
    var key = ResolverService.class.getDeclaredField("RESOLUTION_GRAPH_KEY"); key.setAccessible(true);
    when(scope.getData()).thenReturn(Parameters.create((String) key.get(null), root));
    var runtime = mock(RuntimeService.class);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var requirements = new ResourceSet(); requirements.setEmpty(false);
    when(runtime.resolveContextualizables(any(), any())).thenReturn(requirements);
    var model = mock(Model.class);
    when(model.getCoverage()).thenReturn(geometry);
    when(model.getDependencies()).thenReturn(List.of());
    when(model.getAnnotations()).thenReturn(List.of());
    var computation = new ContextualizableImpl(); computation.setServiceCall(new ServiceCallImpl("test.classify"));
    when(model.getComputation()).thenReturn(List.of(computation));
    var instantiator = mock(Model.class);
    when(instantiator.getCoverage()).thenReturn(geometry);
    when(instantiator.getDependencies()).thenReturn(List.of());
    when(instantiator.getComputation()).thenReturn(List.of(computation));
    when(instantiator.getAnnotations()).thenReturn(List.of());
    var parentModel = mock(Model.class);
    when(parentModel.getCoverage()).thenReturn(geometry);
    when(parentModel.getDependencies()).thenReturn(List.of(directive));
    when(parentModel.getComputation()).thenReturn(List.of(computation));
    when(parentModel.getAnnotations()).thenReturn(List.of());
    var compiler = new ResolutionCompiler(mock(ResolverService.class)) {
      @Override public List<Model> queryModels(Observable o, Concept c, ContextScope s, Scale scale) {
        assertSame(scope, s);
        assertSame(context.getObservable().asConcept(), c);
        if (nested && o.getUrn().equals(request.getObservable().getUrn())) return List.of(parentModel);
        if (o.getUrn().equals(directive.getUrn())) return List.of(model);
        return instantiatorAvailable ? List.of(instantiator) : List.of();
      }
      @Override QueryMatch query(Observable o, Scale scale, ContextScope s) {
        assertNotEquals(directive.getUrn(), o.getUrn(), "Directive cannot query observation coverage");
        if (nested && o.getUrn().equals(request.getObservable().getUrn()))
          return new QueryMatch(null, null, scale, null, Coverage.create(scale, 0));
        if (fraction == 0) return new QueryMatch(null, null, scale, null, Coverage.create(scale, 0));
        var covered = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(
            fraction == 1 ? geometry : Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}"));
        var existing = observation(members, 42, covered);
        return new QueryMatch(existing, existing, scale, covered, Coverage.create(scale, fraction));
      }
    };
    var graph = compiler.resolve(request, scope);
    if (!instantiatorAvailable) { assertTrue(graph.isEmpty()); return; }
    assertFalse(graph.isEmpty());
    assertTrue(graph.graph().vertexSet().stream().noneMatch(v -> v instanceof Observation o && o.getObservable().getUrn().equals(directive.getUrn())));
    var dataflow = new DataflowCompiler(request, graph, scope).compile();
    Dataflow restored = mapper.readValue(mapper.writerFor(Dataflow.class).writeValueAsString(dataflow), Dataflow.class);
    var update = restored.getComputation().getFirst();
    if (nested) update = update.getChildren().getFirst();
    if (nested) {
      assertEquals(directive.getUrn(), update.getModelDependency().getUrn());
      assertEquals(optional, update.getModelDependency().isOptional());
    } else assertNull(update.getModelDependency());
    assertEquals(Actuator.Type.UPDATE, update.getActuatorType());
    assertEquals(Actuator.Effect.SEMANTIC_UPDATE, update.getEffect());
    assertEquals(activity, update.getContextualization());
    assertEquals(directive.getUrn(), update.getOperationObservable().getUrn());
    assertNull(update.getObservation()); assertEquals(0, update.getId());
    assertEquals(1, update.getTargetBindings().size());
    var binding = update.getTargetBindings().getFirst();
    assertNotNull(update.getRequestedSupport());
    if (characterization) {
      assertEquals(Actuator.TargetBinding.Kind.OBSERVATION, binding.getKind());
      assertEquals(context.getId(), binding.getTarget().getId());
      assertTrue(binding.getSources().isEmpty());
      verify(scope, never()).observation(any(Observable.class));
      return;
    }
    assertEquals(Actuator.TargetBinding.Kind.COHORT_MEMBERS, binding.getKind());
    assertFalse(binding.getSources().isEmpty());
    assertEquals(new HashSet<>(binding.getSources()), new HashSet<>(update.getChildren().stream().map(Actuator::getName).toList()));
    var roundtrip = mapper.readValue(mapper.writerFor(Actuator.TargetBinding.class).writeValueAsString(binding), Actuator.TargetBinding.class);
    assertEquals(binding.getSources(), roundtrip.getSources());
    if (fraction == 1) verify(scope, never()).observation(any(Observable.class));
  }

  private static Observable observable(String urn, SemanticType kind, Contextualization activity, boolean collective) {
    var concept = new ConceptImpl(); concept.setUrn(urn); concept.setName("target");
    concept.setReferenceName("target"); concept.setType(EnumSet.of(kind)); concept.setCollective(collective);
    var ret = new ObservableImpl(); ret.setSemantics(concept); ret.setUrn(urn); ret.setName("target"); ret.setDescriptionType(activity); return ret;
  }
  private static Observation observation(Observable o, long id, Geometry g) {
    var ret = new ObservationImpl(); ret.setObservable(o); ret.setName(o.getName()); ret.setId(id); ret.setGeometry(g); return ret;
  }
}
