package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.runtime.CompiledDataflow;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class ComputationalClosureTest {
  @BeforeAll static void configure() { org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators(); }
  static void plan(Observation target, Observation... inputs) {
    var plan = new ActuatorImpl(); plan.setName("compute"); plan.setObservation(target);
    plan.setActuatorType(Actuator.Type.RESOLVE); plan.getComputation().add(new ServiceCallImpl("test.compute"));
    for (var input : inputs) {
      var child = new ActuatorImpl(); child.setName(input.getUrn()); child.setObservation(input);
      child.setActuatorType(Actuator.Type.REFERENCE); plan.getChildren().add(child);
    }
    target.getMetadata().put(Scheduler.PLAN_METADATA_KEY, Utils.Json.asString(CompiledDataflow.portableOccurrencePlan(plan)));
  }

  @Test void semanticCyclesDoNotExecuteButOriginalComputationPathsDo() {
    var process = OccurrenceRegistrationTest.observation("process",SemanticType.PROCESS,1);
    var a = OccurrenceRegistrationTest.observation("a",SemanticType.QUALITY,2);
    var b = OccurrenceRegistrationTest.observation("b",SemanticType.QUALITY,3);
    var c = OccurrenceRegistrationTest.observation("c",SemanticType.QUALITY,4);
    var onlySemantic = OccurrenceRegistrationTest.observation("semantic",SemanticType.QUALITY,5);
    var foreign = OccurrenceRegistrationTest.observation("foreign",SemanticType.QUALITY,6);
    foreign.setParentId(20);
    plan(b,a); plan(c,a,b);
    var links = new ArrayList<KnowledgeGraph.Link>(List.of(
        ConsequenceClosureTest.link(process,a,ProcessPlan.INFLUENCE,10),
        ConsequenceClosureTest.link(b,a,ProcessPlan.DESCRIPTIVE,10),
        ConsequenceClosureTest.link(a,onlySemantic,ProcessPlan.DESCRIPTIVE,10),
        ConsequenceClosureTest.link(onlySemantic,a,ProcessPlan.DESCRIPTIVE,10),
        ConsequenceClosureTest.link(a,b,ProcessPlan.PREREQUISITE,10),
        ConsequenceClosureTest.link(a,c,ProcessPlan.PREREQUISITE,10),
        ConsequenceClosureTest.link(b,c,ProcessPlan.PREREQUISITE,10),
        ConsequenceClosureTest.link(a,foreign,ProcessPlan.PREREQUISITE,10)));
    var graph = mock(KnowledgeGraph.class); var scope = mock(ContextScope.class);
    when(graph.getLinks(any(),eq(GraphModel.Relationship.Direction.OUTGOING),eq(scope),eq(GraphModel.Relationship.AFFECTS)))
        .thenAnswer(call -> links.stream().filter(l -> l.source().getId() == ((Observation)call.getArgument(0)).getId()).toList());
    assertEquals(List.of(b,c),ComputationalClosure.ordered(process,10,process.getGeometry(),graph,scope));
    b.getMetadata().remove(Scheduler.PLAN_METADATA_KEY);
    assertThrows(IllegalStateException.class,()->ComputationalClosure.ordered(process,10,process.getGeometry(),graph,scope));
    plan(b,a,c);
    links.add(ConsequenceClosureTest.link(c,b,ProcessPlan.PREREQUISITE,10));
    assertThrows(IllegalStateException.class,()->ComputationalClosure.ordered(process,10,process.getGeometry(),graph,scope));
  }
}
