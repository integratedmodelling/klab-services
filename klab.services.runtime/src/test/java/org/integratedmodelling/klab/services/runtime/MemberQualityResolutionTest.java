package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.*;
import org.junit.jupiter.api.*;

class MemberQualityResolutionTest {
  @BeforeAll static void configure() { org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators(); }
  org.integratedmodelling.klab.services.scopes.ServiceContextScope scope; Reasoner reasoner; Reasoner.Capabilities caps;
  org.integratedmodelling.klab.api.services.RuntimeService runtime;
  ObservationImpl region,one,two,query; Observable quality,members; MemberQualityRequest request;
  Geometry support;
  @BeforeEach void setup() {
    support=Geometry.create("t0(1){tstart=0,tend=10,ttype=PHYSICAL}");
    scope=mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);reasoner=mock(Reasoner.class);caps=mock(Reasoner.Capabilities.class);
    runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(reasoner.capabilities(scope)).thenReturn(caps);
    when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(7L);
    region=observation(10,"test:Region",SemanticType.SUBJECT);one=observation(11,"test:Stream",SemanticType.SUBJECT);
    two=observation(12,"test:Stream",SemanticType.SUBJECT);query=observation(0,"each test:Stream",SemanticType.SUBJECT);
    members=query.getObservable();((ConceptImpl)members.getSemantics()).setCollective(true);
    quality=observation(30,"test:Gradient",SemanticType.QUALITY).getObservable();
    ((ConceptImpl)quality.getSemantics()).setDescriptionType(Contextualization.QUANTIFICATION);
    ((ObservableImpl)quality).setDescriptionType(Contextualization.QUANTIFICATION);
    when(scope.getContextObservation()).thenReturn(region);
    for(var o:List.of(region,one,two))when(scope.getObservation(o.getId())).thenReturn(o);
    when(reasoner.resolveObservable(members.getUrn())).thenReturn(members);
    when(reasoner.resolveObservable(quality.getUrn())).thenReturn(quality);
    when(reasoner.inherent(quality)).thenReturn(one.getObservable().getSemantics());
    when(reasoner.is(any(),any())).thenReturn(true);
    when(runtime.getMembers(query,scope)).thenReturn(List.of(one,two));
    for(var member:List.of(one,two)) {
      var child=mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);when(scope.within(member)).thenReturn(child);
      when(child.getContextObservation()).thenReturn(member);
      when(runtime.submit(any(),eq(child))).thenAnswer(call->{
        var result=observation(member.getId()+100,"test:Gradient",SemanticType.QUALITY);
        result.setObservable(quality);result.setParentId(member.getId());
        return CompletableFuture.completedFuture(result);
      });
    }
    request=new MemberQualityRequest(new SingularContextBinding(10,region.getUrn(),region.getObservable().getUrn(),
        Geometry.forTransport(support).encode(),quality.getUrn(),"reasoner",7),members.getUrn(),quality.getUrn());
  }
  ObservationImpl observation(long id,String name,SemanticType type) {
    var concept=new ConceptImpl();concept.setUrn(name);concept.setName(name);concept.setReferenceName(name.replace(':','_').replace(' ','_'));concept.setType(EnumSet.of(type));
    var observable=new ObservableImpl();observable.setUrn(name);observable.setSemantics(concept);
    var value=new ObservationImpl();value.setId(id);value.setUrn("observation:"+id);value.setObservable(observable);
    value.setGeometry(support);value.setEventTimestamps(List.of(0L));value.setResolvedCoverage(1);return value;
  }
  CompletableFuture<MemberQualityCollection> resolve() {
    return MemberQualityResolution.resolve(runtime,request,scope,(observable,view)->query);
  }
  @Test void nativeResultsRemainSeparateAndKeyedByActualMember() {
    var view=resolve().join();assertEquals(2,view.getEntries().size());
    assertEquals(List.of(11L,12L),view.getEntries().stream().map(e->e.getMember().getId()).toList());
    for(var entry:view.getEntries())assertEquals(entry.getMember().getId(),entry.getQuality().getParentId());
    var firstScope=scope.within(one);var secondScope=scope.within(two);
    verify(runtime).submit(any(),eq(firstScope));verify(runtime).submit(any(),eq(secondScope));
    assertEquals(10,scope.getContextObservation().getId());
  }
  @Test void completeEmptyDiscoveryYieldsEmptyView() {
    when(runtime.getMembers(query,scope)).thenReturn(List.of());assertTrue(resolve().join().getEntries().isEmpty());
    verify(runtime,never()).submit(any(),any());
  }
  @Test void failedDiscoveryIsNotEmptySuccess() {
    query.setEmpty(true);assertThrows(Exception.class,()->resolve().join());verify(runtime,never()).submit(any(),any());
  }
  @Test void partialDiscoveryRejectedBeforeNativeComputation() {
    query.setResolvedCoverage(.5);assertThrows(Exception.class,()->resolve().join());verify(runtime,never()).submit(any(),any());
  }
  @Test void inaccessibleMemberRejectedBeforeAnyComputation() {
    when(scope.getObservation(12)).thenReturn(null);assertThrows(Exception.class,()->resolve().join());
    verify(runtime,never()).submit(any(),any());
  }
  @Test void changedRevisionRejectedBeforeDiscovery() {
    when(caps.getKnowledgeRevision()).thenReturn(8L);assertThrows(Exception.class,()->resolve().join());verifyNoInteractions(runtime);
  }
  @Test void wrongNativeResultParentRejected() {
    var childScope=scope.within(one);
    when(runtime.submit(any(),eq(childScope))).thenReturn(CompletableFuture.completedFuture(
        observation(111,"test:Gradient",SemanticType.QUALITY)));
    assertThrows(Exception.class,()->resolve().join());
  }
  @Test void unrelatedNativeBearerRejected() {
    when(reasoner.is(any(),any())).thenReturn(false);assertThrows(Exception.class,()->resolve().join());verifyNoInteractions(runtime);
  }
  @Test void cancellingPendingMemberCancelsChildAndNeverSchedulesNextMember() {
    var childScope=scope.within(one);var pending=new CompletableFuture<Observation>();
    when(runtime.submit(any(),eq(childScope))).thenReturn(pending);
    var task=resolve();assertFalse(task.isDone());task.cancel(true);
    assertTrue(pending.isCancelled());assertTrue(task.isCancelled());
    var secondScope=scope.within(two);verify(runtime,never()).submit(any(),eq(secondScope));
  }
  @Test void publicWrapperKeepsInvocationRegionWhileDiscoveryUsesOrdinarySubmit() {
    var service=mock(RuntimeService.class,CALLS_REAL_METHODS);
    doReturn(CompletableFuture.completedFuture(query)).when(service).submit(any(),eq(scope));
    doReturn(query).when(service).queryCollective(any(),eq(scope),isNull(),any(MemberQualityCollection.class));
    doReturn(List.of()).when(service).getMembers(any(),eq(scope));
    var view=service.resolveMemberQualities(request,scope).join();
    assertTrue(view.getEntries().isEmpty());assertEquals(10,view.getRequest().container().contextId());
    verify(scope,never()).within(isNull());
    verify(service).submit(any(),eq(scope));
  }
  @Test void invalidPublicRequestCannotStartDiscovery() {
    var service=mock(RuntimeService.class,CALLS_REAL_METHODS);
    when(reasoner.is(any(),any())).thenReturn(false);
    assertThrows(Exception.class,()->service.resolveMemberQualities(request,scope).join());
    verify(service,never()).submit(any(),any());
  }
  @Test void publicCancellationCancelsDiscoveryAndNeverStartsMembers() {
    var service=mock(RuntimeService.class,CALLS_REAL_METHODS);var pending=new CompletableFuture<Observation>();
    doReturn(pending).when(service).submit(any(),eq(scope));
    var task=service.resolveMemberQualities(request,scope);assertFalse(task.isDone());task.cancel(true);
    assertTrue(pending.isCancelled());verify(service,never()).getMembers(any(),any());
  }
  @Test void implicitNativeRequestRetainsCompatibleSpecificResultsAndAbsoluteFallback() {
    var observer=observation(50,"test:Observer",SemanticType.AGENT);when(scope.getObserver()).thenReturn(observer);
    when(reasoner.semanticDistance(any(),any(),isNull(),eq(7L))).thenReturn(0);
    var specific=ObservableImpl.withObserverSemantics(quality,observer.getObservable().asConcept());
    var firstScope=scope.within(one);
    var result=observation(111,"test:Gradient",SemanticType.QUALITY);result.setObservable(specific);result.setParentId(one.getId());
    when(runtime.submit(any(),eq(firstScope))).thenReturn(CompletableFuture.completedFuture(result));
    var view=resolve().join();
    assertEquals(specific.getUrn(),view.getEntries().getFirst().getQuality().getObservable().getUrn());
    assertEquals(quality.getUrn(),view.getEntries().get(1).getQuality().getObservable().getUrn());
    assertEquals(50L,view.getObserverId());
    for(var entry:view.getEntries())when(scope.getObservation(entry.getQuality().getId())).thenReturn(entry.getQuality());
    assertDoesNotThrow(()->MemberQualityResolution.requireForConsumption(view,scope));
    when(reasoner.semanticDistance(any(),any(),isNull(),eq(7L))).thenReturn(-1);
    assertThrows(IllegalStateException.class,()->MemberQualityResolution.requireForConsumption(view,scope));
    assertThrows(Exception.class,()->resolve().join());
  }
  @Test void consumedNativeResultMustStillBeCompleteAndMemberTypeMustStillMatch() {
    var view=resolve().join();
    for(var entry:view.getEntries())when(scope.getObservation(entry.getQuality().getId())).thenReturn(entry.getQuality());
    var first=(ObservationImpl)view.getEntries().getFirst().getQuality();first.setResolvedCoverage(Double.NaN);
    assertThrows(IllegalStateException.class,()->MemberQualityResolution.requireForConsumption(view,scope));
    first.setResolvedCoverage(1);when(reasoner.is(one.getObservable(),members.getSemantics().singular())).thenReturn(false);
    assertThrows(IllegalStateException.class,()->MemberQualityResolution.requireForConsumption(view,scope));
  }
  @Test void canonicalSupportComparisonPreservesActualBoundsAndDistribution() {
    var subset=Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=4000}");
    var container=Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}");
    assertTrue(MemberQualityResolution.sameSupport(subset,RuntimeService.intersection(subset,container)));
    assertFalse(MemberQualityResolution.sameSupport(subset,container));
    assertFalse(MemberQualityResolution.sameSupport(subset,Geometry.create("T1(3){ttype=GRID,tstart=1000,tend=4000}")));
    assertTrue(MemberQualityResolution.sameSupport(subset,Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=4000,tscope=3,tunit=SECOND}")));
    assertFalse(MemberQualityResolution.sameSupport(subset,Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=4000,tscope=1,tunit=SECOND}")));
  }
  @Test void supportNormalizationCannotEraseExplicitCoverageOrOtherTemporalMetadata() {
    var plain=Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=4000}");
    var covered=Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=4000,coverageunit=SECOND,coveragestart=1,coverageend=4}");
    var other=Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=4000,coverageunit=SECOND,coveragestart=2,coverageend=4}");
    assertTrue(MemberQualityResolution.sameSupport(covered,covered));
    assertFalse(MemberQualityResolution.sameSupport(covered,plain));
    assertFalse(MemberQualityResolution.sameSupport(plain,covered));
    assertFalse(MemberQualityResolution.sameSupport(covered,other));
  }
}
