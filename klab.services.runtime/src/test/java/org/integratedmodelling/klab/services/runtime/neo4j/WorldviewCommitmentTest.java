package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.WorldviewCommitment;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;

class WorldviewCommitmentTest {
  @Test void rootCommitmentIsLockedDurableAndCannotBeRebound() {
    var graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    graph.driver=mock(Driver.class);graph.rootContextId="test";
    var session=mock(Session.class);var transaction=mock(Transaction.class);var result=mock(Result.class);
    var node=mock(org.neo4j.driver.types.Node.class);var record=mock(org.neo4j.driver.Record.class);
    when(graph.driver.session()).thenReturn(session);when(session.beginTransaction()).thenReturn(transaction);
    when(transaction.run(anyString(),anyMap())).thenReturn(result);when(result.hasNext()).thenReturn(true);
    var nodeValue=mock(Value.class);when(nodeValue.asNode()).thenReturn(node);
    when(result.single()).thenReturn(record);when(record.get(0)).thenReturn(nodeValue);
    when(node.elementId()).thenReturn("root");when(node.get("worldviewCommitment")).thenReturn(Values.NULL);
    var first=new WorldviewCommitment(1,"world",Map.of("test","a".repeat(64)));
    graph.bindWorldview(first);verify(transaction).commit();
    verify(transaction).run(contains("SET n.worldviewCommitment"),org.mockito.ArgumentMatchers.<Map<String,Object>>argThat(map->Utils.Json.asString(first).equals(map.get("commitment"))));
    reset(transaction);when(transaction.run(anyString(),anyMap())).thenReturn(result);
    when(node.get("worldviewCommitment")).thenReturn(Values.value(Utils.Json.asString(first)));
    graph.bindWorldview(first);verify(transaction).commit();
    clearInvocations(transaction);
    var changed=new WorldviewCommitment(1,"world",Map.of("test","b".repeat(64)));
    assertThrows(IllegalStateException.class,()->graph.bindWorldview(changed));verify(transaction,never()).commit();
  }
}
