package org.integratedmodelling.klab.services.resources.persistence;

import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;

/** Explicit successful admission for storage/ranking tests that do not test source semantics. */
final class ModelAdmissionFixtures {
  static void valid(Reasoner reasoner) {
    when(reasoner.validateDocument(any(), any())).thenAnswer(invocation -> {
      var response = SemanticValidationResponse.forRequest(invocation.getArgument(0));
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);
      response.setKnowledgeRevision(7);
      return response;
    });
  }
}
