package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FrozenAdmissionCompilationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  static class Fixture {
    final CollectiveReductionPlanningTest f = new CollectiveReductionPlanningTest();
    final ResourcesService resources = mock(ResourcesService.class);
    final ResolutionGraph graph;
    final ActuatorImpl actuator = new ActuatorImpl();
    final ModelValidationGate.AdmissionReceipt receipt;

    Fixture() {
      when(f.model.getObservables()).thenReturn(List.of(f.target));
      when(f.scope.getObserver()).thenReturn(f.observation(90, "observer",
          CollectiveReductionPlanningTest.observable("test:Observer", SemanticType.AGENT)));
      when(f.scope.getService(ResourcesService.class)).thenReturn(resources);
      when(f.capabilities.getSemanticContentFingerprint()).thenReturn("frozen-fingerprint");
      when(f.capabilities.getSemanticSessionId()).thenReturn("frozen-actor");
      graph = ResolutionGraph.create(f.scope);
      graph.graph().addVertex(f.model);
      receipt = new ModelValidationGate.AdmissionReceipt(f.model.getUrn(), f.model.getNamespace(),
          "source-version", "source-hash", ModelValidationGate.executableHash(f.model),
          "reasoner", 3, List.of(), "frozen-fingerprint", List.of(), true, null, "frozen-actor");
      graph.admitModel(f.model, receipt);
      actuator.setObservation(f.request);
      actuator.setCoverage(f.scale);
      clearInvocations(f.reasoner, resources);
    }

    void compile() {
      new DataflowCompiler(f.request, graph, f.scope)
          .compileModel(actuator, f.request, f.scale, null, f.model, null);
    }
  }

  @Test void selectedObserverCompilationConsumesOriginalReceiptWithoutSourceOrSemanticReadmission() {
    var f = new Fixture();
    // A later publication must not replace the source or semantic actor frozen by this attempt.
    when(f.f.capabilities.getKnowledgeRevision()).thenReturn(99L);
    when(f.f.capabilities.getSemanticSessionId()).thenReturn("new-actor");
    f.compile();
    verifyNoInteractions(f.resources, f.f.reasoner);
    var encoded = (List<?>) f.actuator.getData().get(ModelValidationGate.PRODUCER_ADMISSIONS);
    assertEquals(List.of(org.integratedmodelling.klab.utilities.Utils.Json.asString(f.receipt)), encoded);
    assertTrue(f.actuator.getData().containsKey(DataflowCompiler.OBSERVER_CONTRIBUTIONS));
  }

  @Test void changedExecutableOrForeignOriginalEvidenceCannotBeReplacedByFreshAdmission() {
    for (int variant = 0; variant < 6; variant++) {
      var f = new Fixture();
      var original = f.receipt;
      if (variant == 0) when(f.f.model.getDependencies()).thenReturn(List.of(f.f.trees));
      else f.graph.admitModel(f.f.model, new ModelValidationGate.AdmissionReceipt(
          variant == 1 ? "other-model" : original.modelUrn(),
          variant == 2 ? "other-namespace" : original.namespace(), original.sourceVersion(),
          original.sourceHash(), original.executableHash(), original.reasonerId(),
          variant == 3 ? 99 : original.knowledgeRevision(), original.pendingMemberQualities(),
          variant == 4 ? "other-fingerprint" : original.semanticFingerprint(),
          original.semanticObligations(), original.semanticObligationsComplete(),
          original.semanticWitness(), variant == 5 ? "other-actor" : original.semanticSessionId()));
      assertThrows(KlabIllegalStateException.class, f::compile, "variant " + variant);
      verifyNoInteractions(f.resources, f.f.reasoner);
    }
  }

  @Test void missingOriginalReceiptFailsWithoutAttemptingReadmission() {
    var f = new Fixture();
    var model = mock(org.integratedmodelling.klab.api.knowledge.Model.class);
    when(model.getUrn()).thenReturn("test:unadmitted");
    when(model.getNamespace()).thenReturn("test");
    assertThrows(KlabIllegalStateException.class,
        () -> new DataflowCompiler(f.f.request, f.graph, f.f.scope)
            .compileModel(f.actuator, f.f.request, f.f.scale, null, model, null));
    verifyNoInteractions(f.resources, f.f.reasoner);
  }
}
