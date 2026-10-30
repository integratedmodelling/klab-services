package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.klab.api.collections.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.resources.*;
import org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection;
import org.integratedmodelling.klab.services.configuration.ResolverConfiguration;
import org.junit.jupiter.api.*;

/** Actual query/preparation/admission/prioritizer order; component installation is controlled I/O. */
class ContextualizerPreparationOrderTest {
 @BeforeAll static void configure(){CollectiveReductionPlanningTest.configure();}
 final CollectiveReductionPlanningTest f=new CollectiveReductionPlanningTest();
 final ResourcesService resources=mock(ResourcesService.class);
 final ResolverService resolver=mock(ResolverService.class);
 final ResolutionCompiler compiler=spy(new ResolutionCompiler(resolver));
 final org.integratedmodelling.klab.api.services.RuntimeService runtime=f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
 final KimNamespaceImpl namespace=new KimNamespaceImpl();
 boolean installed;
 ContextualizerPreparationOrderTest(){
  when(f.model.getObservables()).thenReturn(List.of(f.target));
  var code=new ContextualizableImpl();code.setServiceCall(new ServiceCallImpl("fixture.on.demand"));when(f.model.getComputation()).thenReturn(List.of(code));
  namespace.setUrn("test");namespace.setSourceCode("fixture saved ordinary source");
  var declaration=new KimModelImpl();declaration.setUrn(f.model.getUrn());declaration.setNamespace("test");namespace.getStatements().add(declaration);
  when(f.scope.getService(ResourcesService.class)).thenReturn(resources);
  when(resources.retrieve(eq("test"),eq(KimNamespace.class),any())).thenReturn(namespace);
  when(resources.declareObservable(anyString())).thenReturn(new KimObservableImpl());
  when(f.reasoner.validateDocument(any(),any())).thenAnswer(call->{
   var response=SemanticValidationResponse.forRequest(call.getArgument(0));response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);return response;});
  when(runtime.inspectMemberQualityInputs(any(),any())).thenAnswer(call->installed?MemberQualityConsumerInspection.known(Set.of()):MemberQualityConsumerInspection.unavailable());
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenAnswer(call->{installed=true;return new ResourceSet();});
  var config=new ResolverConfiguration();config.setRankingStrategy(Map.of("im:semantic-concordance",1));when(resolver.getServiceConfiguration()).thenReturn(config);
  when(resources.query(any(Parameters.class),eq(org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.MODEL),eq(ResourceSet.class),eq(f.scope))).thenReturn(List.of(new ResourceSet()));
  doReturn(List.of(f.model)).when(resolver).ingestResources(any(ResourceSet.class),eq(f.scope),eq(org.integratedmodelling.klab.api.knowledge.Model.class),eq(true));
  when(f.reasoner.semanticDistance(any(),any(),any(),anyLong())).thenAnswer(call->{assertTrue(installed,"No distance before real prerequisite preparation");return 0;});
 }
 @Test void ordinaryOnDemandPreparationPrecedesInstalledAdmissionAndDistanceAndIsReused(){
  assertEquals(List.of(f.model),compiler.queryModels(f.target,f.context.getObservable().asConcept(),f.scope,f.scale));
  assertNotNull(compiler.admissionReceipt(f.model));
  assertEquals(List.of(f.model),compiler.queryModels(f.target,f.context.getObservable().asConcept(),f.scope,f.scale));
  verify(runtime,times(1)).resolveContextualizables(anyList(),eq(f.scope));
  verify(resources,atLeastOnce()).query(argThat(p->ModelDiscoveryMode.PREPARABLE.name().equals(p.get(ModelDiscoveryMode.PARAMETER))),any(),any(),any());
  doReturn(List.of(CollectiveReductionPlanningTest.flat(f.target))).when(f.reasoner).computeObservationStrategies(f.request,f.scope);
  doReturn(new ResolutionCompiler.QueryMatch(null,null,f.scale,null,org.integratedmodelling.klab.api.services.resolver.Coverage.create(f.scale,0))).when(compiler).query(any(),any(),eq(f.scope));
  assertFalse(compiler.resolve(f.request,f.scope).isEmpty());
  verify(runtime,times(1)).resolveContextualizables(anyList(),eq(f.scope));
 }
 @Test void disabledOrFailedPreparationNeverReachesDistance(){
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenReturn(ResourceSet.empty());
  assertTrue(compiler.queryModels(f.target,null,f.scope,f.scale).isEmpty());
  verify(f.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
 }
 @Test void successfulPreparationWithoutInspectableFunctionNeverRanks(){
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenReturn(new ResourceSet());
  assertTrue(compiler.queryModels(f.target,null,f.scope,f.scale).isEmpty());
  verify(f.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
 }
 @Test void sourceDriftDuringPreparationDoesNotGetRestamped(){
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenAnswer(call->{installed=true;namespace.setSourceCode("changed");return new ResourceSet();});
  assertTrue(compiler.queryModels(f.target,null,f.scope,f.scale).isEmpty());
  verify(f.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
 }
 @Test void executableDriftDuringPreparationDoesNotGetRestamped(){
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenAnswer(call->{installed=true;when(f.model.getProjectName()).thenReturn("changed");return new ResourceSet();});
  assertTrue(compiler.queryModels(f.target,null,f.scope,f.scale).isEmpty());
  verify(f.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
 }
 @Test void changedCachedSourceFailsClosedWithoutRepeatedPreparation(){
  assertTrue(compiler.prepareCandidate(f.model,f.target,f.scope));namespace.setSourceCode("later source");
  assertFalse(compiler.prepareCandidate(f.model,f.target,f.scope));
  verify(runtime,times(1)).resolveContextualizables(anyList(),eq(f.scope));
 }
 @Test void typedSignatureWithoutExactSourceOccurrenceIsRejectedAfterPreparationBeforeDistance(){
  when(runtime.inspectMemberQualityInputs(any(),any())).thenAnswer(call->installed?MemberQualityConsumerInspection.known(Set.of("gradients")):MemberQualityConsumerInspection.unavailable());
  assertTrue(compiler.queryModels(f.target,null,f.scope,f.scale).isEmpty());
  verify(runtime).resolveContextualizables(anyList(),eq(f.scope));
  verify(f.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
 }
 @Test void reasonerGenerationDriftDuringPreparationNeverRanks(){
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenAnswer(call->{installed=true;when(f.capabilities.getKnowledgeRevision()).thenReturn(4L);return new ResourceSet();});
  assertTrue(compiler.queryModels(f.target,null,f.scope,f.scale).isEmpty());
  verify(f.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
 }

 @Test @Timeout(20) void concurrentSameInvocationPreparesOnce() throws Exception {
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenAnswer(call->{entered.countDown();assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));installed=true;return new ResourceSet();});
  try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
   var one=executor.submit(()->compiler.prepareCandidate(f.model,f.target,f.scope));
   assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
   var two=executor.submit(()->compiler.prepareCandidate(f.model,f.target,f.scope));release.countDown();
   assertTrue(one.get(10,java.util.concurrent.TimeUnit.SECONDS));assertTrue(two.get(10,java.util.concurrent.TimeUnit.SECONDS));
  } finally {release.countDown();}
  verify(runtime,times(1)).resolveContextualizables(anyList(),eq(f.scope));
 }
 @Test void changedContextOrObserverCannotReuseEarlierPreparation(){
  assertTrue(compiler.prepareCandidate(f.model,f.target,f.scope));
  f.context.setUrn("changed-context");assertTrue(compiler.prepareCandidate(f.model,f.target,f.scope));
  var observer=f.observation(42,"observer:42",CollectiveReductionPlanningTest.observable("test:Person",org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT));
  when(f.scope.getObserver()).thenReturn(observer);assertTrue(compiler.prepareCandidate(f.model,f.target,f.scope));
  observer.setUrn("changed-observer");assertTrue(compiler.prepareCandidate(f.model,f.target,f.scope));
  verify(runtime,times(4)).resolveContextualizables(anyList(),eq(f.scope));
 }

 @Test void explainedSubstantialUsesItsOwnPreparedComputationScopeWithoutBorrowingContextProof(){
  var subject=CollectiveReductionPlanningTest.observable("test:Tree",org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT,org.integratedmodelling.klab.api.knowledge.SemanticType.COUNTABLE);
  f.request.setObservable(subject);when(f.model.getObservables()).thenReturn(List.of(subject));
  var computation=mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
  when(f.scope.within(f.request)).thenReturn(computation);
  when(computation.getContextObservation()).thenReturn(f.request);
  when(computation.getService(Reasoner.class)).thenReturn(f.reasoner);
  when(computation.getService(ResourcesService.class)).thenReturn(resources);
  when(computation.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
  when(computation.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(computation);
  when(f.reasoner.capabilities(computation)).thenReturn(f.capabilities);
  var selectedRequirements=new ResourceSet();var nativeRequirements=new ResourceSet();
  when(runtime.resolveContextualizables(anyList(),eq(f.scope))).thenAnswer(call->{installed=true;return selectedRequirements;});
  when(runtime.resolveContextualizables(anyList(),eq(computation))).thenReturn(nativeRequirements);
  doReturn(List.of(CollectiveReductionPlanningTest.flat(subject))).when(f.reasoner).computeObservationStrategies(f.request,f.scope);
  doReturn(new ResolutionCompiler.QueryMatch(null,null,f.scale,null,org.integratedmodelling.klab.api.services.resolver.Coverage.create(f.scale,0))).when(compiler).query(any(),any(),eq(f.scope));
  assertFalse(compiler.resolve(f.request,f.scope).isEmpty());
  verify(runtime,times(1)).resolveContextualizables(anyList(),eq(f.scope));
  verify(runtime,times(1)).resolveContextualizables(anyList(),eq(computation));
 }

}
