package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EventSupportTest {
  @BeforeAll static void initialize() { ServiceConfiguration.injectInstantiators(); }
  static ObservationImpl event(long start, long end) {
    var concept = new ConceptImpl(); concept.getType().add(SemanticType.EVENT);
    concept.setUrn("test:Event");
    var event = new ObservationImpl(); event.setObservable(ObservableImpl.promote(concept, null));
    event.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=" + start + ",tend=" + end + "}"));
    return event;
  }
  @Test void acceptsPresentAndFutureAtomicPeriodsBeyondInvocationHorizon() {
    assertEquals(10000, EventSupport.validate(event(100, 10000), Scheduler.event(100, 200)).getEnd().getMilliseconds());
    assertNotNull(EventSupport.validate(event(500, 10000), Scheduler.event(100, 200)));
  }
  @Test void rejectsPastZeroDurationInitializationAndSubdivisions() {
    assertThrows(IllegalArgumentException.class, () -> EventSupport.validate(event(99, 10000), Scheduler.event(100, 200)));
    assertThrows(IllegalArgumentException.class, () -> EventSupport.validate(event(100, 100), Scheduler.event(100, 200)));
    assertThrows(IllegalArgumentException.class, () -> EventSupport.validate(event(100, 10000), Scheduler.Event.initialization()));
    var divided = event(100, 10000);
    divided.setGeometry(Geometry.create("T1(10){ttype=GRID,tstart=100,tend=10000,tgrid=990,tscope=1,tunit=MILLISECOND}"));
    assertThrows(IllegalArgumentException.class, () -> EventSupport.validate(divided, Scheduler.event(100, 200)));
  }

  @Test void instantiationValidatesWholeBatchAndRequiresEveryResolvedChild() {
    var collective = event(100, 200);
    ((ConceptImpl) collective.getObservable().getSemantics()).setCollective(true);
    collective.setObservable(((ObservableImpl) collective.getObservable()).as(
        org.integratedmodelling.klab.api.knowledge.Contextualization.INSTANTIATION));
    var context = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    var client = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    org.mockito.Mockito.when(context.within(collective)).thenReturn(context);
    org.mockito.Mockito.when(context.withResolutionConstraints(org.mockito.ArgumentMatchers.any(
        org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(context);
    org.mockito.Mockito.when(context.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(client);
    var runtime = org.mockito.Mockito.mock(RuntimeService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    var outcomes = new ContextualizationScopeImpl(collective, Scheduler.event(100, 200));
    runtime.submitContextualizationResult(outcomes, context, org.integratedmodelling.klab.api.provenance.Activity.Outcome.SUCCESS);
    org.mockito.Mockito.verifyNoInteractions(client); // Empty instantiation is successful.
    var valid = event(150, 10000); var invalid = event(99, 10000);
    outcomes.getOutcomes().add(valid); outcomes.getOutcomes().add(invalid);
    assertThrows(IllegalArgumentException.class, () -> runtime.submitContextualizationResult(outcomes, context,
        org.integratedmodelling.klab.api.provenance.Activity.Outcome.SUCCESS));
    org.mockito.Mockito.verifyNoInteractions(client);
    outcomes.getOutcomes().remove(invalid);
    var failed = event(150, 10000); failed.setEmpty(true);
    org.mockito.Mockito.when(client.submit(valid, context)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(failed));
    assertThrows(RuntimeException.class, () -> runtime.submitContextualizationResult(outcomes, context,
        org.integratedmodelling.klab.api.provenance.Activity.Outcome.SUCCESS));
    org.mockito.Mockito.when(client.submit(valid, context)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(valid));
    runtime.submitContextualizationResult(outcomes, context, org.integratedmodelling.klab.api.provenance.Activity.Outcome.SUCCESS);
    assertTrue(org.integratedmodelling.klab.api.digitaltwin.ObservedEvent.pending(valid));
  }
}
