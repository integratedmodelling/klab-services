package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.*;

/** A selected input may be consumed again after its enclosing operation stages another family.
 * This is a transaction-local input, not permission to relax published view replay guards. */
class CollectiveSelectionDraftReadTest {
  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }

  @Test void attributedNonmemberContextHasIdenticalStoredAndFreshReplayObligations() throws Exception {
    try(var fixture=new CollectiveSelectionAtomicExecutionTest()) { fixture.setup();
      var q=fixture.q;q.member(-30,"Known",q.beech,q.mature).setUrn("member:known");
      var context=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
      context.setId(-40);context.setUrn("context:separate");context.setObservable(q.observable("test:Stream",false));
      context.setGeometry(q.request.getGeometry());q.f.root.add(context);
      when(q.f.scope.getContextObservation()).thenReturn(context);
      var family=q.predicate("test:Environment",SemanticType.REALM,true);
      var value=q.predicate("test:Terrestrial",SemanticType.REALM,false);
      var update=(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl)
          q.f.root.getChild(Activity.of(Activity.Type.CLASSIFICATION),q.f.scope);
      update.stageAttributions(List.of(new MemberClassifierExecutor.PendingAttribution(context,
          context.getObservable(),family,value,q.request.getGeometry(),Scheduler.Event.initialization())),q.f.scope);
      q.recordAttribution(update,context,family,value,context.getObservable());assertTrue(update.commit()>=0);
      String projected=q.f.root.readObservation(context).getObservable().getSemantics().getUrn();
      assertNotEquals(context.getObservable().getSemantics().getUrn(),projected);
      q.complete(); // The actual native producer runs after its prerequisite attribution.
      var source=q.f.root.assets().stream().filter(Activity.class::isInstance).map(Activity.class::cast)
          .filter(a->a.getType()==Activity.Type.INSTANTIATION).findFirst().orElseThrow();
      var stored=CollectiveSelectionExecution.execute(q.f.runtime,fixture.plan(),q.request,q.f.scope,new ArrayList<>());
      assertEquals(projected,q.proof(stored).context().semantics());assertTrue(q.f.root.commit()>0);
      assertEquals(q.proof(stored).context(),org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
          source.getMetadata().get(RuntimeService.COLLECTIVE_CONTEXT).toString(),
          org.integratedmodelling.klab.api.knowledge.CollectiveSelectionBinding.Context.class));
      when(q.f.scope.getCurrentTransaction()).thenReturn(null);
      var fresh=q.query();assertFalse(fresh.isEmpty());
      var storedTerms=q.proof(stored).semanticEvidence().obligations();
      var freshTerms=q.proof(fresh).semanticEvidence().obligations();
      assertEquals(storedTerms,freshTerms,"Stored and freshly revalidated context obligations must be identical");
      assertTrue(storedTerms.contains(projected),"Certify the compound attributed meaning, not only its components");
      var replay=assertDoesNotThrow(()->CollectiveSelectionExecution.query(q.f.runtime,stored,q.f.scope));
      assertEquals(q.proof(stored).selectedIds(),q.proof(replay).selectedIds());
      context.setObservable(q.observable("test:ExternallyChanged",false));
      assertThrows(IllegalStateException.class,()->CollectiveSelectionExecution.query(q.f.runtime,stored,q.f.scope));
    }
  }

  @Test void currentNativeProofKeepsAdmissionUntilPublicationAndRollsBackItsPrerequisite() throws Exception {
    try(var fixture=new CollectiveSelectionAtomicExecutionTest()) { fixture.setup();
      var q=fixture.q;var context=q.member(-30,"Context",q.beech,q.mature);context.setUrn("context:original");
      when(q.f.scope.getContextObservation()).thenReturn(context);
      var binding=org.integratedmodelling.klab.api.knowledge.CollectiveSelectionBinding.capture(q.request,q.f.scope,q.reasoner);
      assertNotNull(binding);var original=context.getObservable().getUrn();
      var family=q.predicate("test:Environment",SemanticType.REALM,true);
      var value=q.predicate("test:Terrestrial",SemanticType.REALM,false);
      var update=(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl)
          q.f.root.getChild(Activity.of(Activity.Type.CLASSIFICATION),q.f.scope);
      update.stageAttributions(List.of(new MemberClassifierExecutor.PendingAttribution(context,
          context.getObservable(),family,value,q.request.getGeometry(),Scheduler.Event.initialization())),q.f.scope);
      q.recordAttribution(update,context,family,value,context.getObservable());assertTrue(update.commit()>=0);
      assertNotEquals(original,q.f.root.readObservation(context).getObservable().getUrn());
      var nativeActivity=Activity.of(Activity.Type.INSTANTIATION);
      nativeActivity.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_OBSERVER_SELECTION_ID,0L);
      RuntimeService.recordCollectiveSemanticRevision(nativeActivity,q.f.scope);
      assertEquals(binding.context(),org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
          nativeActivity.getMetadata().get(RuntimeService.COLLECTIVE_CONTEXT).toString(),
          org.integratedmodelling.klab.api.knowledge.CollectiveSelectionBinding.Context.class));
      assertTrue(CollectiveSelectionExecution.activityMatches(nativeActivity,binding,true,q.f.scope));
      assertFalse(CollectiveSelectionExecution.activityMatches(nativeActivity,binding,false,q.f.scope),
          "Projected context is admitted only for an owned current execution, not persisted proof");
      var geometry=context.getGeometry();context.setGeometry(q.f.period(0,5));
      assertFalse(CollectiveSelectionExecution.activityMatches(nativeActivity,binding,true,q.f.scope));
      context.setGeometry(geometry);
      var before=context.getObservable();context.setObservable(q.observable("test:External test:Stream",false));
      assertFalse(CollectiveSelectionExecution.activityMatches(nativeActivity,binding,true,q.f.scope));
      context.setObservable(before);
      when(q.f.scope.getObserver()).thenReturn(context);
      assertThrows(IllegalStateException.class,()->binding.require(q.f.scope,q.request.getObservable(),q.request.getGeometry()));
      when(q.f.scope.getObserver()).thenReturn(null);
      q.f.root.fail(new IllegalStateException("deliberate downstream consumer failure"));
      assertEquals(-1,q.f.root.commit());assertEquals(original,context.getObservable().getUrn());
      assertTrue(q.f.durable.isEmpty(),"Prerequisite attribution must not escape failed outer transaction");
    }
  }

  @Test void ownUnrelatedFamilyAttributionMustNotInvalidateAStillLiveSelectedInput() throws Exception {
    try (var fixture = new CollectiveSelectionAtomicExecutionTest()) { fixture.setup();
    var q = fixture.q;
    var member = q.member(-30, "Known", q.beech, q.mature); member.setUrn("member-original");
    q.complete();
    var port = new ActuatorImpl(); port.setActuatorType(Actuator.Type.REFERENCE);
    port.setName("selected"); port.setObservation(Observation.forTransport(q.request));
    port.setCollectiveSelectionDependency(fixture.plan());
    var incoming = new DataflowImpl(); incoming.getComputation().add(port);
    // Only the continuation owns a live input capability. A raw helper result/replayed receipt
    // must remain immutable, even if produced in the same transaction.
    var coordinator = new CollectiveSelectionContinuation(q.f.runtime, new ArrayList<>());
    var prepared = coordinator.prepare(incoming, q.f.scope).join();
    var view = prepared.getComputation().getFirst().getObservation();
    assertEquals(List.of(member.getId()), q.f.runtime.getMembers(view, q.f.scope).stream().map(Observation::getId).toList());
    var family = q.predicate("test:Environment", SemanticType.REALM, true);
    var value = q.predicate("test:Terrestrial", SemanticType.REALM, false);
    q.ancestors.put(value.getUrn(), Set.of(family.getUrn()));
    var activity = Activity.of(Activity.Type.CLASSIFICATION, q.f.scope.getActivity(), "Outer family after selected input");
    var classification = (org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl)
        q.f.root.getChild(activity, q.f.scope);
    classification.stageAttributions(List.of(new MemberClassifierExecutor.PendingAttribution(member,
        member.getObservable(), family, value, q.request.getGeometry(), Scheduler.Event.initialization())), q.f.scope);
    q.recordAttribution(classification, member, family, value, member.getObservable());
    assertTrue(classification.commit() >= 0);
    assertNotEquals(member.getObservable().getUrn(), q.f.root.readObservation(member).getObservable().getUrn(),
        "A real child classification must contribute a staged new meaning");
    assertEquals(List.of(member.getId()), assertDoesNotThrow(() -> q.f.runtime.getMembers(view, q.f.scope))
        .stream().map(Observation::getId).toList(), "A later characterizer can still read the actual selected input");
    }
  }
}
