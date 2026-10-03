package org.integratedmodelling.common.review;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.Artifact;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.Candidate;

/** Shared IDE/service binding inspector. Identity inspection is NOT JSON Schema or semantic validation.
 * The service independently verifies attachment membership and checksums and repeats inspection.
 */
public final class ProposalCandidateBinding {
  private ProposalCandidateBinding() {}
  private static final ObjectMapper YAML = new ObjectMapper(
      YAMLFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
  public static Candidate inspect(Artifact proposal, Artifact ontology, byte[] content) {
    try {
      JsonNode document = YAML.readTree(content);
      if (!"classpath:/schemas/llm/domain-context-proposal.schema.json".equals(document.path("proposal_schema").asText())
          || !"1.3".equals(document.path("context_pack_version").asText())) fail("Unsupported proposal schema or context pack version");
      var body = document.path("proposal");
      String id = requiredText(body, "id"), revision = requiredText(body, "revision_id");
      JsonNode actions = body.path("actions");
      if (!actions.isArray()) fail("Proposal actions must be an array");
      var ids = new ArrayList<String>();
      for (var action : actions) {
        String actionId = requiredText(action, "action_id");
        if (ids.contains(actionId)) fail("Duplicate proposal action ID");
        ids.add(actionId);
      }
      if (!body.path("existing_ontologies").isArray()) fail("Proposal import context must be an array");
      var previous = body.path("supersedes_revision");
      if (!previous.isMissingNode() && !previous.isNull() && (!previous.isTextual() || previous.asText().isBlank()))
        fail("Invalid supersedes_revision");
      return new Candidate(id, revision, previous.isNull() || previous.isMissingNode() ? null : previous.asText(),
          proposal, ontology, ids, digest(body.path("existing_ontologies").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (KlabIllegalStateException e) { throw e; }
    catch (Exception e) { throw new KlabIllegalStateException("Cannot read proposal identity: " + e.getMessage()); }
  }
  private static String requiredText(JsonNode node, String field) {
    if (!node.path(field).isTextual() || node.path(field).asText().isBlank()) fail("Missing proposal " + field);
    return node.path(field).asText();
  }
  public static String digest(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (Exception e) { throw new KlabIllegalStateException(e); }
  }
  private static void fail(String message) { throw new KlabIllegalStateException(message); }
}
