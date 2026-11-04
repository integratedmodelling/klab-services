package org.integratedmodelling.klab.services.runtime;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
/** Real execution and deterministic scalar compiler I/O, not a token/proof constructor. */
public final class MembershipCountInvocation {
 public static String accounting(ServiceContextScope scope,org.integratedmodelling.klab.api.knowledge.observation.Observation input) throws Exception {
  var facts=new java.util.ArrayList<Object>();
  for(var tx=scope.getCurrentTransaction();tx!=null;tx=tx.getParent())for(var asset:tx.assets())if(asset instanceof org.integratedmodelling.klab.api.provenance.Activity activity) {
   var diagnostic=activity.getMetadata().get(SemanticExecutionEvidence.DIAGNOSTIC_KEY);if(diagnostic instanceof String text){var node=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper().readTree(text);var gates=new java.util.ArrayList<Object>();for(var event:node.path("events"))if(event.path("gate").asText().equals("UNKNOWN")||event.path("gate").asText().equals("executionFootprintBounded")||event.path("gate").asText().equals("inherit"))gates.add(event);facts.add(java.util.Map.of("activity",activity.getId(),"type",activity.getType(),"complete",node.path("complete").asBoolean(),"gates",gates));}
  }
  var edges=new java.util.ArrayList<Object>();for(var edge:scope.getDigitalTwin().getKnowledgeGraph().getLinks(input,org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.Direction.INCOMING,scope))if(edge.source() instanceof org.integratedmodelling.klab.api.provenance.Activity activity){var proof=SemanticExecutionEvidence.decode(activity);edges.add(java.util.Map.of("activity",activity.getId(),"type",activity.getType(),"relationship",edge.type(),"complete",proof!=null&&proof.complete()));}
  return org.integratedmodelling.klab.utilities.Utils.Json.asString(java.util.Map.of("input",input.getId(),"contextualization",input.getObservable().getContextualization(),"events",input.getEventTimestamps(),"incoming",edges,"accounting",facts));
 }
 public static Throwable failure(CompiledDataflow flow) {
  try {
   var field=CompiledDataflow.class.getDeclaredField("operations");field.setAccessible(true);
   for(var operation:((java.util.Map<?,?>)field.get(flow)).values()) {
    var engines=operation.getClass().getDeclaredField("executors");engines.setAccessible(true);
    for(var value:(java.util.List<?>)engines.get(operation)) {var cause=((CompiledDataflow.ContextualExecutor)value).getCause();if(cause!=null)return cause;}
   }
   return null;
  } catch(ReflectiveOperationException failure){return failure;}
 }
 public static org.integratedmodelling.klab.api.knowledge.observation.Observation query(RuntimeService runtime,org.integratedmodelling.klab.api.knowledge.observation.Observation query,ServiceContextScope scope) {return runtime.queryCollective(query,scope);}
 public static org.integratedmodelling.klab.api.knowledge.observation.Observation query(RuntimeService runtime,org.integratedmodelling.klab.api.knowledge.observation.Observation query,ServiceContextScope scope,org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding binding) {return runtime.queryCollective(query,scope,binding);}
 public static boolean execute(CompiledDataflow flow,Geometry geometry,ServiceContextScope scope) {
  return flow.executeRoot(geometry,Scheduler.Event.initialization(),scope);
 }
 public static void scalar(RuntimeService runtime) {
  doReturn(new ShardExecution(()->1)).when(runtime).shardExecution();
  doAnswer(c->org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy.builder(
      c.getArgument(0),c.getArgument(1),c.getArgument(2),c.getArgument(3)))
      .when(runtime).getComputationBuilder(any(),any(),any(),anyMap());
 }
}
