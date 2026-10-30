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
  private boolean admitInstantiator = true;
  private boolean classifierAvailable = true;
  private boolean qualifiedInput;
  private boolean concreteAny;
  private boolean noContext, observerGeometry, contextGeometry, noGeometry;
  @Test void classificationWithExplicitSupportNeedsNoContextObservation() throws Exception {
    noContext=true;exercise(1,true);
  }
  @Test void classificationUsesObserverPerceivedSupportWithoutContext() throws Exception {
    noContext=true;observerGeometry=true;exercise(1,true);
  }
  @Test void classificationUsesContextSupportWhenRequestOmitsGeometry() throws Exception {
    contextGeometry=true;exercise(1,true);
  }
  @Test void classificationWithNoSupportCannotResolveOrAllocate() throws Exception {
    noContext=true;noGeometry=true;exercise(1,true);
  }
  @Test void characterizationStillRequiresItsContextTarget() throws Exception {
    noContext=true;exercise(1,true,false,true,false);
  }
  @Test void qualifiedDependencyNeedsNoPreviouslyObservedContext() throws Exception {
    noContext=true;qualifiedInput=true;exercise(1,true,true);
  }
  @Test void qualifiedAbstractDependencyClassifiesOneCachedNativeCohort() throws Exception {
    qualifiedInput=true; exercise(1,true,true);
  }
  @Test void qualifiedAbstractDependencyProducesAndClassifiesOneNewNativeCohort() throws Exception {
    qualifiedInput=true; exercise(0,true,true);
  }
  @Test void partialQualifiedDependencyExposesACompleteNativeQueryReference() throws Exception {
    qualifiedInput=true; exercise(.5,true,true);
  }
  @Test void qualifiedConcreteAnyDependencyUsesTheSameClassificationPlan() throws Exception {
    qualifiedInput=true; concreteAny=true; exercise(1,true,true);
  }
  @Test void absentClassifierProducesExplicitSatisfactionCheck() throws Exception {
    classifierAvailable = false; exercise(1, true);
  }
  @Test void namedInstantiatorWithoutSourceAdmissionIsRejected() throws Exception {
    admitInstantiator = false; exercise(0, true);
  }
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void cachedCohort() throws Exception { exercise(1, true); }
  @Test void newCohort() throws Exception { exercise(0, true); }
  @Test void griddedCohort() throws Exception {
    exercise(0, true, false, false, false,
        Geometry.create("T1(1){tend=1420070400000,tstart=1388534400000,ttype=GRID,tunit=YEAR,tscope=1}"
            + "S2(240,258){bbox=[33.796 35.98113986232176 -9.41 -7.077211516709439],proj=EPSG:4326,"
            + "shape=EPSG:4326 000000000300000001000000054040E5E353F7CED9C022D1EB851EB8524040E5E353F7CED9C01C5810624DD2F24041F916872B020CC01C5810624DD2F24041F916872B020CC022D1EB851EB8524040E5E353F7CED9C022D1EB851EB852}"));
  }
  @Test void partialCohortRequiresMissingSupport() throws Exception { exercise(.5, true); }
  @Test void dailyGriddedClassificationDependency() throws Exception {
    exercise(0, true, true, false, false,
        Geometry.create("T1(365){tend=1420070400000,tstart=1388534400000,ttype=GRID,tunit=DAY,tscope=1}"
            + "S2(240,258){bbox=[33.796 35.98113986232176 -9.41 -7.077211516709439],proj=EPSG:4326,"
            + "shape=EPSG:4326 000000000300000001000000054040E5E353F7CED9C022D1EB851EB8524040E5E353F7CED9C01C5810624DD2F24041F916872B020CC01C5810624DD2F24041F916872B020CC022D1EB851EB8524040E5E353F7CED9C022D1EB851EB852}"));
  }
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
    exercise(fraction, instantiatorAvailable, nested, characterization, optional,
        Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
  }

  private void exercise(double fraction, boolean instantiatorAvailable, boolean nested, boolean characterization, boolean optional, Geometry geometry) throws Exception {
    var members = observable("each test:Region", SemanticType.SUBJECT, Contextualization.INSTANTIATION, true);
    var activity = characterization ? Contextualization.CHARACTERIZATION : Contextualization.CLASSIFICATION;
    var directive = observable(characterization ? "test:Forest of test:Region" : "test:Environment of each test:Region", SemanticType.ATTRIBUTE, activity, false);
    ((ObservableImpl) directive).setOptional(optional);
    var qualified = observable("each " + (concreteAny ? "any " : "") + "test:Environment test:Region", SemanticType.SUBJECT, Contextualization.INSTANTIATION, true);
    ((ObservableImpl)qualified).setStatedName("regions");
    var modelInput = qualifiedInput ? qualified : directive;
    var context = observation(observable(characterization ? "test:Region" : "test:Container", SemanticType.SUBJECT, Contextualization.ACKNOWLEDGEMENT, false), 10, geometry);
    var request = observation(nested ? context.getObservable() : directive, nested ? -3 : 0, observerGeometry || contextGeometry || noGeometry ? null : geometry);
    var scope = mock(ContextScope.class);
    when(scope.getId()).thenReturn("classification-test");
    when(scope.getContextObservation()).thenReturn(noContext?null:context);
    if(observerGeometry) {
      var observer=(ObservationImpl)observation(observable("test:Observer",SemanticType.AGENT,Contextualization.ACKNOWLEDGEMENT,false),20,Geometry.UNIVERSAL);
      observer.setPerceivedGeometry(geometry);when(scope.getObserver()).thenReturn(observer);
    }
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
    when(scope.within(any(Observation.class))).thenReturn(scope);
    when(scope.within(isNull())).thenReturn(scope);
    var nextId = new java.util.concurrent.atomic.AtomicLong(-20);
    when(scope.observation(any(Observable.class))).thenAnswer(inv -> {
      Observable target = inv.getArgument(0);
      assertNotEquals(directive.getUrn(), target.getUrn(), "Directive must never be registered");
      return new Observation.NaiveBuilder(target, scope) {
        @Override public Observation register() {
          var result = (ObservationImpl) make();
          result.setId(nextId.getAndDecrement());
          return result;
        }
      };
    });
    var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    if (qualifiedInput) {
      var resources = mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
      when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
      var base = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
      base.setName("test:Region");base.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE));
      var predicate = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
      predicate.setName("test:Environment");predicate.setType(EnumSet.of(SemanticType.PREDICATE,SemanticType.ATTRIBUTE));
      if (!concreteAny) predicate.getType().add(SemanticType.ABSTRACT);
      var slot=predicate.mapSyntax(n->{});if(concreteAny)slot.getType().add(SemanticType.ANY);
      var head=base.mapSyntax(n->{});head.setCollective(true);head.getTraits().add(slot);
      var syntax=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();syntax.setSemantics(head);syntax.setUrn(qualified.getUrn());
      when(resources.declareObservable(qualified.getUrn())).thenReturn(syntax);
      when(resources.declareConcept("test:Region")).thenReturn(base);
      when(resources.declareConcept("test:Environment")).thenReturn(predicate);
      when(reasoner.declareObservable(any(org.integratedmodelling.klab.api.lang.kim.KimObservable.class)))
          .thenAnswer(i->((org.integratedmodelling.klab.api.lang.kim.KimObservable)i.getArgument(0)).getSemantics().getName().equals("test:Region")?members:directive);
    }
    var capabilities = mock(Reasoner.Capabilities.class);
    when(capabilities.getServiceId()).thenReturn("classification-fixture");
    when(capabilities.getKnowledgeRevision()).thenReturn(3L);
    when(reasoner.capabilities(scope)).thenReturn(capabilities);
    when(reasoner.directInherent(any())).thenAnswer(inv ->
        ((Semantics) inv.getArgument(0)).getUrn().equals(directive.getUrn())
            ? (characterization ? context.getObservable().asConcept() : members.asConcept()) : null);
    when(reasoner.resolveObservable(members.getUrn())).thenReturn(members);
    var matcher = new ObservationReasoner(reasoner, ignored -> { throw new AssertionError(); });
    ObservationPipelineTest.document().getStatements().stream().filter(s -> s.getRank() == 0).forEach(matcher::registerStrategy);
    matcher.initializeStrategies();
    var matching = matcher.computeMatchingStrategies(request, scope, true);
    if(!noGeometry) {
      assertEquals(noContext && characterization?0:1,matching.size());
      if(!matching.isEmpty())assertEquals(0,matching.getFirst().getRank());
    }
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
    when(model.getCoverage()).thenReturn(Coverage.universal());
    when(model.getDependencies()).thenReturn(List.of());
    when(model.getAnnotations()).thenReturn(List.of());
    var computation = new ContextualizableImpl(); computation.setServiceCall(new ServiceCallImpl("test.classify"));
    when(model.getComputation()).thenReturn(List.of(computation));
    var instantiator = mock(Model.class);
    when(instantiator.getUrn()).thenReturn("test.instantiator.regions");
    when(instantiator.getNamespace()).thenReturn("test.instantiator");
    when(instantiator.getProjectName()).thenReturn("test.project");
    when(instantiator.getCoverage()).thenReturn(Coverage.universal());
    when(instantiator.getDependencies()).thenReturn(List.of());
    when(instantiator.getComputation()).thenReturn(List.of(computation));
    when(instantiator.getAnnotations()).thenReturn(List.of());
    var parentModel = mock(Model.class);
    when(parentModel.getObservables()).thenReturn(List.of(request.getObservable()));
    when(parentModel.getCoverage()).thenReturn(Coverage.universal());
    when(parentModel.getDependencies()).thenReturn(List.of(modelInput));
    when(parentModel.getComputation()).thenReturn(List.of());
    when(parentModel.getAnnotations()).thenReturn(List.of());
    var compiler = new ResolutionCompiler(mock(ResolverService.class)) {
      @Override org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt admissionReceipt(Model candidate) {
        if (candidate != instantiator || !admitInstantiator) return null;
        // Catalog/source-admission boundary is controlled; do not bypass the production gate.
        return new org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt(
            candidate.getUrn(), candidate.getNamespace(), "fixture-version", "fixture-source",
            org.integratedmodelling.klab.runtime.language.ModelValidationGate.executableHash(candidate),
            "classification-fixture", 3L);
      }
      @Override public List<Model> queryModels(Observable o, Concept c, ContextScope s, Scale scale) {
        assertSame(scope, s);
        if(noContext)assertNull(c);else assertSame(context.getObservable().asConcept(), c);
        if (nested && o.getUrn().equals(request.getObservable().getUrn())) return List.of(parentModel);
        if (o.getUrn().equals(directive.getUrn())) return classifierAvailable ? List.of(model) : List.of();
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
    if(noGeometry || noContext && characterization) {
      assertTrue(graph.isEmpty());verify(scope,never()).observation(any(Observable.class));return;
    }
    if (!instantiatorAvailable || !admitInstantiator) {
      assertTrue(graph.isEmpty());
      if (!admitInstantiator) verify(scope, atLeastOnce()).warn(contains("Named model lacks its original stable source admission"));
      return;
    }
    assertFalse(graph.isEmpty());
    assertTrue(graph.graph().vertexSet().stream().noneMatch(v -> v instanceof Observation o && o.getObservable().getUrn().equals(directive.getUrn())));
    var dataflow = new DataflowCompiler(request, graph, scope).compile();
    Dataflow restored = mapper.readValue(mapper.writerFor(Dataflow.class).writeValueAsString(dataflow), Dataflow.class);
    var pending = new java.util.ArrayDeque<Actuator>(restored.getComputation());
    boolean producerFound = false;
    while (!pending.isEmpty()) {
      var node = pending.removeFirst(); pending.addAll(node.getChildren());
      for (var constraints : node.getComputationConstraints().values()) {
        for (var constraint : constraints) {
          if (constraint.getType() == org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ResolutionNamespace
              && constraint.payload(String.class).contains("test.instantiator")) producerFound = true;
        }
      }
    }
    assertEquals(fraction < 1 && !characterization, producerFound,
        "Only a newly resolved instantiator carries producer constraints through Dataflow transport");
    var update = restored.getComputation().getFirst();
    if (qualifiedInput) {
      assertEquals(2,update.getChildren().size(),"One classification effect and one observation input");
      var port=update.getChildren().stream().filter(a->a.getActuatorType()==Actuator.Type.REFERENCE && "regions".equals(a.getName())).findFirst().orElseThrow();
      var effect=update.getChildren().stream().filter(a->a.getActuatorType()==Actuator.Type.UPDATE).findFirst().orElseThrow();
      assertEquals(1,effect.getChildren().size());
      if (fraction > 0 && fraction < 1) {
        assertEquals(Observation.QUERY_ID,port.getObservation().getId());
        assertTrue(Boolean.TRUE.equals(port.getData().get(Actuator.COMPLETE_COHORT_REFERENCE)));
        assertEquals(geometry.encode(),port.getObservation().getGeometry().encode());
      } else assertEquals(effect.getChildren().getFirst().getObservation().getId(),port.getObservation().getId());
      assertEquals(members.getUrn(),port.getObservation().getObservable().getUrn());
      assertTrue(restored.getComputation().getFirst().getDependencyFamilies().isEmpty());
    }
    if (nested) update = update.getChildren().stream().filter(a->a.getActuatorType()==Actuator.Type.UPDATE).findFirst().orElseThrow();
    if (nested) {
      assertEquals(modelInput.getUrn(), update.getModelDependency().getUrn());
      assertEquals(optional, update.getModelDependency().isOptional());
    } else assertNull(update.getModelDependency());
    assertEquals(!classifierAvailable, Boolean.TRUE.equals(update.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY)));
    if (!classifierAvailable) assertTrue(update.getComputation().isEmpty());
    assertEquals(Actuator.Type.UPDATE, update.getActuatorType());
    assertEquals(Actuator.Effect.SEMANTIC_UPDATE, update.getEffect());
    assertEquals(activity, update.getContextualization());
    assertEquals(directive.getUrn(), update.getOperationObservable().getUrn());
    assertNull(update.getObservation()); assertEquals(0, update.getId());
    assertEquals(1, update.getTargetBindings().size());
    var binding = update.getTargetBindings().getFirst();
    assertNotNull(update.getRequestedSupport());
    if(noContext || contextGeometry) {
      var support=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(update.getRequestedSupport());
      assertNotNull(support.getTime());assertEquals(0,support.getTime().getStart().getMilliseconds());assertEquals(10,support.getTime().getEnd().getMilliseconds());
      verify(scope,never()).error(any(Object[].class));
    }
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
