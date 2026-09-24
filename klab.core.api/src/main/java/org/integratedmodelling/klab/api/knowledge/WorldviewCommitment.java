package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Content-addressed semantic environment. Ontology keys are resolvable URNs, values source hashes. */
public record WorldviewCommitment(int version, String worldviewId, Map<String, String> ontologies)
    implements Serializable {
  public WorldviewCommitment {
    if (version != 1 || worldviewId == null || worldviewId.isBlank() || ontologies == null || ontologies.isEmpty())
      throw new IllegalArgumentException("A loaded, fingerprinted worldview is required for keyed storage");
    ontologies = Collections.unmodifiableMap(new TreeMap<>(ontologies));
    ontologies.forEach((urn, hash) -> {
      if (urn.isBlank() || hash == null || !hash.matches("[0-9a-fA-F]{64}"))
        throw new IllegalArgumentException("Invalid ontology commitment: " + urn);
    });
  }
  public String fingerprint() {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (String field : fields()) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private List<String> fields() {
    var fields = new ArrayList<String>(); fields.add(Integer.toString(version)); fields.add(worldviewId);
    ontologies.forEach((urn, hash) -> { fields.add(urn); fields.add(hash); }); return fields;
  }
}
