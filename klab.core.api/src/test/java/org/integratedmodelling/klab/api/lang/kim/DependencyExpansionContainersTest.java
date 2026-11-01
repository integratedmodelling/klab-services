package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.junit.jupiter.api.Test;

class DependencyExpansionContainersTest {
  private static KimConceptImpl predicate(String name, boolean any) {
    var p = new KimConceptImpl(); p.setName(name); p.setUrn(name);
    p.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE));
    if (any) p.getType().add(SemanticType.ANY);
    return p;
  }

  private static KimObservableImpl source(Object value) {
    var o = new KimObservableImpl(); o.setSemantics(predicate("ns:Concrete", false));
    o.setDefaultValue(value); o.setNamespace("authored"); o.setUrn("original");
    return o;
  }

  private static List<KimObservableImpl> expand(KimObservableImpl o, List<List<String>> paths) {
    return DependencyExpansion.expand(o, name -> predicate(name, false), request -> {
      paths.add(request.identity().path());
      return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,
          List.of(predicate("ns:A1", false), predicate("ns:A2", false)));
    });
  }

  private static KimConceptImpl withExcludedParent() {
    var p = predicate("ns:A", true);
    var parent = predicate("ns:Unrelated", false);
    parent.getMetadata().put("excluded", new Object()); p.setParent(parent);
    return p;
  }

  @Test void nestedPredicatesWithoutParentsAreNotSkipped() {
    var paths = new ArrayList<List<String>>();
    var o = source(List.of(predicate("ns:A", true)));
    var result = expand(o, paths);
    assertEquals(2, result.size());
    assertEquals(List.of(List.of("defaultValue", "items", "0")), paths);
    assertEquals("ns:A1", ((KimConcept) ((List<?>) result.getFirst().getDefaultValue()).getFirst()).getName());
  }

  @Test void nestedCollectionsExcludeParentsAndSubstituteWithoutAliases() {
    var p = withExcludedParent();
    var array = new Object[]{p, new int[]{1, 2}};
    var o = source(List.of(Map.of("nested", array)));
    var paths = new ArrayList<List<String>>();
    var results = expand(o, paths);
    assertEquals(2, results.size());
    assertEquals(List.of(List.of("defaultValue", "items", "0", "entries", "0", "value", "elements", "0")), paths);
    var first = nestedArray(results.getFirst()); var sibling = nestedArray(results.getLast());
    assertEquals("ns:A1", ((KimConcept) first[0]).getName());
    assertEquals("ns:A2", ((KimConcept) sibling[0]).getName());
    assertNull(((KimConcept) first[0]).getParent());
    ((int[]) first[1])[0] = 9;
    assertEquals(1, ((int[]) array[1])[0]); assertEquals(1, ((int[]) sibling[1])[0]);
    ((KimConceptImpl) first[0]).setName("ns:Changed");
    assertEquals("ns:A", p.getName()); assertNotNull(p.getParent());
    var copied = new KimObservableImpl(o);
    assertNull(((KimConcept) nestedArray(copied)[0]).getParent());
    assertEquals("ns:A", ((KimConcept) nestedArray(copied)[0]).getUrn());
  }

  private static Object[] nestedArray(KimObservableImpl o) {
    return (Object[]) ((Map<?, ?>) ((List<?>) o.getDefaultValue()).getFirst()).get("nested");
  }

  @Test void mapEntryPathsDoNotStringifyOrClassifyKeys() {
    var p = withExcludedParent();
    var values = new LinkedHashMap<Object, Object>();
    values.put(1, p); values.put("1", p); values.put("ns:A", p); values.put(p, p);
    var original = source(values); var paths = new ArrayList<List<String>>();
    var results = expand(original, paths);
    assertEquals(16, results.size());
    assertEquals(4, paths.size());
    for (int i = 0; i < 4; i++)
      assertEquals(List.of("defaultValue", "entries", Integer.toString(i), "value"), paths.get(i));
    var result = (Map<?, ?>) results.getFirst().getDefaultValue();
    assertEquals(4, result.size());
    assertTrue(result.containsKey(1)); assertTrue(result.containsKey("1"));
    assertTrue(result.containsKey("ns:A")); assertTrue(result.containsKey(p));
    var copiedKey = (KimConcept) result.keySet().stream().filter(KimConcept.class::isInstance).findFirst().orElseThrow();
    assertNotSame(p, copiedKey); assertNull(copiedKey.getParent());
    assertTrue(copiedKey.is(SemanticType.ANY)); // A key is a label, as in LanguageAdapter.adaptValue.
    assertEquals("ns:A", copiedKey.getUrn());
    result.clear(); assertEquals(4, values.size());
    assertEquals(4, ((Map<?, ?>) results.getLast().getDefaultValue()).size());
  }

  @Test void nestedObservableRetainsContextAndExpandsEverySupportedSlot() {
    var p = withExcludedParent();
    var inner = source(List.of(p)); inner.setSemantics(p); inner.setUnit("km");
    inner.getMetadata().put("mutable", new ArrayList<>(List.of("original")));
    var original = source(new Object[]{inner}); var paths = new ArrayList<List<String>>();
    var results = expand(original, paths);
    assertEquals(4, results.size());
    assertEquals(List.of(
        List.of("defaultValue", "elements", "0", "observable", "semantics"),
        List.of("defaultValue", "elements", "0", "observable", "defaultValue", "items", "0")), paths);
    var first = (KimObservableImpl) ((Object[]) results.getFirst().getDefaultValue())[0];
    var sibling = (KimObservableImpl) ((Object[]) results.getLast().getDefaultValue())[0];
    assertEquals("km", first.getUnit()); assertEquals("authored", first.getNamespace());
    assertEquals("ns:A1", first.getSemantics().getName());
    assertEquals("ns:A2", sibling.getSemantics().getName());
    assertNull(first.getUrn()); assertNull(first.getSemantics().getParent());
    assertFalse(first.isOptional()); assertTrue(results.getFirst().isOptional());
    ((List<?>) first.getMetadata().get("mutable")).clear();
    assertEquals(List.of("original"), inner.getMetadata().get("mutable"));
    assertEquals(List.of("original"), sibling.getMetadata().get("mutable"));
    var plainCopy = new KimObservableImpl(original);
    assertEquals("original", ((KimObservableImpl) ((Object[]) plainCopy.getDefaultValue())[0]).getUrn());
  }

  @Test void typedArraysAndSharedElementsRetainIndependentPositions() {
    var p = withExcludedParent(); var paths = new ArrayList<List<String>>();
    var results = expand(source(new KimConceptImpl[]{p, p}), paths);
    assertEquals(4, results.size());
    assertEquals(KimConceptImpl[].class, results.getFirst().getDefaultValue().getClass());
    assertEquals(List.of(List.of("defaultValue", "elements", "0"),
        List.of("defaultValue", "elements", "1")), paths);
    var combinations = new HashSet<String>();
    for (var output : results) {
      var array = (KimConceptImpl[]) output.getDefaultValue();
      combinations.add(array[0].getName() + "/" + array[1].getName());
      assertNotSame(array[0], array[1]);
    }
    assertEquals(Set.of("ns:A1/ns:A1", "ns:A1/ns:A2", "ns:A2/ns:A1", "ns:A2/ns:A2"), combinations);
  }

  @Test void cyclicContainersAndObservablesFailExplicitly() {
    var list = new ArrayList<Object>(); list.add(list);
    var map = new LinkedHashMap<String, Object>(); map.put("self", map);
    var array = new Object[1]; array[0] = array;
    var observable = source(null); observable.setDefaultValue(observable);
    for (var cycle : List.of(list, map, array, observable)) {
      var failure = assertThrows(IllegalArgumentException.class,
          () -> expand(source(cycle), new ArrayList<>()));
      assertTrue(failure.getMessage().contains("Cyclic syntax value"));
    }
  }

  @Test void unsupportedRetainedPayloadAndIncompatibleArrayShapeFailExplicitly() {
    assertThrows(IllegalArgumentException.class,
        () -> expand(source(List.of(Map.of("retained", new Object()))), new ArrayList<>()));
    var unsupported = new LinkedList<?>[]{new LinkedList<>(List.of(withExcludedParent()))};
    var failure = assertThrows(IllegalArgumentException.class,
        () -> expand(source(unsupported), new ArrayList<>()));
    assertTrue(failure.getMessage().contains("incompatible type"));
  }

  @Test void unsupportedMapKeyEqualityCannotSilentlyDropEntries() {
    var identityMap = new IdentityHashMap<Object, Object>();
    identityMap.put(new String("same"), withExcludedParent());
    identityMap.put(new String("same"), withExcludedParent());
    assertEquals(2, identityMap.size());
    var failure = assertThrows(IllegalArgumentException.class,
        () -> expand(source(identityMap), new ArrayList<>()));
    assertTrue(failure.getMessage().contains("map keys collide"));
  }

  private static class ChangingOrderMap extends LinkedHashMap<Object, Object> {
    private boolean reverse;
    @Override public Set<Map.Entry<Object, Object>> entrySet() {
      var entries = new ArrayList<>(super.entrySet());
      if (reverse) Collections.reverse(entries);
      reverse = !reverse;
      return new LinkedHashSet<>(entries);
    }
  }

  @Test void mapOrderIsFrozenBeforeClassificationAndSubstitution() {
    var input = new ChangingOrderMap();
    input.put("left", predicate("ns:A", true)); input.put("right", predicate("ns:B", true));
    var generated = DependencyExpansion.expand(source(input), name -> predicate(name, false), request -> {
      var snapshot = (Map<?, ?>) request.originalObservable().getDefaultValue();
      int index = Integer.parseInt(request.identity().path().get(2));
      var requestedValue = (KimConcept) new ArrayList<>(snapshot.values()).get(index);
      assertEquals(request.predicate().getName(), requestedValue.getName());
      return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,
          List.of(predicate(request.predicate().getName() + "Concrete", false)));
    });
    var result = (Map<?, ?>) generated.getFirst().getDefaultValue();
    assertEquals("ns:AConcrete", ((KimConcept) result.get("left")).getName());
    assertEquals("ns:BConcrete", ((KimConcept) result.get("right")).getName());
    assertTrue(((KimConcept) input.get("left")).is(SemanticType.ANY));
  }
}
