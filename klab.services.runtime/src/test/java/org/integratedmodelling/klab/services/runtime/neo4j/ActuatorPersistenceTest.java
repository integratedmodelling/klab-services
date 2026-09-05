package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.neo4j.driver.summary.ResultSummary;

class ActuatorPersistenceTest {
  @Test
  void structuredCallsPreserveParametersThatTextEncodingOmits() {
    var actuator = new ActuatorImpl();
    actuator.setId(43);
    actuator.setName("value");
    actuator.setType(Artifact.Type.NUMBER);
    actuator.setActuatorType(Actuator.Type.RESOLVE);
    actuator.setStrategyUrn("strategy.test");
    actuator.setCoverage(Geometry.UNIVERSAL);
    actuator.getData().put("factor", 3);
    var observation = new ObservationImpl();
    observation.setObservable((Observable) Proxy.newProxyInstance(
        Observable.class.getClassLoader(), new Class<?>[] {Observable.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getUrn")) return "test:Value";
          throw new AssertionError(method);
        }));
    actuator.setObservation(observation);
    actuator.getComputation().add(new ServiceCallImpl("test.function",
        "value", 7, "label", "quoted \"value\"", "__internal", List.of(1, 2)));

    var graph = new GraphFixture();
    var stored = graph.asParameters(actuator);
    var restored = graph.decode(stored);
    assertEquals(43, restored.getId());
    assertEquals("value", restored.getName());
    assertEquals(Actuator.Type.RESOLVE, restored.getActuatorType());
    assertEquals(Artifact.Type.NUMBER, restored.getType());
    assertEquals("strategy.test", restored.getStrategyUrn());
    assertEquals(3, restored.getData().get("factor", 0));
    assertEquals(1, restored.getComputation().size());
    var call = restored.getComputation().getFirst();
    assertEquals("test.function", call.getUrn());
    assertEquals(7, call.getParameters().get("value", 0));
    assertEquals("quoted \"value\"", call.getParameters().get("label"));
    assertEquals(List.of(1L, 2L), ((List<?>) call.getParameters().get("__internal"))
        .stream().map(value -> ((Number) value).longValue()).toList());
  }

  @Test
  void legacyTextIsInspectableButNotSilentlyTreatedAsExecutable() {
    var restored = new GraphFixture().decode(Map.of(
        "id", 9L, "parentId", -1L, "computation", List.of("test.function(value = 1)")));
    assertEquals(9, restored.getId());
    assertTrue(restored.getComputation().isEmpty());
  }

  private static class GraphFixture extends KnowledgeGraphNeo4j {
    Actuator decode(Map<String, Object> properties) {
      var record = (Record) Proxy.newProxyInstance(Record.class.getClassLoader(),
          new Class<?>[] {Record.class}, (proxy, method, args) -> {
            if (method.getName().equals("values")) return List.of(Values.value(properties));
            throw new AssertionError(method);
          });
      return adapt(new EagerResult() {
        public List<String> keys() { return List.of("n"); }
        public List<Record> records() { return List.of(record); }
        public ResultSummary summary() { throw new AssertionError(); }
      }, Actuator.class, null).getFirst();
    }

    public KnowledgeGraph contextualize(DigitalTwin.Configuration configuration, UserScope scope) {
      throw new UnsupportedOperationException();
    }
    public KnowledgeGraph merge(URL url) { throw new UnsupportedOperationException(); }
    public boolean isOnline() { return true; }
    public void shutdown() {}
  }
}
