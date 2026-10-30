package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class ObserverSubmissionSelectionTest {
  private ObservationImpl agent(long id,String urn,SemanticType type) {
    var concept=new ConceptImpl();concept.setUrn("test:Agent");concept.setType(EnumSet.of(type));
    var observable=new ObservableImpl();observable.setSemantics(concept);observable.setUrn("test:Agent");
    var result=new ObservationImpl();result.setId(id);result.setUrn(urn);result.setObservable(observable);return result;
  }
  private ServiceContextScope scope(KnowledgeGraph graph) {
    when(graph.query(eq(Observation.class),any())).thenAnswer(call ->
        new org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery<Observation>(
            org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.AssetType.OBSERVATION));
    when(graph.query(any(org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.class),
        eq(Observation.class),any())).thenAnswer(call -> {
      var query = call.getArgument(0,org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery.class);
      var value = ((KnowledgeGraph.Query.Criterion)query.getCriteria().getFirst()).argument();
      Observation result = value instanceof Number number
          ? graph.getAsset(number.longValue(),call.getArgument(2),Observation.class)
          : graph.getAsset(value.toString(),call.getArgument(2),Observation.class);
      return result == null ? List.of() : List.of(result);
    });
    var user=mock(UserIdentity.class);when(user.getId()).thenReturn("alice");
    var session=new ServiceSessionScope(new ServiceUserScope(user,mock(RuntimeService.class)));
    var result=new ServiceContextScope(session,DigitalTwin.Configuration.builder().id("session.twin").name("Twin").build(),user);
    var twin=mock(DigitalTwin.class);when(twin.getKnowledgeGraph()).thenReturn(graph);result.setDigitalTwin(twin);return result;
  }
  @Test void urnOverrideWinsIndependentOfDefaultConstraintOrderAndDoesNotMutateSessionOrSyntax() {
    var graph=mock(KnowledgeGraph.class);var first=agent(11,"urn:observer:first",SemanticType.AGENT);var second=agent(12,"urn:observer:second",SemanticType.AGENT);
    when(graph.getAsset(eq(11L),any(),eq(Observation.class))).thenReturn(first);
    when(graph.getAsset(eq(12L),any(),eq(Observation.class))).thenReturn(second);
    when(graph.getAsset(eq(second.getUrn()),any(),eq(Observation.class))).thenReturn(second);
    var root=scope(graph).withObserver(first);
    var named=ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,second.getUrn());
    var inherited=ResolutionConstraint.of(ResolutionConstraint.Type.Observer,11L);
    for(var order:List.of(new ResolutionConstraint[]{named,inherited},new ResolutionConstraint[]{inherited,named})) {
      var selected=root.withResolutionConstraints(order);
      assertEquals(12L,selected.getObserver().getId());assertEquals(11L,root.getObserver().getId());
      assertEquals(12L,selected.getConstraint(ResolutionConstraint.Type.Observer,Long.class));
      assertNull(selected.getConstraint(ResolutionConstraint.Type.ObserverUrn,String.class));
      assertNull(selected.getObserver().getObservable().getObserverSemantics());
      assertNull(root.getConfiguration().getObserver());
    }
  }
  @Test void missingWrongKindBlankAndAmbiguousNamedSelectionFailWithoutChangingDefault() {
    var graph=mock(KnowledgeGraph.class);var root=scope(graph);var wrong=agent(13,"urn:thing",SemanticType.SUBJECT);
    when(graph.getAsset(eq(wrong.getUrn()),any(),eq(Observation.class))).thenReturn(wrong);
    when(graph.getAsset(eq("urn:pending"),any(),eq(Observation.class))).thenReturn(agent(0,"urn:pending",SemanticType.AGENT));
    assertThrows(IllegalArgumentException.class,()->root.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,"urn:missing")));
    assertThrows(IllegalArgumentException.class,()->root.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,"urn:thing")));
    assertThrows(IllegalArgumentException.class,()->root.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,"urn:pending")));
    assertThrows(IllegalArgumentException.class,()->root.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn," ")));
    assertThrows(IllegalArgumentException.class,()->root.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,"urn:first","urn:second")));
    assertNull(root.getObserver());assertTrue(root.getResolutionConstraints().isEmpty());
  }
  @Test void publicNamedConstraintSurvivesProductionInterfaceTransport()throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var source=ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,"urn:observer:chosen");
    var copy=mapper.readValue(mapper.writeValueAsBytes(source),ResolutionConstraint.class);
    assertEquals(ResolutionConstraint.Type.ObserverUrn,copy.getType());assertEquals(List.of("urn:observer:chosen"),copy.payload(String.class));
  }
  @Test void clientNamedSelectionUpdatesOnlyDerivedScopeAndNormalizesTransportIdentity()throws Exception {
    var graph=mock(org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph.class);var first=agent(21,"urn:observer:first",SemanticType.AGENT);var second=agent(22,"urn:observer:second",SemanticType.AGENT);
    when(graph.getAsset(eq(second.getUrn()),any(),eq(Observation.class))).thenReturn(second);
    var user=mock(UserIdentity.class);when(user.getId()).thenReturn("alice");when(user.getUsername()).thenReturn("alice");
    when(user.getData()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
    var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");when(runtime.getUrl()).thenReturn(java.net.URI.create("http://localhost:8183").toURL());
    var session=new org.integratedmodelling.common.services.client.scope.ClientSessionScope(new org.integratedmodelling.common.services.client.scope.ClientUserScope(user,null),"session",runtime).withId("session");
    var config=DigitalTwin.Configuration.builder().id("session.twin").name("Twin").build();
    var original=new org.integratedmodelling.common.services.client.scope.ClientContextScope(session,runtime,config);original.setId("session.twin");
    var twin=mock(org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin.class);when(twin.getKnowledgeGraph()).thenReturn(graph);
    var history=new org.integratedmodelling.common.services.client.digitaltwin.TransitionHistory("session.twin");
    when(twin.getTransitionHistory()).thenReturn(history);
    var field=original.getClass().getDeclaredField("digitalTwin");field.setAccessible(true);field.set(original,twin);
    var root=original.withObserver(first);
    var selected=root.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,second.getUrn()),ResolutionConstraint.of(ResolutionConstraint.Type.Observer,21L));
    assertEquals(22L,selected.getObserver().getId());assertEquals(21L,root.getObserver().getId());
    assertEquals(22L,selected.getConstraint(ResolutionConstraint.Type.Observer,Long.class));
    assertTrue(org.integratedmodelling.klab.api.scope.ContextScope.getScopeId(selected).endsWith("#22"));
    assertNull(selected.getObserver().getObservable().getObserverSemantics());
    var commit=new org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl();commit.setId(100);commit.setModifiedAssets(Set.of(22L));
    var journal=new org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal(1,"change-observer",null,org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.Type.INITIALIZATION,0,0,"selection","test","T0(1)",100,List.of(22L),true);
    history.accept(new org.integratedmodelling.klab.api.digitaltwin.TransitionCommit(1,"session.twin",commit,List.of(journal),List.of()));
    assertThrows(IllegalStateException.class,selected::getObserver);
    when(graph.getAsset(eq(22L),any(),eq(Observation.class))).thenReturn(agent(22,"urn:observer:second",SemanticType.SUBJECT));
    assertThrows(IllegalStateException.class,selected::getObserver);
    assertEquals(21L,root.getObserver().getId());
  }
  @Test void serverRevisionRefreshCannotRetainRemovedOrWrongKindObserver() {
    var graph=mock(KnowledgeGraph.class);var selected=agent(12,"urn:observer:chosen",SemanticType.AGENT);
    when(graph.getSemanticRevision()).thenReturn(1L);when(graph.getAsset(eq(12L),any(),eq(Observation.class))).thenReturn(selected);
    var scoped=scope(graph).withObserver(selected);assertSame(selected,scoped.getObserver());
    when(graph.getSemanticRevision()).thenReturn(2L);when(graph.getAsset(eq(12L),any(),eq(Observation.class))).thenReturn(null);
    assertThrows(IllegalStateException.class,scoped::getObserver);
    when(graph.getAsset(eq(12L),any(),eq(Observation.class))).thenReturn(agent(12,"urn:observer:chosen",SemanticType.SUBJECT));
    assertThrows(IllegalStateException.class,scoped::getObserver);
  }
}
