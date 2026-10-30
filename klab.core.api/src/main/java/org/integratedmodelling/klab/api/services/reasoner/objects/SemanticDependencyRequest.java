package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact semantic obligations only. The caller cannot assert that these cover a whole execution. */
public record SemanticDependencyRequest(String expectedServiceId, long expectedKnowledgeRevision,
    String expectedBaseFingerprint, List<String> obligations) implements java.io.Serializable {
  public SemanticDependencyRequest {
    if (obligations == null) obligations = List.of();
    if (obligations.size() > 4096) throw new IllegalArgumentException("Too many semantic obligations");
    var normalized = new TreeSet<String>();
    long total=0;
    for (var obligation : obligations) {
      if (obligation == null || obligation.isBlank() || obligation.length() > 65536)
        throw new IllegalArgumentException("Invalid semantic obligation");
      if((total+=obligation.length())>4*1024*1024)throw new IllegalArgumentException("Semantic obligations exceed bounds");
      normalized.add(obligation.trim());
    }
    obligations = List.copyOf(normalized);
  }
  /** Versioned length-prefixed encoding, independent of transport object/map ordering. */
  public String inputHash() {
    try {
      var bytes = new java.io.ByteArrayOutputStream();
      var out = new java.io.DataOutputStream(bytes);
      out.writeUTF("semantic-obligations-v1");
      out.writeInt(obligations.size());
      for (var term : obligations) { var encoded=term.getBytes(StandardCharsets.UTF_8); out.writeInt(encoded.length); out.write(encoded); }
      return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    } catch (Exception failure) { throw new IllegalStateException(failure); }
  }
}
