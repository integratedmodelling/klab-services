package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.WorldviewCommitment;

/** Published discovery data: no references to a changing worldview or its source/authority maps. */
public record SemanticCapabilitiesSnapshot(
    long generation, String worldviewId, String worldviewUrn, boolean consistent,
    String authorityBindingsJson, WorldviewCommitment commitment) {
  private static final ObjectMapper MAPPER = JacksonConfiguration.newObjectMapper();
  private static final TypeReference<List<Worldview.AuthorityBinding>> BINDINGS =
      new TypeReference<>() {};

  public static SemanticCapabilitiesSnapshot initial() {
    return new SemanticCapabilitiesSnapshot(0, null, null, false, "[]", null);
  }

  public static SemanticCapabilitiesSnapshot capture(
      long generation, String worldviewId, String worldviewUrn, boolean consistent,
      List<Worldview.AuthorityBinding> bindings, WorldviewCommitment commitment) {
    try {
      // Authority descriptors contain mutable Version and list values. Freeze them too, and
      // decode an owned DTO per response so an in-process caller cannot mutate the publication.
      return new SemanticCapabilitiesSnapshot(generation, worldviewId, worldviewUrn, consistent,
          MAPPER.writeValueAsString(bindings), commitment);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot publish authority discovery metadata", e);
    }
  }

  public List<Worldview.AuthorityBinding> authorityBindings() {
    try {
      return MAPPER.readValue(authorityBindingsJson, BINDINGS);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot read published authority discovery metadata", e);
    }
  }
}
