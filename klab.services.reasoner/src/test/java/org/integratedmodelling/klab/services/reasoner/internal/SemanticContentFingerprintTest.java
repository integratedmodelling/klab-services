package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationStrategiesImpl;
import org.junit.jupiter.api.Test;

class SemanticContentFingerprintTest {
  private WorldviewImpl world() {
    var w = new WorldviewImpl(); w.setWorldviewId("stable-id"); w.setUrn("world");
    var o = new KimOntologyImpl(); o.setUrn("world"); o.setSourceCode("ontology world;");
    w.setOntologies(new ArrayList<>(List.of(o)));
    var s = new KimObservationStrategiesImpl(); s.setUrn("essential"); s.setSourceCode("strategy original");
    w.setObservationStrategies(new ArrayList<>(List.of(s))); return w;
  }
  private String fingerprint(WorldviewImpl w, Map<String,byte[]> core, String compiler, Object authority, Object lists) {
    return SemanticContentFingerprint.compute(w, core, Map.of("odo", "https://example.org/odo.owl"),
        compiler, "operand-contract", authority, lists);
  }
  @Test void deterministicAcrossRestartMapOrderAndIncidentalMetadata() {
    var a = world(); var b = world();
    a.getMetadata().put("captureTime", 100); b.getMetadata().put("captureTime", 900);
    var coreA = new LinkedHashMap<String,byte[]>(); coreA.put("b.owl", new byte[]{2}); coreA.put("a.owl", new byte[]{1});
    var coreB = new LinkedHashMap<String,byte[]>(); coreB.put("a.owl", new byte[]{1}); coreB.put("b.owl", new byte[]{2});
    assertEquals(fingerprint(a,coreA,"compiler",Map.of("a",1,"b",2),List.of()),
        fingerprint(b,coreB,"compiler",Map.of("b",2,"a",1),List.of()));
    assertTrue(fingerprint(a,coreA,"compiler",Map.of(),List.of()).matches("semantic-content-v2:[a-f0-9]{64}"));
  }
  @Test void reconstructedWorldviewUsesSemanticNamespaceRatherThanGeneratedInstanceIdentity() {
    var a=world(); var b=world();
    a.setWorldviewId("_uu_wv:first-resource-incarnation");
    b.setWorldviewId("_uu_wv:second-resource-incarnation");
    var core=Map.of("odo.owl",new byte[]{1});
    var authority=Map.of("provider","fixed","configuration",Map.of("worldview","world"));
    var codelists=Map.of("codes",List.of("A","B"));
    String original=fingerprint(a,core,"compiler",authority,codelists);
    assertEquals(original,fingerprint(b,core,"compiler",authority,codelists));
    // Instance/commitment guards remain independent; changing the semantic namespace does not.
    assertNotEquals(a.getWorldviewId(),b.getWorldviewId());
    b.setUrn("other-world");
    assertNotEquals(original,fingerprint(b,core,"compiler",authority,codelists));
    b.setUrn("world");
    assertNotEquals(original,fingerprint(b,core,"compiler",Map.of("provider","changed"),codelists));
    Collections.reverse(b.getOntologies());
    ((KimOntologyImpl)b.getOntologies().getFirst()).setSourceCode("ontology changed;");
    assertNotEquals(original,fingerprint(b,core,"compiler",authority,codelists));
  }
  @Test void namespaceIsRequiredAndDocumentOrderRemainsSemantic() {
    var w=world(); var core=Map.of("odo.owl",new byte[]{1});
    var second=new KimOntologyImpl(); second.setUrn("extension"); second.setSourceCode("ontology extension;");
    w.getOntologies().add(second);
    var original=fingerprint(w,core,"compiler",Map.of(),List.of());
    Collections.reverse(w.getOntologies());
    assertNotEquals(original,fingerprint(w,core,"compiler",Map.of(),List.of()));
    w.setUrn(null);
    assertThrows(IllegalArgumentException.class,()->fingerprint(w,core,"compiler",Map.of(),List.of()));
    w.setUrn(" ");
    assertThrows(IllegalArgumentException.class,()->fingerprint(w,core,"compiler",Map.of(),List.of()));
  }
  @Test void bindingOffsetsAndHostingUrlsAreIncidentalButNamespaceConfigurationIsSemantic() throws Exception {
    var a=world(); var b=world();
    var descriptor=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor(
        "test.provider",org.integratedmodelling.klab.api.data.Version.create("1.0.0"),true,true,List.of(),List.of());
    var version=org.integratedmodelling.klab.api.data.Version.create("1.0.0");
    a.setAuthorityBindings(new ArrayList<>(List.of(new org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding(
        "TAXA","world:Identity","world",1,2,"source",descriptor,"component",version,new java.net.URL("http://one/")))));
    b.setAuthorityBindings(new ArrayList<>(List.of(new org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding(
        "TAXA","world:Identity","world",20,40,"source",descriptor,"component",version,new java.net.URL("http://two/")))));
    var core=Map.of("odo.owl",new byte[]{1});
    assertEquals(fingerprint(a,core,"compiler",Map.of(),List.of()),fingerprint(b,core,"compiler",Map.of(),List.of()));
    var first=new org.integratedmodelling.klab.api.services.Authority.ConfigurationRequest("world","TAXA","world:Identity",
        Map.of("urn","test.provider","codelists",Map.of("species","world.species")));
    var second=new org.integratedmodelling.klab.api.services.Authority.ConfigurationRequest("world","TAXA","world:Identity",
        Map.of("urn","test.provider","codelists",Map.of("species","world.other")));
    assertNotEquals(fingerprint(a,core,"compiler",first,List.of()),fingerprint(a,core,"compiler",second,List.of()));
  }

  @Test void everySemanticInputChangesIdentityAndArbitraryParameterNamesAreRetained() {
    var w=world(); var core=Map.of("odo.owl",new byte[]{1});
    String original=fingerprint(w,core,"compiler",Map.of("parameters",Map.of("timestamp",1)),List.of("A"));
    assertNotEquals(original,fingerprint(w,core,"compiler",Map.of("parameters",Map.of("timestamp",2)),List.of("A")));
    assertNotEquals(original,fingerprint(w,core,"compiler2",Map.of("parameters",Map.of("timestamp",1)),List.of("A")));
    assertNotEquals(original,fingerprint(w,Map.of("odo.owl",new byte[]{2}),"compiler",Map.of("parameters",Map.of("timestamp",1)),List.of("A")));
    assertNotEquals(original,fingerprint(w,core,"compiler",Map.of("parameters",Map.of("timestamp",1)),List.of("B")));
    ((KimObservationStrategiesImpl)w.getObservationStrategies().iterator().next()).setSourceCode("strategy changed");
    assertNotEquals(original,fingerprint(w,core,"compiler",Map.of("parameters",Map.of("timestamp",1)),List.of("A")));
    w=world(); ((KimOntologyImpl)w.getOntologies().getFirst()).setSourceCode("ontology changed");
    assertNotEquals(original,fingerprint(w,core,"compiler",Map.of("parameters",Map.of("timestamp",1)),List.of("A")));
  }
}
