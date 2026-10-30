package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;

/** Planning-only composition: normal native model admission plus normal checked UPDATEs. */
class CollectiveSelectionPlanningTest {
  @org.junit.jupiter.api.BeforeAll static void configure() { CollectiveReductionPlanningTest.configure(); }
  final CollectiveReductionPlanningTest f = new CollectiveReductionPlanningTest();
  final Observable requested = CollectiveReductionPlanningTest.observable("each test:Beech test:Tree", SemanticType.SUBJECT, SemanticType.COUNTABLE);
  final List<Observable> directives = List.of(directive("test:Species"), directive("test:Condition"));
  final CollectiveSelectionBinding binding;
  final org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl request;
  final ObservationStrategyImpl selection = new ObservationStrategyImpl();

  CollectiveSelectionPlanningTest() {
    when(f.scope.getId()).thenReturn("selection-test");
    ((ConceptImpl)requested.asConcept()).setCollective(true);
    ((ObservableImpl)requested).setDescriptionType(Contextualization.INSTANTIATION);
    request=f.observation(0,"request",requested);
    binding=new CollectiveSelectionBinding(requested.getUrn(),f.trees.getUrn(),
        List.of("test:Beech","test:Healthy"), List.of("test:Species","test:Condition"),
        directives.stream().map(Observable::getUrn).toList(),"reasoner",3,
        org.integratedmodelling.klab.api.geometry.Geometry.forTransport(f.scale).encode(),null,null,f.scope.getId(),CollectiveSelectionBinding.context(f.scope),null);
    selection.setUrn("klab:collective-concrete-selection");selection.setRank(1);selection.setCollectiveSelection(binding);
    when(f.reasoner.resolveObservable(f.trees.getUrn())).thenReturn(f.trees);
    doAnswer(call->call.getArgument(0)==f.trees?List.of(f.model):List.of()).when(f.compiler)
        .queryModels(any(Observable.class),any(),eq(f.scope),eq(f.scale));
    for(var d:directives)when(f.reasoner.resolveObservable(d.getUrn())).thenReturn(d);
    for(var name:List.of("test:Beech","test:Healthy","test:Species","test:Condition")) {
      var concept=new ConceptImpl();concept.setUrn(name);when(f.reasoner.resolveConcept(name)).thenReturn(concept);
    }
    when(f.reasoner.computeObservationStrategies(any(),eq(f.scope))).thenAnswer(call->{
      var o=call.getArgument(0,org.integratedmodelling.klab.api.knowledge.observation.Observation.class).getObservable();
      if(o==requested)return List.of(selection);
      if(o==f.trees)return List.of(CollectiveReductionPlanningTest.flat(f.trees));
      return List.of(classification(o));
    });
  }
  static Observable directive(String family) {
    var o=CollectiveReductionPlanningTest.observable(family+" of each test:Tree",SemanticType.ATTRIBUTE,SemanticType.PREDICATE);
    ((ObservableImpl)o).setDescriptionType(Contextualization.CLASSIFICATION);return o;
  }
  ObservationStrategyImpl classification(Observable directive) {
    var s=new ObservationStrategyImpl();s.setUrn("test:classification");
    var members=new ObservationStrategyImpl.OperationImpl();members.setId("cohort");members.setType(ObservationStrategy.Operation.Type.RESOLVE);members.setObservable(f.trees);
    var operation=new ObservationStrategyImpl.OperationImpl();operation.setId("classified");operation.setType(ObservationStrategy.Operation.Type.OBSERVE);operation.setObservable(directive);operation.getInputs().put("members","cohort");
    s.getOperations().addAll(List.of(members,operation));return s;
  }
  @Test void nativeDiscoveryOnceThenOrderedFamilyUpdatesWithCompletedReferences() {
    var graph=f.compiler.resolve(request,f.scope);assertFalse(graph.isEmpty());assertTrue(graph.getCoverage().isComplete());
    var flow=new DataflowCompiler(request,graph,f.scope).compile();
    assertNotNull(flow.getCollectiveSelection());assertEquals(3,flow.getComputation().size());
    assertEquals(org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.RESOLVED,flow.getResolutionOutcome());
    assertEquals("native",flow.getComputation().getFirst().getName());
    for(var operation:flow.getComputation().subList(1,3)) {
      assertEquals(Actuator.Type.UPDATE,operation.getActuatorType());assertNull(operation.getObservation());
      assertEquals(Contextualization.CLASSIFICATION,operation.getContextualization());
      assertTrue(Boolean.TRUE.equals(operation.getData().get(Actuator.CLASSIFICATION_CHECK_ONLY)));
      var members=operation.getChildren().getFirst();assertEquals(Actuator.Type.REFERENCE,members.getActuatorType());
      assertTrue(Boolean.TRUE.equals(members.getData().get(Actuator.COMPLETE_COHORT_REFERENCE)));
      assertEquals(List.of("members_0"),operation.getTargetBindings().getFirst().getSources());
    }
    verify(f.scope,times(1)).observation(f.trees);
    assertEquals(binding,org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        flow.getMetadata().get(CollectiveSelectionBinding.DATA_KEY).toString(),CollectiveSelectionBinding.class));
  }
  @Test void fullObservableTierZeroPrecedesAutomaticDiscovery() {
    when(f.reasoner.computeObservationStrategies(request,f.scope)).thenReturn(List.of(selection,CollectiveReductionPlanningTest.flat(requested)));
    when(f.model.getObservables()).thenReturn(List.of(requested));
    doReturn(List.of(f.model)).when(f.compiler).queryModels(eq(requested),any(),eq(f.scope),eq(f.scale));
    var graph=f.compiler.resolve(request,f.scope);assertFalse(graph.isEmpty());
    var flow=new DataflowCompiler(request,graph,f.scope).compile();assertNull(flow.getCollectiveSelection());
    verify(f.scope,never()).observation(f.trees);
  }
  @Test void unknownNativeDomainDoesNotBecomeAnEmptySuccessfulSelection() {
    doReturn(List.of()).when(f.compiler).queryModels(eq(f.trees),any(),eq(f.scope),eq(f.scale));
    assertTrue(f.compiler.resolve(request,f.scope).isEmpty());
  }
  @Test void changedGenerationRejectsPlanBeforeDiscovery() {
    when(f.capabilities.getKnowledgeRevision()).thenReturn(4L);
    assertTrue(f.compiler.resolve(request,f.scope).isEmpty());verify(f.scope,never()).observation(f.trees);
  }
  @Test void nativePlanningQueryCarriesObserverGenerationAndFocusAdmission() {
    var builder=mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.Builder.class,RETURNS_SELF);
    when(f.scope.observation(f.trees)).thenReturn(builder);when(builder.register()).thenReturn(f.discovery);
    when(builder.submit()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
        org.integratedmodelling.klab.api.knowledge.observation.Observation.empty()));
    doCallRealMethod().when(f.compiler).query(eq(f.trees),eq(f.scale),eq(f.scope));
    assertFalse(f.compiler.resolve(request,f.scope).isEmpty());
    verify(builder).metadata(CollectiveSelectionBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(binding));
  }
  @Test void nestedSelectionRetainsItsEnclosingResultAndAliasWithPortableDeferredInput() throws Exception {
    var root=ResolutionGraph.create(f.scope);
    var selected=f.compiler.resolve(request,f.scope);assertFalse(selected.isEmpty());
    var enclosing=root.createChild(f.model,f.scale);enclosing.merge(selected);
    assertNull(enclosing.collectiveSelection);
    assertSame(selected,enclosing.selectionDependencies.get(request));
    var compiler=new DataflowCompiler(f.request,enclosing,f.scope);
    var nested=compiler.compileObservation(request,f.scale,null,"local_trees").getFirst();
    assertEquals("local_trees",nested.getName());assertEquals(Actuator.Type.REFERENCE,nested.getActuatorType());
    assertEquals(org.integratedmodelling.klab.api.knowledge.observation.Observation.QUERY_ID,nested.getId());
    assertNotNull(nested.getCollectiveSelectionDependency());
    assertEquals(binding,nested.getCollectiveSelectionDependency().getMetadata().get(CollectiveSelectionBinding.DATA_KEY) instanceof String encoded
        ?org.integratedmodelling.klab.utilities.Utils.Json.parseObject(encoded,CollectiveSelectionBinding.class):null);
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var recovered=mapper.readValue(mapper.writeValueAsBytes(nested),Actuator.class);
    assertEquals("local_trees",recovered.getName());assertNotNull(recovered.getCollectiveSelectionDependency().getCollectiveSelection());
    assertSame(f.target,f.request.getObservable(),"The enclosing result must remain its original observation");
  }
  @Test void ordinaryParentModelResolvesItsQualifiedDependencyThroughTheNestedSelectionRoute() {
    ((ObservableImpl)requested).setStatedName("local_trees");
    var outer=mock(Model.class);
    when(outer.getObservables()).thenReturn(List.of(f.target));when(outer.getDependencies()).thenReturn(List.of(requested));
    when(outer.getCoverage()).thenReturn(f.scale);
    var code=new org.integratedmodelling.common.lang.ContextualizableImpl();code.setLiteral(1.0);
    when(outer.getComputation()).thenReturn(List.of(code));
    var resources=mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    when(f.scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
    var builder=mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.Builder.class,RETURNS_SELF);
    var dependency=f.observation(-3,"qualified-dependency",requested);
    when(f.scope.observation(requested)).thenReturn(builder);when(builder.register()).thenReturn(dependency);
    when(f.reasoner.computeObservationStrategies(f.request,f.scope)).thenReturn(List.of(CollectiveReductionPlanningTest.flat(f.target)));
    doReturn(List.of(outer)).when(f.compiler).queryModels(eq(f.target),any(),eq(f.scope),eq(f.scale));
    var graph=f.compiler.resolve(f.request,f.scope);assertFalse(graph.isEmpty());assertNull(graph.collectiveSelection);
    var flow=new DataflowCompiler(f.request,graph,f.scope).compile();
    assertNull(flow.getCollectiveSelection());
    assertEquals(org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.DEFERRED,flow.getResolutionOutcome());
    assertEquals(f.target,flow.getComputation().getFirst().getObservation().getObservable());
    var child=flow.getComputation().getFirst().getChildren().stream()
        .filter(a->a.getCollectiveSelectionDependency()!=null).findFirst().orElseThrow();
    assertEquals("local_trees",child.getName());assertEquals(Actuator.Type.REFERENCE,child.getActuatorType());
    assertEquals(requested.getSemantics(),child.getObservation().getObservable().getSemantics());
    assertNotNull(child.getCollectiveSelectionDependency().getCollectiveSelection());
    verify(f.scope,times(1)).observation(f.trees);
  }
  @Test void unsupportedTemporalExclusionRetainsPrefixAndConservativelyRediscoversWholeSupport() {
    var half=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(
        org.integratedmodelling.klab.api.geometry.Geometry.create("T0(1){tstart=0,tend=5,ttype=PHYSICAL}"));
    var existing=f.observation(42,"existing-native",f.trees);existing.setGeometry(half);
    doReturn(new ResolutionCompiler.QueryMatch(existing,existing,f.scale,half,
        org.integratedmodelling.klab.api.services.resolver.Coverage.create(f.scale,.5)))
        .when(f.compiler).query(eq(f.trees),eq(f.scale),eq(f.scope));
    when(f.scope.observation(f.trees)).thenAnswer(call->new org.integratedmodelling.klab.api.knowledge.observation.Observation.NaiveBuilder(f.trees,f.scope) {
      @Override public org.integratedmodelling.klab.api.knowledge.observation.Observation register() {
        var observation=(org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)make();observation.setId(-20);return observation;
      }
    });
    doAnswer(call->call.getArgument(0)==f.trees?List.of(f.model):List.of()).when(f.compiler)
        .queryModels(any(Observable.class),any(),eq(f.scope),any(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class));
    var graph=f.compiler.resolve(request,f.scope);assertFalse(graph.isEmpty());assertTrue(graph.getCoverage().isComplete());
    var flow=new DataflowCompiler(request,graph,f.scope).compile();
    var nativeRoot=flow.getComputation().getFirst();assertEquals(Actuator.Type.OBSERVE,nativeRoot.getActuatorType());
    for(var support:List.of(nativeRoot.getObservation().getGeometry(),nativeRoot.getRequestedSupport(),nativeRoot.getCoverage())) {
      var time=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(support).getTime();
      // TimeImpl.merge(EXCLUSION) does not yet express temporal complements. The resolver
      // checks the claimed complement and deliberately reuses the complete request instead.
      assertEquals(0,time.getStart().getMilliseconds());assertEquals(10,time.getEnd().getMilliseconds());
    }
    var prefix=nativeRoot.getChildren().stream().filter(a->a.getActuatorType()==Actuator.Type.REFERENCE && a.getId()==42).findFirst().orElseThrow();
    var prefixTime=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(prefix.getCoverage()).getTime();
    assertEquals(0,prefixTime.getStart().getMilliseconds());assertEquals(5,prefixTime.getEnd().getMilliseconds());
    assertEquals(10,f.scale.getTime().getEnd().getMilliseconds());
    assertEquals(2,flow.getCollectiveSelection().classificationOperations().size());
    verify(f.scope,times(1)).observation(f.trees);
  }
  @Test void representableSpatialExclusionLowersOnlyMissingRectangleAndRetainsCompletedPrefix() {
    var repository=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE;
    var full=rectangle(0,10);var half=rectangle(0,5);
    request.setGeometry(full);f.context.setGeometry(full);when(f.model.getCoverage()).thenReturn(full);
    var spatialBinding=new CollectiveSelectionBinding(binding.requested(),binding.nativeMembers(),binding.predicates(),binding.families(),binding.directives(),
        binding.reasonerId(),binding.revision(),org.integratedmodelling.klab.api.geometry.Geometry.forTransport(full).encode(),
        null,null,f.scope.getId(),CollectiveSelectionBinding.context(f.scope),null);
    selection.setCollectiveSelection(spatialBinding);
    var existing=f.observation(42,"existing-spatial-native",f.trees);existing.setGeometry(half);
    doAnswer(call->{
      var target=call.getArgument(0,Observable.class);
      var requested=call.getArgument(1,org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class);
      return target==f.trees?new ResolutionCompiler.QueryMatch(existing,existing,requested,half,
          org.integratedmodelling.klab.api.services.resolver.Coverage.create(requested,.5)):
          new ResolutionCompiler.QueryMatch(null,null,requested,null,org.integratedmodelling.klab.api.services.resolver.Coverage.create(requested,0));
    }).when(f.compiler).query(any(Observable.class),any(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class),eq(f.scope));
    when(f.scope.observation(f.trees)).thenAnswer(call->new org.integratedmodelling.klab.api.knowledge.observation.Observation.NaiveBuilder(f.trees,f.scope) {
      @Override public org.integratedmodelling.klab.api.knowledge.observation.Observation register() {
        var observation=(org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)make();observation.setId(-20);return observation;
      }
    });
    doAnswer(call->call.getArgument(0)==f.trees?List.of(f.model):List.of()).when(f.compiler)
        .queryModels(any(Observable.class),any(),eq(f.scope),any(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class));
    var graph=f.compiler.resolve(request,f.scope);assertFalse(graph.isEmpty());assertTrue(graph.getCoverage().isComplete());
    var flow=new DataflowCompiler(request,graph,f.scope).compile();var nativeRoot=flow.getComputation().getFirst();
    assertEquals(Actuator.Type.OBSERVE,nativeRoot.getActuatorType());
    for(var support:List.of(nativeRoot.getObservation().getGeometry(),nativeRoot.getRequestedSupport(),nativeRoot.getCoverage())) {
      var envelope=repository.scale(support).getSpace().getGeometricShape().getEnvelope();
      assertEquals(5,envelope.getMinX(),1e-9);assertEquals(10,envelope.getMaxX(),1e-9);
      assertEquals(0,envelope.getMinY(),1e-9);assertEquals(10,envelope.getMaxY(),1e-9);
    }
    var prefix=nativeRoot.getChildren().stream().filter(a->a.getActuatorType()==Actuator.Type.REFERENCE && a.getId()==42).findFirst().orElseThrow();
    var prefixEnvelope=repository.scale(prefix.getCoverage()).getSpace().getGeometricShape().getEnvelope();
    assertEquals(0,prefixEnvelope.getMinX(),1e-9);assertEquals(5,prefixEnvelope.getMaxX(),1e-9);
    assertEquals(2,flow.getCollectiveSelection().classificationOperations().size());
    verify(f.scope,times(1)).observation(f.trees);
  }
  private org.integratedmodelling.klab.api.knowledge.observation.scale.Scale rectangle(int west,int east) {
    var shape=org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create("EPSG:3857 POLYGON (("+west+" 0,"+west+" 10,"+east+" 10,"+east+" 0,"+west+" 0))");
    var geometry=org.integratedmodelling.klab.api.geometry.Geometry.create("s2(1){proj=EPSG:3857,shape="+shape.asWKB()+"}");
    return org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(geometry);
  }
}
