package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class ResolutionOutcomeTest {
  @Test void instantiatedIndividualsProduceNonEmptyTrivialPlansAcrossTransport() throws Exception {
    for (var type : List.of(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT,
        org.integratedmodelling.klab.api.knowledge.SemanticType.AGENT,
        org.integratedmodelling.klab.api.knowledge.SemanticType.RELATIONSHIP)) {
      var collective = new org.integratedmodelling.common.knowledge.ConceptImpl();
      collective.setType(java.util.EnumSet.of(type, org.integratedmodelling.klab.api.knowledge.SemanticType.COUNTABLE));
      collective.setCollective(true);
      collective.setUrn("each test:Member"); collective.setReferenceName("each_test_member");
      var creation = type == org.integratedmodelling.klab.api.knowledge.SemanticType.RELATIONSHIP
          ? Contextualization.CONNECTION : Contextualization.INSTANTIATION;
      collective.setDescriptionType(creation);
      var individual = collective.singular();
      assertEquals(Contextualization.ACKNOWLEDGEMENT, individual.getDescriptionType());
      assertEquals(creation, individual.collective().getDescriptionType());
      assertEquals(creation, collective.getDescriptionType());
      var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
      observable.setSemantics(individual); observable.setDescriptionType(individual.getDescriptionType());
      var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      for (var description : List.of(Contextualization.ACKNOWLEDGEMENT, creation)) {
        observable.setDescriptionType(description);
        var plan = ResolverService.unresolvedOutcome(observable, false, List.of());
        var received = mapper.readValue(mapper.writeValueAsString(plan), Dataflow.class);
        assertFalse(received.isEmpty());
        assertTrue(received.getComputation().isEmpty());
        assertEquals(Dataflow.ResolutionOutcome.NO_MODEL, received.getResolutionOutcome());
      }
    }
  }
  @Test void onlyMissingOptionalLifecycleExplanationSucceeds() {
    var observable = mock(Observable.class);
    for (var type : List.of(Contextualization.CHARACTERIZATION, Contextualization.ACKNOWLEDGEMENT)) {
      when(observable.getContextualization()).thenReturn(type);
      assertEquals(Dataflow.ResolutionOutcome.NO_MODEL,
          ResolverService.unresolvedOutcome(observable, true, List.of()).getResolutionOutcome());
      assertEquals(type == Contextualization.CHARACTERIZATION,
          ResolverService.unresolvedOutcome(observable, false, List.of()).isEmpty());
      assertTrue(ResolverService.unresolvedOutcome(observable, true,
          List.of(Notification.error("Discovery failed"))).isEmpty());
    }
    when(observable.getContextualization()).thenReturn(Contextualization.CLASSIFICATION);
    assertTrue(ResolverService.unresolvedOutcome(observable, true, List.of()).isEmpty());
  }

  @Test void acknowledgmentDoesNotRequireAnExplanatoryDiscoveryResult() {
    var observable = mock(Observable.class);
    when(observable.getContextualization()).thenReturn(Contextualization.ACKNOWLEDGEMENT);
    // false includes no strategy/model query and candidate models with no significant graph.
    var result = ResolverService.unresolvedOutcome(observable, false, List.of());
    assertFalse(result.isEmpty());
    assertEquals(Dataflow.ResolutionOutcome.NO_MODEL, result.getResolutionOutcome());
    assertTrue(result.getComputation().isEmpty());
    assertTrue(ResolverService.unresolvedOutcome(observable, false,
        List.of(Notification.error("Resolution service failed"))).isEmpty());
  }
}
