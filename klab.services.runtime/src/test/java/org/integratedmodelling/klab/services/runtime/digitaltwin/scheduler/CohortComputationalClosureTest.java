package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.utilities.Utils;
class CohortComputationalClosureTest {
 @Test void schedulerCatalogueAssociationRetainsExactPlanAndNativeMeaningGuards() {
  var f=new Fixture();assertDoesNotThrow(()->ComputationalClosure.requireCollectiveInput(f.cohort,f.edge,f.plan,f.scope));
  f.edge.properties().put(ProcessPlan.COLLECTIVE_COHORT_OBSERVABLE,"test:Other");
  assertThrows(IllegalStateException.class,()->ComputationalClosure.requireCollectiveInput(f.cohort,f.edge,f.plan,f.scope));
 }
 @Test void unrelatedOrUnqualifiedCatalogueEdgeCannotBypassPrerequisiteValidation() {
  var f=new Fixture();f.edge.properties().remove(ProcessPlan.EDGE_ROLE);
  assertThrows(IllegalStateException.class,()->ComputationalClosure.requireCollectiveInput(f.cohort,f.edge,f.plan,f.scope));
  f.edge.properties().put(ProcessPlan.EDGE_ROLE,ProcessPlan.PREREQUISITE);f.edge.properties().put(CollectiveReductionBinding.DATA_KEY,"different");
  assertThrows(IllegalStateException.class,()->ComputationalClosure.requireCollectiveInput(f.cohort,f.edge,f.plan,f.scope));
 }
 @BeforeAll static void setup(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
 static class Fixture {
  final KnowledgeGraph graph=mock(KnowledgeGraph.class);final ContextScope scope=mock(ContextScope.class);
  final Reasoner reasoner=mock(Reasoner.class);final CohortImpl cohort=new CohortImpl();
  final org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl count=
      OccurrenceRegistrationTest.observation("count",SemanticType.QUALITY,80);
  final ActuatorImpl plan=new ActuatorImpl();final LinkImpl edge;final String encoded;
  Fixture() {
   var base=new ConceptImpl();base.setUrn("test:Stream");base.setType(EnumSet.of(SemanticType.SUBJECT));
   var full=new ConceptImpl();full.setUrn("each test:Stream");full.setType(EnumSet.of(SemanticType.SUBJECT));full.setCollective(true);
   count.setParentId(10);
   cohort.setId(70);cohort.setObservable(ObservableImpl.promote(base,null));cohort.setGeometry(count.getGeometry());
   when(scope.getService(Reasoner.class)).thenReturn(reasoner);
   when(reasoner.resolveObservable("each test:Stream")).thenReturn(ObservableImpl.promote(full,null));
   when(reasoner.resolveObservable("test:Stream")).thenReturn(cohort.getObservable());
   when(reasoner.baseSubstantialType(any(),same(scope))).thenReturn(base);
   var context=new SingularContextBinding(10,"context.10","test:Region",count.getGeometry().encode(),
       count.getObservable().getUrn(),"reasoner",1);
   var binding=new CollectiveReductionBinding(CollectiveReductionBinding.Mode.COUNT,context,"each test:Stream",count.getGeometry().encode(),null,null,null);
   encoded=Utils.Json.asString(binding);plan.setId(80);plan.setObservation(count);plan.setActuatorType(Actuator.Type.OBSERVE);
   plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);plan.getData().put(CollectiveReductionBinding.DATA_KEY,encoded);
   count.getMetadata().put(Scheduler.PLAN_METADATA_KEY,Utils.Json.asString(plan));
   edge=new LinkImpl(cohort,count,GraphModel.Relationship.AFFECTS);edge.properties().put(ProcessPlan.EDGE_ROLE,ProcessPlan.PREREQUISITE);
   edge.properties().put(CollectiveReductionBinding.DATA_KEY,encoded);edge.properties().put("collectiveCohortObservable","test:Stream");
   when(graph.getLinks(any(),eq(GraphModel.Relationship.Direction.OUTGOING),same(scope),eq(GraphModel.Relationship.AFFECTS)))
       .thenAnswer(call->((RuntimeAsset)call.getArgument(0)).getId()==70?List.of(edge):List.of());
   when(graph.isObservationCurrent(count,scope)).thenReturn(true);
  }
  List<org.integratedmodelling.klab.api.knowledge.observation.Observation> ordered(){return ComputationalClosure.ordered(cohort,10,count.getGeometry(),graph,scope);}
 }
 @Test void genuineEmptyComputationReductionIsReachableThroughItsExactCohortBinding() {
  var f=new Fixture();assertTrue(f.plan.getComputation().isEmpty());assertEquals(List.of(f.count),f.ordered());
 }
 @Test void modifiedInputProofOrCohortHeaderRejectsRatherThanPropagatingToUnrelatedCount() {
  var f=new Fixture();f.edge.properties().put(CollectiveReductionBinding.DATA_KEY,"{}");assertThrows(IllegalStateException.class,f::ordered);
  f.edge.properties().put(CollectiveReductionBinding.DATA_KEY,f.encoded);f.edge.properties().put("collectiveCohortObservable","test:Other");
  assertThrows(IllegalStateException.class,f::ordered);
 }
 @Test void deactivatedConsumerIsNeverSelectedForScheduling() {
  var f=new Fixture();when(f.graph.isObservationCurrent(f.count,f.scope)).thenReturn(false);assertTrue(f.ordered().isEmpty());
 }
 @Test void rootFocusedMembershipChangeSelectsItsCertifiedConsumerInAnotherNativeBearer() {
  var f=new Fixture();f.count.setParentId(50);
  assertEquals(List.of(f.count),ComputationalClosure.ordered(f.cohort,0,f.count.getGeometry(),f.graph,f.scope));
 }

}
