package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.junit.jupiter.api.Test;
import org.neo4j.harness.Neo4jBuilders;

class ContextDeletionTest {
  @Test
  void deletesEveryOwnedEffectAndItsDataWithoutFollowingEffectsToForeignObservations() {
    try (var database = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled, false).build()) {
      var db = database.defaultDatabaseService();
      db.executeTransactionally("CREATE (:Context {id:'delete'})-[:HAS_PROVENANCE]->"
          + "(:Provenance)-[:HAS_CHILD]->(:Activity {name:'activity'})");
      for (var effect : GraphModel.Relationship.CONTEXTUALIZATION_EFFECTS) {
        db.executeTransactionally("MATCH (a:Activity) CREATE "
            + "(a)-[:" + effect.name() + "]->(:Observation {`im:context-id`:'delete'})"
            + "-[:HAS_DATA]->(:Data), "
            + "(a)-[:" + effect.name() + "]->(:Observation {ownerContextId:'foreign'})");
      }
      db.executeTransactionally(KnowledgeGraphNeo4j.Queries.REMOVE_CONTEXT,
          Map.of("contextId", "delete"));
      assertEquals((long) GraphModel.Relationship.CONTEXTUALIZATION_EFFECTS.size(),
          count(db, "MATCH (n) RETURN count(n) AS n"));
      assertEquals(0, count(db, "MATCH (n:Observation) WHERE n.ownerContextId IS NULL RETURN count(n) AS n"));
    }
  }

  @Test
  void deletesDisconnectedAssetsButPreservesSharedGeometryAndForeignDescendants() {
    try (var database = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled, false).build()) {
      var db = database.defaultDatabaseService();
      db.executeTransactionally("""
          CREATE (:Context {id:'delete'}),
            (o:Observation {ownerContextId:'delete'})-[:HAS_DATA]->(:Data),
            (:Actuator {ownerContextId:'delete'}),
            (o)-[:HAS_GEOMETRY]->(g:Geometry {name:'shared'}),
            (o)-[:PERCEIVES_GEOMETRY]->(:Geometry {name:'exclusive'}),
            (foreign:Observation {`im:context-id`:'other'})-[:HAS_GEOMETRY]->(g),
            (o)-[:HAS_DATA]->(shared:Data {name:'shared-data'}),
            (foreign)-[:HAS_DATA]->(shared),
            (o)-[:HAS_CHILD]->(foreign),
            (foreign)-[:HAS_DATA]->(:Data {name:'foreign-data'}),
            (:Geometry {name:'unrelated'})
          """);
      db.executeTransactionally(KnowledgeGraphNeo4j.Queries.REMOVE_CONTEXT,
          Map.of("contextId", "delete"));
      assertEquals(5, count(db, "MATCH (n) RETURN count(n) AS n"));
      assertEquals(0, count(db, "MATCH (n) WHERE n.ownerContextId='delete' RETURN count(n) AS n"));
      assertEquals(0, count(db, "MATCH (n:Geometry {name:'exclusive'}) RETURN count(n) AS n"));
      assertEquals(2, count(db, "MATCH (n:Data) RETURN count(n) AS n"));
    }
  }

  @Test
  void sharedDescriptorsAndForeignContextsCannotBridgeDeletionTraversal() {
    try (var database = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(org.neo4j.configuration.connectors.BoltConnector.enabled, false).build()) {
      var db = database.defaultDatabaseService();
      db.executeTransactionally("""
          CREATE (c:Context {id:'delete'}),
            (c)-[:HAS_CHILD]->(:Agent)-[:CREATED]->(:Observation),
            (c)-[:HAS_CHILD]->(:Geometry)-[:HAS_CHILD]->(:Observation),
            (c)-[:HAS_CHILD]->(:Context {id:'other'})-[:HAS_CHILD]->(:Observation)
          """);
      db.executeTransactionally(KnowledgeGraphNeo4j.Queries.REMOVE_CONTEXT,
          Map.of("contextId", "delete"));
      assertEquals(6, count(db, "MATCH (n) RETURN count(n) AS n"));
    }
  }

  private long count(org.neo4j.graphdb.GraphDatabaseService db, String query) {
    return db.executeTransactionally(query, Map.of(), result -> (Long) result.next().get("n"));
  }
}
