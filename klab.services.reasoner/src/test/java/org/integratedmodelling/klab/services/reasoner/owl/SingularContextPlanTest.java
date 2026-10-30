package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.reasoner.*;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class SingularContextPlanTest {
  private final ReasonerService service=mock(ReasonerService.class);
  private final ContextScope scope=mock(ContextScope.class);
  private final OWL owl;
  private final Ontology ontology;
  private final ObservationReasoner lowerer;
  private final ObservationImpl context=new ObservationImpl();
  private final ObservationImpl request=new ObservationImpl();
  private final KimObservationStrategyImpl strategy=new KimObservationStrategyImpl();
  private final GraphProducerImpl producer=new GraphProducerImpl();
  private final ContextPlanImpl block=new ContextPlanImpl();
  SingularContextPlanTest() {
    owl=new OWL(mock(Scope.class)); owl.manager=OWLManager.createOWLOntologyManager();
    owl.requireOntology("odo").define(List.of(Axiom.ObjectPropertyAssertion("isInherentTo"),Axiom.ObjectPropertyAssertion("observationallyContains")));
    ontology=owl.requireOntology("test");
    ontology.define(List.of(Axiom.ClassAssertion("System",EnumSet.of(SemanticType.SUBJECT)),
        Axiom.ClassAssertion("Other",EnumSet.of(SemanticType.SUBJECT)),
        Axiom.ClassAssertion("Temperature",EnumSet.of(SemanticType.QUALITY))));
    var f=owl.manager.getOWLDataFactory();
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(ontology.getConcept("Temperature")),
        f.getOWLObjectSomeValuesFrom(owl.getProperty("odo:isInherentTo").getOWLEntity().asOWLObjectProperty(),owl.getOWLClass(ontology.getConcept("System")))));
    request.setObservable(observable("Temperature"));
    context.setId(21); context.setUrn("context:21"); context.setObservable(observable("System"));
    context.setGeometry(Geometry.UNIVERSAL); context.setEventTimestamps(List.of(0L));
    when(scope.getContextObservation()).thenReturn(context);
    when(service.owl()).thenReturn(owl); when(service.knowledgeRevision()).thenReturn(3L);
    var capabilities=mock(Reasoner.Capabilities.class); when(capabilities.getServiceId()).thenReturn("reasoner");
    when(capabilities.getKnowledgeRevision()).thenReturn(3L);
    when(service.capabilities(scope)).thenReturn(capabilities);
    when(service.match(any(),any())).thenReturn(true);
    lowerer=new ObservationReasoner(service, ignored->request.getObservable());
    strategy.setUrn("singular"); strategy.setNamespace("test"); strategy.setRank(1);
    strategy.setType(KimObservationStrategy.Type.OBSERVATION);
    var selection=new StrategySelectionImpl(); var alternative=new MatchAlternativeImpl();
    alternative.setObservable(new KimObservableImpl()); selection.getAlternatives().add(alternative); strategy.setSelection(selection);
    var reference=new GraphReferenceImpl(); reference.setName("known"); reference.setObservation(variable("$context"));
    block.setContextGraph(symbol("known")); block.setVariable("ctx"); block.setName("bound");
    producer.setMode(ProducerMode.OBSERVE); producer.setContext(variable("$ctx")); producer.setName("child");
    var target=new StrategyTargetImpl(); target.setExpression(variable("$this")); producer.setTarget(target);
    var body=new PlanBodyImpl(); body.getSteps().addAll(List.of(producer,resultYield("child"))); block.setBody(body);
    var plan=new PlanBodyImpl(); plan.getSteps().addAll(List.of(reference,block,resultYield("bound"))); strategy.setPlan(plan);
    lowerer.registerStrategy(strategy);
  }
  private Observable observable(String name) {
    var o=new ObservableImpl(); o.setSemantics(ontology.getConcept(name)); o.setUrn("test:"+name);
    o.setDescriptionType(name.equals("Temperature")?Contextualization.QUANTIFICATION:Contextualization.ACKNOWLEDGEMENT); return o;
  }
  private static StrategyVariableImpl variable(String name) { var v=new StrategyVariableImpl();v.setSpelling(name);return v; }
  private static SymbolReferenceImpl symbol(String name) { var s=new SymbolReferenceImpl();s.setName(name);return s; }
  private static PlanYieldImpl resultYield(String name) { var y=new PlanYieldImpl();y.setGraph(symbol(name));return y; }
  private List<ObservationStrategy> matches() { return lowerer.computeMatchingStrategies(request,scope,true); }
  @Test void productionLoweringCarriesNativeContextWithoutLookupOrDiscovery() {
    var result=matches(); assertEquals(1,result.size());
    assertEquals(21,result.getFirst().getContextBinding().contextId());
    assertEquals(request.getObservable().getUrn(),result.getFirst().getOperations().getFirst().getObservable().getUrn());
    verify(scope,never()).getObservation(anyLong()); verify(scope,never()).observation(any());
  }
  @Test void incompatibleNativeBearerDoesNotBecomeValidThroughContainment() {
    context.setObservable(observable("Other")); assertTrue(matches().isEmpty());
  }
  @Test void tierZeroAndSameObligationRecursiveResolveAreUnavailable() {
    strategy.setRank(0); assertTrue(matches().isEmpty()); strategy.setRank(1);
    producer.setMode(ProducerMode.RESOLVE); assertTrue(matches().isEmpty());
  }
  @Test void unresolvedAndPluralAndNestedContextsAreUnavailable() {
    context.setEventTimestamps(List.of()); assertTrue(matches().isEmpty());
    context.setEventTimestamps(List.of(0L)); context.setId(-1); assertTrue(matches().isEmpty());
    context.setId(21); block.getBody().getSteps().add(block); assertTrue(matches().isEmpty());
  }
  @Test void wrongChildCannotFulfillOuterRequest() {
    var target=new StrategyTargetImpl(); target.setExpression(variable("$context")); producer.setTarget(target);
    assertTrue(matches().isEmpty());
  }
  @Test void completeNativeConjunctionMustHoldForOneActualContext() {
    var f=owl.manager.getOWLDataFactory();
    owl.manager.addAxiom(ontology.getOWLOntology(), f.getOWLSubClassOfAxiom(owl.getOWLClass(ontology.getConcept("Temperature")),
        f.getOWLObjectSomeValuesFrom(owl.getProperty("odo:isInherentTo").getOWLEntity().asOWLObjectProperty(),
            owl.getOWLClass(ontology.getConcept("Other")))));
    assertTrue(matches().isEmpty());
  }
  @Test void unavailableSemanticGenerationDoesNotAbortOtherCandidates() {
    when(service.capabilities(scope)).thenReturn(null);assertTrue(matches().isEmpty());
  }

  @Test void acceptedBoundaryKeepsPrivateProofFenceAndPublishesPublicGeneration() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var configuration=new org.integratedmodelling.klab.services.configuration.ReasonerConfiguration();
    configuration.setServiceId("private-compiler");
    var validation=new org.integratedmodelling.klab.services.reasoner.internal.SemanticValidationState();
    validation.settle(3,true);
    var values=new LinkedHashMap<String,Object>();
    values.put("semanticOnly",true);values.put("configuration",configuration);
    values.put("knowledgeRevision",new java.util.concurrent.atomic.AtomicLong(3));
    values.put("semanticValidationState",validation);
    values.put("publishedSemanticCapabilities",org.integratedmodelling.klab.services.reasoner.internal.SemanticCapabilitiesSnapshot.capture(
        0,"w","w",true,List.of(),null));
    for(var entry:values.entrySet()) {
      var field=ReasonerService.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(service,entry.getValue());
    }
    doCallRealMethod().when(service).portableCapabilities(anyString(),anyLong());
    doCallRealMethod().when(service).portableCapabilities(anyString(),anyLong(),anyString());
    doCallRealMethod().when(service).capabilities(any());
    when(service.resolveConcept(anyString())).thenAnswer(i->ontology.getConcept(i.getArgument(0,String.class).substring(5)));
    when(service.computeObservationStrategies(any(),any())).thenAnswer(i->lowerer.computeMatchingStrategies(i.getArgument(0),i.getArgument(1),true));
    var state=mock(org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironment.class);
    when(state.actor()).thenReturn(service);
    var factory=mock(org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.class);
    when(factory.dependencyRevision()).thenReturn("test");
    when(factory.capture(any(Worldview.class))).thenAnswer(i->{Worldview world=i.getArgument(0);return new org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.Input<>(
        org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.sources(world),
        org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.sourceOrder(world),"test",
        org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory.CONFIGURATION_REVISION,
        world,org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.BeanStatus.VALID,List.of());});
    when(factory.build(any(),any())).thenReturn(new org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.Result<>(
        state,org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()));
    var world=new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();world.setUrn("w");world.setWorldviewId("w");world.setEmpty(false);
    var source=new KimOntologyImpl();source.setUrn("w");source.setProjectName("test");source.setSourceCode("ontology w in domain root;");
    world.getOntologies().add(source);
    try(var runtime=new org.integratedmodelling.klab.services.reasoner.internal.AcceptedSemanticRuntime(factory,"deployed-reasoner")) {
      runtime.submit(world);assertTrue(runtime.awaitIdle(java.time.Duration.ofSeconds(5)));
      List<ObservationStrategy> result=runtime.invoke("computeObservationStrategies",new Class<?>[]{Observation.class,ContextScope.class},new Object[]{request,scope});
      assertEquals(1,result.size());var binding=result.getFirst().getContextBinding();assertNotNull(binding);
      assertEquals("deployed-reasoner",binding.reasonerId());
      assertEquals(runtime.status().acceptedGeneration(),binding.knowledgeRevision());
      assertEquals(3,service.knowledgeRevision(),"The proof's private mutation counter is unchanged");
      assertEquals("private-compiler",service.capabilities(scope).getServiceId());
    }
  }

  @Test void localProofMutationIsRejectedEvenWhenPortableGenerationIsUnchanged() {
    when(service.knowledgeRevision()).thenReturn(3L,4L);assertTrue(matches().isEmpty());
  }

}
