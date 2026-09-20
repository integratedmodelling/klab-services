package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ConsequenceClosureTest {
  @Test void closureFollowsEitherEndpointDeduplicatesCyclesAndIsolatesBearers() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var process = OccurrenceRegistrationTest.observation("P", SemanticType.PROCESS, 1);
    var a = OccurrenceRegistrationTest.observation("A", SemanticType.QUALITY, 2);
    var b = OccurrenceRegistrationTest.observation("B", SemanticType.QUALITY, 3);
    var c = OccurrenceRegistrationTest.observation("C", SemanticType.QUALITY, 4);
    var foreign = OccurrenceRegistrationTest.observation("Foreign", SemanticType.QUALITY, 5);
    var input = OccurrenceRegistrationTest.observation("Input", SemanticType.QUALITY, 6);
    var links = new ArrayList<KnowledgeGraph.Link>();
    links.add(link(process, a, ProcessPlan.INFLUENCE, 10));
    links.add(link(b, a, ProcessPlan.DESCRIPTIVE, 10));
    links.add(link(b, c, ProcessPlan.DESCRIPTIVE, 10));
    links.add(link(c, a, ProcessPlan.DESCRIPTIVE, 10));
    links.add(link(b, a, ProcessPlan.DESCRIPTIVE, 10));
    links.add(link(c, foreign, ProcessPlan.DESCRIPTIVE, 20));
    links.add(link(input, a, ProcessPlan.PREREQUISITE, 10));
    var graph = mock(KnowledgeGraph.class); var scope = mock(ContextScope.class);
    when(graph.getLinks(any(), any(), eq(scope), eq(GraphModel.Relationship.AFFECTS))).thenAnswer(call -> {
      var observation = (Observation) call.getArgument(0);
      var direction = (GraphModel.Relationship.Direction) call.getArgument(1);
      return links.stream().filter(l -> (direction == GraphModel.Relationship.Direction.INCOMING
          ? l.target() : l.source()).getId() == observation.getId()).toList();
    });
    assertEquals(List.of(a,b,c), ConsequenceClosure.affected(process,10,graph,scope));
    c.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T0(1){tstart=1704067200000,tend=1735689600000,ttype=PHYSICAL}"));
    assertEquals(List.of(a,b), ConsequenceClosure.affected(process,10,graph,scope));
    c.setGeometry(a.getGeometry());
    assertEquals(List.of(a,b,c), ConsequenceClosure.affected(c,10,graph,scope));
    assertEquals(List.of(a,b,c), ConsequenceClosure.affected(process,10,graph,scope));
    verify(graph, never()).createTransaction(any());
  }
  static KnowledgeGraph.Link link(Observation source, Observation target, String role, long bearer) {
    var link = mock(KnowledgeGraph.Link.class);
    when(link.source()).thenReturn(source); when(link.target()).thenReturn(target);
    when(link.properties()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create(
        ProcessPlan.EDGE_ROLE,role,"bearerId",bearer));
    return link;
  }
}
