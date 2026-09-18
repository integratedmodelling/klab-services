package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.integratedmodelling.klab.services.runtime.neo4j.ClassificationPersistenceTest.rows;

import java.util.*;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.*;

/** Real Neo4j storage and query execution, using the existing embedded driver test adapter. */
class RelationshipPersistenceTest {
  @BeforeAll static void start() { ClassificationPersistenceTest.start(); }
  @AfterAll static void stop() { ClassificationPersistenceTest.stop(); }
  @BeforeEach void seed() {
    rows("MATCH (n) DETACH DELETE n", Map.of());
    rows("CREATE (ctx:Context {id:'a'})-[:HAS_CHILD]->(cohort:Cohort {id:90}), "
        + "(cohort)-[:HAS_MEMBER]->(:Observation {id:41}), "
        + "(cohort)-[:HAS_MEMBER]->(:Observation {id:42}), "
        + "(cohort)-[:HAS_MEMBER]->(:Observation {id:43}), "
        + "(cohort)-[:HAS_MEMBER]->(:Observation {id:44}), "
        + "(:Context {id:'b'})-[:HAS_CHILD]->(:Observation {id:45})", Map.of());
  }

  private ObservationImpl observation(long id) { var ret = new ObservationImpl(); ret.setId(id); return ret; }

  private List<Long> query(long id, boolean fromRelationship, GraphModel.Relationship type) {
    var query = new KnowledgeGraphQuery<Observation>(KnowledgeGraphQuery.AssetType.OBSERVATION);
    if (fromRelationship) query.source(observation(id)); else query.target(observation(id));
    query.along(type);
    var statement = Neo4jQueryCompiler.compile(query, "a");
    return ClassificationPersistenceTest.database.defaultDatabaseService().executeTransactionally(
        statement.cypher(), statement.parameters(), result -> {
          var ids = new ArrayList<Long>();
          while (result.hasNext()) ids.add((Long) ((org.neo4j.graphdb.Node) result.next().get("n")).getProperty("id"));
          return ids;
        });
  }

  @Test void relationshipsRemainCohortMembersWithDirectedParticipantRoles() throws Exception {
    try (var tx = new ClassificationPersistenceTest.Fixture("a").createTransaction(mock(ContextScope.class))) {
      tx.link(observation(43), observation(41), GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE);
      tx.link(observation(43), observation(42), GraphModel.Relationship.HAS_RELATIONSHIP_TARGET);
    }
    assertEquals(List.of(43L), query(41, false, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE));
    assertEquals(List.of(43L), query(42, false, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET));
    assertEquals(List.of(), query(42, false, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE));
    assertEquals(List.of(41L), query(43, true, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE));
    assertEquals(List.of(42L), query(43, true, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET));
    assertEquals(1L, rows("MATCH (:Cohort)-[:HAS_MEMBER]->(:Observation {id:43}) RETURN count(*) AS n",
        Map.of()).getFirst().get("n"));
  }

  @Test void bondsUseOnlyUnorderedParticipantEdgesAndCannotExposeOtherContexts() throws Exception {
    try (var tx = new ClassificationPersistenceTest.Fixture("a").createTransaction(mock(ContextScope.class))) {
      tx.link(observation(44), observation(41), GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);
      tx.link(observation(44), observation(42), GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);
      // Even a malformed stored reference must not grant visibility into another context.
      tx.link(observation(44), observation(45), GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);
    }
    assertEquals(List.of(44L), query(41, false, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT));
    assertEquals(List.of(44L), query(42, false, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT));
    assertEquals(Set.of(41L, 42L), new HashSet<>(query(44, true, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT)));
    assertEquals(List.of(), query(44, true, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE));
    assertEquals(List.of(), query(44, true, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET));
    assertFalse(KnowledgeGraphNeo4j.Queries.DELETION_OWNERSHIP.contains("HAS_RELATIONSHIP"));
  }
}
