package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.services.runtime.digitaltwin.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.*;

/** Actual Activity property encoder/decoder, then unchanged role-arrival reader.
 * Opaque provenance is round-trip data here, never a claim of semantic admission. */
class ConsequenceRoleAuditPersistenceTest {
  private Activity persist(Activity activity) {
    var graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    var properties=graph.asParameters(activity);
    var record=mock(org.neo4j.driver.Record.class);when(record.values()).thenReturn(List.of(Values.value(properties)));
    var result=mock(EagerResult.class);when(result.records()).thenReturn(List.of(record));
    return graph.adapt(result,Activity.class,null).getFirst();
  }
  private Map<String,Object> row() {
    var row=new LinkedHashMap<String,Object>();row.put("sourceId",4986L);row.put("recipientId",4985L);
    row.put("ruleId","authored-rule");row.put("role","imod:Marked");
    row.put("before","imod:Bearer");row.put("after","imod:Marked imod:Bearer");
    row.put("authority","opaque-original-server-provenance");return row;
  }
  private Activity activity() {
    var activity=Activity.of(Activity.Type.CLASSIFICATION);activity.setId(5021);activity.setOutcome(Activity.Outcome.SUCCESS);
    return activity;
  }
  private RoleConsequenceRuntime.Arrivals read(Activity activity) {
    var graph=mock(KnowledgeGraph.class);var scope=mock(ServiceContextScope.class);var twin=mock(DigitalTwin.class);
    when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(graph.getAsset(5021,scope,Activity.class)).thenReturn(activity);
    var commit=new CommitImpl();commit.setId(5020);commit.getAddedAssets().add(5021L);
    commit.getAddedLinks().add(Triple.of(5021L,4985L,"CLASSIFIED"));
    var role=mock(Concept.class);when(role.getUrn()).thenReturn("imod:Marked");when(role.is(SemanticType.ROLE)).thenReturn(true);
    return RoleAttributionEvents.read(commit,scope,urn->role);
  }
  @Test void roleRowsAndOriginalScientificBindingSurviveNativeActivityProjection() {
    var activity=activity();var row=row();
    activity.getMetadata().put(RoleAttributionEvents.METADATA_KEY,List.of(row));
    // Preserve an honest incomplete sample verbatim; this test never upgrades its authority.
    var evidence=Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,false,List.of("imod:Birth"),null));
    activity.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,evidence);
    var restored=persist(activity);
    assertEquals(Activity.Type.CLASSIFICATION,restored.getType());assertEquals(Activity.Outcome.SUCCESS,restored.getOutcome());
    var rows=assertInstanceOf(List.class,restored.getMetadata().get(RoleAttributionEvents.METADATA_KEY));
    var actual=assertInstanceOf(Map.class,rows.getFirst());
    assertEquals(row.keySet(),actual.keySet());
    row.forEach((key,value)->{if(value instanceof Number n)assertEquals(n.longValue(),((Number)actual.get(key)).longValue());else assertEquals(value,actual.get(key));});
    assertEquals(evidence,restored.getMetadata().get(SemanticEvidenceBinding.DATA_KEY));
    var arrivals=read(restored);assertEquals(ConsequenceRecipients.Status.COMPLETE,arrivals.status());
    assertEquals(1,arrivals.values().size());assertEquals(4986L,arrivals.values().getFirst().sourceId());
    assertEquals(4985L,arrivals.values().getFirst().recipientId());assertEquals("authored-rule",arrivals.values().getFirst().ruleId());
    assertTrue(arrivals.values().getFirst().characterize());
  }
  @Test void missingAuditRemainsUnavailableAfterPersistence() {
    assertEquals(ConsequenceRecipients.Status.DEFERRED,read(persist(activity())).status());
  }
  @Test void malformedAuditIsRetainedButStillRejectedWithoutPartialArrivals() {
    var activity=activity();activity.getMetadata().put(RoleAttributionEvents.METADATA_KEY,List.of(row(),Map.of("role","imod:Marked")));
    var arrivals=read(persist(activity));assertEquals(ConsequenceRecipients.Status.DEFERRED,arrivals.status());assertTrue(arrivals.values().isEmpty());
  }
}
