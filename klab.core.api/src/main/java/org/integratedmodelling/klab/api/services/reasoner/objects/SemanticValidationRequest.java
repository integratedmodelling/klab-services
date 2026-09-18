package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.integratedmodelling.klab.api.lang.kim.*;

/** One parsed document snapshot. Validation never publishes an ontology or changes its source. */
public class SemanticValidationRequest implements Serializable {
  private KimNamespace namespace;
  private KimOntology ontology;
  private String documentVersion;
  private long knowledgeRevision = -1;

  public static SemanticValidationRequest of(KlabDocument<?> document, String version) {
    var ret = new SemanticValidationRequest();
    if (document instanceof KimNamespace namespace) ret.setNamespace(namespace);
    else if (document instanceof KimOntology ontology) ret.setOntology(ontology);
    else throw new IllegalArgumentException("Semantic validation supports namespaces and ontologies");
    ret.setDocumentVersion(version);
    return ret;
  }

  public KlabDocument<?> document() {
    if ((namespace == null) == (ontology == null))
      throw new IllegalArgumentException("Specify exactly one namespace or ontology");
    return namespace == null ? ontology : namespace;
  }

  /** Hash exact source text, including whitespace: source offsets are revision-specific. */
  public static String sourceHash(String source) {
    if (source == null) return null;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  public KimNamespace getNamespace() { return namespace; }
  public void setNamespace(KimNamespace namespace) { this.namespace = namespace; }
  public KimOntology getOntology() { return ontology; }
  public void setOntology(KimOntology ontology) { this.ontology = ontology; }
  public String getDocumentVersion() { return documentVersion; }
  public void setDocumentVersion(String version) { this.documentVersion = version; }
  /** Optional expected reasoner revision; -1 accepts the current revision. */
  public long getKnowledgeRevision() { return knowledgeRevision; }
  public void setKnowledgeRevision(long revision) { this.knowledgeRevision = revision; }
}
