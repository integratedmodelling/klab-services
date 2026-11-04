package org.integratedmodelling.klab.services.resolver;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.*;
import org.junit.jupiter.api.Test;
class ModelCandidateIngestionTest {
 @Test void exactCoreMatchesDoNotExpandGeneralizedHeads() {
  var scope=mock(ContextScope.class);var actor=mock(Reasoner.class);
  when(scope.getService(Reasoner.class)).thenReturn(actor);
  var meaning=new ConceptImpl();meaning.setUrn("test:Quality");meaning.setType(EnumSet.of(SemanticType.QUALITY));
  var requested=ObservableImpl.promote(meaning,null);
  var output=ObservableImpl.promote(meaning,null);
  when(actor.coreObservable(any())).thenReturn(meaning);
  when(actor.resolving(meaning)).thenReturn(List.of(meaning));
  var model=mock(Model.class);when(model.getObservables()).thenReturn(List.of(output));
  var filter=ResolutionCompiler.candidateCoreFilter(requested,scope);
  for(int i=0;i<10;i++)assertTrue(filter.test(model));
  verify(actor,never()).resolving(any());
  verify(actor,times(11)).coreObservable(any());
 }
 @Test void qualityCatalogWithOnlyExactQualityAndSubstantialsSkipsGeneralization() {
  var scope=mock(ContextScope.class);var actor=mock(Reasoner.class);when(scope.getService(Reasoner.class)).thenReturn(actor);
  when(actor.coreObservable(any())).thenAnswer(call->call.getArgument(0,Semantics.class).asConcept());
  var quality=new ConceptImpl();quality.setUrn("geography:Elevation");quality.setType(EnumSet.of(SemanticType.QUALITY));
  var requested=ObservableImpl.promote(quality,null);
  var filter=ResolutionCompiler.candidateCoreFilter(requested,scope);
  for(var type:List.of(SemanticType.SUBJECT,SemanticType.PROCESS,SemanticType.QUALITY)) {
   var concept=new ConceptImpl();concept.setUrn(type==SemanticType.QUALITY?quality.getUrn():"test:"+type);concept.setType(EnumSet.of(type));
   var model=mock(Model.class);when(model.getObservables()).thenReturn(List.of(ObservableImpl.promote(concept,null)));
   assertEquals(type==SemanticType.QUALITY,filter.test(model));
  }
  verify(actor,never()).resolving(any());
 }
 @Test void generalizedCandidatesAndSubstantialSubtypesStillExpandOnce() {
  for(var types:List.of(List.of(SemanticType.QUALITY,SemanticType.QUALITY),
      List.of(SemanticType.AGENT,SemanticType.SUBJECT),List.of(SemanticType.QUALITY,SemanticType.DOMAIN))) {
   var scope=mock(ContextScope.class);var actor=mock(Reasoner.class);when(scope.getService(Reasoner.class)).thenReturn(actor);
   when(actor.coreObservable(any())).thenAnswer(call->call.getArgument(0,Semantics.class).asConcept());
   var leaf=new ConceptImpl();leaf.setUrn("test:Leaf");leaf.setType(EnumSet.of(types.getFirst()));
   var general=new ConceptImpl();general.setUrn("test:General");general.setType(EnumSet.of(types.getLast()));
   when(actor.resolving(leaf)).thenReturn(List.of(leaf,general));
   var filter=ResolutionCompiler.candidateCoreFilter(ObservableImpl.promote(leaf,null),scope);
   var model=mock(Model.class);when(model.getObservables()).thenReturn(List.of(ObservableImpl.promote(general,null)));
   for(int i=0;i<3;i++)assertTrue(filter.test(model));
   verify(actor,times(1)).resolving(leaf);
  }
 }
 @org.junit.jupiter.api.BeforeAll static void setup() {
  org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
 }
 @Test void conceptOnlyRequestMapsPublicSourcesAgainstCommittedActorWithoutGlobalUrnCache() {
  var scope=mock(ContextScope.class);var actor=mock(Reasoner.class);when(scope.getService(Reasoner.class)).thenReturn(actor);
  var meaning=new ConceptImpl();meaning.setUrn("geography:Elevation");meaning.setType(EnumSet.of(SemanticType.QUALITY));
  var observable=ObservableImpl.promote(meaning,null);
  when(actor.resolveObservable("geography:Elevation")).thenReturn(observable);
  when(actor.coreObservable(any())).thenReturn(meaning);when(actor.resolving(meaning)).thenReturn(List.of(meaning));
  var namespace=new KimNamespaceImpl();namespace.setUrn("supply");namespace.setProjectName("project");namespace.setScope(KlabStatement.Scope.PUBLIC);
  namespace.setSourceCode("namespace supply; model geography:Elevation named elevation;");
  var syntax=new KimModelImpl();syntax.setUrn("supply.elevation");syntax.setNamespace("supply");syntax.setProjectName("project");syntax.setScope(KlabStatement.Scope.PUBLIC);
  var output=new KimObservableImpl();output.setUrn("geography:Elevation");syntax.getObservables().add(output);namespace.getStatements().add(syntax);
  var response=ModelCandidateSources.capture(List.of(namespace),ModelCandidateRequest.from(null),"provider",null);
  var resolver=mock(ResolverService.class,CALLS_REAL_METHODS);
  var models=resolver.ingestResources(response,scope,Model.class,true);assertEquals(1,models.size(),()->response.getNotifications().toString());
  var mapped=models.getFirst();assertEquals("supply.elevation",mapped.getUrn());
  assertTrue(ResolutionCompiler.candidateCoreMatches(mapped,observable,scope));
  assertEquals("geography:Elevation",mapped.getObservables().getFirst().getUrn());
  var semantic=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding(
      org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding.VERSION,"reasoner","session",3,"fingerprint","lease");
  when(scope.getResolutionCycleBinding()).thenReturn(
      new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
          1,"cycle","context","runtime",true,semantic));
  clearInvocations(actor);
  try(var session=ModelCandidateSources.openSession()) {
   var first=resolver.ingestResources(response,scope,Model.class,true).getFirst();
   first.getObservables().clear();first.getMetadata().put("consumer","mutated");
   var second=resolver.ingestResources(response,scope,Model.class,true).getFirst();
   assertEquals("geography:Elevation",second.getObservables().getFirst().getUrn());
   assertFalse(second.getMetadata().containsKey("consumer"));
   assertNotNull(second.getCoverage());
   assertNotNull(ModelCandidateSources.read(second));
   verify(actor,times(1)).resolveObservable("geography:Elevation");
  }
  namespace.setLastUpdateTimestamp(2);namespace.setSourceCode("changed source");
  var later=ModelCandidateSources.capture(List.of(namespace),ModelCandidateRequest.from(null),"provider",null);
  var remapped=resolver.ingestResources(later,scope,Model.class,true).getFirst();
  assertNotSame(mapped,remapped);assertEquals(0,ModelCandidateSources.read(mapped).getLastUpdateTimestamp());
  assertEquals(2,ModelCandidateSources.read(remapped).getLastUpdateTimestamp());
  verify(scope,never()).getService(RuntimeService.class);
  verify(scope,never()).getService(ResourcesService.class);
 }
 @Test void failedSupplierCannotBecomeOptionalNoModelButCompleteEmptyCatalogCan() {
  var scope=mock(ContextScope.class);var resources=mock(ResourcesService.class);when(scope.getService(ResourcesService.class)).thenReturn(resources);
  var resolver=mock(ResolverService.class);when(resolver.getServiceConfiguration()).thenReturn(new org.integratedmodelling.klab.services.configuration.ResolverConfiguration());
  when(resolver.ingestResources(any(),same(scope),eq(Model.class),eq(true))).thenReturn(List.of());
  var semantic=new ConceptImpl();semantic.setUrn("test:Quality");semantic.setType(EnumSet.of(SemanticType.QUALITY));
  var requested=ObservableImpl.promote(semantic,null);requested.setOptional(true);
  var scale=GeometryRepository.INSTANCE.scale(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));
  var diagnostic=org.integratedmodelling.klab.api.services.runtime.Notification.warning(ModelCandidateSources.INCOMPLETE+": remote");
  when(resources.query(any(),eq(KlabAsset.KnowledgeClass.MODEL),eq(ResourceSet.class),same(scope))).thenReturn(List.of(ResourceSet.empty(diagnostic)));
  var incomplete=new ResolutionCompiler(resolver);assertTrue(incomplete.queryModels(requested,null,scope,scale).isEmpty());
  assertTrue(incomplete.isDiscoveryIncomplete());assertFalse(incomplete.hasNoExplanatoryModel());
  var deferred=ResolverService.incompleteDiscovery(incomplete.discoveryNotifications());
  var transported=org.integratedmodelling.common.utils.Utils.Json.parseObject(
      org.integratedmodelling.common.utils.Utils.Json.asString(deferred),org.integratedmodelling.klab.api.services.runtime.Dataflow.class);
  assertEquals(org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.DEFERRED,transported.getResolutionOutcome());
  when(resources.query(any(),eq(KlabAsset.KnowledgeClass.MODEL),eq(ResourceSet.class),same(scope))).thenReturn(List.of(ResourceSet.empty()));
  var complete=new ResolutionCompiler(resolver);assertTrue(complete.queryModels(requested,null,scope,scale).isEmpty());
  assertFalse(complete.isDiscoveryIncomplete());assertTrue(complete.hasNoExplanatoryModel());
 }
 @Test void localSpatialScaleBandIsCheckedBeforePrerequisitePreparation() {
  var model=new ModelImpl();model.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_MIN_SPATIAL_SCALE,11);
  var requested=mock(org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.class);
  var space=mock(org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space.class);when(space.getRank()).thenReturn(10);when(requested.getSpace()).thenReturn(space);
  assertFalse(ResolutionCompiler.candidateSpaceMatches(model,requested));
  model.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_MIN_SPATIAL_SCALE,0);
  assertTrue(ResolutionCompiler.candidateSpaceMatches(model,requested));
 }

}
