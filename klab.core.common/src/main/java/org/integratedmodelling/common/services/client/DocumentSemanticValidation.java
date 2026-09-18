package org.integratedmodelling.common.services.client;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;

/** Shared orchestration for editors and Resources callers; invoke off the UI/save thread. */
public final class DocumentSemanticValidation {
  private DocumentSemanticValidation() {}

  public static SemanticValidationResponse validate(
      SemanticValidationRequest request, Scope scope) {
    var response = SemanticValidationResponse.forRequest(request);
    try {
      var reasoner = scope == null ? null : scope.getService(Reasoner.class);
      if (reasoner == null) {
        response.setReason("No reasoner is available in the current scope");
        return response;
      }
      var result = reasoner.validateDocument(request, scope);
      if (result != null) return result;
      response.setReason("The selected reasoner did not return semantic validation results");
    } catch (RuntimeException e) {
      response.setReason("Semantic validation is unavailable: " + e.getMessage());
    }
    return response;
  }
}
