package org.integratedmodelling.klab.api.services.reasoner.objects;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SemanticDependencyWitnessTest {
  private static final String BASE="semantic-content-v2:"+"a".repeat(64),HASH="b".repeat(64),PROOF="c".repeat(64);
  private SemanticDependencyWitness witness(long generation,long expiry,SemanticDependencyWitness.Kind kind,String proof) {
    return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "deployment",generation,BASE,HASH,List.of(new SemanticDependencyWitness.AuthorityUse("TAXA",kind,proof,expiry)),null);
  }
  @Test void normalizedInputHashIsStableAndCannotBeAssertedByCaller() {
    var a=new SemanticDependencyRequest("deployment",1,BASE,List.of(" w:Tree ","w:Code","w:Tree"));
    var b=new SemanticDependencyRequest("deployment",99,BASE,List.of("w:Code","w:Tree"));
    assertEquals(List.of("w:Code","w:Tree"),a.obligations());assertEquals(a.inputHash(),b.inputHash());
    assertNotEquals(a.inputHash(),new SemanticDependencyRequest("deployment",1,BASE,List.of("w:Other")).inputHash());
    assertThrows(IllegalArgumentException.class,()->new SemanticDependencyRequest("deployment",1,BASE,Arrays.asList((String)null)));
  }
  @Test void unchangedContentCanBeFreshlyRevalidatedAcrossNumericGenerationAndExpiryChanges() {
    var old=witness(1,10,SemanticDependencyWitness.Kind.CAPTURED,PROOF);
    var current=witness(9,100,SemanticDependencyWitness.Kind.CAPTURED,PROOF);
    assertTrue(SemanticDependencyWitness.revalidates(old,current,50));
    assertFalse(SemanticDependencyWitness.revalidates(old,current,100));
    assertFalse(SemanticDependencyWitness.revalidates(old,witness(1,100,SemanticDependencyWitness.Kind.CAPTURED,"d".repeat(64)),50));
  }
  @Test void unknownLegacyUnversionedDuplicateOrMalformedProofsNeverCertify() {
    var good=witness(1,Long.MAX_VALUE,SemanticDependencyWitness.Kind.SNAPSHOT,PROOF);
    assertTrue(good.completeAt(10));
    assertFalse(witness(1,Long.MAX_VALUE,SemanticDependencyWitness.Kind.UNVERSIONED,null).completeAt(10));
    assertFalse(witness(1,Long.MAX_VALUE,SemanticDependencyWitness.Kind.SNAPSHOT,"bad").completeAt(10));
    var duplicate=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "deployment",1,BASE,HASH,List.of(good.authorities().getFirst(),good.authorities().getFirst()),null);
    assertFalse(duplicate.completeAt(10));
    var legacy=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "deployment",1,"semantic-content-v1:"+"a".repeat(64),HASH,List.of(),null);
    assertFalse(legacy.completeAt(10));assertFalse(SemanticDependencyWitness.revalidates(legacy,good,10));
  }
}
