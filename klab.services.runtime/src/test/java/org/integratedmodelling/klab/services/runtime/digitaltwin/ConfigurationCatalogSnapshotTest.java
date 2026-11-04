package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

class ConfigurationCatalogSnapshotTest {
  final ServiceContextScope scope=mock(ServiceContextScope.class);
  final DigitalTwin twin=mock(DigitalTwin.class);
  final KnowledgeGraph graph=mock(KnowledgeGraph.class);
  final Reasoner reasoner=mock(Reasoner.class);
  final CohortImpl cohort=new CohortImpl();
  final ObservationImpl configuration=new ObservationImpl();
  final ConceptImpl meaning=new ConceptImpl();
  final KnowledgeGraph.Transaction firstTx=mock(KnowledgeGraph.Transaction.class),membersTx=mock(KnowledgeGraph.Transaction.class),finalTx=mock(KnowledgeGraph.Transaction.class);
  final List<String> events=new ArrayList<>();
  final KnowledgeGraph.ConsequenceReadSet first,members,complete;
  ConfigurationCatalogSnapshotTest()throws Exception {
    when(scope.getId()).thenReturn("context");when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    meaning.setUrn("test:Network");meaning.setType(EnumSet.of(SemanticType.CONFIGURATION,SemanticType.OBSERVABLE));
    var observable=new ObservableImpl();observable.setUrn(meaning.getUrn());observable.setSemantics(meaning);
    cohort.setId(100);cohort.setObservable(observable);configuration.setId(50);configuration.setObservable(observable);configuration.setGeometry(Geometry.create("1"));
    configuration.getMetadata().put("klab.configuration.current",Utils.Json.asString(new ConfigurationConsequenceRuntime.Revision(50,1,1,List.of(10L),"1")));
    configuration.getMetadata().put(ConfigurationPerspective.METADATA,Utils.Json.asString(new ConfigurationPerspective("context",0,0)));
    var root=asset(100,"Cohort");var config=asset(50,"Observation");var participant=asset(10,"Observation");
    var membership=edge("member",100,50,GraphModel.Relationship.HAS_MEMBER,Map.of());
    var emerged=edge("ingredient",50,10,GraphModel.Relationship.EMERGED_FROM,Map.of());
    var affects=edge("modeled-input",10,50,GraphModel.Relationship.AFFECTS,Map.of("role","configuration-input"));
    var selection=new KnowledgeGraph.ConsequenceEdgeSelection(100,GraphModel.Relationship.HAS_MEMBER,GraphModel.Relationship.Direction.OUTGOING);
    var selected=List.of(selection,new KnowledgeGraph.ConsequenceEdgeSelection(50,GraphModel.Relationship.EMERGED_FROM,GraphModel.Relationship.Direction.OUTGOING),new KnowledgeGraph.ConsequenceEdgeSelection(50,GraphModel.Relationship.AFFECTS,GraphModel.Relationship.Direction.INCOMING));
    first=new KnowledgeGraph.ConsequenceReadSet("context",List.of(root),List.of(selection),List.of(membership));
    members=new KnowledgeGraph.ConsequenceReadSet("context",List.of(root,config),selected,List.of(membership,emerged,affects));
    complete=new KnowledgeGraph.ConsequenceReadSet("context",List.of(root,config,participant),selected,List.of(membership,emerged,affects));
    when(graph.createTransaction(scope)).thenReturn(firstTx,membersTx,finalTx);
    when(firstTx.captureConsequenceInputs(any(),any())).thenReturn(first);when(membersTx.captureConsequenceInputs(any(),any())).thenReturn(members);
    when(finalTx.captureConsequenceInputs(any(),any())).thenReturn(complete);
    doAnswer(call->{events.add("firstClosed");return null;}).when(firstTx).close();
    doAnswer(call->{events.add("membersClosed");return null;}).when(membersTx).close();
    doAnswer(call->{events.add("finalClosed");return null;}).when(finalTx).close();
    when(graph.materializeConsequenceObservation(config,scope)).thenAnswer(call->{assertEquals(List.of("firstClosed","membersClosed","finalClosed"),events);return configuration;});
    when(reasoner.is(meaning,meaning)).thenAnswer(call->{assertEquals(3,events.size());return true;});
  }
  static KnowledgeGraph.ConsequenceAssetSnapshot asset(long id,String label){return new KnowledgeGraph.ConsequenceAssetSnapshot(id,"n"+id,List.of(label),Map.of("id",id),"1",null);}
  static KnowledgeGraph.ConsequenceEdgeSnapshot edge(String key,long source,long target,GraphModel.Relationship relationship,Map<String,Object> properties){
    return new KnowledgeGraph.ConsequenceEdgeSnapshot(key,new KnowledgeGraph.ConsequenceAssetRef("n"+source,source,List.of(source==100?"Cohort":"Observation")),new KnowledgeGraph.ConsequenceAssetRef("n"+target,target,List.of("Observation")),relationship,properties);
  }
  @Test void lowerIdExpansionUsesShortTransactionsAndOneFinalCompleteCapture()throws Exception {
    var result=ConfigurationCatalogSnapshot.capture(cohort,meaning,scope);assertEquals(Set.of(50L),result.configurations().keySet());
    verify(firstTx,times(1)).captureConsequenceInputs(any(),any());verify(membersTx,times(1)).captureConsequenceInputs(any(),any());
    verify(finalTx,times(1)).captureConsequenceInputs(eq(new TreeSet<>(List.of(10L,50L,100L))),any());
    var order=inOrder(firstTx,membersTx,finalTx);order.verify(firstTx).close();order.verify(membersTx).close();
    order.verify(finalTx).captureConsequenceInputs(any(),any());order.verify(finalTx).validateConsequenceInputs(first);order.verify(finalTx).validateConsequenceInputs(members);order.verify(finalTx).close();
    assertTrue(result.reads().edges().stream().anyMatch(edge->edge.relationship()==GraphModel.Relationship.AFFECTS));
  }
  @Test void changedProvisionalMembershipCannotPublishAnIncompleteFinalView() {
    doThrow(new IllegalStateException("membership changed")).when(finalTx).validateConsequenceInputs(first);
    assertThrows(IllegalStateException.class,()->ConfigurationCatalogSnapshot.capture(cohort,meaning,scope));
    verify(graph,never()).materializeConsequenceObservation(any(),any());verifyNoInteractions(reasoner);
  }
  @Test void otherPerspectiveIsNotAdopted() {
    configuration.getMetadata().put(ConfigurationPerspective.METADATA,Utils.Json.asString(new ConfigurationPerspective("context",17,0)));
    assertTrue(ConfigurationCatalogSnapshot.capture(cohort,meaning,scope).configurations().isEmpty());
  }
  @Test void missingOrMalformedManagedPerspectiveFailsClosed()throws Exception {
    configuration.getMetadata().remove(ConfigurationPerspective.METADATA);
    assertThrows(IllegalStateException.class,()->ConfigurationCatalogSnapshot.capture(cohort,meaning,scope));
    var fresh=new ConfigurationCatalogSnapshotTest();
    fresh.configuration.getMetadata().put(ConfigurationPerspective.METADATA,"not json");
    assertThrows(IllegalStateException.class,()->ConfigurationCatalogSnapshot.capture(fresh.cohort,fresh.meaning,fresh.scope));
  }
  @Test void differentContextOrFocusCannotAdoptSameManagedIdentity()throws Exception {
    configuration.getMetadata().put(ConfigurationPerspective.METADATA,Utils.Json.asString(new ConfigurationPerspective("other",0,0)));
    assertTrue(ConfigurationCatalogSnapshot.capture(cohort,meaning,scope).configurations().isEmpty());
    var fresh=new ConfigurationCatalogSnapshotTest();
    fresh.configuration.getMetadata().put(ConfigurationPerspective.METADATA,Utils.Json.asString(new ConfigurationPerspective("context",0,21)));
    assertTrue(ConfigurationCatalogSnapshot.capture(fresh.cohort,fresh.meaning,fresh.scope).configurations().isEmpty());
  }
}
