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
  private static final class ConfigurationRequest {
    final org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl observation =
        new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    final org.integratedmodelling.klab.api.scope.ContextScope scope =
        mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    ConfigurationRequest(long id) {
      var semantics = new org.integratedmodelling.common.knowledge.ConceptImpl();
      semantics.setUrn("test:Pair");
      semantics.setType(java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.CONFIGURATION,
          org.integratedmodelling.klab.api.knowledge.SemanticType.OBSERVABLE));
      var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
      observable.setSemantics(semantics); observable.setUrn("test:Pair");
      observable.setDescriptionType(Contextualization.DETECTION);
      observation.setId(id); observation.setObservable(observable);
      when(scope.getId()).thenReturn("context");
      when(scope.getConstraint(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ConfigurationExplanation,
          Long.class)).thenReturn(id);
      when(scope.getConsequenceCause()).thenReturn(new org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause(
          1,"context",1,2,0,null,null,"decision",
          org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind.CONFIGURATION_DETECTION));
      when(scope.getResolutionCycleBinding()).thenReturn(new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
          1,"cycle","context","runtime",true,null));
    }
  }

  @Test void actualConfigurationExplanationAbsenceUsesPurposeWithoutChangingMeaning() {
    for (long id : List.of(42L, -7L)) {
      var request = new ConfigurationRequest(id);
      assertFalse(request.observation.getObservable().isOptional());
      var result = ResolverService.unresolvedOutcome(request.observation, request.scope, true, List.of());
      assertEquals(Dataflow.ResolutionOutcome.NO_MODEL, result.getResolutionOutcome());
      assertFalse(result.isEmpty()); assertTrue(result.getComputation().isEmpty());
      assertEquals("test:Pair", request.observation.getObservable().getUrn());
      assertFalse(request.observation.getObservable().isOptional());
      assertTrue(ResolverService.unresolvedOutcome(request.observation, request.scope, false, List.of()).isEmpty());
      assertTrue(ResolverService.unresolvedOutcome(request.observation, request.scope, true,
          List.of(Notification.error("Actual resolution failed"))).isEmpty());
      // The observable-only contract has no purpose and retains the old result.
      assertTrue(ResolverService.unresolvedOutcome(request.observation.getObservable(), true, List.of()).isEmpty());
    }
  }

  @Test void explanationPurposeCannotAuthorizeForeignOrUnmarkedConfiguration() {
    var foreign = new ConfigurationRequest(42);
    foreign.observation.setId(43);
    assertTrue(ResolverService.unresolvedOutcome(foreign.observation, foreign.scope, true, List.of()).isEmpty());
    var ordinary = new ConfigurationRequest(42);
    when(ordinary.scope.getConstraint(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ConfigurationExplanation,
        Long.class)).thenReturn(null);
    assertTrue(ResolverService.unresolvedOutcome(ordinary.observation, ordinary.scope, true, List.of()).isEmpty());
  }

  @Test void malformedExplanationTargetCauseAndCycleFailClosed() {
    for (long invalid : List.of(
        org.integratedmodelling.klab.api.knowledge.observation.Observation.UNASSIGNED_ID,
        org.integratedmodelling.klab.api.knowledge.observation.Observation.QUERY_ID)) {
      var request = new ConfigurationRequest(invalid);
      assertThrows(IllegalArgumentException.class,
          () -> ResolverService.unresolvedOutcome(request.observation, request.scope, true, List.of()));
    }
    for (int failure = 0; failure < 6; failure++) {
      var request = new ConfigurationRequest(42);
      switch (failure) {
        case 0 -> when(request.scope.getConsequenceCause()).thenReturn(null);
        case 1 -> when(request.scope.getConsequenceCause()).thenReturn(new org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause(
            1,"foreign",1,2,0,null,null,"decision",
            org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind.CONFIGURATION_DETECTION));
        case 2 -> when(request.scope.getResolutionCycleBinding()).thenReturn(null);
        case 3 -> when(request.scope.getResolutionCycleBinding()).thenReturn(new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
            1,"cycle","foreign","runtime",true,null));
        case 4 -> when(request.scope.getConsequenceCause()).thenReturn(new org.integratedmodelling.klab.api.digitaltwin.Consequence.Cause(
            1,"context",1,2,0,null,null,"decision",
            org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind.ROLE_CONFERRAL));
        case 5 -> ((org.integratedmodelling.common.knowledge.ConceptImpl)request.observation.getObservable().getSemantics())
            .setType(java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECT));
      }
      assertThrows(IllegalArgumentException.class,
          () -> ResolverService.unresolvedOutcome(request.observation, request.scope, true, List.of()));
    }
  }

}
