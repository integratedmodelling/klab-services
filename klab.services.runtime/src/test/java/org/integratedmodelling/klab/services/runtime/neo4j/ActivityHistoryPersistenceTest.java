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
  @Test void collectiveCompletionTypeAndOutcomeSurviveProductionPropertyEncodingAndRecovery() {
    var graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    for(var type:List.of(Activity.Type.INSTANTIATION,Activity.Type.CONNECTION)) {
      for(var outcome:Activity.Outcome.values()) {
        var activity=Activity.of(type); activity.setId(42); activity.setOutcome(outcome);
        activity.setObservationUrn("context.collective.10");
        activity.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_EXECUTION_GEOMETRY,"T0(1){tstart=0,tend=10,ttype=PHYSICAL}");
        var recovered=decode(graph.asParameters(activity));
        assertEquals(42,recovered.getId()); assertEquals(type,recovered.getType());
        assertEquals(outcome,recovered.getOutcome());
        assertEquals("context.collective.10",recovered.getObservationUrn());
        assertEquals(activity.getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.IM_EXECUTION_GEOMETRY),
            recovered.getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.IM_EXECUTION_GEOMETRY));
      }
    }
    assertNull(decode(Map.of("id",43L,"type","INSTANTIATION")).getOutcome());
  }
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
