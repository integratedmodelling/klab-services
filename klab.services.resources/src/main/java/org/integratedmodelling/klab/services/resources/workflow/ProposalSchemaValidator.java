package org.integratedmodelling.klab.services.resources.workflow;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;

/** Validates proposal structure, never the truth of scientific or validation claims in a proposal.
 * Schema selection is server-owned. No network or file schema retrieval is permitted.
 */
public final class ProposalSchemaValidator {
  static final String RESOURCE = "/schemas/llm/domain-context-proposal.schema.json";
  static final String SCHEMA_ID = "https://integratedmodelling.org/schemas/llm/domain-context-proposal.schema.json";
  private static final ObjectMapper YAML = new ObjectMapper(
      YAMLFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
  private static final int MAX_MESSAGES = 50;
  private static final int MAX_MESSAGE_CHARS = 1000;
  private final Schema schema;

  private static final class Bundled {
    private static final ProposalSchemaValidator INSTANCE = load();
    private static ProposalSchemaValidator load() {
      try (var input = ProposalSchemaValidator.class.getResourceAsStream(RESOURCE)) {
        return new ProposalSchemaValidator(input == null ? null
            : new String(input.readAllBytes(), StandardCharsets.UTF_8));
      } catch (Exception e) {
        return new ProposalSchemaValidator(null);
      }
    }
  }

  public static ProposalSchemaValidator bundled() { return Bundled.INSTANCE; }

  // Package-private seam for missing/broken schema and dialect regression tests.
  ProposalSchemaValidator(String schemaText) {
    Schema compiled = null;
    try {
      if (schemaText != null) {
        var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemas(Map.of(SCHEMA_ID, schemaText))
                .resourceLoaders(loaders -> loaders.add(iri -> {
                  throw new IllegalStateException("External schema retrieval is disabled");
                })));
        compiled = registry.getSchema(SchemaLocation.of(SCHEMA_ID));
        compiled.initializeValidators();
      }
    } catch (RuntimeException e) {
      compiled = null;
    }
    schema = compiled;
  }

  public Check validate(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > ProposalReview.MAX_PROPOSAL_BYTES)
      return result(CheckStatus.FAIL, "Proposal is empty or exceeds the byte limit");
    JsonNode document;
    try {
      var source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
      try (var parser = YAML.createParser(source)) {
        document = YAML.readTree(parser);
        if (document == null || parser.nextToken() != null)
          return result(CheckStatus.FAIL, "Exactly one proposal document is required");
      }
    } catch (Exception e) {
      return result(CheckStatus.FAIL, "Proposal must be valid UTF-8 JSON/YAML with unique keys and one document");
    }
    if (schema == null) return result(CheckStatus.BLOCKED, "Bundled proposal schema could not be initialized");
    try {
      var errors = schema.validate(document,
          context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)));
      if (errors.isEmpty()) return result(CheckStatus.PASS,
          "Bundled context-pack 1.3 schema; Draft 2020-12; proposal SHA-256 "
              + org.integratedmodelling.common.review.ProposalCandidateBinding.digest(bytes)
              + "; structure only, no scientific or semantic approval");
      var messages = errors.stream().limit(MAX_MESSAGES).map(error -> {
        String message = error.getInstanceLocation() + " [" + error.getKeyword() + "]: " + error.getMessage();
        return message.length() <= MAX_MESSAGE_CHARS ? message : message.substring(0, MAX_MESSAGE_CHARS) + "…";
      }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
      if (errors.size() > MAX_MESSAGES) messages.add("Further schema diagnostics omitted");
      return new Check(CheckKind.DOCUMENT_SCHEMA, CheckStatus.FAIL, List.copyOf(messages));
    } catch (RuntimeException e) {
      return result(CheckStatus.BLOCKED, "Proposal schema validation could not complete");
    }
  }

  private static Check result(CheckStatus status, String message) {
    return new Check(CheckKind.DOCUMENT_SCHEMA, status, List.of(message));
  }
}
