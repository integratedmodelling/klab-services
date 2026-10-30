package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;

/** Versioned semantic input identity; distinct from admission keys and ontology commitments. */
public final class SemanticContentFingerprint {
  public static final String VERSION = "semantic-content-v2";
  private SemanticContentFingerprint() {}

  static String compute(Worldview worldview, Map<String,byte[]> core, Map<String,String> locators,
      String compiler, String operands, Object authorityContent, Object publishedCodelists) {
    if (worldview.getUrn() == null || worldview.getUrn().isBlank())
      throw new IllegalArgumentException("Semantic content requires a stable worldview namespace");
    var bindings = worldview.getAuthorityBindings().stream()
        .sorted(Comparator.comparing(Worldview.AuthorityBinding::localId))
        .map(b -> Arrays.asList(b.localId(), b.rootIdentity(), b.sourceOntology(), b.sourceHash(),
            b.provider(), b.componentUrn(), b.componentVersion())).toList();
    var coreHashes = new TreeMap<String,String>();
    core.forEach((name, bytes) -> coreHashes.put(name, sha256(bytes)));
    // Fixed typed fields only: arbitrary configuration/response fields are never stripped.
    // The Resources instance ID is generated on reconstruction. Keep it in commitment/
    // admission guards, not content identity. The semantic namespace and all typed inputs
    // below distinguish worldviews and changes within a worldview.
    return VERSION + ":" + sha256(canonical(Arrays.asList(VERSION,
        worldview.getUrn(), new TreeMap<>(SemanticEnvironmentFactory.sources(worldview)),
        SemanticEnvironmentFactory.sourceOrder(worldview), bindings, coreHashes,
        new TreeMap<>(locators), compiler, operands, authorityContent, publishedCodelists)));
  }
  static byte[] canonical(Object value) {
    try { return JacksonConfiguration.newObjectMapper()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(value); }
    catch (Exception e) { throw new IllegalArgumentException("Cannot encode semantic content", e); }
  }
  static String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
}
