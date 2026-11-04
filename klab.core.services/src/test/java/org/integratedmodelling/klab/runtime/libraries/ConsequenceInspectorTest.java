package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.*;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class ConsequenceInspectorTest {
  static class Fixture {
    final ContextScope scope=mock(ContextScope.class);final KnowledgeGraph graph=mock(KnowledgeGraph.class);
    final Reasoner reasoner=mock(Reasoner.class);final Observation source=mock(Observation.class),recipient=mock(Observation.class);
    final Concept role=mock(Concept.class);final Observable observable=mock(Observable.class);
    final ActivityImpl activity=(ActivityImpl)Activity.of(Activity.Type.CLASSIFICATION);
    final LinkImpl link=new LinkImpl(activity,recipient,GraphModel.Relationship.CLASSIFIED);
    final Map<String,Object> row=new HashMap<>();
    Fixture() {
      var twin=mock(DigitalTwin.class);when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(source.getId()).thenReturn(10L);when(recipient.getId()).thenReturn(20L);
      when(recipient.getObservable()).thenReturn(observable);var semantics=mock(Concept.class);when(observable.getSemantics()).thenReturn(semantics);
      when(semantics.getType()).thenReturn(EnumSet.of(SemanticType.SUBJECT));when(role.getUrn()).thenReturn("test:Role");when(role.is(SemanticType.ROLE)).thenReturn(true);
      when(reasoner.resolveConcept("test:Role")).thenReturn(role);when(reasoner.hasRole(observable,role)).thenReturn(true);
      when(graph.getAsset(10L,scope,Observation.class)).thenReturn(source);when(graph.getAsset(20L,scope,Observation.class)).thenReturn(recipient);
      activity.setId(30);activity.setOutcome(Activity.Outcome.SUCCESS);
      row.putAll(Map.of("sourceId",10L,"recipientId",20L,"role","test:Role","ruleId","authored-rule","before","test:Before","after","test:After","authority","persisted-native-proof"));
      link.properties().putAll(row);activity.getMetadata().put("klab.consequence.roleAttributions",List.of(row));
      when(graph.getLinks(recipient,GraphModel.Relationship.Direction.INCOMING,scope,GraphModel.Relationship.CLASSIFIED)).thenReturn(List.of(link));
    }
  }
  @Test void requiresActualRolePlusMatchingDurableSourceEdgeAndActivityAudit() {
    var f=new Fixture();assertTrue(ConsequenceInspector.role(f.scope,f.source,f.recipient,"test:Role"));
    when(f.reasoner.hasRole(f.observable,f.role)).thenReturn(false);assertFalse(ConsequenceInspector.role(f.scope,f.source,f.recipient,"test:Role"));
    verify(f.scope,never()).observation(any());
  }
  @Test void missingMismatchedOrUnchangedAuditCannotPass() {
    var f=new Fixture();f.row.put("sourceId",99L);assertFalse(ConsequenceInspector.role(f.scope,f.source,f.recipient,"test:Role"));
    f.row.put("sourceId",10L);f.row.put("after","test:Before");assertFalse(ConsequenceInspector.role(f.scope,f.source,f.recipient,"test:Role"));
    f.activity.getMetadata().clear();assertFalse(ConsequenceInspector.role(f.scope,f.source,f.recipient,"test:Role"));
    verify(f.reasoner,never()).hasRole(any(),any());
  }
  @Test void boundedWaitHonorsCancellationAndInvalidBoundsWithoutResolving() {
    var f=new Fixture();assertThrows(IllegalArgumentException.class,()->ConsequenceInspector.awaitRole(f.scope,f.source,f.recipient,"test:Role",60001));
    when(f.scope.isInterrupted()).thenReturn(true);assertThrows(InterruptedException.class,()->ConsequenceInspector.awaitRole(f.scope,f.source,f.recipient,"test:Role",1));
    verify(f.scope,never()).observation(any());
  }
  @Test void impliedTargetAssertionReadsOnlyActualRecipientChildrenAndNeverSubmits() {
    var f=new Fixture();var expected=mock(Observable.class);when(f.reasoner.resolveObservable("test:Quality")).thenReturn(expected);
    var query=mock(KnowledgeGraph.Query.class);when(f.graph.query(Observation.class,f.scope)).thenReturn(query);
    when(query.source(f.recipient)).thenReturn(query);when(query.along(GraphModel.Relationship.HAS_CHILD)).thenReturn(query);
    when(query.run(f.scope)).thenReturn(List.of(f.recipient));when(f.recipient.getResolvedCoverage()).thenReturn(1.0);
    when(f.reasoner.is(f.observable,expected)).thenReturn(true);assertTrue(ConsequenceInspector.target(f.scope,f.recipient,"test:Quality"));
    when(query.run(f.scope)).thenReturn(List.of());assertFalse(ConsequenceInspector.target(f.scope,f.recipient,"test:Quality"));
    verify(f.scope,never()).observation(any());
  }
}
