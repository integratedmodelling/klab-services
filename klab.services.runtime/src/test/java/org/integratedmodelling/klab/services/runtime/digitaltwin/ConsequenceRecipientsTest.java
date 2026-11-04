package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ConsequenceRecipientsTest {
  static Observation observation(long id,SemanticType type) {
    var semantics=mock(Concept.class);var observable=mock(Observable.class);
    when(observable.getSemantics()).thenReturn(semantics);when(observable.is(type)).thenReturn(true);
    when(observable.getUrn()).thenReturn("test:observation"+id);
    when(observable.getContextualization()).thenReturn(type==SemanticType.PROCESS?org.integratedmodelling.klab.api.knowledge.Contextualization.SIMULATION:type==SemanticType.RELATIONSHIP?org.integratedmodelling.klab.api.knowledge.Contextualization.CONNECTION:org.integratedmodelling.klab.api.knowledge.Contextualization.ACKNOWLEDGEMENT);
    var result=new ObservationImpl();result.setId(id);result.setObservable(observable);return result;
  }
  static class Fixture {
    final ServiceContextScope scope=mock(ServiceContextScope.class);
    final KnowledgeGraph graph=mock(KnowledgeGraph.class);
    final Observation process=observation(10,SemanticType.PROCESS);
    final Observation relationship=observation(11,SemanticType.RELATIONSHIP);
    final Observation source=observation(20,SemanticType.SUBJECT);
    final Observation target=observation(21,SemanticType.SUBJECT);
    final ConsequenceRecipients.Resolver resolver=mock(ConsequenceRecipients.Resolver.class);
    Fixture() {
      var twin=mock(DigitalTwin.class);when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
      for(var o:List.of(process,relationship,source,target))when(scope.getObservation(o.getId())).thenReturn(o);
      when(scope.getParentOf(process)).thenReturn(source);
      when(scope.getRelationshipParticipants(relationship)).thenReturn(List.of(source,target));
      when(graph.getLinks(relationship,GraphModel.Relationship.Direction.OUTGOING,scope,
          GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE)).thenReturn(List.of(new LinkImpl(relationship,source,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE)));
      when(graph.getLinks(relationship,GraphModel.Relationship.Direction.OUTGOING,scope,
          GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)).thenReturn(List.of(new LinkImpl(relationship,target,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)));
    }
    List<Long> ids(ConsequenceRecipients.Selection selection) {
      assertEquals(ConsequenceRecipients.Status.COMPLETE,selection.status(),selection.reason());
      return selection.recipients().stream().map(Observation::getId).toList();
    }
    ConsequenceRecipients.Selection select(Observation trigger,ConsequenceRecipients.Selector selector) throws Exception {
      return ConsequenceRecipients.select(trigger,selector,null,scope,resolver);
    }
  }
  @Test void processDefaultsToItsDurableBearerWithoutCharacterization() throws Exception {
    var f=new Fixture();assertEquals(List.of(20L),f.ids(f.select(f.process,ConsequenceRecipients.Selector.DEFAULT)));
    verifyNoInteractions(f.resolver);
    verify(f.scope,never()).observation(any());
  }
  @Test void relationshipDefaultsToBothAndExplicitEndpointsRemainDistinct() throws Exception {
    var f=new Fixture();
    assertEquals(List.of(20L,21L),f.ids(f.select(f.relationship,ConsequenceRecipients.Selector.DEFAULT)));
    assertEquals(List.of(20L),f.ids(f.select(f.relationship,ConsequenceRecipients.Selector.SOURCE)));
    assertEquals(List.of(21L),f.ids(f.select(f.relationship,ConsequenceRecipients.Selector.TARGET)));
    verifyNoInteractions(f.resolver);
  }
  @Test void unorientedBondDefaultsToBothButCannotInventAnEndpointDirection() throws Exception {
    var f=new Fixture();when(f.relationship.getObservable().is(SemanticType.BIDIRECTIONAL)).thenReturn(true);
    assertEquals(List.of(20L,21L),f.ids(f.select(f.relationship,ConsequenceRecipients.Selector.DEFAULT)));
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(f.relationship,ConsequenceRecipients.Selector.SOURCE).status());
    verifyNoInteractions(f.graph);
  }
  @Test void reflexiveRelationshipConfersOnceOnItsSingleRecipient() throws Exception {
    var f=new Fixture();when(f.scope.getRelationshipParticipants(f.relationship)).thenReturn(List.of(f.source));
    when(f.graph.getLinks(f.relationship,GraphModel.Relationship.Direction.OUTGOING,f.scope,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET))
        .thenReturn(List.of(new LinkImpl(f.relationship,f.source,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)));
    assertEquals(List.of(20L),f.ids(f.select(f.relationship,ConsequenceRecipients.Selector.DEFAULT)));
  }
  @Test void missingOrAmbiguousTypedEndpointDoesNotBecomeAPartialDefaultConferral() throws Exception {
    var f=new Fixture();when(f.graph.getLinks(f.relationship,GraphModel.Relationship.Direction.OUTGOING,f.scope,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)).thenReturn(List.of());
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(f.relationship,ConsequenceRecipients.Selector.DEFAULT).status());
    when(f.graph.getLinks(f.relationship,GraphModel.Relationship.Direction.OUTGOING,f.scope,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET))
        .thenReturn(List.of(new LinkImpl(f.relationship,f.source,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET),new LinkImpl(f.relationship,f.target,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET)));
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(f.relationship,ConsequenceRecipients.Selector.DEFAULT).status());
  }
  @Test void explicitResolutionPreservesSuccessfulEmptyVersusUnavailableAndFailure() throws Exception {
    var f=new Fixture();var expression=mock(KimConcept.class);
    when(f.resolver.resolveEach(expression,f.scope)).thenReturn(ConsequenceRecipients.Selection.complete(List.of()));
    assertTrue(f.ids(ConsequenceRecipients.select(f.process,ConsequenceRecipients.Selector.EXPRESSION,expression,f.scope,f.resolver)).isEmpty());
    when(f.resolver.resolveEach(expression,f.scope)).thenReturn(new ConsequenceRecipients.Selection(ConsequenceRecipients.Status.DEFERRED,List.of(),"discovery incomplete"));
    assertEquals(ConsequenceRecipients.Status.DEFERRED,ConsequenceRecipients.select(f.process,ConsequenceRecipients.Selector.EXPRESSION,expression,f.scope,f.resolver).status());
    when(f.resolver.resolveEach(expression,f.scope)).thenReturn(new ConsequenceRecipients.Selection(ConsequenceRecipients.Status.FAILED,List.of(),"resolution failed"));
    assertEquals(ConsequenceRecipients.Status.FAILED,ConsequenceRecipients.select(f.process,ConsequenceRecipients.Selector.EXPRESSION,expression,f.scope,f.resolver).status());
  }
  @Test void incompleteRecipientSetCannotYieldPartialAttribution() throws Exception {
    var f=new Fixture();var expression=mock(KimConcept.class);
    when(f.scope.getObservation(f.target.getId())).thenReturn(null);
    when(f.resolver.resolveEach(expression,f.scope)).thenReturn(ConsequenceRecipients.Selection.complete(List.of(f.source,f.target)));
    var result=ConsequenceRecipients.select(f.process,ConsequenceRecipients.Selector.EXPRESSION,expression,f.scope,f.resolver);
    assertEquals(ConsequenceRecipients.Status.DEFERRED,result.status());assertTrue(result.recipients().isEmpty());
    assertThrows(IllegalArgumentException.class,()->new ConsequenceRecipients.Selection(ConsequenceRecipients.Status.DEFERRED,List.of(f.source),"partial"));
  }
  @Test void dependentCollectiveMissingAndWrongSourceRecipientsRemainDeferred() throws Exception {
    var f=new Fixture();var quality=observation(30,SemanticType.QUALITY);when(f.scope.getObservation(30)).thenReturn(quality);
    when(f.scope.getParentOf(f.process)).thenReturn(quality);
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(f.process,ConsequenceRecipients.Selector.DEFAULT).status());
    when(f.scope.getParentOf(f.process)).thenReturn(f.source);when(f.source.getObservable().getSemantics().isCollective()).thenReturn(true);
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(f.process,ConsequenceRecipients.Selector.DEFAULT).status());
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(f.process,ConsequenceRecipients.Selector.SOURCE).status());
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(quality,ConsequenceRecipients.Selector.DEFAULT).status());
    when(f.scope.getObservation(f.process.getId())).thenReturn(null);
    assertEquals(ConsequenceRecipients.Status.DEFERRED,f.select(f.process,ConsequenceRecipients.Selector.DEFAULT).status());
  }
  @Test void cancellationPreventsDiscoveryAndResolverWork() {
    var f=new Fixture();when(f.scope.isInterrupted()).thenReturn(true);
    assertThrows(InterruptedException.class,()->f.select(f.process,ConsequenceRecipients.Selector.DEFAULT));
    verify(f.scope,never()).getObservation(anyLong());verifyNoInteractions(f.resolver,f.graph);
  }
}
