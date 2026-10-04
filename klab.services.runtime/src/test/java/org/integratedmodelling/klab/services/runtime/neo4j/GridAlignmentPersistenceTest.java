package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;

class GridAlignmentPersistenceTest {
  @Test void resumeRetainsSuppressionAnnotationsAndRawWarnings() {
    var frozen=new GridAlignment(1,"test.grid","fingerprint","EPSG:4326",0,0,1,1,false,true,
        List.of(1.0),List.of("intentional adjustment"),null,null,
        List.of(org.integratedmodelling.klab.api.lang.Annotation.of("suppress")));
    var requested=DigitalTwin.Configuration.builder().grid("test.grid").build();
    KnowledgeGraphNeo4j.restoreGrid(requested,Values.value(Utils.Json.asString(frozen)));
    assertEquals(frozen,requested.getGridAlignment());
    assertEquals(List.of("intentional adjustment"),requested.getGridAlignment().warnings());
    assertTrue(requested.getGridAlignment().emittedWarnings().isEmpty());
  }
  static GridAlignment grid(String urn,double step) {
    return new GridAlignment(1,urn,"test-fingerprint","EPSG:32634",0,0,step,step,true,true,List.of(1.0),List.of());
  }
  @Test void resumeRestoresFrozenStepsInsteadOfAChangedDefinition() {
    var frozen=grid("test.grid",50);
    var requested=DigitalTwin.Configuration.builder().name("test").gridAlignment(grid("test.grid",100)).build();
    KnowledgeGraphNeo4j.restoreGrid(requested,Values.value(Utils.Json.asString(frozen)));
    assertEquals(frozen,requested.getGridAlignment());
    var other=DigitalTwin.Configuration.builder().name("test").grid("other.grid").build();
    assertThrows(IllegalStateException.class,()->KnowledgeGraphNeo4j.restoreGrid(other,Values.value(Utils.Json.asString(frozen))));
    assertThrows(IllegalStateException.class,()->KnowledgeGraphNeo4j.restoreGrid(requested,Values.NULL));
    var unspecified=DigitalTwin.Configuration.builder().name("test").build();
    KnowledgeGraphNeo4j.restoreGrid(unspecified,Values.value(Utils.Json.asString(frozen)));
    assertEquals(frozen,unspecified.getGridAlignment());assertEquals("test.grid",unspecified.getGridUrn());
  }
  @Test void inlineResumeRestoresFrozenAlignmentAndRejectsDifferentInstructions() {
    var specification=Map.<String,Object>of("anchor","POINT (0 0)","projection","EPSG:32634","span",50);
    var frozen=org.integratedmodelling.klab.runtime.scale.space.GridAlignmentSupport.decode(specification);
    var requested=DigitalTwin.Configuration.builder().grid(specification).build();
    KnowledgeGraphNeo4j.restoreGrid(requested,Values.value(Utils.Json.asString(frozen)));
    assertEquals(frozen,requested.getGridAlignment());
    assertNotNull(requested.getGridAlignment().codeDefinition());
    var other=DigitalTwin.Configuration.builder().grid(Map.of("anchor","POINT (0 0)","span",100)).build();
    assertThrows(IllegalStateException.class,()->KnowledgeGraphNeo4j.restoreGrid(other,Values.value(Utils.Json.asString(frozen))));
  }
  @Test void installIsLockedPersistedIdempotentAndRejectsReplacementOrLateConfiguration() {
    var graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);graph.driver=mock(Driver.class);graph.rootContextId="test";
    var session=mock(Session.class);var tx=mock(Transaction.class);var result=mock(Result.class);
    var node=mock(org.neo4j.driver.types.Node.class);var record=mock(org.neo4j.driver.Record.class);
    when(graph.driver.session()).thenReturn(session);when(session.beginTransaction()).thenReturn(tx);
    when(tx.run(anyString(),anyMap())).thenReturn(result);when(result.hasNext()).thenReturn(true);
    when(result.single()).thenReturn(record);
    var nodeValue=mock(Value.class);when(nodeValue.asNode()).thenReturn(node);when(record.get(0)).thenReturn(nodeValue);
    when(record.get("occupied")).thenReturn(Values.value(false));when(node.get(anyString())).thenReturn(Values.NULL);
    var first=grid("test.grid",50);
    assertEquals(first,graph.installGrid(first));verify(tx).commit();
    verify(tx).run(contains("SET ctx.gridAlignment"),org.mockito.ArgumentMatchers.<Map<String,Object>>argThat(map->Utils.Json.asString(first).equals(map.get("grid"))));
    when(node.get("gridAlignment")).thenReturn(Values.value(Utils.Json.asString(first)));clearInvocations(tx);
    assertEquals(first,graph.installGrid(grid("test.grid",100)));verify(tx).commit();
    clearInvocations(tx);assertThrows(IllegalStateException.class,()->graph.installGrid(grid("other.grid",50)));verify(tx,never()).commit();
    when(node.get("gridAlignment")).thenReturn(Values.NULL);when(record.get("occupied")).thenReturn(Values.value(true));
    assertThrows(IllegalStateException.class,()->graph.installGrid(first));
    when(record.get("occupied")).thenReturn(Values.value(false));when(node.get("gridFrozen")).thenReturn(Values.value(true));
    assertThrows(IllegalStateException.class,()->graph.installGrid(first));
  }
}
