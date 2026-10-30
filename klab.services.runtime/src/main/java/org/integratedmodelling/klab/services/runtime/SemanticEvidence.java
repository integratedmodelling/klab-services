package org.integratedmodelling.klab.services.runtime;

import java.util.Objects;
import org.integratedmodelling.klab.api.provenance.Activity;

/** Separates an active execution fence from certification of persisted semantic evidence. */
final class SemanticEvidence {
  private SemanticEvidence() {}

  static boolean matches(Activity activity, String reasonerId, long revision,
      String fingerprint, String semanticSessionId, boolean currentExecution,
      org.integratedmodelling.klab.services.scopes.ServiceContextScope scope) {
    return matches(activity,reasonerId,revision,fingerprint,semanticSessionId,currentExecution,scope,null);
  }

  static boolean matches(Activity activity,String reasonerId,long revision,String fingerprint,
      String semanticSessionId,boolean currentExecution,
      org.integratedmodelling.klab.services.scopes.ServiceContextScope scope,
      java.util.Map<String,Object> diagnostic) {
    boolean owner=Objects.equals(reasonerId,activity.getMetadata().get(RuntimeService.COLLECTIVE_REASONER));
    if(diagnostic!=null) {diagnostic.put("ownerMatches",owner);diagnostic.put("currentExecution",currentExecution);}
    if(!owner)return false;
    if(currentExecution) {
      boolean active=activeMatches(activity,reasonerId,revision,fingerprint);
      boolean session=Objects.equals(semanticSessionId,activity.getMetadata().get(RuntimeService.COLLECTIVE_SESSION));
      if(diagnostic!=null){diagnostic.put("activeFenceMatches",active);diagnostic.put("activeSessionMatches",session);}
      return active&&session;
    }
    var proof=SemanticExecutionEvidence.decode(activity);
    boolean witness=proof!=null&&proof.witness()!=null;
    boolean proofOwner=witness&&Objects.equals(reasonerId,proof.witness().serviceId());
    boolean base=witness&&Objects.equals(fingerprint,proof.witness().baseFingerprint());
    if(diagnostic!=null){diagnostic.put("witnessPresent",witness);diagnostic.put("witnessOwnerMatches",proofOwner);diagnostic.put("witnessBaseMatches",base);}
    if(!witness||!proofOwner||!base)return false;
    boolean verified=SemanticExecutionEvidence.verified(proof,scope);
    if(diagnostic!=null){diagnostic.put("verified",verified);diagnostic.put("verification",SemanticExecutionEvidence.verificationDiagnostic());}
    return verified;
  }

  private static boolean activeMatches(Activity activity, String reasonerId, long revision,
      String fingerprint) {
    if (reasonerId == null || reasonerId.isBlank() || revision < 0
        || !Objects.equals(reasonerId, activity.getMetadata().get(RuntimeService.COLLECTIVE_REASONER)))
      return false;
    var previous = activity.getMetadata().get(RuntimeService.COLLECTIVE_FINGERPRINT);
    var counter = activity.getMetadata().get(RuntimeService.COLLECTIVE_REVISION);
    return counter instanceof Number n && n.longValue() == revision
        && Objects.equals(fingerprint, previous);
  }
}
