package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class RejectedCandidateDiagnosticsTest {
  private KimOntologyImpl document() {
    var source = new KimOntologyImpl();
    source.setUrn("earth"); source.setProjectName("imod");
    source.setSourceCode("ontology earth; thing Waterway is Riverine Subject;");
    return source;
  }

  private List<CandidateDiagnostic> diagnostics(KimOntologyImpl source) {
    var statement = new KimConceptImpl();
    statement.setOffsetInDocument(33); statement.setLength(8);
    var first = Notification.error("Predicate outside its applies to domain",
        Notification.LexicalContext.of(statement, source));
    statement.setOffsetInDocument(42);
    var second = Notification.error(first.getMessage(), Notification.LexicalContext.of(statement, source));
    return CandidateDiagnostic.capture(List.of(first, first, second));
  }

  private CandidatePublicationCoordinator.Status status(KimOntologyImpl source, long accepted) {
    return new CandidatePublicationCoordinator.Status(2, accepted,
        CandidatePublicationCoordinator.BeanStatus.VALID,
        CandidatePublicationCoordinator.LogicalStatus.INVALID,
        Map.of(SemanticEnvironmentFactory.sourceKey(source),
            SemanticValidationRequest.sourceHash(source.getSourceCode())),
        Map.of(), List.of("Predicate outside its applies to domain"), List.of(), diagnostics(source));
  }

  @Test void rejectedSourceKeepsBothOccurrencesWithoutPretendingKnowledgeWasAccepted() {
    var source = document(); var request = SemanticValidationRequest.of(source, "saved");
    for (long accepted : List.of(0L, 3L)) {
      var response = AcceptedSemanticRuntime.unavailableValidation(request,
          SemanticValidationResponse.forRequest(request), status(source, accepted));
      assertEquals(SemanticValidationResponse.Status.FAILED, response.getStatus());
      assertFalse(response.valid()); assertEquals(accepted, response.getKnowledgeRevision());
      assertEquals(List.of(33, 42), response.getNotifications().stream()
          .map(n -> n.getLexicalContext().getOffsetInDocument()).toList());
      assertTrue(response.getNotifications().stream().allMatch(n ->
          n.getLevel() == Notification.Level.Error && n.getLexicalContext().getLength() == 8));
      assertNull(response.getSemanticContentFingerprint());
    }
  }

  @Test void changedTextAndOtherProjectsCannotReceiveOldOffsets() {
    var source = document(); var status = status(source, 0);
    source.setSourceCode(source.getSourceCode() + " ");
    for (var changed : List.of(source, document())) {
      if (changed != source) changed.setProjectName("other");
      var request = SemanticValidationRequest.of(changed, "next");
      var response = AcceptedSemanticRuntime.unavailableValidation(request,
          SemanticValidationResponse.forRequest(request), status);
      assertEquals(1, response.getNotifications().size());
      assertEquals(0, response.getNotifications().getFirst().getLexicalContext().getOffsetInDocument());
    }
  }

  @Test void statusTransportPreservesCandidateSourceAndLexicalAdvisories() throws Exception {
    var candidate = status(document(), 0);
    var status = new org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl();
    status.getMetadata().put("semantic.candidate", candidate);
    candidate.sourceDiagnostics().stream().map(CandidateDiagnostic::notification)
        .forEach(status.getAdvisories()::add);
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var transported = mapper.readValue(mapper.writeValueAsBytes(status),
        org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);
    var metadata = (Map<?, ?>) transported.getMetadata().get("semantic.candidate");
    assertEquals(candidate.candidateSources(), metadata.get("candidateSources"));
    assertEquals(List.of(33, 42), transported.getAdvisories().stream()
        .map(n -> n.getLexicalContext().getOffsetInDocument()).toList());
    assertEquals("imod", transported.getAdvisories().getFirst().getLexicalContext().getProjectUrn());
  }

  @Test void rejectionRetainsImmutableDiagnosticsAfterDisposalAndInvalidationClearsThem() throws Exception {
    var source = document(); var diagnostics = diagnostics(source);
    var disposed = new java.util.concurrent.atomic.AtomicBoolean();
    try (var coordinator = new CandidatePublicationCoordinator<String, AutoCloseable>((input, cancellation) ->
        new CandidatePublicationCoordinator.Result<>(() -> disposed.set(true),
            CandidatePublicationCoordinator.LogicalStatus.INVALID, true, List.of("invalid"), diagnostics),
        input -> true)) {
      coordinator.submit(new CandidatePublicationCoordinator.Input<>(Map.of("earth", "hash"),
          List.of("earth"), "dependencies", "configuration", "source",
          CandidatePublicationCoordinator.BeanStatus.VALID, List.of()));
      assertTrue(coordinator.awaitIdle(Duration.ofSeconds(5)));
      assertTrue(disposed.get()); assertTrue(coordinator.accepted().isEmpty());
      assertEquals(diagnostics, coordinator.status().sourceDiagnostics());
      var transported = coordinator.status().sourceDiagnostics().getFirst().notification();
      ((org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl.LexicalContextImpl)
          transported.getLexicalContext()).setOffsetInDocument(0);
      assertEquals(33, coordinator.status().sourceDiagnostics().getFirst().offset());
      coordinator.invalidate(); assertTrue(coordinator.status().sourceDiagnostics().isEmpty());
    }
  }
}
