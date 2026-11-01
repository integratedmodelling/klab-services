package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.DependencyExpansion;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.junit.jupiter.api.Test;

class DependencyExpansionSerializationTest {
  private static KimConceptImpl predicate(String name, boolean any) {
    var p = new KimConceptImpl(); p.setName(name);
    p.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE));
    if (any) p.getType().add(SemanticType.ANY);
    return p;
  }

  @Test void serviceMapperRetainsSourceAndGeneratedDefaultSyntaxWithoutAliases() throws Exception {
    var original = new KimObservableImpl();
    original.setSemantics(predicate("ns:Concrete", false));
    original.setDefaultValue(predicate("ns:A", true));
    original.setUnit("m"); original.setNamespace("authored");
    original.setProjectName("project"); original.setServiceId("service");
    original.setOffsetInDocument(23); original.setLength(50);
    original.setRange(new NumericRangeImpl(0.0, 1.0, false, true));
    original.getMetadata().put("values", new ArrayList<>(List.of("original")));
    original.getAnnotations().add(Annotation.of("source", "value", "original"));
    var mapper = JacksonConfiguration.newObjectMapper();
    var transported = (KimObservableImpl) mapper.readValue(
        mapper.writeValueAsString(original), KimObservable.class);
    assertInstanceOf(KimConcept.class, transported.getDefaultValue());
    var generated = DependencyExpansion.expand(transported, name -> predicate(name, false),
        request -> new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,
            List.of(predicate("ns:A1", false), predicate("ns:A2", false))));
    assertEquals(2, generated.size());
    var restored = (KimObservableImpl) mapper.readValue(
        mapper.writeValueAsString(generated.getFirst()), KimObservable.class);
    assertEquals("ns:A1", ((KimConcept) restored.getDefaultValue()).getName());
    assertFalse(((KimConcept) restored.getDefaultValue()).is(SemanticType.ANY));
    assertTrue(restored.isOptional()); assertNull(restored.getUrn());
    assertEquals("m", restored.getUnit()); assertEquals("authored", restored.getNamespace());
    assertEquals("project", restored.getProjectName()); assertEquals("service", restored.getServiceId());
    assertEquals(23, restored.getOffsetInDocument()); assertEquals(50, restored.getLength());
    assertEquals("original", restored.getAnnotations().getFirst().get("value"));
    restored.getRange().setLowerExclusive(true);
    ((List<?>) restored.getMetadata().get("values")).clear();
    assertFalse(original.getRange().isLowerExclusive());
    assertEquals(List.of("original"), generated.getLast().getMetadata().get("values"));
    assertTrue(((KimConcept) original.getDefaultValue()).is(SemanticType.ANY));
  }

  @Test void serviceMapperRetainsSemanticValuesInsideContainersAndNestedObservables() throws Exception {
    var inner = new KimObservableImpl(); inner.setSemantics(predicate("ns:A", true));
    inner.setUnit("m"); inner.setNamespace("authored");
    var original = new KimObservableImpl(); original.setSemantics(predicate("ns:Concrete", false));
    original.setDefaultValue(java.util.Map.of("nested", List.of(inner)));
    var mapper = JacksonConfiguration.newObjectMapper();
    var transported = (KimObservableImpl) mapper.readValue(
        mapper.writeValueAsString(original), KimObservable.class);
    var paths = new java.util.ArrayList<List<String>>();
    var generated = DependencyExpansion.expand(transported, name -> predicate(name, false), request -> {
      paths.add(request.identity().path());
      return new DependencyExpansion.Classification(DependencyExpansion.Status.SUCCESS,
          List.of(predicate("ns:A1", false), predicate("ns:A2", false)));
    });
    assertEquals(2, generated.size());
    assertEquals(List.of(List.of("defaultValue", "entries", "0", "value", "items", "0", "observable", "semantics")), paths);
    var restored = (KimObservableImpl) mapper.readValue(
        mapper.writeValueAsString(generated.getFirst()), KimObservable.class);
    var nested = (KimObservable) ((List<?>) ((java.util.Map<?, ?>) restored.getDefaultValue()).get("nested")).getFirst();
    assertEquals("ns:A1", nested.getSemantics().getName());
    assertFalse(nested.getSemantics().is(SemanticType.ANY));
    assertEquals("m", nested.getUnit()); assertEquals("authored", nested.getNamespace());
    assertTrue(inner.getSemantics().is(SemanticType.ANY));
  }
}
