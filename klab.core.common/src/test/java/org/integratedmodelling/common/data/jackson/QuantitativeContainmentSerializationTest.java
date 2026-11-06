package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class QuantitativeContainmentSerializationTest {
  private KimConceptImpl leaf(String name,SemanticType type) {
    var c=new KimConceptImpl();c.setName("test:"+name);
    c.setType(type==SemanticType.IDENTITY ? EnumSet.of(type,SemanticType.PREDICATE)
        : EnumSet.of(type,SemanticType.QUALITY,SemanticType.OBSERVABLE,SemanticType.QUANTIFIABLE,SemanticType.EXTENSIVE));
    c.finalizeDefinition();return c;
  }
  @Test void jsonRetainsBothOrderedIdentitiesAndCanonicalDefinition() throws Exception {
    var c=leaf("Volume",SemanticType.VOLUME);
    c.setPortionIdentity(leaf("Oxygen",SemanticType.IDENTITY));
    c.setWholeIdentity(leaf("Air",SemanticType.IDENTITY));c.finalizeDefinition();
    var mapper=JacksonConfiguration.newObjectMapper();
    var copy=(KimConceptImpl)mapper.readValue(mapper.writeValueAsBytes(c),KimConcept.class);
    assertEquals("test:Oxygen",copy.getPortionIdentity().getUrn());
    assertEquals("test:Air",copy.getWholeIdentity().getUrn());
    copy.resetDefinition();assertEquals(c.getUrn(),copy.getUrn());
    assertTrue(copy.is(SemanticType.VOLUME));assertNull(copy.getInherent());
    var forward=org.integratedmodelling.common.knowledge.SemanticLabels.describe(copy,urn->null,false);
    copy.setPortionIdentity(leaf("Air",SemanticType.IDENTITY));
    copy.setWholeIdentity(leaf("Oxygen",SemanticType.IDENTITY));
    var reverse=org.integratedmodelling.common.knowledge.SemanticLabels.describe(copy,urn->null,false);
    assertNotEquals(forward,reverse);assertTrue(forward.toLowerCase().contains("oxygen within air"));
  }
}
