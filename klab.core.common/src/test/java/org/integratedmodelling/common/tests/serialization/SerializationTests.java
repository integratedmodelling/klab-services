package org.integratedmodelling.common.tests.serialization;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.lang.AnnotationImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SerializationTests {

  static String centralColombia =
      "τ0(1){ttype=LOGICAL,period=[1609459200000 1640995200000],tscope=1.0,"
          + "tunit=YEAR}S2(934,631){bbox=[-75.2281407807369 -72.67107290964314 3.5641500380320963 5"
          + ".302943221927137],"
          + "shape"
          + "=00000000030000000100000005C0522AF2DBCA0987400C8361185B1480C052CE99DBCA0987400C8361185B1480C052CE99DBCA098740153636BF7AE340C0522AF2DBCA098740153636BF7AE340C0522AF2DBCA0987400C8361185B1480,proj=EPSG:4326}";

  @Test
  void parameters() {
    Parameters<Object> object =
        Parameters.create("one", 1, "oneString", "one", "params", Parameters.create("one", 1));
    String serialized = Utils.Json.asString(object);
    Parameters<?> deserialized = Utils.Json.parseObject(serialized, Parameters.class);
    System.out.println(serialized);
    System.out.println(deserialized.getClass());
    System.out.println(deserialized.get("params").getClass());
    assert (deserialized instanceof Parameters);
    assert (deserialized.get("params") instanceof Parameters);
  }

  //    @Test
  void pair() {
    Geometry geometry = GeometryRepository.INSTANCE.get(centralColombia, Geometry.class);
    Parameters<Object> object =
        Parameters.create("one", 1, "oneString", "one", "params", Parameters.create("one", 1));
    var pair = Pair.of(geometry, object);
    String serialized = Utils.Json.asString(pair);
    Pair<?, ?> deserialized = Utils.Json.parseObject(serialized, Pair.class);
    System.out.println(serialized);
    System.out.println(deserialized.getClass());
    System.out.println(deserialized.getFirst().getClass());
    System.out.println(deserialized.getSecond().getClass());
    assert (deserialized.getFirst() instanceof Geometry);
    assert (deserialized.getSecond() instanceof Parameters);
  }

  @Test
  void metadata() {
    Metadata object =
        Metadata.create("one", 1, "oneString", "one", "params", Metadata.create("one", 1));
    String serialized = Utils.Json.asString(object);
    Metadata deserialized = Utils.Json.parseObject(serialized, Metadata.class);
    System.out.println(serialized);
    System.out.println(deserialized.getClass());
    System.out.println(deserialized.get("params").getClass());
    assert (deserialized instanceof Metadata);
    assert (deserialized.get("params") instanceof Metadata);
  }

  @Test
  void annotation() {
    Annotation object =
        AnnotationImpl.create(
            "belaCagada",
            "one",
            1,
            "oneString",
            "one",
            "params",
            AnnotationImpl.create("cazzarola", "one", 1));
    String serialized = Utils.Json.asString(object);
    Annotation deserialized = Utils.Json.parseObject(serialized, Annotation.class);
    System.out.println(serialized);
    System.out.println(deserialized.getClass());
    System.out.println(deserialized.get("params").getClass());
    assert (deserialized instanceof Annotation);
    assert (deserialized.get("params") instanceof Annotation);
  }

  @Test
  void geometry() {
    Geometry geometry = Geometry.create(centralColombia);
    String serialized = Utils.Json.asString(geometry);
    Geometry deserialized = Utils.Json.parseObject(serialized, Geometry.class);
    System.out.println(serialized);
    assert (deserialized instanceof Geometry);
    String before = geometry.encode();
    String after = deserialized.encode();
    assertEquals(before, after);
  }

  //    private Object serializeAndDeserializeLiteral(Object o) {
  //        LiteralImpl literal = LiteralImpl.of(o);
  //        String serialized = Utils.Json.asString(literal);
  //        System.out.println(serialized);
  //        return Utils.Json.parseObject(serialized, Literal.class).get(Object.class);
  //    }

  //    @Test
  //    void literals() {
  //        // TODO use the above with all kinds of things
  //        assert (serializeAndDeserializeLiteral(10) instanceof Integer);
  //        assert (serializeAndDeserializeLiteral("Zorba") instanceof String);
  //    }
}
