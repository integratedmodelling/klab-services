package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.LinkedHashMap;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;

/** Opt-in profiling of requests that reach a pinned actor, including successful proof hits. */
final class DocumentValidationProfile {
  static final String PROPERTY = "klab.profile.documentValidation";

  private DocumentValidationProfile() {}

  enum Outcome { FULL_VALIDATION, NAMESPACE_PROOF, DURABLE_SOURCE_PROOF, ACCEPTED_ONTOLOGY, FAILED }

  static void record(
      SemanticValidationRequest request, SemanticValidationResponse response, Scope scope,
      boolean retained, long refinement, long finalRefinement, String proofKey, Outcome outcome,
      long elapsedNanos, long actorWaitNanos) {
    try {
      var fields = new LinkedHashMap<String, Object>();
      fields.put("document", SemanticEnvironmentFactory.sourceKey(request.document()));
      fields.put("sourceHash", response.getSourceHash());
      fields.put("executionContextHash", SemanticValidationRequest.sourceHash(request.getExecutionContext()));
      fields.put("semanticSession", response.getSemanticSessionId());
      fields.put("knowledgeRevision", response.getKnowledgeRevision());
      fields.put("refinement", refinement);
      fields.put("finalRefinement", finalRefinement);
      fields.put("executable", request.isExecutableModels());
      fields.put("retained", retained);
      fields.put("proofKey", proofKey);
      var identity = scope == null ? null : scope.getIdentity();
      fields.put("principalHash", identity == null ? null : SemanticValidationRequest.sourceHash(identity.getId()));
      fields.put("outcome", outcome.name());
      fields.put("valid", response.valid());
      fields.put("sourceSemanticsComplete", response.isSourceSemanticsComplete());
      fields.put("semanticObligationsComplete", response.isSemanticObligationsComplete());
      var witness = response.getSemanticWitness();
      fields.put("witness", witness == null ? null : witness.status());
      fields.put("authorityUses", witness == null ? null : witness.authorities().size());
      fields.put("elapsedNanos", elapsedNanos);
      fields.put("actorWaitNanos", actorWaitNanos);
      Logging.INSTANCE.info("DOCUMENT_VALIDATION_PROFILE " + Utils.Json.asString(fields));
    } catch (RuntimeException ignored) {
      // Optional profiling must not change the outcome of validation.
    }
  }
}
