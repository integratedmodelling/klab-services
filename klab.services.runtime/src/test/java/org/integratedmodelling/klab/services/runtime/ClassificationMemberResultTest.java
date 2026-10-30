package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.ClassificationMemberResult;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.junit.jupiter.api.Test;

class ClassificationMemberResultTest {
  @org.junit.jupiter.api.BeforeAll static void configureScaleFactory() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  private MemberClassifierExecutor.DetailedResult run(MemberClassifierExecutorTest.Fixture f,MemberClassifierExecutor e) {
    return e.executeDetailed(Map.of("cohort",List.of(f.member)),Geometry.UNIVERSAL,f.event,f.scope);
  }
  @Test void realInvocationReportsFreshAndReusedMembersButStagesOnlyFresh() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();
    when(f.reasoner.directTraits(f.member.getObservable())).thenReturn(List.of(f.result));
    var fresh=new ObservationImpl();fresh.setId(43);fresh.setObservable(mock(Observable.class));
    var detailed=f.executor().executeDetailed(Map.of("cohort",List.of(f.member,fresh)),Geometry.UNIVERSAL,f.event,f.scope);
    assertEquals(List.of(ClassificationMemberResult.Origin.EXISTING,ClassificationMemberResult.Origin.CLASSIFIER),
        detailed.members().stream().map(ClassificationMemberResult::origin).toList());
    assertEquals(List.of("test:Concrete"),detailed.members().getFirst().concretePredicates());
    assertEquals(1,detailed.pendingAttributions().size());assertSame(fresh,detailed.pendingAttributions().getFirst().member());
    assertEquals(1,f.classifier.calls.get());assertEquals(-1,detailed.knowledgeRevision(),"Unavailable generation is explicit and unproven");
  }
  @Test void everyValidExistingPredicateIsRetainedWithoutReinvocationOrCharacterization() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();var other=mock(Concept.class);
    when(other.getUrn()).thenReturn("test:OtherConcrete");when(other.is(SemanticType.PREDICATE)).thenReturn(true);
    when(f.reasoner.is(other,f.predicate)).thenReturn(true);
    when(f.reasoner.directTraits(f.member.getObservable())).thenReturn(List.of(f.result));
    when(f.reasoner.directRoles(f.member.getObservable())).thenReturn(List.of(other,f.result));
    var result=run(f,f.executor());assertEquals(List.of("test:Concrete","test:OtherConcrete"),result.members().getFirst().concretePredicates());
    assertTrue(result.pendingAttributions().isEmpty());assertEquals(0,f.classifier.calls.get());
  }
  @Test void optionalAbsenceEmptyMembershipAndMissingProducerRemainDifferent() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();f.optionalDependency();f.classifier.result=null;var executor=f.executor();
    var result=run(f,executor);assertEquals(1,result.members().size());
    assertEquals(ClassificationMemberResult.Origin.OPTIONAL_ABSENCE,result.members().getFirst().origin());
    assertTrue(result.members().getFirst().concretePredicates().isEmpty());assertTrue(result.pendingAttributions().isEmpty());
    assertTrue(executor.executeDetailed(Map.of("cohort",List.of()),Geometry.UNIVERSAL,f.event,f.scope).members().isEmpty());
    assertThrows(IllegalStateException.class,()->executor.executeDetailed(Map.of(),Geometry.UNIVERSAL,f.event,f.scope));
  }
  @Test void inputSnapshotChangeCannotReusePriorInvocationEvidence() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();when(f.member.getObservable().getUrn()).thenReturn("test:Before");var executor=f.executor();
    var fresh=run(f,executor);assertEquals("test:Before",fresh.members().getFirst().inputObservable());
    when(f.member.getObservable().getUrn()).thenReturn("test:After");when(f.reasoner.directTraits(f.member.getObservable())).thenReturn(List.of(f.result));
    var reused=run(f,executor);assertEquals("test:After",reused.members().getFirst().inputObservable());
    assertEquals(ClassificationMemberResult.Origin.EXISTING,reused.members().getFirst().origin());assertTrue(reused.pendingAttributions().isEmpty());
    assertEquals(1,f.classifier.calls.get());assertEquals("test:Before",fresh.members().getFirst().inputObservable());
  }
  @Test void generationDriftPreventsReturningACompleteMemberBatch() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();var revision=new AtomicLong(3);
    var caps=mock(Reasoner.Capabilities.class);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenAnswer(i->revision.get());
    when(f.reasoner.capabilities(any())).thenReturn(caps);
    var contextualizer=new GenerationChangingClassifier(revision,f.result);
    var executor=new MemberClassifierExecutor(f.actuator,GenerationChangingClassifier.class.getMethod("classify",Observable.class,Scope.class),contextualizer,f.scope);
    assertThrows(IllegalStateException.class,()->run(f,executor));assertEquals(4,revision.get());
    assertThrows(IllegalStateException.class,()->run(f,executor));
  }
  public static class GenerationChangingClassifier {
    final AtomicLong revision;final Concept result;
    GenerationChangingClassifier(AtomicLong revision,Concept result){this.revision=revision;this.result=result;}
    public Concept classify(Observable observable,Scope scope){revision.incrementAndGet();return result;}
  }
  @Test void cancellationAndFailedParentCannotBecomeEmptySuccessfulResults() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();var executor=f.executor();when(f.scope.isInterrupted()).thenReturn(true);
    assertThrows(CancellationException.class,()->run(f,executor));assertEquals(0,f.classifier.calls.get());
    when(f.scope.isInterrupted()).thenReturn(false);var tx=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction.class);
    when(f.scope.getCurrentTransaction()).thenReturn(tx);when(tx.isFailed()).thenReturn(true);
    assertThrows(IllegalStateException.class,()->run(f,executor));assertEquals(0,f.classifier.calls.get());
  }
  @Test void actualClassifierCancellationPreservesCancellationIdentity() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();var executor=new MemberClassifierExecutor(f.actuator,
        CancellingClassifier.class.getMethod("classify",Observable.class,Scope.class),new CancellingClassifier(),f.scope);
    assertThrows(CancellationException.class,()->run(f,executor));
  }
  public static class CancellingClassifier {
    public Concept classify(Observable observable,Scope scope){throw new CancellationException("cancelled classifier");}
  }
  @Test void returnedResultsAreImmutablePortableInvocationFactsWithTransientIdentity() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();f.member.setId(-4);f.member.setUrn("tentative");when(f.member.getObservable().getUrn()).thenReturn("test:Input");
    var result=run(f,f.executor()).members().getFirst();
    var mapper=JacksonConfiguration.newObjectMapper();var restored=mapper.readValue(mapper.writeValueAsString(result),ClassificationMemberResult.class);
    assertEquals(result,restored);assertEquals(-4,restored.memberIdAtInvocation());assertEquals(f.member.getTransientId(),restored.memberTransientId());
    assertThrows(UnsupportedOperationException.class,()->result.concretePredicates().clear());
    f.member.setId(99);when(f.result.getUrn()).thenReturn("changed");assertEquals(-4,result.memberIdAtInvocation());assertEquals(List.of("test:Concrete"),result.concretePredicates());
  }
  @Test void ordinaryObservationRegistrationCannotPretendToBeAClassifierContinuation() {
    var f=new MemberClassifierExecutorTest.Fixture();var request=new ObservationImpl();request.setObservable(f.observable);
    var runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);
    var failure=assertThrows(IllegalArgumentException.class,()->runtime.register(request,f.scope));
    assertTrue(failure.getMessage().contains("Dataflow operations"));
    verify(f.scope,never()).observation(any());
  }
  @Test void spatialSupportEvidenceUsesPortableSanitizedGeometry() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();
    var geometry=Geometry.create(org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
        "EPSG:4326 POLYGON ((0 0, 2 0, 2 2, 0 2, 0 0))").encode());
    var result=f.executor().executeDetailed(Map.of("cohort",List.of(f.member)),geometry,f.event,f.scope).members().getFirst();
    var parsed=Geometry.create(result.support());
    var expected=org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.sanitize(geometry).encode();
    assertEquals(expected,result.support());
    assertFalse(org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(parsed).getSpace().isEmpty());
    assertEquals(expected,org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.sanitize(parsed).encode());
  }
  @Test void lateMemberFailureReturnsNoPartialBatch() throws Exception {
    var f=new MemberClassifierExecutorTest.Fixture();f.classifier.failAt=2;var next=new ObservationImpl();next.setId(43);next.setObservable(mock(Observable.class));
    var executor=f.executor();assertThrows(IllegalStateException.class,()->executor.executeDetailed(Map.of("cohort",List.of(f.member,next)),Geometry.UNIVERSAL,f.event,f.scope));
    assertEquals(2,f.classifier.calls.get());
  }
}
