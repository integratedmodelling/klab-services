package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

/** Real embedded storage/query/reopen and production Reasoner construction from captured typed syntax.
 * The Resources parsing boundary is fixture syntax, not a live parser/service. */
class ObserverDurableSelectionTest {
  private static void stage(String message) { System.err.println("OBSERVER-DURABLE: "+message); }
  static final class Vocabulary extends SubmissionNativeFixture {
    final Map<String,KimObservable> expressions = new HashMap<>();
    Vocabulary() throws Exception {
      declare("Observer",SemanticType.AGENT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE);
      register(leaf("Observer"));
      when(resources.declareObservable(anyString())).thenAnswer(call -> expressions.get(call.getArgument(0)));
      installMatcher();
    }
    Observable meaning(String head, boolean observer) {
      stage("construct "+head+" observer="+observer);
      var syntax = head.equals("Input") ? request(head,named("Region")) : new KimObservableImpl();
      if (!head.equals("Input")) syntax.setSemantics(named(head));
      register((org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl)syntax.getSemantics());
      if(observer) syntax.setObserverSemantics(register(named("Observer")));
      var result = reasoner.declareObservable(syntax);
      assertNotNull(result); expressions.put(result.getUrn(),syntax);stage("constructed "+result.getUrn());return result;
    }
    void clearObservableCache() throws Exception {
      var field=org.integratedmodelling.klab.services.reasoner.ReasonerService.class.getDeclaredField("observables");
      field.setAccessible(true); ((com.github.benmanes.caffeine.cache.Cache<?,?>)field.get(reasoner)).invalidateAll();
    }
  }
  ObservationImpl observation(Observable meaning) {
    var result=new ObservationImpl();result.setObservable(meaning);result.setGeometry(Geometry.create("1"));return result;
  }
  ServiceContextScope scope(Vocabulary vocabulary) {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var user=mock(UserIdentity.class);when(user.getId()).thenReturn("tester");when(user.getUsername()).thenReturn("tester");
    var runtime=mock(RuntimeService.class);
    var scope=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,runtime)),
        DigitalTwin.Configuration.builder().id("observer-durable").name("Observer storage").build(),user);
    scope.setId("observer-durable");
    var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);
    when(runtime.status()).thenReturn(status);when(runtime.serviceId()).thenReturn("runtime");
    doReturn(status).when(vocabulary.reasoner).status();scope.addService(runtime);scope.addService(vocabulary.reasoner);
    return scope;
  }
  void attach(ServiceContextScope scope,org.integratedmodelling.klab.api.data.KnowledgeGraph graph) {
    var twin=mock(DigitalTwin.class);when(twin.getKnowledgeGraph()).thenReturn(graph);
    var storage=mock(org.integratedmodelling.klab.api.digitaltwin.StorageManager.class);
    when(twin.getStorageManager()).thenReturn(storage);scope.setDigitalTwin(twin);
  }

  @Test @org.junit.jupiter.api.Timeout(90)
  void coldCommittedWrappersAndSubmissionSelectionRetainPerspectiveWithinOwningGraph() throws Exception {
    try(var vocabulary=new Vocabulary();var database=new NativeFamilyRecovery()) {
      stage("initialize graph");
      var scope=scope(vocabulary);var graph=database.initializeRoot(scope);attach(scope,graph);
      var agent=observation(vocabulary.meaning("Observer",false));
      var absolute=observation(vocabulary.meaning("Input",false));
      var specific=observation(vocabulary.meaning("Input",true));
      var wrong=observation(vocabulary.meaning("Region",false));
      var foreign=observation(agent.getObservable());
      var receipt=org.integratedmodelling.klab.api.provenance.Activity.of(
          org.integratedmodelling.klab.api.provenance.Activity.Type.RESOLUTION);
      stage("open transaction");
      try(var tx=graph.createTransaction(scope)) {
        for(var item:List.of(agent,absolute,specific,wrong,foreign)) {stage("store "+item.getObservable().getUrn());tx.store(item);}
        stage("link owned observations");
        for(var item:List.of(agent,absolute,specific,wrong)) tx.link(RuntimeAsset.CONTEXT_ASSET,item,GraphModel.Relationship.HAS_CHILD);
        stage("store receipt");tx.store(receipt);tx.link(RuntimeAsset.PROVENANCE_ASSET,receipt,GraphModel.Relationship.HAS_ACTIVITY);
        tx.link(receipt,agent,GraphModel.Relationship.HAS_OBSERVER);
        stage("close transaction");
      }
      stage("transaction committed");
      database.execute("CREATE (c:Context {id:'foreign'}) WITH c MATCH (n:Observation {id:$id}) CREATE (c)-[:HAS_CHILD]->(n)",Map.of("id",foreign.getId()));
      // A fresh graph has no service-side asset cache; force production observable re-construction too.
      vocabulary.clearObservableCache();var cold=database.coldGraph();attach(scope,cold);
      stage("cold restore specific");
      var restored=cold.getAsset(specific.getId(),scope,Observation.class);
      assertEquals(specific.getObservable().getUrn(),restored.getObservable().getUrn());
      assertEquals("audit:Observer",restored.getObservable().getObserverSemantics().getUrn());
      assertNull(cold.getAsset(absolute.getId(),scope,Observation.class).getObservable().getObserverSemantics());
      var storedAgent=cold.getAsset(agent.getId(),scope,Observation.class);
      stage("query receipt observer");
      // Same graph provenance primitive used by Runtime.submit; this is not a full submit commit.
      var recorded=cold.query(Observation.class,scope).source(receipt)
          .along(GraphModel.Relationship.HAS_OBSERVER).run(scope);
      assertEquals(1,recorded.size());assertEquals(storedAgent.getId(),recorded.getFirst().getId());
      assertEquals(storedAgent.getUrn(),recorded.getFirst().getUrn());
      var selected=scope.withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,storedAgent.getUrn()));
      stage("selected observer");
      assertEquals(agent.getId(),selected.getObserver().getId());assertNull(scope.getObserver());
      assertNull(selected.getObserver().getObservable().getObserverSemantics());
      assertThrows(IllegalArgumentException.class,()->scope.withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,cold.getAsset(foreign.getId(),scope,Observation.class).getUrn())));
      assertThrows(IllegalArgumentException.class,()->scope.withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.Observer,foreign.getId())));
      assertThrows(IllegalArgumentException.class,()->scope.withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,cold.getAsset(wrong.getId(),scope,Observation.class).getUrn())));
      var transaction=mock(DigitalTwin.Transaction.class);when(transaction.getId()).thenReturn("active-submission");
      stage("header selection");
      scope.registerTransaction(transaction);
      var headers=Map.of(org.integratedmodelling.klab.api.ServicesAPI.TRANSACTION_ID_HEADER,"active-submission");
      var manager=new ScopeManager(scope.getService(RuntimeService.class));
      var same=new org.integratedmodelling.klab.api.scope.ContextScope.ScopeData(
          org.integratedmodelling.klab.api.scope.Scope.Type.CONTEXT,scope.getId(),null,agent.getId());
      var headerScope=manager.contextualizeScope(scope,same,headers);
      assertSame(transaction,((ServiceContextScope)headerScope).getCurrentTransaction());
      assertEquals(agent.getId(),headerScope.getObserver().getId());assertNull(scope.getObserver());
      var inaccessible=new org.integratedmodelling.klab.api.scope.ContextScope.ScopeData(
          org.integratedmodelling.klab.api.scope.Scope.Type.CONTEXT,scope.getId(),null,foreign.getId());
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,
          ()->manager.contextualizeScope(scope,inaccessible,headers));
      assertNull(scope.getObserver());
      verify(vocabulary.resources).declareObservable(specific.getObservable().getUrn());
      stage("selection complete");
    }
  }

  @Test @org.junit.jupiter.api.Timeout(90)
  void coldOrdinaryActuatorRetainsOriginalImplicitOperationApartFromActualOutput() throws Exception {
    try(var vocabulary=new Vocabulary();var database=new NativeFamilyRecovery()) {
      stage("initialize actuator graph");
      var scope=scope(vocabulary);var graph=database.initializeRoot(scope);attach(scope,graph);
      var absolute=vocabulary.meaning("Input",false);var actual=observation(vocabulary.meaning("Input",true));
      var plan=new ActuatorImpl();plan.setName("perspective");plan.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.OBSERVE);
      plan.setObservation(actual);plan.setOperationObservable(absolute);plan.setCoverage(Geometry.create("1"));
      stage("store actuator");try(var tx=graph.createTransaction(scope)) {tx.store(plan);}stage("actuator committed");
      vocabulary.clearObservableCache();var cold=database.coldGraph();attach(scope,cold);
      var restored=cold.getAsset(plan.getId(),scope,org.integratedmodelling.klab.api.services.runtime.Actuator.class);
      stage("actuator restored");
      assertNotNull(restored.getOperationObservable());assertEquals(absolute.getUrn(),restored.getOperationObservable().getUrn());
      assertNull(restored.getOperationObservable().getObserverSemantics());
    }
  }
}
