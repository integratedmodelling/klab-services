package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConceptCopyTest {
  @Test
  void replacingSemanticsRefreshesAbstractStatusAndSelectorsDoNotMutateCanonicalConcept() {
    var concept = new ConceptImpl();
    concept.setAbstract(true);
    assertTrue(concept.is(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT));
    var observable = new ObservableImpl();
    observable.setSemantics(concept);
    assertTrue(observable.isAbstract());
    observable.setSemantics((org.integratedmodelling.klab.api.knowledge.Concept) new ConceptImpl());
    assertFalse(observable.isAbstract());
    var selected = concept.withSelectors(java.util.Set.of(
        org.integratedmodelling.klab.api.knowledge.SemanticType.ANY));
    assertTrue(selected.is(org.integratedmodelling.klab.api.knowledge.SemanticType.ANY));
    assertFalse(concept.is(org.integratedmodelling.klab.api.knowledge.SemanticType.ANY));
    assertTrue(selected.isAbstract());
    concept.setAbstract(false);
    assertFalse(concept.is(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT));
  }

  @Test
  void copiesAndCollectiveVariantsKeepIdentityAndAbstractStatus() {
    var concept = new ConceptImpl();
    concept.setUrn("test:Thing");
    concept.setName("Thing");
    concept.setNamespace("test");
    concept.setReferenceName("test_thing");
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
