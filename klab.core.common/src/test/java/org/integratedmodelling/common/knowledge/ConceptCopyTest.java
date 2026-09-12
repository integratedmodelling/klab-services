package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConceptCopyTest {
  @Test
  void copiesAndCollectiveVariantsKeepIdentityAndAbstractStatus() {
    var concept = new ConceptImpl();
    concept.setUrn("test:Thing");
    concept.setServiceId("reasoner-test");
    concept.setNonSemanticId(ConceptImpl.NONSEMANTIC_SUBJECT_ID);
    concept.setAbstract(true);
    for (var copy : new ConceptImpl[] {new ConceptImpl(concept), concept.collective()}) {
      assertEquals(concept.getServiceId(), copy.getServiceId());
      assertEquals(concept.getNonSemanticId(), copy.getNonSemanticId());
      assertTrue(copy.isAbstract());
      assertTrue(ObservableImpl.promote(copy, null).isAbstract());
    }
  }
}
