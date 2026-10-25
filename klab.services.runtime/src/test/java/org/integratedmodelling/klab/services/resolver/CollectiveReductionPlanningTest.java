package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.collections.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.*;
import org.integratedmodelling.klab.api.services.resolver.objects.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.ObservationReasoner;
import org.junit.jupiter.api.*;

/** Production strategy generation/Resolver/Dataflow; controlled catalog and runtime I/O. */
public class CollectiveReductionPlanningTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  final ContextScope scope=mock(ContextScope.class);
  final Reasoner reasoner=mock(Reasoner.class);
  final Reasoner.Capabilities capabilities=mock(Reasoner.Capabilities.class);
  final org.integratedmodelling.klab.api.knowledge.observation.scale.Scale scale=GeometryRepository.INSTANCE.scale(
      Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}"));
  final Observable target=observable("test:TreeCount",SemanticType.QUALITY,SemanticType.NUMEROSITY);
  final Observable trees=observable("each test:Tree",SemanticType.SUBJECT,SemanticType.COUNTABLE);
  final Concept tree=trees.asConcept().singular();
  final ObservationImpl context=observation(21,"context:21",observable("test:Region",SemanticType.SUBJECT));
  final ObservationImpl request=observation(-1,"request",target);
  final ObservationImpl discovery=observation(-2,"discovery",trees);
  final Model model=mock(Model.class);
  final ResolutionCompiler compiler=spy(new ResolutionCompiler(mock(ResolverService.class)));
  final ObservationReasoner strategies=new ObservationReasoner(reasoner,ignored->null);
  CollectiveReductionPlanningTest(){
    ((ConceptImpl)trees.asConcept()).setCollective(true);((ObservableImpl)trees).setDescriptionType(Contextualization.INSTANTIATION);
    when(reasoner.splitOperators(target)).thenReturn(Pair.of(tree,List.of(SemanticType.NUMEROSITY)));
    when(reasoner.resolveObservable(anyString())).thenReturn(trees);
    when(reasoner.capabilities(scope)).thenReturn(capabilities);when(capabilities.getServiceId()).thenReturn("reasoner");when(capabilities.getKnowledgeRevision()).thenReturn(3L);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(scope.getContextObservation()).thenReturn(context);when(scope.getObservation(21)).thenReturn(context);
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);when(scope.within(nullable(Observation.class))).thenReturn(scope);
    var builder=mock(Observation.Builder.class,RETURNS_SELF);when(scope.observation(trees)).thenReturn(builder);when(builder.register()).thenReturn(discovery);
    var data=Parameters.<String>create();data.put("__RESOLUTION_GRAPH__",ResolutionGraph.create(scope));when(scope.getData()).thenReturn(data);
    when(reasoner.computeObservationStrategies(any(),eq(scope))).thenAnswer(call->{
      Observation o=call.getArgument(0);return o.getObservable()==trees?List.of(flat(trees)):strategies.computeMatchingStrategies(o,scope,true);
    });
    doReturn(new ResolutionCompiler.QueryMatch(null,null,scale,null,Coverage.create(scale,0))).when(compiler).query(any(),eq(scale),eq(scope));
    when(model.getUrn()).thenReturn("test:instantiateTrees");when(model.getNamespace()).thenReturn("test");when(model.getCoverage()).thenReturn(scale);
    when(model.getObservables()).thenReturn(List.of(trees));var code=new org.integratedmodelling.common.lang.ContextualizableImpl();code.setLiteral(1.0);
    when(model.getComputation()).thenReturn(List.of(code));
    doReturn(List.of(model)).when(compiler).queryModels(eq(trees),any(),eq(scope),eq(scale));
    doAnswer(call->new org.integratedmodelling.klab.runtime.language.ModelValidationGate.AdmissionReceipt(
        model.getUrn(),model.getNamespace(),"fixture-version","fixture-source",
        org.integratedmodelling.klab.runtime.language.ModelValidationGate.executableHash(model),"reasoner",3L))
        .when(compiler).admissionReceipt(model);
    var runtime=mock(RuntimeService.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);when(runtime.resolveContextualizables(anyList(),eq(scope))).thenReturn(new ResourceSet());
  }
  static Observable observable(String urn,SemanticType...types){
    var c=new ConceptImpl();c.setUrn(urn);c.setName(urn);c.setReferenceName(urn);c.setType(EnumSet.copyOf(List.of(types)));
    var o=new ObservableImpl();o.setUrn(urn);o.setName(urn);o.setSemantics(c);o.setDescriptionType(types[0]==SemanticType.QUALITY?Contextualization.QUANTIFICATION:Contextualization.ACKNOWLEDGEMENT);return o;
  }
  ObservationImpl observation(long id,String urn,Observable observable){var o=new ObservationImpl();o.setId(id);o.setUrn(urn);o.setObservable(observable);o.setGeometry(scale);o.setEventTimestamps(List.of(0L));return o;}
  static ObservationStrategyImpl flat(Observable observable){var s=new ObservationStrategyImpl();s.setUrn("test:direct");s.setRank(0);var op=new ObservationStrategyImpl.OperationImpl();op.setType(ObservationStrategy.Operation.Type.OBSERVE);op.setObservable(observable);s.getOperations().add(op);return s;}
  @Test void missingTierZeroPlansRealDiscoveryAndPortableReductionWithoutContainmentAxiom(){
    var graph=compiler.resolve(request,scope);assertFalse(graph.isEmpty());assertTrue(graph.getCoverage().isComplete());
    var flow=new DataflowCompiler(request,graph,scope).compile();var root=flow.getComputation().getFirst();
    assertTrue(root.getData().containsKey(CollectiveReductionBinding.DATA_KEY));assertEquals(1,root.getChildren().size());
    assertEquals("members",root.getChildren().getFirst().getName());assertEquals(trees.getUrn(),root.getChildren().getFirst().getOperationObservable().getUrn());
    verify(scope,times(1)).observation(trees);verify(reasoner,never()).directInherent(any());
  }
  @Test void successfulTierZeroPerformsNoCollectiveDiscovery(){
    doAnswer(call->{var all=new ArrayList<ObservationStrategy>();all.add(flat(target));all.addAll(strategies.computeMatchingStrategies(request,scope,true));return all;}).when(reasoner).computeObservationStrategies(request,scope);
    when(model.getObservables()).thenReturn(List.of(target));doReturn(List.of(model)).when(compiler).queryModels(eq(target),any(),eq(scope),eq(scale));
    var graph=compiler.resolve(request,scope);assertFalse(graph.isEmpty());assertTrue(graph.getCoverage().isComplete());
    verify(scope,never()).observation(trees);verify(compiler,never()).queryModels(eq(trees),any(),any(),any());
  }
  @Test void failedTierZeroFallsBackToCollectiveRoute(){
    doAnswer(call->{var all=new ArrayList<ObservationStrategy>();all.add(flat(target));all.addAll(strategies.computeMatchingStrategies(request,scope,true));return all;}).when(reasoner).computeObservationStrategies(request,scope);
    doReturn(List.of()).when(compiler).queryModels(eq(target),any(),eq(scope),eq(scale));
    assertFalse(compiler.resolve(request,scope).isEmpty());verify(scope).observation(trees);
  }
  @Test void validCompletedCohortReferenceAvoidsAnotherInstantiation(){
    var existing=observation(42,"existing",trees);
    doReturn(new ResolutionCompiler.QueryMatch(existing,existing,scale,scale,Coverage.create(scale,1))).when(compiler).query(trees,scale,scope);
    var graph=compiler.resolve(request,scope);assertFalse(graph.isEmpty());
    var flow=new DataflowCompiler(request,graph,scope).compile();assertTrue(flow.getComputation().getFirst().getData().containsKey(CollectiveReductionBinding.DATA_KEY));
    verify(scope,never()).observation(trees);verify(compiler,never()).queryModels(eq(trees),any(),any(),any());
  }
  @Test void observerSpecificSubstantialDiscoveryDoesNotLoseItsMeaningInAnAbsoluteReduction() {
    var agent=new ConceptImpl();agent.setUrn("test:Observer");agent.setType(EnumSet.of(SemanticType.AGENT));
    when(model.getObservables()).thenReturn(List.of(ObservableImpl.withObserverSemantics(trees,agent)));
    assertTrue(compiler.resolve(request,scope).isEmpty());
  }
  public record NamedPlan(org.integratedmodelling.klab.api.services.runtime.Actuator plan,Model model,
      org.integratedmodelling.klab.api.lang.kim.KimNamespace namespace) {}
  public static NamedPlan admittedNamedPlan() {
    var f=new CollectiveReductionPlanningTest();
    doReturn(List.of(flat(f.target))).when(f.reasoner).computeObservationStrategies(f.request,f.scope);
    when(f.model.getObservables()).thenReturn(List.of(f.target));
    doReturn(List.of(f.model)).when(f.compiler).queryModels(eq(f.target),any(),eq(f.scope),eq(f.scale));
    var resources=mock(ResourcesService.class);when(f.scope.getService(ResourcesService.class)).thenReturn(resources);
    var namespace=new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();namespace.setUrn("test");namespace.setSourceCode("fixture source");
    var declaration=new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();declaration.setUrn(f.model.getUrn());namespace.getStatements().add(declaration);
    when(resources.retrieve("test",org.integratedmodelling.klab.api.lang.kim.KimNamespace.class,f.scope)).thenReturn(namespace);
    when(resources.declareObservable(anyString())).thenReturn(new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl());
    when(f.reasoner.validateDocument(any(),eq(f.scope))).thenAnswer(call->{
      var response=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(call.getArgument(0));
      response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);return response;});
    var admission=org.integratedmodelling.klab.runtime.language.ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,f.target);
    assertNotNull(admission);doReturn(admission).when(f.compiler).admissionReceipt(f.model);
    var plan=new DataflowCompiler(f.request,f.compiler.resolve(f.request,f.scope),f.scope).compile().getComputation().getFirst();
    return new NamedPlan(plan,f.model,namespace);
  }
  public static org.integratedmodelling.klab.api.services.runtime.Actuator compiledNamedPlan() {return admittedNamedPlan().plan();}

  @Test void semanticRevisionOrReasonerChangeAfterResolutionRetainsThePlannedSnapshot() {
    var graph=compiler.resolve(request,scope);assertFalse(graph.isEmpty());
    when(capabilities.getKnowledgeRevision()).thenReturn(4L);
    assertDoesNotThrow(()->new DataflowCompiler(request,graph,scope).compile());
    when(capabilities.getKnowledgeRevision()).thenReturn(3L);when(capabilities.getServiceId()).thenReturn("replacement");
    assertDoesNotThrow(()->new DataflowCompiler(request,graph,scope).compile());
  }
  @Test void strategyTransportRetainsTypedProofAndWrongOperatorCannotForgeIt(){
    var result=strategies.computeMatchingStrategies(request,scope,true);assertEquals(1,result.size());
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    assertDoesNotThrow(()->{var recovered=mapper.readValue(mapper.writeValueAsBytes(result.getFirst()),ObservationStrategy.class);assertEquals(result.getFirst().getCollectiveReduction(),recovered.getCollectiveReduction());});
    when(reasoner.splitOperators(target)).thenReturn(Pair.of(tree,List.of(SemanticType.MAGNITUDE)));
    assertTrue(strategies.computeMatchingStrategies(request,scope,true).isEmpty());
  }
}
