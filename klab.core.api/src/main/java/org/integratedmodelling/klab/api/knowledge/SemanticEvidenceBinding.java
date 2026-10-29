package org.integratedmodelling.klab.api.knowledge;

import java.util.List;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness;

/** Runtime accounting of a complete execution signature, distinct from its Reasoner proof.
 * Missing accounting is UNKNOWN, never evidence that no authority was used. */
public record SemanticEvidenceBinding(String contractVersion, boolean complete,
    List<String> obligations, SemanticDependencyWitness witness) implements java.io.Serializable {
  public static final String DATA_KEY = "collectiveSemanticEvidence";
  public static final String VERSION = "semantic-execution-evidence-v1";
  public SemanticEvidenceBinding {
    obligations = new SemanticDependencyRequest(null, -1, null, obligations).obligations();
    if (!VERSION.equals(contractVersion)) throw new IllegalArgumentException("Unknown semantic execution evidence");
  }
}
