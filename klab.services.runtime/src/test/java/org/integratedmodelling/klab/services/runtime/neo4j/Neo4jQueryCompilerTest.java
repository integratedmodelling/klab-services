package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.Query;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.QueryException;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.parser.CypherParser;

class Neo4jQueryCompilerTest {
  private KnowledgeGraphQuery<Observation> observations() {
    return new KnowledgeGraphQuery<>(KnowledgeGraphQuery.AssetType.OBSERVATION);
  }

  private Neo4jQueryCompiler.Statement compile(KnowledgeGraphQuery<?> query) {
    var statement = Neo4jQueryCompiler.compile(query, "owner.context");
    assertDoesNotThrow(() -> CypherParser.parseStatement(statement.cypher()));
    return statement;
  }

  @Test
  void wholeContextRangeIsTypedParameterizedAndPaginated() {
    var query = observations();
    query.where(GraphModel.Fields.SIZE, Query.Operator.GE, 10L)
        .where(GraphModel.Fields.SIZE, Query.Operator.LT, 30L)
        .order(Query.Order.descending(GraphModel.Fields.SIZE)).offset(4).limit(8);
    var statement = compile(query);
    assertTrue(statement.cypher().contains("n.`size` >= $"));
    assertTrue(statement.cypher().contains("n.`size` < $"));
    assertTrue(statement.cypher().contains("ORDER BY n.`size` DESC, elementId(n) ASC SKIP $"));
    assertTrue(statement.parameters().containsValue(10L));
    assertTrue(statement.parameters().containsValue("owner.context"));
  }

  @Test
  void outgoingAndIncomingMultiHopPathsRetainTheirDirection() {
    var observation = new ObservationImpl();
    observation.setId(42);
    var outgoing = observations();
    outgoing.source(observation).along(GraphModel.Relationship.AFFECTS).hops(0, 4);
    var incoming = observations();
    incoming.target(observation).along(GraphModel.Relationship.AFFECTS).depth(3);
    assertTrue(compile(outgoing).cypher().contains("(a0)-[:AFFECTS*0..4]->(n)"));
    assertTrue(compile(incoming).cypher().contains("(n)-[:AFFECTS*1..3]->(a0)"));
  }

  @Test
  void compositionRetainsResultTypeAndBooleanMeaningAcrossJson() {
    var a = observations();
    a.where(GraphModel.Fields.NAME, Query.Operator.EQUALS, "a");
    var b = observations();
    b.where(GraphModel.Fields.NAME, Query.Operator.EQUALS, "b");
    var or = (KnowledgeGraphQuery<?>) a.or(b);
    var and = (KnowledgeGraphQuery<?>) a.and(b);
    assertEquals(KnowledgeGraphQuery.QueryType.OR, or.getType());
    assertEquals(KnowledgeGraphQuery.QueryType.AND, and.getType());
    var copy = Utils.Json.parseObject(Utils.Json.asString(or), KnowledgeGraphQuery.class);
    assertEquals(KnowledgeGraphQuery.AssetType.OBSERVATION, copy.getResultType());
    assertTrue(compile(copy).cypher().contains(" OR "));
    assertTrue(compile((KnowledgeGraphQuery<?>) and.not()).cypher().contains("NOT "));
  }

  @Test
  void quoteBearingIdentifiersAreNeverExecutableCypher() {
    var query = observations();
    String attack = "x\"}) DETACH DELETE n //";
    query.where(GraphModel.Fields.URN, Query.Operator.EQUALS, attack);
    var statement = compile(query);
    assertFalse(statement.cypher().contains(attack));
    assertTrue(statement.parameters().containsValue(attack));
  }

  @Test
  void nullMatchesAbsenceAndTemporalPredicatesNormalizeInstants() {
    var query = observations();
    query.where(GraphModel.Fields.NAME, Query.Operator.EQUALS, null)
        .where(GraphModel.Fields.START, Query.Operator.BEFORE, "1970-01-01T00:00:01Z");
    var statement = compile(query);
    assertTrue(statement.cypher().contains("n.`name` IS NULL"));
    assertTrue(statement.parameters().containsValue(1000L));
  }

  @Test
  void unsupportedPredicatesAndInvalidFieldsFailExplicitly() {
    var spatial = observations();
    spatial.where(GraphModel.Fields.GEOMETRY, Query.Operator.COVERS, "shape");
    assertEquals(QueryException.Code.UNSUPPORTED_QUERY,
        assertThrows(QueryException.class, () -> compile(spatial)).getCode());
    var invalid = observations();
    invalid.where("name`) DELETE n //", Query.Operator.EQUALS, "x");
    assertEquals(QueryException.Code.INVALID_QUERY,
        assertThrows(QueryException.class, () -> compile(invalid)).getCode());
  }

  @Test
  void badHopRangesAndNestedPaginationAreRejected() {
    var query = observations();
    query.hops(4, 2);
    assertThrows(QueryException.class, () -> compile(query));
    var paginated = observations();
    paginated.limit(2);
    var combined = (KnowledgeGraphQuery<?>) paginated.or(observations());
    assertThrows(QueryException.class, () -> compile(combined));
  }

  @Test
  void betweenReturnsDirectedLinksWithProperties() {
    var a = new ObservationImpl(); a.setId(10);
    var b = new ObservationImpl(); b.setId(11);
    var query = new KnowledgeGraphQuery<KnowledgeGraph.Link>(KnowledgeGraphQuery.AssetType.LINK);
    query.between(a, b, GraphModel.Relationship.AFFECTS)
        .where(GraphModel.Fields.SEQUENCE, Query.Operator.GE, 2);
    var statement = compile(query);
    assertTrue(statement.cypher().contains("RETURN DISTINCT n, s, t"));
    assertTrue(statement.parameters().containsValue(10L));
    assertTrue(statement.parameters().containsValue(11L));
  }
}
