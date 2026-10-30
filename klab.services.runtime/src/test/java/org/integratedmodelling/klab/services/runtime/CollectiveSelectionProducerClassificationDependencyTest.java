package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Actual native instantiator with a direct collective CLASSIFICATION dependency. The two
 * native domains are distinct; no already-observed singular Other context is required. */
class CollectiveSelectionProducerClassificationDependencyTest {
  SelectedModelClassifierSubmissionTest.Fixture fixture;
  int otherInvocations;
  boolean failOther;
  boolean driftNativeInput;
  boolean expectExisting;

  @BeforeAll static void configure() { SelectedModelClassifierSubmissionTest.configure(); }

  public void discoverAfterClassification(Data.Builder ignored, ContextScope scope) {
    var current = scope.getCurrentTransaction().readObservation(fixture.region);
    if ((!expectExisting && fixture.classifier.calls.get() == 0) || fixture.reasoner.directTraits(current.getObservable()).stream()
        .noneMatch(predicate -> predicate.getUrn().equals("audit:Concrete")))
      throw new IllegalStateException("Native Other discovery ran before its collective classification dependency");
    otherInvocations++;
    if (driftNativeInput) {
      var before = fixture.persistedClassifierInputs.get(fixture.region.getId());
      if (before == null) throw new IllegalStateException("Direct classification did not capture its native member baseline");
      var properties = new LinkedHashMap<String,Object>(before.properties());
      properties.put("name", "Concurrent native name change");
      fixture.persistedClassifierInputs.put(before.id(), new org.integratedmodelling.klab.api.data.KnowledgeGraph.ClassificationMemberSnapshot(
          before.id(), before.observable(), before.geometry(), before.semanticRevision(), properties, before.perceivedGeometry()));
    }
    if (failOther) throw new IllegalStateException("Deliberate Other discovery failure after classification");
    // A normal completed zero-object discovery; no completion receipt is fabricated by this fixture.
  }

  private void exercise(boolean fail) throws Exception { exercise(fail, false, false); }

  private void exercise(boolean fail, boolean drift, boolean existing) throws Exception {
    try (var f = new SelectedModelClassifierSubmissionTest.Fixture()) {
      fixture = f; failOther = fail; driftNativeInput = drift; expectExisting = existing;
      if (!existing) f.region.setObservable(f.reasoner.declareObservable(f.observable(f.semantic.named("Region"))));
      var before = f.region.getObservable();
      org.mockito.stubbing.Answer<Void> publishSemantics = call -> {
        var changed = call.getArgument(0, Observation.class);
        var expected = call.getArgument(1, String.class);
        var existingNative = f.durable.get(changed.getId());
        if (existingNative == null || !Objects.equals(expected, existingNative.getObservable().getUrn()))
          throw new IllegalStateException("Fixture native semantic compare-and-set failed");
        f.durable.put(changed.getId(), Observation.forTransport(changed));
        return null;
      };
      doAnswer(publishSemantics).when(f.nativeTx).updateSemantics(any(), anyString());
      doAnswer(publishSemantics).when(f.nativeTx).updateSemantics(any(), anyString(), anyInt());
      f.memberReadSet(); // Establish the explicit native database snapshot before the attempt reads it.
      // This direct operation uses normal graph membership enumeration. The older fixture's
      // abstract-substitution tests instead captured the same members through native read sets.
      var observations = mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class, RETURNS_SELF);
      var regionSource = mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class, RETURNS_SELF);
      var regionMembers = mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.class, RETURNS_SELF);
      when(f.graph.query(eq(Observation.class), any())).thenReturn(observations);
      when(observations.source(f.cohort)).thenReturn(regionSource);
      when(regionSource.along(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_MEMBER)).thenReturn(regionMembers);
      when(regionMembers.run(any())).thenAnswer(call -> List.copyOf(f.classificationMembers));
      var other = f.semantic.named("Other"); other.setCollective(true); f.semantic.register(other);
      var nativeSyntax = f.observable(other); f.remember(nativeSyntax);
      var classification = (KimModel) f.sources.values().stream().filter(ns -> ns.getSourceCode().equals("classifier"))
          .findFirst().orElseThrow().getStatements().getFirst();
      var dependency = classification.getObservables().getFirst();
      var computation = new ContextualizableImpl(); computation.setServiceCall(new ServiceCallImpl("fixture.discoverAfter"));
      f.model("dependent_other", nativeSyntax, List.of(dependency), computation);
      var registry = f.runtime.getComponentRegistry();
      var descriptor = new Extensions.FunctionDescriptor(); descriptor.serviceInfo = new ServiceInfoImpl();
      var implementation = new ComponentRegistry.ServiceImplementation(); implementation.mainClassInstance = this;
      implementation.method = getClass().getMethod("discoverAfterClassification", Data.Builder.class, ContextScope.class);
      when(registry.getFunctionDescriptor(org.mockito.ArgumentMatchers.<ServiceCall>argThat(call -> call != null && "fixture.discoverAfter".equals(call.getUrn())), any(org.integratedmodelling.klab.api.scope.Scope.class)))
          .thenReturn(List.of(descriptor));
      when(registry.implementation(descriptor)).thenReturn(implementation);
      var execution = f.scope.executing(Activity.of(Activity.Type.SUBMISSION, null, "Collective classification prerequisite"));
      var transaction = (DigitalTwinImpl.TransactionImpl) execution.getCurrentTransaction();
      var otherCohort = new CohortImpl(); otherCohort.setId(-10000);
      otherCohort.setObservable(f.reasoner.declareObservable(f.observable(f.semantic.named("Other")))); otherCohort.setGeometry(f.geometry);
      transaction.add(otherCohort);
      doAnswer(call -> call.getArgument(0, Semantics.class).asConcept().getUrn().contains("audit:Other")
          ? otherCohort : call.callRealMethod()).when(f.runtime).getCohortFor(any(), any(), anyBoolean());
      var request = new ObservationImpl(); request.setObservable(f.reasoner.declareObservable(nativeSyntax)); request.setGeometry(f.geometry);
      var target = f.runtime.register(request, execution);
      var plan = f.resolver.resolve(target, execution).get(30, TimeUnit.SECONDS);
      assertFalse(plan.isEmpty(), plan.getNotifications()+" "+f.failures());
      plan = SelectedModelClassifierContinuation.resume(f.runtime, plan, execution, new ArrayList<>()).get(30, TimeUnit.SECONDS);
      assertEquals(0, f.classifier.calls.get(), "Planning does not execute the classifier");
      var compiled = new CompiledDataflow(f.runtime, target, execution, plan);
      assertTrue(compiled.compile(plan.getComputation().getFirst())); assertTrue(compiled.store(transaction));
      transaction.registerExecutors();
      boolean success = compiled.executeRoot(f.geometry, Scheduler.Event.initialization(), execution);
      assertEquals(!fail, success);
      if (existing) assertEquals(0, f.classifier.calls.get(), "Known-family checks do not invoke a classifier");
      else assertTrue(f.classifier.calls.get() > 0, "A real classifier method must have run");
      assertEquals(1, otherInvocations, "The normal instantiator must run after observing the staged classified Region");
      assertSame(before, f.region.getObservable(), "Prerequisite effects remain staged until the shared root commits");
      if (drift) {
        var changed = f.persistedClassifierInputs.get(f.region.getId());
        assertEquals(before.getUrn(), changed.observable(), "The concurrent change leaves the semantic CAS value unchanged");
        assertEquals("Concurrent native name change", changed.properties().get("name"));
        assertEquals(-1, transaction.commit(), "A prerequisite cohort's native payload changed after classification/no-op inspection");
        assertSame(before, f.region.getObservable()); assertEquals(0, f.rootCommits.get());
        verify(f.nativeTx, atLeastOnce()).validateClassificationMembers(any());
      } else if (fail) {
        transaction.fail(new IllegalStateException("Failed native discovery")); assertEquals(-1, transaction.commit());
        assertSame(before, f.region.getObservable()); assertEquals(0, f.rootCommits.get());
      } else {
        assertTrue(transaction.commit() > 0, f.failures().toString());
        var published = f.durable.get(f.region.getId());
        assertNotNull(published); assertEquals(f.region.getId(), published.getId());
        assertNotEquals(before.getUrn(), published.getObservable().getUrn(),
            "Publication uses the native canonical member, rather than requiring an old cached bean to mutate");
        assertEquals(1, f.rootCommits.get());
      }
    }
  }

  @Test void directCollectiveClassifierRunsBeforeNativeInstantiatorAndSharesItsRootCommit() throws Exception { exercise(false); }
  @Test void nativeInstantiatorFailureRollsBackTheAlreadyExecutedClassifier() throws Exception { exercise(true); }
  @Test void nativePayloadChangeAfterFreshPrerequisiteAttributionAbortsTheSharedRoot() throws Exception { exercise(false, true, false); }
  @Test void nativePayloadChangeAfterKnownFamilyNoOpAbortsTheSharedRoot() throws Exception { exercise(false, true, true); }
}
