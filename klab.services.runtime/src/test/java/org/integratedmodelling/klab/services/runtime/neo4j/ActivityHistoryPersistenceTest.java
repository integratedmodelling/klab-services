package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.Values;

class ActivityHistoryPersistenceTest {
  @Test void contextCreationActivityWithoutSchedulerTimeCanBeRecovered() {
    // CREATE_CONTEXT writes only id, type, start and end for this activity.
    var activity=decode(Map.of("id",1L,"type","CONTEXT_INITIALIZATION","start",100L,"end",100L));
    assertEquals(1L,activity.getId());
    assertEquals(Activity.Type.CONTEXT_INITIALIZATION,activity.getType());
    assertTrue(activity.getSchedulerTime().isEmpty());
  }

  @Test void temporalActivityRetainsSchedulerTime() {
    assertEquals(List.of(100L,200L),decode(Map.of("id",2L,"type","SIMULATION",
        "schedulerTime",List.of(100L,200L))).getSchedulerTime());
  }

  private Activity decode(Map<String,Object> properties) {
    var record=mock(org.neo4j.driver.Record.class);
    when(record.values()).thenReturn(List.of(Values.value(properties)));
    var result=mock(EagerResult.class);when(result.records()).thenReturn(List.of(record));
    var graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    return graph.adapt(result,Activity.class,null).getFirst();
  }
}
