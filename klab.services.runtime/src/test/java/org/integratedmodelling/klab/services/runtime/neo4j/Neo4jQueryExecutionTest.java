package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4jBuilders;

/** Execute the generated query, rather than only checking its syntax. */
class Neo4jQueryExecutionTest {
  @Test
  void childrenOfCollectiveRemainVisibleAndOtherContextsStayExcluded() {
    assertChildren("(root)-[:HAS_CHILD]->(parent)");
  }

  @Test
  void childrenOfCollectiveCreatedByNestedActivityRemainVisible() {
    assertChildren("(root)-[:HAS_PROVENANCE]->(:Provenance)-[:HAS_CHILD]->(:Activity)"
        + "-[:TRIGGERED]->(:Activity)-[:CREATED]->(parent)");
  }

  @Test
  void childrenOfContextualizedCollectiveRemainVisible() {
    assertChildren("(root)-[:HAS_PROVENANCE]->(:Provenance)-[:HAS_CHILD]->(:Activity)"
        + "-[:CONTEXTUALIZED]->(parent)");
  }

  private void assertChildren(String ownership) {
    try (var database = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled, false).build()) {
      database.defaultDatabaseService().executeTransactionally("""
          CREATE (root:Context {id:'owner.context'}),
                 (parent:Observation {id:10}), (child:Observation {id:11}),
                 (foreign:Context {id:'other.context'}), (other:Observation {id:12}),
                 %s, (parent)-[:HAS_CHILD]->(child),
                 (foreign)-[:HAS_CHILD]->(other), (parent)-[:AFFECTS]->(other)
          """.formatted(ownership));
      var parent = new ObservationImpl();
      parent.setId(10);
      var query = new KnowledgeGraphQuery<RuntimeAsset>(KnowledgeGraphQuery.AssetType.ANY);
      query.source(parent).along(GraphModel.Relationship.HAS_CHILD);
      var compiled = Neo4jQueryCompiler.compile(query, "owner.context");
      var ids = database.defaultDatabaseService().executeTransactionally(
          compiled.cypher(), compiled.parameters(), result -> {
            var values = new ArrayList<Long>();
            while (result.hasNext()) {
              var node = (org.neo4j.graphdb.Node) result.next().get("n");
              values.add((Long) node.getProperty(GraphModel.Fields.ID));
            }
            return values;
          });
      assertEquals(List.of(11L), ids);
      // A causal edge to a foreign observation must not authorize that observation.
      var foreignQuery = new KnowledgeGraphQuery<RuntimeAsset>(KnowledgeGraphQuery.AssetType.ANY);
      foreignQuery.source(parent).along(GraphModel.Relationship.AFFECTS);
      var foreignCompiled = Neo4jQueryCompiler.compile(foreignQuery, "owner.context");
      long foreignCount = database.defaultDatabaseService().executeTransactionally(
          foreignCompiled.cypher(), foreignCompiled.parameters(), result -> result.stream().count());
      assertEquals(0L, foreignCount);
      var absentName = new KnowledgeGraphQuery<RuntimeAsset>(KnowledgeGraphQuery.AssetType.OBSERVATION);
      absentName.where(GraphModel.Fields.NAME,
          org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.Operator.EQUALS, "absent");
      var complement = Neo4jQueryCompiler.compile((KnowledgeGraphQuery<?>) absentName.not(), "owner.context");
      long complementCount = database.defaultDatabaseService().executeTransactionally(
          complement.cypher(), complement.parameters(), result -> result.stream().count());
      assertEquals(2L, complementCount);
    }
  }
}
