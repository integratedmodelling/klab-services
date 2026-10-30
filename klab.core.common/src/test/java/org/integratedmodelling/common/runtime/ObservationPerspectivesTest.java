package org.integratedmodelling.common.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class ObservationPerspectivesTest {
  static ObservableImpl observable(String base, String observer) {
    var concept = new ConceptImpl(); concept.setUrn(base);
    concept.setType(EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE));
    var result = new ObservableImpl(); result.setSemantics(concept); result.setUrn(base);
    if (observer != null) {
      var agent = new ConceptImpl(); agent.setUrn(observer);
      agent.setType(EnumSet.of(SemanticType.AGENT, SemanticType.OBSERVABLE));
      result.setObserverSemantics(agent); result.setUrn(base + " observed as " + observer);
    }
    return result;
  }
  static Observation observation(long id, Observable observable) {
    var result = new ObservationImpl(); result.setId(id); result.setUrn("obs:" + id);
    result.setObservable(observable);
    result.getMetadata().put("im:observer-selection-id", 100L);
    return result;
  }
  static Reasoner canonicalBuilder(ContextScope scope, Map<String, Observable> canonical) {
    var reasoner = mock(Reasoner.class);
    doReturn(reasoner).when(scope).getService(Reasoner.class);
    when(reasoner.buildObservable(any(), same(scope))).thenAnswer(call -> {
      org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy builder = call.getArgument(0);
      assertEquals(List.of(
          org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.OperationType.WITH_OBSERVER_SEMANTICS,
          org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.OperationType.WITH_UNIT,
          org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.OperationType.WITH_CURRENCY),
          builder.getOperations().stream().map(org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.Operation::getType).toList());
      assertNull(builder.getOperations().get(1).getUnit());
      assertNull(builder.getOperations().get(2).getCurrency());
      assertEquals(org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.OperationType.WITH_OBSERVER_SEMANTICS,
          builder.getOperations().getFirst().getType());
      assertEquals(1,builder.getOperations().getFirst().getConcepts().size());
      assertNull(builder.getOperations().getFirst().getConcepts().getFirst());
      return Objects.requireNonNull(canonical.get(builder.getBaseObservable().getUrn()));
    });
    return reasoner;
  }
  @Test void retainsEveryOccurrenceAndAbsoluteButRejectsOtherCanonicalClauses() {
    var scope = mock(ContextScope.class);
    var absolute = observable("test:Height of test:Tree in m", null);
    var x = observable("test:Height of test:Tree in m", "test:X");
    var y = observable("test:Height of test:Tree in m", "test:Y");
    var otherBearer = observable("test:Height of test:Rock in m", "test:X");
    var otherUnit = observable("test:Height of test:Tree in ft", "test:X");
    var canonical = new HashMap<String, Observable>();
    for (var value : List.of(absolute,x,y)) canonical.put(value.getUrn(),absolute);
    canonical.put(otherBearer.getUrn(),observable("test:Height of test:Rock in m",null));
    canonical.put(otherUnit.getUrn(),observable("test:Height of test:Tree in ft",null));
    var reasoner = canonicalBuilder(scope, canonical);
    var first = observation(1,x); var repeated = observation(2,x); var third = observation(3,y);
    var fourth = observation(4,absolute);
    when(scope.getObservations()).thenReturn(List.of(first,repeated,third,fourth,
        observation(5,otherBearer),observation(6,otherUnit)));
    assertEquals(List.of(first,repeated,third,fourth), new ArrayList<>(ObservationPerspectives.find(scope,x)));
    verify(scope, never()).getObserver();
    verify(scope, never()).getCurrentTransaction();
    verify(reasoner, never()).semanticDistance(any(),any(),any());
    assertEquals("test:X",x.getObserverSemantics().getUrn());
  }
  @Test void queryFailurePropagatesAndDoesNotBecomeSuccessfulEmptyEnumeration() {
    var scope=mock(ContextScope.class); var absolute=observable("test:Height",null);
    canonicalBuilder(scope,Map.of(absolute.getUrn(),absolute));
    when(scope.getObservations()).thenThrow(new IllegalStateException("unavailable"));
    assertThrows(IllegalStateException.class,()->ObservationPerspectives.find(scope,absolute));
  }
  @Test void clientQueriesInsertionPointThroughProductionHttpAndInterfaceObservationTransport() throws Exception {
    var scope=mock(org.integratedmodelling.common.services.client.scope.ClientContextScope.class, CALLS_REAL_METHODS);
    doReturn("test.context").when(scope).getId();
    doReturn("test.runtime").when(scope).getHostServiceId();
    doReturn(org.integratedmodelling.klab.api.collections.Parameters.create()).when(scope).getData();
    doReturn(List.of()).when(scope).getResolutionConstraints();
    var runtime=mock(org.integratedmodelling.common.services.client.RuntimeClient.class);
    doCallRealMethod().when(runtime).queryKnowledgeGraph(any(),same(scope));
    var graph=new org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph(scope,runtime);
    var twin=mock(org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin.class);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var twinField=org.integratedmodelling.common.services.client.scope.ClientContextScope.class.getDeclaredField("digitalTwin");
    twinField.setAccessible(true);twinField.set(scope,twin);
    var absolute=observable("test:Height",null);var x=observable("test:Height","test:X");
    canonicalBuilder(scope,Map.of(absolute.getUrn(),absolute,x.getUrn(),absolute));
    var rootRecord=observation(11,x);var focusedRecord=observation(12,x);
    var focal=observation(20,observable("test:Tree",null));
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var anchors=new ArrayList<String>();
    var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      var query=mapper.readValue(exchange.getRequestBody(),org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.class);
      assertEquals(org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_CHILD,query.getRelationship());
      anchors.add(query.getSource().getType()+":"+query.getSource().getUrn());
      var records=query.getSource().getType()==org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.AssetType.SCOPE
          ? List.of(rootRecord) : List.of(focusedRecord);
      var bytes=mapper.writeValueAsBytes(records);
      exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
      try(var output=exchange.getResponseBody()){output.write(bytes);}
    });server.start();
    try(var http=org.integratedmodelling.common.utils.Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var field=org.integratedmodelling.common.services.client.BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);field.set(runtime,http);
      var root=scope.<Observation>getPerspectives(x);assertEquals(List.of(11L),root.stream().map(Observation::getId).toList());
      assertEquals("test:X",root.iterator().next().getObservable().getObserverSemantics().getUrn());
      doReturn(focal).when(scope).getContextObservation();
      assertEquals(List.of(12L),scope.<Observation>getPerspectives(x).stream().map(Observation::getId).toList());
      assertEquals(List.of("SCOPE:null","OBSERVATION:obs:20"),anchors);
      verify(runtime,never()).submit(any(),any());
    } finally {server.stop(0);}
  }

  @Test void preservesTypedRangeAndExplicitActivityAndAllowsSubstantialPerspectives() {
    var scope=mock(ContextScope.class);
    var query=observable("test:Height","test:X");
    query.setRange(org.integratedmodelling.klab.api.data.mediation.NumericRange.create(0,10));
    query.setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.QUANTIFICATION);
    var match=new ObservableImpl(query);
    var otherRange=new ObservableImpl(query);
    otherRange.setRange(org.integratedmodelling.klab.api.data.mediation.NumericRange.create(0,20));
    var otherActivity=new ObservableImpl(query);
    otherActivity.setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.VERIFICATION);
    var canonical=observable("test:Height",null);
    canonicalBuilder(scope,Map.of(query.getUrn(),canonical));
    var accepted=observation(1,match);
    when(scope.getObservations()).thenReturn(List.of(accepted,observation(2,otherRange),observation(3,otherActivity)));
    assertEquals(List.of(accepted),new ArrayList<>(ObservationPerspectives.find(scope,query)));
    var subject=observable("test:Tree",null);
    ((ConceptImpl)subject.getSemantics()).setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));
    canonicalBuilder(scope,Map.of(subject.getUrn(),subject));
    var substantial=observation(4,subject);
    when(scope.getObservations()).thenReturn(List.of(substantial));
    assertEquals(List.of(substantial),new ArrayList<>(ObservationPerspectives.find(scope,subject)));
  }

}
