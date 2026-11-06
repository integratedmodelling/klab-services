package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class QuantitativeContainmentSyntaxTest {
  static KimConceptImpl leaf(String name,SemanticType type) {
    var ret=new KimConceptImpl(); ret.setName("test:"+name);
    ret.setType(type==SemanticType.IDENTITY ? EnumSet.of(type,SemanticType.PREDICATE)
        : EnumSet.of(type,SemanticType.QUALITY,SemanticType.OBSERVABLE,SemanticType.QUANTIFIABLE,SemanticType.EXTENSIVE)); return ret;
  }
  @Test void operandsHaveIndependentOrderedSlotsAndDetachedCopies() {
    var quantity=leaf("Volume",SemanticType.VOLUME);
    quantity.setPortionIdentity(leaf("Oxygen",SemanticType.IDENTITY));
    quantity.setWholeIdentity(leaf("Air",SemanticType.IDENTITY));
    var definition="test:Oxygen within test:Air test:Volume";
    assertEquals(definition,quantity.finalizeDefinition());
    assertNull(quantity.getInherent());assertTrue(quantity.getTraits().isEmpty());
    var visited=new ArrayList<String>();
    var copy=quantity.mapSyntax(List.of(),(path,node)->visited.add(String.join("/",path)));
    assertTrue(visited.contains("portionIdentity"));assertTrue(visited.contains("wholeIdentity"));
    assertNotSame(quantity.getPortionIdentity(),copy.getPortionIdentity());
    assertEquals(definition,copy.finalizeDefinition());
    copy.setPortionIdentity(leaf("Air",SemanticType.IDENTITY));
    copy.setWholeIdentity(leaf("Oxygen",SemanticType.IDENTITY));
    assertNotEquals(quantity.getUrn(),copy.finalizeDefinition());
    assertEquals(definition,quantity.removeComponents().getUrn());
  }
  @Test void incompleteTransportCannotSilentlyBecomeAnOrdinaryMeasurement() {
    var quantity=leaf("Volume",SemanticType.VOLUME);
    quantity.setPortionIdentity(leaf("Oxygen",SemanticType.IDENTITY));
    assertThrows(IllegalStateException.class,quantity::finalizeDefinition);
  }
}
