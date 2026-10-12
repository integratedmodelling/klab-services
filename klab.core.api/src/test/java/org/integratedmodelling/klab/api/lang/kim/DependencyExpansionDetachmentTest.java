package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.junit.jupiter.api.Test;

class DependencyExpansionDetachmentTest {
  private static KimConceptImpl predicate(String name, boolean any) {
    var p = new KimConceptImpl(); p.setName(name);
    p.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE));
    if (any) p.getType().add(SemanticType.ANY);
    return p;
  }

  private static KimObservableImpl source() {
    var o = new KimObservableImpl(); o.setSemantics(predicate("ns:A", true));
    o.setNamespace("source"); o.setProjectName("project"); o.setServiceId("service");
    o.setOffsetInDocument(21); o.setLength(12); o.setUnit("m");
    return o;
  }

  private static List<KimObservableImpl> expand(KimObservableImpl o) {
    return DependencyExpansion.expand(o, name -> predicate(name, false),
        request -> new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,
            List.of(predicate("ns:A1", false), predicate("ns:A2", false))));
  }

  @Test void conceptValuedInlineAndDefaultPositionsAreExpanded() {
    var o = source(); o.setSemantics(predicate("ns:Concrete", false));
    o.setValue(predicate("ns:Inline", true)); o.setDefaultValue(predicate("ns:Default", true));
    var results = expand(o);
    assertEquals(4, results.size());
    for (var r : results) {
      assertFalse(((KimConcept) r.getValue()).is(SemanticType.ANY));
      assertFalse(((KimConcept) r.getDefaultValue()).is(SemanticType.ANY));
      assertTrue(r.isOptional());
    }
    assertEquals("ns:Inline", ((KimConcept) o.getValue()).getName());
    assertEquals("ns:Default", ((KimConcept) o.getDefaultValue()).getName());
  }

  @Test void rangeAndNestedMetadataDoNotAliasOriginalOrSibling() {
    var o = source(); o.setRange(new NumericRangeImpl(0.0, 10.0, false, true));
    o.getMetadata().put("nested", new ArrayList<>(List.of("original")));
    o.getSemantics().getMetadata().put("nested", new ArrayList<>(List.of("original")));
    var results = expand(o); var first = results.getFirst(); var second = results.getLast();
    first.getRange().setLowerExclusive(true);
    ((List<String>) first.getMetadata().get("nested")).add("changed");
    ((List<String>) first.getSemantics().getMetadata().get("nested")).add("changed");
    for (var untouched : List.of(o, second)) {
      assertFalse(untouched.getRange().isLowerExclusive());
      assertEquals(List.of("original"), untouched.getMetadata().get("nested"));
      assertEquals(List.of("original"), untouched.getSemantics().getMetadata().get("nested"));
    }
  }

  @Test void allMutablePayloadsAreDetachedIncludingNestedAnnotationsAndNotifications() {
    var o = source();
    o.setValue(new ArrayList<>(List.of(new int[]{1})));
    o.setDefaultValue(new LinkedHashMap<>(Map.of("key", new ArrayList<>(List.of("default")))));
    o.setVersion(new Version("1.2.3"));
    o.getAnnotations().add(Annotation.of("test", "values", new ArrayList<>(List.of("original"))));
    var note = new NotificationImpl("original", Notification.Level.Info);
    var context = new NotificationImpl.LexicalContextImpl(); context.setOffsetInDocument(7);
    note.setLexicalContext(context); o.getNotifications().add(note);
    o.getValueOperators().add(Pair.of(ValueOperator.BY, new ArrayList<>(List.of("operator"))));
    o.getSemantics().getValueOperators().add(Pair.of(ValueOperator.BY, new int[]{2}));
    var results = expand(o); var first = results.getFirst();
    ((int[]) ((List<?>) first.getValue()).getFirst())[0] = 9;
    ((List<String>) ((Map<?, ?>) first.getDefaultValue()).get("key")).clear();
    first.getVersion().setMajor(9);
    ((List<String>) first.getAnnotations().getFirst().get("values")).clear();
    ((NotificationImpl) first.getNotifications().getFirst()).setMessage("changed");
    ((NotificationImpl.LexicalContextImpl) first.getNotifications().getFirst().getLexicalContext()).setOffsetInDocument(99);
    ((List<?>) first.getValueOperators().getFirst().getSecond()).clear();
    ((int[]) first.getSemantics().getValueOperators().getFirst().getSecond())[0] = 9;
    for (var untouched : List.of(o, results.getLast())) {
      assertEquals(1, ((int[]) ((List<?>) untouched.getValue()).getFirst())[0]);
      assertEquals(List.of("default"), ((Map<?, ?>) untouched.getDefaultValue()).get("key"));
      assertEquals(1, untouched.getVersion().getMajor());
      assertEquals(List.of("original"), untouched.getAnnotations().getFirst().get("values"));
      assertEquals("original", untouched.getNotifications().getFirst().getMessage());
      assertEquals(7, untouched.getNotifications().getFirst().getLexicalContext().getOffsetInDocument());
      assertEquals(List.of("operator"), untouched.getValueOperators().getFirst().getSecond());
      assertEquals(2, ((int[]) untouched.getSemantics().getValueOperators().getFirst().getSecond())[0]);
    }
  }

  @Test void serializedSyntaxRetainsProvenanceAndCanStillExpand() throws Exception {
    var o = source(); o.getAnnotations().add(Annotation.of("source"));
    var restored = roundTrip(o);
    assertEquals("project", restored.getProjectName());
    assertEquals("service", restored.getServiceId());
    assertEquals(21, restored.getOffsetInDocument());
    assertEquals(12, restored.getLength());
    assertEquals("source", restored.getAnnotations().getFirst().getName());
    var expanded = roundTrip(expand(restored).getFirst());
    assertTrue(expanded.isOptional());
    assertNull(expanded.getUrn());
    assertEquals("ns:A1", expanded.getSemantics().getName());
    assertEquals("project", expanded.getProjectName());
    assertEquals("m", expanded.getUnit());
  }

  private static KimObservableImpl roundTrip(KimObservableImpl o) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var out = new ObjectOutputStream(bytes)) { out.writeObject(o); }
    try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (KimObservableImpl) in.readObject();
    }
  }

  @Test void observableCopyConstructorDetachesSyntaxAndPayloadsDirectly() {
    var o = source(); o.setDefaultValue(predicate("ns:Default", true));
    o.setRange(new NumericRangeImpl(0.0, 1.0, false, false));
    o.getMetadata().put("list", new ArrayList<>(List.of(1)));
    var copy = new KimObservableImpl(o);
    ((KimConceptImpl) copy.getSemantics()).setName("ns:Changed");
    ((KimConceptImpl) copy.getDefaultValue()).getType().clear();
    copy.getRange().setLowerExclusive(true);
    ((List<?>) copy.getMetadata().get("list")).clear();
    assertEquals("ns:A", o.getSemantics().getName());
    assertTrue(((KimConcept) o.getDefaultValue()).is(SemanticType.ANY));
    assertFalse(o.getRange().isLowerExclusive());
    assertEquals(List.of(1), o.getMetadata().get("list"));
  }

  @Test void unknownMutablePayloadFailsRatherThanAliasing() {
    var o = source(); o.setDefaultValue(new Object());
    assertThrows(IllegalArgumentException.class, () -> expand(o));
  }

  @Test void malformedOrNonPredicateClassificationCandidatesAreRejected() {
    for (var flag : List.of(SemanticType.ANY, SemanticType.ALL, SemanticType.NONE,
        SemanticType.ABSTRACT, SemanticType.NOTHING)) {
      var invalid = predicate("ns:Invalid", false); invalid.getType().add(flag);
      assertThrows(IllegalArgumentException.class, () -> classification(invalid));
    }
    var nonPredicate = predicate("ns:Quality", false);
    nonPredicate.setType(EnumSet.of(SemanticType.QUALITY));
    assertThrows(IllegalArgumentException.class, () -> classification(nonPredicate));
    for (var name : Arrays.asList(null, "", " ")) {
      var invalid = predicate(name, false);
      assertThrows(IllegalArgumentException.class, () -> classification(invalid));
    }
    assertThrows(NullPointerException.class, () -> new DependencyExpansion.Classification(
        DependencyExpansion.Status.SUCCESS, Arrays.asList((KimConcept) null)));
  }

  @Test void compoundCandidateCannotSilentlyLoseItsClauses() {
    var invalid = predicate("ns:Decorated", false);
    invalid.setInherent(predicate("ns:Context", false));
    assertThrows(IllegalArgumentException.class, () -> classification(invalid));
    invalid.setInherent(null); invalid.setNegated(true);
    assertThrows(IllegalArgumentException.class, () -> classification(invalid));
  }

  private static DependencyExpansion.Classification classification(KimConcept candidate) {
    return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS, List.of(candidate));
  }

  @Test void excludedParentIsNeverCopiedByExpansionOrOrdinaryObservableCopy() {
    var o = source();
    var unrelatedParent = predicate("ns:UnrelatedParent", false);
    unrelatedParent.getMetadata().put("not-retained", new Object());
    ((KimConceptImpl) o.getSemantics()).setParent(unrelatedParent);
    var defaultValue = predicate("ns:Default", true); defaultValue.setParent(unrelatedParent);
    o.setDefaultValue(defaultValue);
    var operand = predicate("ns:Operand", true); operand.setParent(unrelatedParent);
    o.getValueOperators().add(Pair.of(ValueOperator.BY, operand));
    var plainCopy = new KimObservableImpl(o);
    assertNull(plainCopy.getSemantics().getParent());
    var outputs = expand(o);
    assertEquals(8, outputs.size());
    for (var output : outputs) {
      assertNull(output.getSemantics().getParent());
      assertNull(((KimConcept) output.getDefaultValue()).getParent());
      assertNull(((KimConcept) output.getValueOperators().getFirst().getSecond()).getParent());
    }
    assertSame(unrelatedParent, o.getSemantics().getParent());
  }

  @Test void ordinaryCopyPreservesDefinitionsWhileMappedCopyInvalidatesThem() {
    var o = source();
    ((KimConceptImpl) o.getSemantics()).setUrn("any ns:A");
    o.setUrn("original observable");
    var copy = new KimObservableImpl(o);
    assertEquals("any ns:A", copy.getSemantics().getUrn());
    assertEquals("original observable", copy.getUrn());
    assertNotSame(o.getSemantics(), copy.getSemantics());
    var expanded = expand(o).getFirst();
    assertNull(expanded.getSemantics().getUrn());
    assertNull(expanded.getUrn());
    assertEquals("any ns:A", o.getSemantics().getUrn());
  }

  private static final class CountedPayload implements Serializable {
    private static final Map<String, Integer> copies = new HashMap<>();
    private final String slot;
    private CountedPayload(String slot) { this.slot = slot; }
    @Serial private void writeObject(ObjectOutputStream out) throws IOException {
      copies.merge(slot, 1, Integer::sum);
      out.defaultWriteObject();
    }
  }

  @Test void mappedCopyCopiesEachRetainedPayloadOnce() {
    var o = source();
    o.getSemantics().getMetadata().put("payload", new CountedPayload("semantics"));
    o.setDefaultValue(new CountedPayload("default"));
    o.getValueOperators().add(Pair.of(ValueOperator.BY, new CountedPayload("operator")));
    CountedPayload.copies.clear();
    var copy = new KimObservableImpl(o, (path, node) -> {});
    assertEquals(Map.of("semantics", 1, "default", 1, "operator", 1), CountedPayload.copies);
    assertNotSame(o.getDefaultValue(), copy.getDefaultValue());
  }
}
