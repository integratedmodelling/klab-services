package org.integratedmodelling.klab.services.scopes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.junit.jupiter.api.Test;
class RemoteContextCacheGenerationTest {
 static ObservationImpl observation(long id,String meaning) {
  var c=new ConceptImpl();c.setUrn(meaning);c.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));
  var o=new ObservableImpl();o.setSemantics(c);o.setUrn(meaning);
  var v=new ObservationImpl();v.setId(id);v.setUrn("context:"+id);v.setObservable(o);v.setGeometry(Geometry.create("S2(1,1)"));return v;
 }
 @Test void scopedTransportAdmissionFetchesOnlyNamedFocusWhenVirtualCompletionCarriesNoInvalidation() throws Exception {
  var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("user");var owner=mock(KlabService.class);
  var session=new ServiceSessionScope(new ServiceUserScope(user,owner));
  var root=new ServiceContextScope(session,DigitalTwin.Configuration.builder().id("context").name("test").build(),user);root.setId("context");
  var remote=mock(RuntimeClient.class);var graph=new ClientKnowledgeGraph(root,remote);var twin=mock(DigitalTwin.class);
  when(twin.getKnowledgeGraph()).thenReturn(graph);var field=ServiceContextScope.class.getDeclaredField("digitalTwin");field.setAccessible(true);field.set(root,twin);
  var before=observation(42,"test:Region");var after=observation(42,"test:Reference test:Region");
  var current=new java.util.concurrent.atomic.AtomicReference<Observation>(before);
  when(remote.getAsset(eq(42L),eq(RuntimeAsset.class),any(Scope.class))).thenAnswer(call->current.get());
  graph.ingest(before);assertSame(before,root.getObservation(42));
  var manager=new ScopeManager(owner);var path=new ContextScope.ScopeData(Scope.Type.CONTEXT,"context",new long[]{42},Observation.UNASSIGNED_ID);
  var first=manager.contextualizeScope(root,path,Map.of());var captured=CollectiveSelectionBinding.context(first);
  current.set(after); // Committed by a virtual selection: no ordinary observation/commit event arrives.
  assertSame(before,root.getObservation(42),"Reproduce both existing peer caches without manufacturing an event");
  var second=manager.contextualizeScope(root,path,Map.of());var fresh=CollectiveSelectionBinding.context(second);
  assertEquals("test:Reference test:Region",fresh.semantics());
  assertEquals(captured.id(),fresh.id());assertEquals(captured.urn(),fresh.urn());assertEquals(captured.support(),fresh.support());
  assertEquals("test:Region",CollectiveSelectionBinding.context(first).semantics(),"Previously admitted planning input is immutable");
  var requested=new ObservableImpl();requested.setUrn("each test:A test:Region");
  var binding=new CollectiveSelectionBinding(requested.getUrn(),"each test:Region",List.of("test:A"),List.of("test:Family"),List.of("test:Family of each test:Region"),"reasoner",1,Geometry.forTransport(after.getGeometry()).encode(),null,null,"context",captured,null);
  assertThrows(IllegalStateException.class,()->binding.require(second,requested,after.getGeometry()));
  current.set(observation(99,"test:Region"));assertThrows(IllegalStateException.class,()->manager.contextualizeScope(root,path,Map.of()),"Wrong authoritative ID cannot be admitted");
  current.set(null);assertThrows(RuntimeException.class,()->manager.contextualizeScope(root,path,Map.of()),"Unavailable authoritative focus cannot fall back to cached meaning");
  verify(remote,times(4)).getAsset(eq(42L),eq(RuntimeAsset.class),any(Scope.class));
  verifyNoMoreInteractions(remote);
 } @Test void nextScopedRequestSeesClassifiedMeaningButCapturedOldBindingStillRejectsIt() throws Exception {
  var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("user");var owner=mock(KlabService.class);
  var session=new ServiceSessionScope(new ServiceUserScope(user,owner));
  var root=new ServiceContextScope(session,DigitalTwin.Configuration.builder().id("context").name("test").build(),user);
  root.setId("context");
  var remote=mock(RuntimeClient.class);var graph=new ClientKnowledgeGraph(root,remote);var twin=mock(DigitalTwin.class);
  when(twin.getKnowledgeGraph()).thenReturn(graph);
  var field=ServiceContextScope.class.getDeclaredField("digitalTwin");field.setAccessible(true);field.set(root,twin);
  var before=observation(42,"test:Region");var after=observation(42,"test:Reference test:Region");graph.ingest(before);
  when(remote.getAsset(eq(42L),eq(RuntimeAsset.class),any(Scope.class))).thenReturn(before);
  var manager=new ScopeManager(owner);var path=new ContextScope.ScopeData(Scope.Type.CONTEXT,"context",new long[]{42},Observation.UNASSIGNED_ID);
  var first=manager.contextualizeScope(root,path,Map.of());var old=CollectiveSelectionBinding.context(first);
  assertEquals("test:Region",old.semantics());
  var commit=new CommitImpl();commit.setId(77);commit.getModifiedAssets().add(42L);
  when(remote.getCommit(77,root)).thenReturn(commit);when(remote.getAsset(eq(42L),eq(RuntimeAsset.class),any(Scope.class))).thenReturn(after);
  var submitted=observation(43,"test:Other");submitted.getMetadata().put(Metadata.IM_COMMIT_ID,77L);graph.ingest(submitted);
  assertSame(after,graph.getAsset(42,root,RuntimeAsset.class),"The remote graph already invalidates its own asset cache");
  var second=manager.contextualizeScope(root,path,Map.of());var fresh=CollectiveSelectionBinding.context(second);
  assertEquals("test:Reference test:Region",fresh.semantics(),"The enclosing service-scope cache must observe that committed update");
  assertEquals(old.id(),fresh.id());assertEquals(old.urn(),fresh.urn());assertEquals(old.support(),fresh.support());
  assertEquals("test:Region",CollectiveSelectionBinding.context(first).semantics(),"Do not silently rewrite an already captured planning input");
  var requested=new ObservableImpl();requested.setUrn("each test:A test:Region");
  var binding=new CollectiveSelectionBinding(requested.getUrn(),"each test:Region",List.of("test:A"),List.of("test:Family"),List.of("test:Family of each test:Region"),"reasoner",1,Geometry.forTransport(after.getGeometry()).encode(),null,null,"context",old,null);
  var failure=assertThrows(IllegalStateException.class,()->binding.require(second,requested,after.getGeometry()));
  assertEquals("Changed collective selection native context",failure.getMessage());
 }
}
