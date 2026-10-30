package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;
import org.integratedmodelling.klab.runtime.libraries.ObservationInspector;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The permit-gated inspector calls the existing native catalog routine against real Cypher. */
class NativeCatalogPreparationIntegrationTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  private static ConceptImpl nativeType(String name) {
    var concept=new ConceptImpl();concept.setUrn("test:"+name);concept.setName(name);
    concept.setReferenceName(name.toLowerCase(Locale.ROOT));
    concept.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));
    concept.setDescriptionType(Contextualization.ACKNOWLEDGEMENT);
    return concept;
  }

  private static class Fixture implements AutoCloseable {
    final NativeFamilyRecovery backend=new NativeFamilyRecovery();
    final RuntimeService runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);
    final DigitalTwinImpl twin=mock(DigitalTwinImpl.class);
    final ServiceContextScope scope=mock(ServiceContextScope.class);
    final Reasoner reasoner=mock(Reasoner.class);
    final ConceptImpl region=nativeType("Region"),other=nativeType("Other");
    final AtomicReference<DigitalTwin.Transaction> current=new AtomicReference<>();
    final TestCaseBase agent=mock(TestCaseBase.class);
    final TestCaseBase.TestCaseScope owner=new TestCaseBase.TestCaseScope(agent,null,null);
    final org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j graph;
    final Geometry support=Geometry.create("T0(1){ttype=PHYSICAL,tstart=0,tend=100}");
    Fixture() throws Exception {
      when(scope.getId()).thenReturn("native-catalog-inspector");
      when(scope.getDigitalTwin()).thenReturn(twin);when(scope.getData()).thenReturn(Parameters.create());
      when(scope.getCurrentTransaction()).thenAnswer(call->current.get());
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);
      when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
      when(scope.getService(RuntimeService.class)).thenReturn(runtime);
      when(reasoner.baseSubstantialType(any(Semantics.class),eq(scope)))
          .thenAnswer(call->call.getArgument(0,Semantics.class).asConcept().singular());
      when(reasoner.satisfiable(any())).thenReturn(true);
      var concepts=Map.of(region.getUrn(),region,other.getUrn(),other);
      when(reasoner.resolveConcept(anyString())).thenAnswer(call->concepts.get(call.getArgument(0)));
      when(reasoner.resolveObservable(anyString())).thenAnswer(call->{
        var concept=concepts.get(call.getArgument(0));return concept==null?null:Observable.promote(concept);
      });
      graph=backend.initializeRoot(scope);when(twin.getKnowledgeGraph()).thenReturn(graph);
      var field=DigitalTwinImpl.class.getDeclaredField("knowledgeGraph");field.setAccessible(true);field.set(twin,graph);
      when(agent.status()).thenReturn("running");owner.registerContext(scope);
      assertTrue(CoreActorLibrary.Inspector.fixtures(owner,scope),"Grant the actual private permit through its public inspector verb");
    }
    ObservationInspector.NativeSnapshot snapshot() {return ObservationInspector.nativeSnapshot(scope);}
    Set<Long> activities() {return graph.query(Activity.class,scope).run(scope).stream()
        .map(Activity::getId).collect(java.util.stream.Collectors.toSet());}
    Observation query(ConceptImpl nativeType) {
      var observable=Observable.promote(nativeType.collective());
      return runtime.queryCollective(new Observation.NaiveBuilder(observable,scope).geometry(support).query().build(),scope);
    }
    Cohort catalog(ConceptImpl nativeType) {return runtime.getCohortFor(nativeType,scope,false);}
    void prepare(ConceptImpl nativeType) {assertTrue(CoreActorLibrary.Inspector.preparecatalog(owner,scope,nativeType));}
    @Override public void close() {current.set(null);CoreActorLibrary.Inspector.clearfixtures(owner,scope);backend.close();}
  }

  @Test void realCatalogPreparationAddsOnlyEmptyCatalogsAndNeverCertifiesDiscovery() throws Exception {
    try(var f=new Fixture()) {
      var initial=f.snapshot();var provenance=f.activities();
      assertTrue(initial.observations().isEmpty());assertTrue(initial.cohorts().isEmpty());assertTrue(initial.contextualizations().isEmpty());
      f.prepare(f.region);var region=f.catalog(f.region);assertNotNull(region);assertTrue(region.getId()>0);
      var first=f.snapshot();assertEquals(1,first.cohorts().size());
      assertEquals("test:Region",first.cohorts().get(region.getId()).semantics());
      assertTrue(first.cohorts().get(region.getId()).members().isEmpty());
      assertTrue(first.cohorts().get(region.getId()).contributors().isEmpty());
      f.prepare(f.other);var other=f.catalog(f.other);assertNotNull(other);assertTrue(other.getId()>0);assertNotEquals(region.getId(),other.getId());
      var prepared=f.snapshot();assertEquals(2,prepared.cohorts().size());
      assertEquals(first.cohorts().get(region.getId()),prepared.cohorts().get(region.getId()),"Preparing another catalog must preserve the existing catalog exactly");
      assertTrue(prepared.observations().isEmpty());assertTrue(prepared.contextualizations().isEmpty());assertEquals(provenance,f.activities());
      f.prepare(f.region);f.prepare(f.other);assertEquals(prepared,f.snapshot(),"Repeated setup must not duplicate or mutate catalogs");
      assertEquals(region.getId(),f.catalog(f.region).getId());assertEquals(other.getId(),f.catalog(f.other).getId());
      for(var type:List.of(f.region,f.other)) {
        var unknown=f.query(type);assertTrue(unknown.isEmpty(),"Catalog registration is not affirmative empty discovery");
        assertTrue(unknown.getResolvedCoverage()<1);assertEquals(0,f.backend.observations());
      }
      assertEquals(prepared,f.snapshot());assertEquals(provenance,f.activities());
      verify(f.runtime,never()).submit(any(Observation.class),any());
    }
  }

  @Test void independentlyPreparedCatalogsSurviveLaterRootRollbackWithoutNewNativeState() throws Exception {
    try(var f=new Fixture()) {
      f.prepare(f.region);f.prepare(f.other);var prepared=f.snapshot();var provenance=f.activities();
      var transaction=f.twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),f.scope,RuntimeAsset.PROVENANCE_ASSET);
      f.current.set(transaction);
      var tentative=new ObservationImpl();tentative.setObservable(Observable.promote(f.region.collective()));
      tentative.setGeometry(f.support);tentative.setName("Uncommitted native producer");
      transaction.add(tentative);transaction.link(RuntimeAsset.CONTEXT_ASSET,tentative,GraphModel.Relationship.HAS_CHILD);
      transaction.link(tentative,f.catalog(f.region),GraphModel.Relationship.CONTRIBUTED_TO);
      assertTrue(transaction.assets().stream().anyMatch(asset->asset==tentative),"The failed attempt must actually contain its tentative contribution");
      transaction.fail(new CancellationException("Deliberate root rollback after catalog preparation"));
      assertTrue(transaction.isFailed());f.current.set(null);
      assertEquals(prepared,f.snapshot(),"Independent catalog transactions survive root execution rollback");
      assertEquals(provenance,f.activities());assertEquals(0,f.backend.observations());
      assertTrue(f.query(f.region).isEmpty());assertTrue(f.query(f.other).isEmpty());
      assertTrue(f.catalog(f.region).getId()>0);assertTrue(f.catalog(f.other).getId()>0);
    }
  }
}
