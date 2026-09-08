package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl;
import org.junit.jupiter.api.Test;

class ObservationPlanSerializationTest {
  @Test void everyPlanInterfaceUsesTheRegisteredPolymorphicContract() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var seen = new HashSet<Class<?>>();
    for (var implementation : KimObservationPlanImpl.class.getDeclaredClasses()) {
      if (Modifier.isAbstract(implementation.getModifiers())) continue;
      Object bean = implementation.getDeclaredConstructor().newInstance();
      var interfaces = new LinkedHashSet<Class<?>>();
      collectInterfaces(implementation, interfaces);
      for (var type : interfaces) {
        if (type.getEnclosingClass() != KimObservationPlan.class) continue;
        seen.add(type);
        String json = mapper.writerFor(type).writeValueAsString(bean);
        assertEquals(implementation.getName(), mapper.readTree(json).get("@CLASS").asText(), type.getName());
        assertEquals(implementation, mapper.readValue(json, type).getClass(), type.getName());
      }
    }
    for (var type : KimObservationPlan.class.getDeclaredClasses()) {
      if (type.isInterface()) assertTrue(seen.contains(type), "Unexercised interface: " + type.getName());
    }
  }

  @Test void falseEmptyAndOrderedEnumValuesSurviveInterfaceTransport() throws Exception {
    var constraint = new KimObservationPlanImpl.KindConstraintImpl();
    constraint.setKinds(new ArrayList<>(List.of(KimObservationPlan.PatternKind.AGENT, KimObservationPlan.PatternKind.SUBJECT)));
    var source = new KimObservationPlanImpl.SourceImpl();
    source.setCode("");
    source.setUri("memory:/strategy.obs#node");
    source.setOffset(17);
    source.setLength(0);
    constraint.setSource(source);
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(constraint), KimObservationPlan.KindConstraint.class);
    assertEquals(constraint.getKinds(), restored.getKinds());
    assertEquals("", restored.getSource().getCode());
    assertEquals(17, restored.getSource().getOffset());
    assertEquals(source.getUri(), restored.getSource().getUri());
    var flag = new KimObservationPlanImpl.FlagPatternFieldImpl();
    flag.setFlag(KimObservationPlan.PatternFlag.ABSTRACT);
    flag.setValue(false);
    assertEquals(false, mapper.readValue(mapper.writeValueAsString(flag), KimObservationPlan.PatternField.class)
        instanceof KimObservationPlan.FlagPatternField field ? field.getValue() : null);
  }

  private static void collectInterfaces(Class<?> type, Set<Class<?>> result) {
    if (type == null) return;
    for (var contract : type.getInterfaces()) {
      if (result.add(contract)) collectInterfaces(contract, result);
    }
    collectInterfaces(type.getSuperclass(), result);
  }
}
