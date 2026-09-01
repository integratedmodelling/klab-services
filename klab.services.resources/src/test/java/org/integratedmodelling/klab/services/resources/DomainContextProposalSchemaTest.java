package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.integratedmodelling.common.review.ProposalDocument;
import org.junit.jupiter.api.Test;

class DomainContextProposalSchemaTest {

  private static final String SCHEMA_RESOURCE =
      "schemas/llm/domain-context-proposal.schema.json";

  @Test
  void proposalSchemaIsPackagedAndExposesSupportedAssetDefinitions() throws Exception {
    try (var stream = getClass().getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
      assertNotNull(stream, "Missing classpath schema " + SCHEMA_RESOURCE);

      var schema = new ObjectMapper().readTree(stream);
      assertEquals("https://json-schema.org/draft/2020-12/schema", schema.path("$schema").asText());
      assertEquals(
          ProposalDocument.SCHEMA_RESOURCE,
          schema.path("properties").path("proposal_schema").path("const").asText());
      assertEquals(
          ProposalDocument.CONTEXT_PACK_VERSION,
          schema.path("properties").path("context_pack_version").path("const").asText());

      var definitions = schema.path("$defs");
      assertTrue(definitions.has("conceptAsset"));
      assertTrue(definitions.has("ontologyAsset"));
      assertTrue(definitions.has("namespaceAsset"));
      assertTrue(definitions.has("modelAsset"));
      assertTrue(definitions.has("action"));
    }
  }
}
