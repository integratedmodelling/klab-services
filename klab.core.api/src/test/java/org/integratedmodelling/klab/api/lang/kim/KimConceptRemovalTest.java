package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class KimConceptRemovalTest {
  private KimConceptImpl concept(String name, SemanticType... types) {
    var result = new KimConceptImpl(); result.setName(name); result.setUrn(name);
    result.setType(java.util.EnumSet.copyOf(java.util.List.of(types))); return result;
  }

  @Test void directRoleRemovalPreservesOtherPredicatesAndInherentScope() {
    var tree = concept("test:Tree",SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
    var role = concept("test:Participant",SemanticType.ROLE,SemanticType.PREDICATE);
    var trait = concept("test:Beech",SemanticType.IDENTITY,SemanticType.PREDICATE);
    var forest = concept("test:Forest",SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
    tree.getRoles().add(role); tree.getTraits().add(trait); tree.setInherent(forest);
    assertTrue(tree.remove(role)); assertTrue(tree.getRoles().isEmpty());
    assertEquals(java.util.List.of(trait), tree.getTraits()); assertSame(forest, tree.getInherent());
    assertFalse(tree.remove(role));
  }

  @Test void removalClearsEveryDirectOccurrenceWithoutEditingTheSourceCopy() {
    var tree = concept("test:Tree",SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
    var predicate = concept("test:Marked",SemanticType.ATTRIBUTE,SemanticType.PREDICATE);
    tree.getTraits().add(predicate); tree.getRoles().add(predicate);
    var copy = (KimConceptImpl) tree.removeComponents();
    assertTrue(copy.remove(predicate)); assertTrue(copy.getTraits().isEmpty()); assertTrue(copy.getRoles().isEmpty());
    assertEquals(java.util.List.of(predicate), tree.getTraits()); assertEquals(java.util.List.of(predicate), tree.getRoles());
  }

  @Test void collectiveUnaryOperandsHaveOneEachAndOneGroupingAcrossTransparentWrappers() {
    var head = concept("test:Tree", SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE);
    var collective = new KimConceptImpl(); collective.setObservable(head); collective.setCollective(true);
    collective.setType(java.util.EnumSet.copyOf(head.getType()));
    collective.getTraits().add(concept("test:Beech", SemanticType.IDENTITY, SemanticType.PREDICATE));
    var wrapper = new KimConceptImpl(); wrapper.setObservable(collective); wrapper.setCollective(true);
    wrapper.setType(java.util.EnumSet.copyOf(head.getType()));
    var count = new KimConceptImpl(); count.setObservable(wrapper);
    count.setSemanticModifier(org.integratedmodelling.klab.api.lang.UnarySemanticOperator.COUNT);
    count.setType(java.util.EnumSet.of(SemanticType.NUMEROSITY, SemanticType.QUALITY, SemanticType.OBSERVABLE));
    assertEquals("count of (each test:Beech test:Tree)", count.finalizeDefinition());
    assertFalse(count.isCollective());
    assertTrue(count.is(SemanticType.QUALITY));
    assertTrue(count.is(SemanticType.NUMEROSITY));
    assertEquals("each test:Beech test:Tree", wrapper.finalizeDefinition());
    assertEquals(java.util.List.of(collective.getTraits().getFirst()), collective.getTraits());
  }

  @Test void collectiveWrappersWithOwnClausesAreNotDiscarded() {
    var head = concept("test:Tree", SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE);
    var collective = new KimConceptImpl();collective.setObservable(head);collective.setCollective(true);
    collective.setType(java.util.EnumSet.copyOf(head.getType()));
    var wrapper = new KimConceptImpl();wrapper.setObservable(collective);wrapper.setCollective(true);
    wrapper.setType(java.util.EnumSet.copyOf(head.getType()));
    wrapper.setInherent(concept("test:Forest", SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    assertTrue(wrapper.finalizeDefinition().contains(" of test:Forest"));
    assertSame(collective, wrapper.getObservable());
    assertEquals("test:Forest", wrapper.getInherent().getUrn());
  }

  @Test void freshDeepCollectiveWrappersDoNotDependOnFinalizationReadOrder() {
    for (boolean childFirst : new boolean[]{false, true}) {
      var leaf = concept("test:Tree", SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE);
      var nativeMembers = new KimConceptImpl();nativeMembers.setObservable(leaf);nativeMembers.setCollective(true);
      nativeMembers.setType(java.util.EnumSet.copyOf(leaf.getType()));
      KimConceptImpl operand = nativeMembers;
      for (int depth = 0; depth < 4; depth++) {
        var wrapper = new KimConceptImpl();wrapper.setObservable(operand);
        wrapper.setType(java.util.EnumSet.copyOf(leaf.getType()));operand = wrapper;
      }
      if (childFirst) operand.finalizeDefinition();
      var count = new KimConceptImpl();count.setObservable(operand);
      count.setSemanticModifier(org.integratedmodelling.klab.api.lang.UnarySemanticOperator.COUNT);
      count.setType(java.util.EnumSet.of(SemanticType.NUMEROSITY, SemanticType.QUALITY, SemanticType.OBSERVABLE));
      assertEquals("count of (each test:Tree)", count.finalizeDefinition());
      assertFalse(count.isCollective());assertTrue(operand.isCollective());
      count.resetDefinition();assertEquals("count of (each test:Tree)", count.finalizeDefinition());
      assertFalse(count.isCollective());
    }
  }
}
