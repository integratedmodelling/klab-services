package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery;
import org.junit.jupiter.api.*;

class ChildContextualizationPersistenceTest {
  private void field(DigitalTwinImpl twin,String name,Object value)throws Exception {
    var field=DigitalTwinImpl.class.getDeclaredField(name);field.setAccessible(true);field.set(twin,value);
  }
  @Test @Timeout(90) void actualRootCommitColdReloadsTypedEffectWithoutRecreatingPersistedObservation()throws Exception {
    var helpers=new ObserverDurableSelectionTest();
    try(var vocabulary=new ObserverDurableSelectionTest.Vocabulary();var database=new NativeFamilyRecovery()) {
      var scope=helpers.scope(vocabulary);var graph=database.initializeRoot(scope);
      helpers.attach(scope,graph);var original=helpers.observation(vocabulary.meaning("Input",false));
      try(var tx=graph.createTransaction(scope)) {tx.store(original);tx.link(RuntimeAsset.CONTEXT_ASSET,original,GraphModel.Relationship.HAS_CHILD);}
      long originalId=original.getId();String originalUrn=original.getUrn();long count=database.observations();
      var cold=database.coldGraph();helpers.attach(scope,cold);
      var persisted=cold.getAsset(originalId,scope,Observation.class);assertNotSame(original,persisted);
      var twin=mock(DigitalTwinImpl.class);field(twin,"knowledgeGraph",cold);
      field(twin,"commitCache",com.google.common.cache.CacheBuilder.newBuilder().build());
      var root=twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
      var measured=Activity.of(Activity.Type.forContextualization(Contextualization.MEASURE));
      var child=root.getChild(measured,scope,persisted);
      assertTrue(child.outgoing(measured).stream().anyMatch(link->link.type()==GraphModel.Relationship.MEASURED&&link.target()==persisted));
      assertTrue(root.commit()>0);assertEquals(originalId,persisted.getId());assertEquals(count,database.observations());
      var reopened=database.coldGraph();helpers.attach(scope,reopened);
      var targets=reopened.query(Observation.class,scope).source(measured).along(GraphModel.Relationship.MEASURED).run(scope);
      assertEquals(1,targets.size());assertEquals(originalId,targets.getFirst().getId());assertEquals(originalUrn,targets.getFirst().getUrn());
      assertTrue(reopened.query(Activity.class,scope).target(targets.getFirst()).along(GraphModel.Relationship.CREATED).run(scope).isEmpty());
      assertEquals(1,reopened.query(Activity.class,scope).target(measured).along(GraphModel.Relationship.TRIGGERED).run(scope).size());
    }
  }
}
