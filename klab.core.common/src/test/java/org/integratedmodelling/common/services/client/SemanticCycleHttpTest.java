package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class SemanticCycleHttpTest {
  @Test void cleanupSurvivesDeletedContextAndRetriesOnlyTransientFailures() throws Exception {
    var calls = new AtomicInteger(); var mode = new AtomicInteger(503);
    var failure = new AtomicReference<Throwable>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.REASONER.SEMANTIC_CYCLE_RELEASE, exchange -> {
      int call = calls.incrementAndGet();
      try {
        assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.SCOPE_HEADER));
        assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.SERVICE_ID_HEADER));
        assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.TRANSACTION_ID_HEADER));
        assertEquals("tester", exchange.getRequestHeaders().getFirst(ServicesAPI.USERNAME_HEADER));
        exchange.getRequestBody().readAllBytes();
      } catch (Throwable problem) { failure.set(problem); }
      int status = mode.get() == 503 && call > 1 ? 200 : mode.get();
      byte[] body = "true".getBytes();
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    var context = SemanticCycleFacadeTest.scope();
    var user = mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    var identity = mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);
    when(identity.getId()).thenReturn("user-id"); when(identity.getUsername()).thenReturn("tester");
    when(identity.getData()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
    when(user.getUser()).thenReturn(identity);
    when(context.getParentScope(Scope.Type.USER, org.integratedmodelling.klab.api.scope.UserScope.class)).thenReturn(user);
    when(context.getContextObservation()).thenThrow(new AssertionError("Deleted context must not be hydrated"));
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var binding = new SemanticCycleBinding(SemanticCycleBinding.VERSION, "reasoner", "session", 1, "fingerprint", "lease");
      client.releaseSemanticCycle(binding, "runtime", "context", "cycle", context);
      assertEquals(2, calls.get());
      mode.set(403); calls.set(0);
      assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> client.releaseSemanticCycle(binding, "runtime", "context", "cycle", context));
      assertEquals(1, calls.get(), "Authorization denial must not be retried");
      mode.set(500); calls.set(0);
      assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> client.releaseSemanticCycle(binding, "runtime", "context", "cycle", context));
      assertEquals(3, calls.get(), "Transport retries must be bounded");
      assertNull(failure.get(), () -> String.valueOf(failure.get()));
    } finally { server.stop(0); }
  }
  @Test void typedUnavailabilityIsDistinctFromHttpAuthorizationAndMalformedProtocolFailures() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var status=new AtomicInteger(200);
    var body=new AtomicReference<>(mapper.writeValueAsBytes(new SemanticCycleBeginResult(
        SemanticCycleBeginResult.Status.NO_ACCEPTED_VIEW,null,"No accepted view")));
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext(ServicesAPI.REASONER.SEMANTIC_CYCLE_BEGIN,exchange->{
      exchange.getRequestBody().readAllBytes();byte[] bytes=body.get();
      exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status.get(),bytes.length);
      try(var output=exchange.getResponseBody()){output.write(bytes);}
    });server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=mock(ReasonerClient.class,CALLS_REAL_METHODS);var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      var scope=SemanticCycleFacadeTest.scope();
      assertEquals(SemanticCycleBeginResult.Status.NO_ACCEPTED_VIEW,client.beginSemanticCycleResult("runtime","context","cycle",scope).status());
      assertThrows(IllegalStateException.class,()->client.beginSemanticCycle("runtime","context","cycle",scope));
      status.set(403);body.set("{\"detail\":\"Wrong owner\"}".getBytes());
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,()->client.beginSemanticCycleResult("runtime","context","cycle",scope));
      status.set(200);body.set("{\"status\":\"BOUND\"}".getBytes());
      assertThrows(RuntimeException.class,()->client.beginSemanticCycleResult("runtime","context","cycle",scope));
    }finally{server.stop(0);}
  }
  @Test void actualClientBeginBindBuilderMapsAndClosedFailureUseOnlyPinnedHttpProtocol() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var owner=SemanticCycleFacadeTest.request("cycle");
    var calls=new ArrayList<String>();var released=new AtomicBoolean();var failure=new AtomicReference<Throwable>();
    var actor=mock(Reasoner.class);var meaning=SemanticCycleFacadeTest.concept("test:Tree");
    when(actor.resolveConcept("test:Tree")).thenReturn(meaning);
    when(actor.buildConcept(any(),isNull())).thenAnswer(i->{
      ObservableBuildStrategy builder=i.getArgument(0);assertEquals("test:Tree",builder.getBaseConcept().getUrn());
      assertEquals(1,builder.getOperations().size());return meaning;
    });
    when(actor.match(any(),any(),any())).thenAnswer(i->{((Map<Concept,Concept>)i.getArgument(2)).put(meaning,meaning);return true;});
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      byte[] response;int status=200;
      try {
        var path=exchange.getRequestURI().getPath();calls.add(path);assertEquals("POST",exchange.getRequestMethod());
        var bytes=exchange.getRequestBody().readAllBytes();
        if(path.equals(ServicesAPI.REASONER.SEMANTIC_CYCLE_BEGIN)) {
          var request=mapper.readValue(bytes,SemanticCycleRequest.class);assertNull(request.binding());
          assertEquals(owner.contextId(),request.contextId());response=mapper.writeValueAsBytes(
              new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,owner.binding(),null));
        }else if(path.equals(ServicesAPI.REASONER.SEMANTIC_CYCLE_RELEASE)) {
          assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.SCOPE_HEADER));
          assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.TRANSACTION_ID_HEADER));
          assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.RESOLUTION_CYCLE_HEADER));
          assertEquals(owner,mapper.readValue(bytes,SemanticCycleRequest.class));released.set(true);response="true".getBytes();
        }else {
          assertEquals(ServicesAPI.REASONER.SEMANTIC_CYCLE_INVOKE,path);
          var invocation=mapper.readValue(bytes,SemanticCycleInvocation.class);assertEquals(owner,invocation.cycle());
          if(invocation.method().equals("buildConcept")) {
            assertEquals("context.42#7",exchange.getRequestHeaders().getFirst(ServicesAPI.SCOPE_HEADER));
            assertEquals("transaction",exchange.getRequestHeaders().getFirst(ServicesAPI.TRANSACTION_ID_HEADER));
          } else {
            assertEquals("context",exchange.getRequestHeaders().getFirst(ServicesAPI.SCOPE_HEADER));
            assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.TRANSACTION_ID_HEADER));
            assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER));
          }
          if(released.get()){status=403;response="{\"detail\":\"closed semantic cycle\"}".getBytes();}
          else response=mapper.writeValueAsBytes(SemanticCycleFacadeTest.invoke(invocation,actor));
        }
      }catch(Throwable problem){failure.set(problem);status=500;response="{}".getBytes();}
      exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,response.length);
      try(var output=exchange.getResponseBody()){output.write(response);}
    });server.start();
    var scope=SemanticCycleFacadeTest.scope();
    when(scope.getParentScope(Scope.Type.USER, org.integratedmodelling.klab.api.scope.UserScope.class))
        .thenReturn(mock(org.integratedmodelling.klab.api.scope.UserScope.class));
    var focus=mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    when(focus.getId()).thenReturn(42L);when(scope.getContextObservation()).thenReturn(focus);
    when(scope.getObserverId()).thenReturn(7L);when(scope.getTransactionId()).thenReturn("transaction");
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=mock(ReasonerClient.class,CALLS_REAL_METHODS);doReturn("reasoner").when(client).serviceId();
      var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      var binding=client.beginSemanticCycle("runtime","context","cycle",scope);assertEquals(owner.binding(),binding);
      var pinned=client.bindSemanticCycle(binding,"runtime","context","cycle",scope);
      assertEquals("test:Tree",pinned.resolveConcept("test:Tree").getUrn());
      var builder=new ObservableBuildStrategy(meaning,scope);builder.of(meaning);
      assertEquals("test:Tree",pinned.buildConcept(builder,scope).getUrn());
      var matches=new LinkedHashMap<Concept,Concept>();assertTrue(pinned.match(meaning,meaning,matches));assertEquals(1,matches.size());
      client.releaseSemanticCycle(binding,"runtime","context","cycle",scope);
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,()->pinned.resolveConcept("test:Tree"));
      assertNull(failure.get(),()->String.valueOf(failure.get()));
      assertEquals(6,calls.size());verify(client,never()).resolveConcept(anyString());
    }finally{server.stop(0);}
  }
}
