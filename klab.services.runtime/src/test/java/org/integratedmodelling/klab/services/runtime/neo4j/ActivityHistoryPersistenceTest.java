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

  @Test void memberResultsRetainProvisionalIdentityAndAllConcretePredicatesOnRecovery() {
    var graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    var activity=Activity.of(Activity.Type.CLASSIFICATION);activity.setOutcome(Activity.Outcome.SUCCESS);
    var item=new org.integratedmodelling.klab.api.services.runtime.objects.ClassificationMemberResult(-7,53,null, "test:Input", "test:Abstract", List.of("test:A","test:B"),
        org.integratedmodelling.klab.api.services.runtime.objects.ClassificationMemberResult.Origin.EXISTING,"T0(1){tstart=0,tend=10,ttype=PHYSICAL}","initialization");
    var evidence=new java.util.LinkedHashMap<String,Object>();
    evidence.put("version",1);evidence.put("rootAttempt","root-1");evidence.put("knowledgeRevision",-1);
    evidence.put("reasonerId",null);evidence.put("memberResults",List.of(item.metadata()));
    activity.getMetadata().put(org.integratedmodelling.klab.api.data.Metadata.IM_CLASSIFICATION_MEMBER_RESULTS,evidence);
    var restored=decode(graph.asParameters(activity));
    var result=(Map<?,?>)restored.getMetadata().get(org.integratedmodelling.klab.api.data.Metadata.IM_CLASSIFICATION_MEMBER_RESULTS);
    assertNotNull(result.get("memberResults"), ()->"Recovered: "+result+"; encoded: "+graph.asParameters(activity));
    var member=(Map<?,?>)((List<?>)result.get("memberResults")).getFirst();
    assertEquals(-7L,((Number)member.get("memberIdAtInvocation")).longValue());
    assertEquals(53L,((Number)member.get("memberTransientId")).longValue());
    assertNull(member.get("memberUrnAtInvocation"));assertNull(result.get("reasonerId"));
    assertEquals(-1,((Number)result.get("knowledgeRevision")).intValue());
    assertEquals(List.of("test:A","test:B"),member.get("concretePredicates"));assertEquals("EXISTING",member.get("origin"));assertEquals("root-1",result.get("rootAttempt"));
  }

  private Activity decode(Map<String,Object> properties) {
    var record=mock(org.neo4j.driver.Record.class);
    when(record.values()).thenReturn(List.of(Values.value(properties)));
    var result=mock(EagerResult.class);when(result.records()).thenReturn(List.of(record));
    var graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    return graph.adapt(result,Activity.class,null).getFirst();
  }
}
