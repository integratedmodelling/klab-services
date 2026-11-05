package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.knowledge.InstantiationResolution;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual member-submission/compilation path; native persistence and Scheduler are controlled I/O. */
class InstantiationSubmissionPolicyTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  SubmissionCancellationTest.Fixture fixture() throws Exception {
    var f = new SubmissionCancellationTest.Fixture();
    doCallRealMethod().when(f.runtime).submitContextualizationResult(any(), any(), any());
    doCallRealMethod().when(f.runtime).submitInstantiated(any(), any(), any());
    doReturn(mock(org.integratedmodelling.klab.components.ComponentRegistry.class)).when(f.runtime).getComponentRegistry();
    doReturn(mock(org.integratedmodelling.klab.api.configuration.Settings.class)).when(f.runtime).settings();
    var status = f.resolver.status(); when(f.runtime.status()).thenReturn(status); f.scope.addService(f.runtime);
    when(f.reasoner.baseSubstantialType(any(), any())).thenAnswer(call -> call.getArgument(0, org.integratedmodelling.klab.api.knowledge.Semantics.class).asConcept());
    var field = RuntimeService.class.getDeclaredField("inFlightSubmissions"); field.setAccessible(true);
    field.set(f.runtime, new java.util.concurrent.ConcurrentHashMap<>());
    var current = org.integratedmodelling.klab.services.scopes.ServiceContextScope.class.getDeclaredField("currentTransaction");
    current.setAccessible(true); current.set(f.scope, f.rootTransaction);
    when(f.rootTransaction.getActivity()).thenReturn(Activity.of(Activity.Type.INSTANTIATION));
    when(f.resolutionTransaction.getChild(any(),any(),any(Object[].class))).thenReturn(f.resolutionTransaction);
    when(f.resolutionTransaction.getParent()).thenReturn(f.rootTransaction);
    var cohort = new org.integratedmodelling.common.knowledge.CohortImpl(); cohort.setId(77);
    doReturn(cohort).when(f.runtime).getCohortFor(any(),any(),eq(true));
    return f;
  }

  @Test void noneUsesOrdinaryBindingAndSchedulerWithoutResolvingAndCollectiveSelectsOnce() throws Exception {
    for (var mode : List.of("none", "collective")) {
      var f = fixture();
      when(f.resolver.resolve(any(), any())).thenAnswer(call -> CompletableFuture.completedFuture(
          InstantiationPlanning.acknowledgment(call.getArgument(0))));
      var target = InstantiationPolicyTest.observation(-2,"roads",true,InstantiationPolicyTest.FULL);
      var a = InstantiationPolicyTest.observation(-3,"a",false,InstantiationPolicyTest.time(0,2000));
      var b = InstantiationPolicyTest.observation(-4,"b",false,InstantiationPolicyTest.time(4000,6000));
      var outputs = InstantiationPolicyTest.batch(target,List.of(a,b),mode);
      f.runtime.submitContextualizationResult(outputs, f.scope, Activity.Outcome.SUCCESS);
      verify(f.resolver,times(mode.equals("none")?0:1)).resolve(any(),any());
      verify(f.scheduler,times(2)).submit(any(),any());
      var plans = org.mockito.ArgumentCaptor.forClass(org.integratedmodelling.klab.api.services.runtime.Actuator.class);
      verify(f.resolutionTransaction,times(2)).registerInputPlan(any(),plans.capture());
      assertEquals(List.of(a.getId(),b.getId()),plans.getAllValues().stream().map(p->p.getObservation().getId()).toList());
      assertNotSame(plans.getAllValues().get(0),plans.getAllValues().get(1));
      assertEquals(mode,a.getMetadata().get(InstantiationPlanning.MODE));
      verify(f.resolutionTransaction,atLeastOnce()).link(any(),eq(a),eq(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.CREATED));
      verify(f.resolutionTransaction).link(eq(target),eq(a),eq(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_CHILD));
      verify(f.resolutionTransaction).link(any(),eq(a),eq(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_MEMBER));
    }
  }

  @Test void deferredSharedSelectionFailsTheBatchWithoutFallingBackOrPublishing() throws Exception {
    var f = fixture();
    var deferred = Dataflow.empty(); ((org.integratedmodelling.klab.api.services.runtime.impl.EmptyDataflow)deferred).setResolutionOutcome(Dataflow.ResolutionOutcome.DEFERRED);
    when(f.resolver.resolve(any(),any())).thenReturn(CompletableFuture.completedFuture(deferred));
    var target=InstantiationPolicyTest.observation(-2,"roads",true,InstantiationPolicyTest.FULL);
    var children=List.of(InstantiationPolicyTest.observation(-3,"a",false,InstantiationPolicyTest.FULL),
        InstantiationPolicyTest.observation(-4,"b",false,InstantiationPolicyTest.FULL));
    assertThrows(RuntimeException.class,()->f.runtime.submitContextualizationResult(
        InstantiationPolicyTest.batch(target,children,"collective"),f.scope,Activity.Outcome.SUCCESS));
    verify(f.resolver,times(1)).resolve(any(),any());
    verify(f.scheduler,never()).submit(any(),any());
    verify(f.rootTransaction,never()).commit();
  }
}
