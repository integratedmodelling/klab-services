package org.integratedmodelling.klab.services.scopes;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;import java.net.*;import java.util.*;import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.knowledge.*;import org.integratedmodelling.common.services.client.*;import org.integratedmodelling.common.data.jackson.JacksonConfiguration;import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;import org.integratedmodelling.klab.api.collections.Parameters;import org.integratedmodelling.klab.api.digitaltwin.*;import org.integratedmodelling.klab.api.identities.UserIdentity;import org.integratedmodelling.klab.api.knowledge.*;import org.integratedmodelling.klab.api.knowledge.observation.*;import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;import org.integratedmodelling.klab.api.scope.*;import org.integratedmodelling.klab.api.services.*;import org.integratedmodelling.klab.api.services.reasoner.objects.*;import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.junit.jupiter.api.Test;
public class ProspectiveFocusTransportTest {
 static ServiceContextScope root(KlabService owner,UserIdentity user){var session=new ServiceSessionScope(new ServiceUserScope(user,owner));var root=new ServiceContextScope(session,DigitalTwin.Configuration.builder().id("context").name("test").build(),user);root.setId("context");root.setHostServiceId("runtime");return root;}
 static ObservationImpl observation(long id){var concept=new ConceptImpl();concept.setUrn("imod:Bearer");concept.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));var observable=new ObservableImpl();observable.setUrn(concept.getUrn());observable.setSemantics(concept);var result=new ObservationImpl();result.setId(id);result.setObservable(observable);return result;}
 @Test public void ownedRegisteredTransientFocusSurvivesActualPinnedHeaderRestoration()throws Exception {
  org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("alice");when(user.getData()).thenReturn(Parameters.create());
  var runtime=mock(RuntimeService.class);when(runtime.serviceId()).thenReturn("runtime");var runtimeRoot=root(runtime,user);
  var tx=mock(DigitalTwin.Transaction.class,withSettings().extraInterfaces(org.integratedmodelling.klab.runtime.lifecycle.CycleScopedTransaction.class));when(tx.getId()).thenReturn("owned-transaction");when(tx.assets()).thenReturn(List.of());runtimeRoot.registerTransaction(tx);
  long allocated=runtimeRoot.getNextObservationId();assertTrue(allocated<Observation.UNASSIGNED_ID);var prospective=observation(allocated);prospective.setGeometry(new org.integratedmodelling.klab.runtime.scale.ScaleImpl(org.integratedmodelling.klab.api.geometry.Geometry.UNIVERSAL));runtimeRoot.withTransaction(tx).registerProvisionalObservation(prospective);
  assertSame(prospective,runtimeRoot.withTransaction(tx).getObservation(allocated));assertNull(runtimeRoot.getObservation(allocated));
  assertNull(runtimeRoot.withTransaction(tx).getObservation(Observation.UNASSIGNED_ID));assertEquals(0,Observation.QUERY_ID); // QUERY is not an owned transient ID (<-1).
  var cycle=new ResolutionCycleBinding(1,"cycle","context","runtime",true,new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,"fingerprint","lease"));
  var owned=runtimeRoot.withResolutionCycleBinding(cycle);when(((org.integratedmodelling.klab.runtime.lifecycle.CycleScopedTransaction)tx).executionScope()).thenReturn(owned);
  var outbound=owned.withTransaction(tx).within(prospective);
  assertEquals("context",ContextScope.getScopeId(outbound));
  var receivingRoot=root(mock(Reasoner.class),user);var manager=new ScopeManager(mock(Reasoner.class));
  var remote=mock(RuntimeClient.class);var remoteGraph=new org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph(receivingRoot,remote);var remoteTwin=mock(DigitalTwin.class);when(remoteTwin.getKnowledgeGraph()).thenReturn(remoteGraph);
  var twinField=ServiceContextScope.class.getDeclaredField("digitalTwin");twinField.setAccessible(true);twinField.set(receivingRoot,remoteTwin);
  var controller=new org.integratedmodelling.klab.services.runtime.server.controllers.RuntimeServerController();
  when(remote.getAsset(anyLong(),eq(org.integratedmodelling.klab.api.data.RuntimeAsset.class),any(Scope.class))).thenAnswer(call->{
   ContextScope lookup=call.getArgument(2);assertNull(lookup.getContextObservation(),"Lookup must not recursively carry the prospective focus header");
   var local=owned.withResolutionCycleBinding(lookup.getResolutionCycleBinding()).withTransaction(runtimeRoot.getTransaction(lookup.getTransactionId()));
   var authorization=new org.integratedmodelling.klab.services.application.security.EngineAuthorization("hub","alice","credential",Map.of(),List.of(),List.of());authorization.setScope(local);authorization.setAuthenticated(true);
   return controller.retrieveAsset(call.getArgument(0),authorization);
  });
  var failure=new AtomicReference<Throwable>();var restored=new AtomicReference<ContextScope>();var headers=new AtomicReference<Map<String,String>>();var mapper=JacksonConfiguration.newObjectMapper();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE,exchange->{
   try {var h=new HashMap<String,String>();for(var entry:exchange.getRequestHeaders().entrySet())for(var name:List.of(ServicesAPI.SCOPE_HEADER,ServicesAPI.SERVICE_ID_HEADER,ServicesAPI.TRANSACTION_ID_HEADER,ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER,ServicesAPI.RESOLUTION_CYCLE_HEADER))if(entry.getKey().equalsIgnoreCase(name))h.put(name,entry.getValue().getFirst());headers.set(h);
    var invocation=mapper.readValue(exchange.getRequestBody(),SemanticCycleInvocation.class);assertEquals(cycle.semanticBinding(),invocation.cycle().binding());
    var scope=manager.contextualizeScope(receivingRoot,ContextScope.parseScopeId(h.get(ServicesAPI.SCOPE_HEADER)),h);restored.set(scope);
    var caps=new org.integratedmodelling.common.services.ReasonerCapabilitiesImpl();caps.setKnowledgeRevision(1);caps.setServiceId("reasoner");
    var bytes=mapper.writeValueAsBytes(new SemanticCycleResult(SemanticCycleCodec.encode(caps,Reasoner.Capabilities.class),List.of("null")));
    exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}
   }catch(Throwable problem){failure.set(problem);exchange.sendResponseHeaders(500,-1);exchange.close();}
  });server.start();
  try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
   var request=new SemanticCycleRequest("runtime","context","cycle",cycle.semanticBinding());var pinned=SemanticCycleFacade.create(mock(Reasoner.class),request,outbound,(invocation,scope)->http.withScope(scope).postRequired(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE,invocation,SemanticCycleResult.class));
   pinned.capabilities(outbound);assertNull(failure.get(),()->String.valueOf(failure.get()));assertEquals(Long.toString(allocated),headers.get().get(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER));assertEquals(tx.getId(),headers.get().get(ServicesAPI.TRANSACTION_ID_HEADER));
   System.out.println("FOCUS origin="+allocated+" header="+headers.get().get(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER)+" runtimeOwned="+runtimeRoot.withTransaction(tx).getObservation(allocated).getId()+" reasonerRestored="+(restored.get().getContextObservation()==null?"null":restored.get().getContextObservation().getId()));
   assertEquals(allocated,restored.get().getContextObservation()==null?null:restored.get().getContextObservation().getId());
   assertEquals(cycle,restored.get().getResolutionCycleBinding());assertEquals(tx.getId(),restored.get().getTransactionId());
   assertSame(prospective,owned.withTransaction(tx).getObservation(allocated),"Canonical owned planning object remains unchanged");
   assertNotSame(prospective,restored.get().getContextObservation(),"Remote focus is a detached portable observation");
   for(long invalid:List.of(Observation.UNASSIGNED_ID,Observation.QUERY_ID))assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->manager.contextualizeScope(receivingRoot,ContextScope.parseScopeId("context"),Map.of(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER,Long.toString(invalid))));
   assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->manager.contextualizeScope(receivingRoot,ContextScope.parseScopeId("context"),Map.of(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER,Long.toString(allocated))));
   verify(remote,times(1)).getAsset(eq(allocated),eq(org.integratedmodelling.klab.api.data.RuntimeAsset.class),any(Scope.class));
   java.util.function.Function<ServiceContextScope,org.integratedmodelling.klab.services.application.security.EngineAuthorization> authorize=value->{var auth=new org.integratedmodelling.klab.services.application.security.EngineAuthorization("hub","alice","credential",Map.of(),List.of(),List.of());auth.setScope(value);auth.setAuthenticated(true);return auth;};
   var local=owned.withTransaction(tx);
   var unauthenticated=authorize.apply(local);unauthenticated.setAuthenticated(false);assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated,unauthenticated));
   var wrongPrincipal=new org.integratedmodelling.klab.services.application.security.EngineAuthorization("hub","bob","credential",Map.of(),List.of(),List.of());wrongPrincipal.setScope(local);wrongPrincipal.setAuthenticated(true);assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated,wrongPrincipal));
   var portable=(Observation)controller.retrieveAsset(allocated,authorize.apply(local));assertEquals(allocated,portable.getId());assertFalse(portable.getGeometry() instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Scale);
   assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated,authorize.apply(owned)));
   assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated-100,authorize.apply(local)));
   var foreignUser=spy(local);var bob=mock(UserIdentity.class);when(bob.getUsername()).thenReturn("bob");doReturn(bob).when(foreignUser).getUser();
   var foreignContext=spy(local);doReturn("other-context").when(foreignContext).getId();
   var foreignObserver=spy(local);doReturn(11L).when(foreignObserver).getObserverId();
   var foreignCycle=local.withResolutionCycleBinding(new ResolutionCycleBinding(1,"other-cycle","context","runtime",true,cycle.semanticBinding()));
   for(var bad:List.of(foreignUser,foreignContext,foreignObserver,foreignCycle))assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated,authorize.apply(bad)));
   var other=mock(DigitalTwin.Transaction.class,withSettings().extraInterfaces(org.integratedmodelling.klab.runtime.lifecycle.CycleScopedTransaction.class));when(other.getId()).thenReturn("other-transaction");when(other.assets()).thenReturn(List.of());when(((org.integratedmodelling.klab.runtime.lifecycle.CycleScopedTransaction)other).executionScope()).thenReturn(owned);runtimeRoot.registerTransaction(other);
   assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated,authorize.apply(owned.withTransaction(other))));
   when(tx.isFailed()).thenReturn(true);assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated,authorize.apply(local)));when(tx.isFailed()).thenReturn(false);
   runtimeRoot.unregisterTransaction(tx);assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException.class,()->controller.retrieveAsset(allocated,authorize.apply(local)));
   System.out.println("PASS owned transient portable-focus transport; missing/wrong/failed/closedTX, unownedID, foreignuser/context/cycle/observer andsentinels rejected");
  }finally{server.stop(0);manager.shutdown();}
 }
 public static void main(String[] args)throws Exception{new ProspectiveFocusTransportTest().ownedRegisteredTransientFocusSurvivesActualPinnedHeaderRestoration();}
}

