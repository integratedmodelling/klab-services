package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.neo4j.driver.summary.ResultSummary;

/** Exercises persisted-link adaptation through getLinks, without requiring a live database. */
class KnowledgeGraphLinkDirectionTest {

  @Test
  void incomingDependencyRetainsItsProducerAsSource() {
    assertDirection(GraphModel.Relationship.Direction.INCOMING);
  }

  @Test
  void outgoingDependencyRetainsItsConsumerAsTarget() {
    assertDirection(GraphModel.Relationship.Direction.OUTGOING);
  }

  private void assertDirection(GraphModel.Relationship.Direction direction) {
    var producer = new ObservationImpl();
    producer.setId(11L);
    var consumer = new ObservationImpl();
    consumer.setId(12L);
    boolean incoming = direction == GraphModel.Relationship.Direction.INCOMING;
    var selected = incoming ? consumer : producer;
    var opposite = incoming ? producer : consumer;
    var scope =
        (ContextScope)
            Proxy.newProxyInstance(
                ContextScope.class.getClassLoader(),
                new Class<?>[] {ContextScope.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("getCurrentTransaction")) {
                    return null;
                  }
                  throw new AssertionError("Unexpected scope call: " + method);
                });
    var graph = new LinkResultGraph(opposite);
    var links = graph.getLinks(selected, direction, scope, GraphModel.Relationship.AFFECTS);

    assertEquals(1, links.size());
    var link = links.iterator().next();
    assertSame(producer, link.source());
    assertSame(consumer, link.target());
    assertEquals(GraphModel.Relationship.AFFECTS, link.type());
    assertEquals(7, ((Number) link.properties().get("sequence")).intValue());
    assertEquals(selected.getId(), graph.selectedId);
    assertTrue(graph.queryText.contains(incoming ? "(n)<-[r:AFFECTS]-(m)" : "(n)-[r:AFFECTS]->(m)"));
  }

  private static class LinkResultGraph extends KnowledgeGraphNeo4j {
    private final RuntimeAsset opposite;
    private String queryText;
    private long selectedId;

    LinkResultGraph(RuntimeAsset opposite) {
      this.opposite = opposite;
    }

    @Override
    protected synchronized EagerResult query(String query, Map<String, Object> parameters, Scope scope) {
      queryText = query;
      selectedId = ((Number) parameters.get("id")).longValue();
      var fields = Map.of(
          "rtype", Values.value("AFFECTS"),
          "rprops", Values.value(Map.of("sequence", 7)),
          "mid", Values.value(opposite.getId()));
      var record = (Record) Proxy.newProxyInstance(
          Record.class.getClassLoader(), new Class<?>[] {Record.class},
          (proxy, method, args) -> {
            if (method.getName().equals("get") && args[0] instanceof String key) {
              return fields.get(key);
            }
            throw new AssertionError("Unexpected record call: " + method);
          });
      return new EagerResult() {
        public List<String> keys() { return List.copyOf(fields.keySet()); }
        public List<Record> records() { return List.of(record); }
        public ResultSummary summary() { throw new AssertionError("Unexpected summary request"); }
      };
    }

    @Override
    public <T extends RuntimeAsset> T getAsset(long id, Scope scope, Class<T> resultClass) {
      assertEquals(opposite.getId(), id);
      return resultClass.cast(opposite);
    }

    @Override
    public KnowledgeGraph contextualize(DigitalTwin.Configuration configuration, UserScope scope) {
      throw new UnsupportedOperationException();
    }

    @Override
    public KnowledgeGraph merge(URL url) { throw new UnsupportedOperationException(); }

    @Override
    public boolean isOnline() { return true; }

    @Override
    public void shutdown() {}
  }
}
