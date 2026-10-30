package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ReflectionMetadataTransportTest {
  public static class Parent { private int inherited; public String shadow; }
  public static class Child extends Parent { public String shadow; }
  public static class Box<T> { public T value; }
  public static class Containers {
    public Box<String> text;
    public Box<Integer> number;
    public List<Map<String, List<Integer>>> nested;
    public Object plain;
  }

  private <T> T read(String json, Class<T> type) throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var module = new SimpleModule();
    module.addDeserializer(type, new JacksonConfiguration.PolymorphicDeserializer<>(type));
    mapper.registerModule(module);
    return mapper.readValue(json, type);
  }

  private static String tagged(Class<?> type, String fields) {
    return "{\"@CLASS\":\"" + type.getName() + "\"," + fields + "}";
  }

  @Test void inheritedPrivateFieldAndNearestShadowRetainTheirMeaning() throws Exception {
    var child = read(tagged(Child.class, "\"inherited\":7,\"shadow\":\"child\""), Child.class);
    assertEquals(7, ((Parent) child).inherited);
    assertEquals("child", child.shadow);
    assertNull(((Parent) child).shadow);
    // A new mapper/deserializer shares the metadata, without sharing any bean values.
    var other = read(tagged(Child.class, "\"inherited\":9,\"shadow\":\"other\""), Child.class);
    assertEquals(9, ((Parent) other).inherited);
    assertEquals("child", child.shadow);
  }

  @Test void missingFieldsStillFailAndPlainMapKeysRemainUserData() throws Exception {
    for (int i = 0; i < 100; i++) {
      int key = i;
      assertThrows(Exception.class, () -> read(tagged(Child.class, "\"unknown" + key + "\":1"), Child.class));
    }
    var value = read(tagged(Containers.class, "\"plain\":{\"size\":1,\"values\":[2,3]}"), Containers.class);
    var plain = assertInstanceOf(Map.class, value.plain);
    assertEquals(Set.of("size", "values"), plain.keySet());
    assertEquals(1, ((Number) plain.get("size")).intValue());
    assertEquals(List.of(2, 3), ((List<?>) plain.get("values")).stream()
        .map(number -> ((Number) number).intValue()).toList());
  }

  @Test void genericBindingsAndNestedContainersRemainRequestSpecific() throws Exception {
    var value = read(containers(), Containers.class);
    assertEquals("text", value.text.value);
    assertInstanceOf(String.class, value.text.value);
    assertEquals(42, value.number.value);
    assertInstanceOf(Integer.class, value.number.value);
    assertEquals(List.of(Map.of("numbers", List.of(1, 2))), value.nested);
  }

  @Test void concurrentNewMappersDoNotShareGenericBindingsOrValues() throws Exception {
    try (var pool = Executors.newFixedThreadPool(4)) {
      var tasks = new ArrayList<Callable<Void>>();
      for (int i = 0; i < 24; i++) tasks.add(() -> {
        genericBindingsAndNestedContainersRemainRequestSpecific();
        return null;
      });
      for (var result : pool.invokeAll(tasks)) result.get();
    }
  }

  @Test void sameBinaryNameFromAnotherLoaderUsesItsOwnFields() throws Exception {
    byte[] bytes;
    try (var input = Child.class.getResourceAsStream("ReflectionMetadataTransportTest$Child.class")) {
      assertNotNull(input);
      bytes = input.readAllBytes();
    }
    class OtherLoader extends ClassLoader {
      OtherLoader() { super(Child.class.getClassLoader()); }
      Class<?> define() { return defineClass(Child.class.getName(), bytes, 0, bytes.length); }
    }
    var other = new OtherLoader().define();
    assertEquals(Child.class.getName(), other.getName());
    assertNotSame(Child.class, other);
    var decoder = new JacksonConfiguration.PolymorphicDeserializer<>(Object.class);
    assertSame(Child.class, decoder.findField(Child.class, "shadow").field().getDeclaringClass());
    assertSame(other, decoder.findField(other, "shadow").field().getDeclaringClass());
  }

  private static String containers() {
    return tagged(Containers.class,
        "\"text\":" + tagged(Box.class, "\"value\":\"text\"")
            + ",\"number\":" + tagged(Box.class, "\"value\":42")
            + ",\"nested\":[{\"numbers\":[1,2]}]");
  }
}
