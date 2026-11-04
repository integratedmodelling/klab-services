package org.integratedmodelling.klab.services.runtime.neo4j;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
class ConsequenceReadSetDiagnosticsTest {
 @Test void valuesAreNeverIncludedAndBaselineRemainsImmutable() {
  var a=new ConsequenceAssetSnapshot(41,"n41",List.of("Observation"),Map.of("payload","SECRET-BEFORE"),"SECRET-GEOMETRY",null);
  var b=new ConsequenceAssetSnapshot(41,"n41",List.of("Observation"),Map.of("payload","SECRET-AFTER","https://secret.invalid",42),"CHANGED",null);
  var before=new ConsequenceReadSet("a",List.of(a),List.of(),List.of());var after=new ConsequenceReadSet("a",List.of(b),List.of(),List.of());
  var facts=ConsequenceReadSetDiagnostics.differences(before,after);
  assertEquals(List.of("asset:41:geometry","asset:41:property:[redacted-key]","asset:41:property:payload"),facts);
  assertFalse(facts.toString().contains("SECRET"));assertFalse(facts.toString().contains("https"));
  assertEquals("SECRET-BEFORE",before.assets().getFirst().properties().get("payload"));
  assertTrue(ConsequenceReadSetDiagnostics.differences(before,before).isEmpty());
 }
}
