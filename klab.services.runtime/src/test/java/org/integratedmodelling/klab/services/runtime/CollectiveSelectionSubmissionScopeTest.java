package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** The public submission boundary must preserve selection scope before resolution starts. */
class CollectiveSelectionSubmissionScopeTest {
  @BeforeAll static void configure() { SelectedModelClassifierSubmissionTest.configure(); }

  private ObservationImpl selection(SelectedModelClassifierSubmissionTest.Fixture f, boolean explicitGeometry) {
    f.remember(f.observable(f.semantic.bearer("Concrete")));
    var meaning=f.semantic.bearer("Concrete");meaning.setCollective(true);meaning.resetDefinition();f.semantic.register(meaning);
    var syntax=f.observable(meaning);f.remember(syntax);
    var request=new ObservationImpl();request.setObservable(f.reasoner.declareObservable(syntax));
    if(explicitGeometry)request.setGeometry(f.geometry);
    return request;
  }

  private ServiceContextScope observed(SelectedModelClassifierSubmissionTest.Fixture f, ServiceContextScope base) {
    var observerSyntax=f.semantic.observerVocabulary();f.remember(observerSyntax);
    var observer=new ObservationImpl();observer.setId(73);observer.setUrn("observer:73");
    observer.setObservable(f.reasoner.declareObservable(observerSyntax));
    observer.setGeometry(f.geometry);observer.setPerceivedGeometry(f.geometry);f.durable.put(73L,observer);
    var lookup=mock(KnowledgeGraph.Query.class,RETURNS_SELF);
    when(f.graph.query(eq(Observation.class),any())).thenReturn(lookup);
    when(lookup.run(any())).thenReturn(List.of(observer));
    return base.withObserver(observer);
  }

  private void nativeMaterialization(SelectedModelClassifierSubmissionTest.Fixture f) {
    doCallRealMethod().when(f.graph).materializeClassificationMember(any(),any());
  }

  @Test void explicitSelectionGeometryRetainsOriginalNativeFocus() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      var request=selection(f,true);
      assertSame(f.scope,RuntimeService.submissionScope(request,f.scope));
      assertSame(f.region,f.scope.getContextObservation());assertSame(f.geometry,request.getGeometry());
    }
  }

  @Test void inheritedFocusGeometryIsProbedWithoutMutatingTheCaller() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      var request=selection(f,false);
      assertSame(f.geometry,ContextScope.getResolutionGeometry(f.scope));
      assertSame(f.scope,RuntimeService.submissionScope(request,f.scope));
      assertNull(request.getGeometry(),"The eligibility probe is detached from the caller");
      assertSame(f.region,f.scope.getContextObservation());
    }
  }

  @Test void unfocusedObserverGeometryAndSelectionRemainAvailableToNormalSubmission() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      var scope=observed(f,f.scope.within(null));
      var request=selection(f,false);
      assertSame(f.geometry,ContextScope.getResolutionGeometry(scope));
      assertSame(scope,RuntimeService.submissionScope(request,scope));
      assertNull(request.getGeometry());assertEquals(73,scope.getObserver().getId());
    }
  }

  @Test void ordinaryUnqualifiedCollectiveStillResetsIndependentRootFocus() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      var meaning=f.semantic.named("Region");meaning.setCollective(true);f.semantic.register(meaning);
      var syntax=f.observable(meaning);f.remember(syntax);
      var request=new ObservationImpl();request.setObservable(f.reasoner.declareObservable(syntax));
      var scope=RuntimeService.submissionScope(request,f.scope);
      assertNotSame(f.scope,scope);assertNull(scope.getContextObservation());
      assertSame(f.region,f.scope.getContextObservation());assertNull(request.getGeometry());
      request.setId(Observation.QUERY_ID);
      assertSame(f.scope,RuntimeService.submissionScope(request,f.scope),
          "A read-only native query must retain focus rather than enter another root scope");
    }
  }

  @Test void publicSubmitReturnsVirtualReceiptBoundToItsActualFocusedContext() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture();var permit=f.useGatedEmptyProducer()) {
      nativeMaterialization(f);
      var request=selection(f,true);
      var result=f.runtime.submit(request,f.scope).get(45,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      assertEquals(Observation.QUERY_ID,result.getId());assertTrue(result.getObservable().getSemantics().isCollective());
      var encoded=result.getMetadata().get(CollectiveSelectionView.DATA_KEY);
      assertInstanceOf(String.class,encoded);
      var proof=Utils.Json.parseObject(encoded.toString(),CollectiveSelectionView.class);
      assertNotNull(proof.context(),"The public boundary must not erase the native focus");
      assertEquals(CollectiveSelectionBinding.context(f.scope),proof.context());
      assertTrue(proof.selectedIds().contains(f.region.getId()));assertTrue(f.rootCommits.get()>0);
    }
  }

  @Test void invalidSelectionFocusCannotBecomeAnUnrestrictedRootEvenWithExplicitSupport() throws Exception {
    for(int invalid=0;invalid<4;invalid++)try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      var focus=(ObservationImpl)Observation.forTransport(f.region);
      if(invalid==0)focus.setId(Observation.QUERY_ID);
      if(invalid==1)focus.setId(Observation.UNASSIGNED_ID);
      if(invalid==2)focus.setGeometry(null);
      Observation targetFocus=invalid==3?Observation.empty():focus;
      var scope=f.scope.within(targetFocus);var request=selection(f,true);
      assertThrows(IllegalArgumentException.class,()->f.runtime.submit(request,scope));
      assertTrue(f.modelQueries.isEmpty());assertEquals(0,f.rootCommits.get());
      assertEquals(0,f.classifier.discoveries.get());assertSame(f.geometry,request.getGeometry());
    }
  }

  @Test void invalidFocusCannotBeErasedWhenObserverSuppliesTheRequestGeometry() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      var focus=(ObservationImpl)Observation.forTransport(f.region);focus.setGeometry(null);
      var scope=observed(f,f.scope.within(focus));var request=selection(f,false);
      assertSame(f.geometry,ContextScope.getResolutionGeometry(scope));
      assertThrows(IllegalArgumentException.class,()->f.runtime.submit(request,scope));
      assertNull(request.getGeometry());assertTrue(f.modelQueries.isEmpty());assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void missingFocusAndRequestSupportFailsBeforeAnyProviderCanExpandCoverage() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      var focus=(ObservationImpl)Observation.forTransport(f.region);focus.setGeometry(null);
      var scope=f.scope.within(focus);var request=selection(f,false);
      assertNull(ContextScope.getResolutionGeometry(scope));
      assertThrows(IllegalArgumentException.class,()->f.runtime.submit(request,scope));
      assertNull(request.getGeometry());assertTrue(f.modelQueries.isEmpty());assertEquals(0,f.rootCommits.get());
      assertEquals(0,f.classifier.discoveries.get());
    }
  }

  @Test void publicFullObservableTierZeroProducerRetainsPriorityAndRealOwnership() throws Exception {
    try(var f=new SelectedModelClassifierSubmissionTest.Fixture()) {
      nativeMaterialization(f);
      var request=selection(f,true);
      var syntax=f.observables.get(request.getObservable().getUrn());
      assertNotNull(syntax);
      var discovery=new ContextualizableImpl();discovery.setServiceCall(new ServiceCallImpl("fixture.discover"));
      f.model("fullyQualified",syntax,List.of(),discovery);
      var result=f.runtime.submit(request,f.scope).get(45,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      assertTrue(result.getId()>0,"The exact full-observable producer remains a committed observation");
      assertEquals(request.getObservable().getSemantics(),result.getObservable().getSemantics());
      assertNull(result.getMetadata().get(CollectiveSelectionView.DATA_KEY));
      assertEquals(1,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());
      assertEquals(f.region.getId(),result.getParentId());
      verify(f.nativeTx,atLeastOnce()).link(any(Observation.class),eq(f.cohort),eq(GraphModel.Relationship.CONTRIBUTED_TO),any(Object[].class));
    }
  }
}
