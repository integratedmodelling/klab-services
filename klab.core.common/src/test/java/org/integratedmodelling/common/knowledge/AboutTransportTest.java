package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class AboutTransportTest {
  private KimConceptImpl leaf(String name) {
    var value = new KimConceptImpl(); value.setName("test:" + name);
    value.setType(EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE));
    value.finalizeDefinition(); return value;
  }

  @Test void canonicalEncodingPreservesIndependentTargetsAndBearerIsUnchanged() {
    var value = leaf("Knowledge"); value.getAbout().add(leaf("Earth")); value.getAbout().add(leaf("Ocean"));
    value.resetDefinition();
    assertEquals("test:Knowledge about test:Earth about test:Ocean", value.finalizeDefinition());
    java.util.Collections.reverse(value.getAbout()); value.resetDefinition();
    assertEquals("test:Knowledge about test:Earth about test:Ocean", value.finalizeDefinition());
    assertNull(value.getInherent()); assertTrue(value.getModifiers().isEmpty());
    assertFalse(value.is(SemanticType.OBSERVABLE));
  }

  @Test void jacksonTransportRetainsAboutTargets() throws Exception {
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var value = leaf("Knowledge"); value.getAbout().add(leaf("Earth")); value.getAbout().add(leaf("Ocean")); value.resetDefinition();
    var restored = (KimConceptImpl) mapper.readValue(mapper.writeValueAsString(value), org.integratedmodelling.klab.api.lang.kim.KimConcept.class);
    assertEquals(2, restored.getAbout().size());
    assertEquals(value.finalizeDefinition(), restored.finalizeDefinition());
    assertNull(restored.getInherent());
  }
}
