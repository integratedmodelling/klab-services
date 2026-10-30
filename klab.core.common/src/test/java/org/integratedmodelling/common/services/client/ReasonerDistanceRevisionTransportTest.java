package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.junit.jupiter.api.Test;

class ReasonerDistanceRevisionTransportTest {
  @Test void olderServerCannotAnswerRevisionBoundRequestThroughItsLegacyScalarRoute() throws Exception {
    var legacyCalls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/reasoner/api/v1/distance", exchange -> {
      exchange.getRequestBody().readAllBytes();
      boolean legacyPath = exchange.getRequestURI().getPath().equals("/reasoner/api/v1/distance");
      if (legacyPath) legacyCalls.incrementAndGet();
      var body = (legacyPath ? "0" : "{}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(legacyPath ? 200 : 404, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort() + "/reasoner", null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var concept = new ConceptImpl(); concept.setUrn("w:Value");
      var error = assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,
          () -> client.semanticDistance(concept, concept, null, 42));
      assertTrue(error.getMessage().contains("HTTP 404"));
      assertEquals(0, legacyCalls.get(), "Missing validated route must not fall back to legacy arithmetic");
      server.createContext("/reasoner/api/v1/distance/validated", exchange -> {
        exchange.getRequestBody().readAllBytes();
        var body = new byte[0];
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
      });
      var missing = assertThrows(Utils.Http.Client.RequestFailure.class,
          () -> client.semanticDistance(concept, concept, null, 42));
      assertTrue(missing.getMessage().contains("no score"), "A missing scalar must not become an unboxing NPE");
      assertEquals(0, legacyCalls.get());
    } finally { server.stop(0); }
  }

  @Test void unsupportedRevisionAwareReasonerCannotFallBackToUnversionedArithmetic() {
    var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class, CALLS_REAL_METHODS);
    var a = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    var b = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    assertTrue(reasoner.semanticDistance(a, b, null, 42) < 0);
    verify(reasoner, never()).semanticDistance(a, b, null);
  }

  private org.integratedmodelling.common.knowledge.ObservableImpl observable(ConceptImpl base,ConceptImpl observer) {
    var ret=new org.integratedmodelling.common.knowledge.ObservableImpl();ret.setSemantics(base);ret.setObserverSemantics(observer);ret.setUrn(base.getUrn()+(observer==null?"":" observed as "+observer.getUrn()));return ret;
  }
  @Test void completeObserverComparisonUsesOneRevisionBoundServerRequest()throws Exception {
    var stale=new AtomicBoolean();var calls=new AtomicInteger();var queries=new ArrayList<String>();
    var observerPayload=new java.util.concurrent.atomic.AtomicReference<org.integratedmodelling.klab.api.knowledge.Semantics[]>();
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/reasoner/api/v1/distance/validated",exchange->{
      byte[] bytes=exchange.getRequestBody().readAllBytes();observerPayload.set(mapper.readValue(bytes,org.integratedmodelling.klab.api.knowledge.Semantics[].class));
      synchronized(queries){queries.add(exchange.getRequestURI().getRawQuery());}calls.incrementAndGet();
      var body=(stale.get()?"-50":"8").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
    });server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort()+"/reasoner",null)) {
      var client=mock(ReasonerClient.class,CALLS_REAL_METHODS);var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      var base=new ConceptImpl();base.setUrn("w:Value");var general=new ConceptImpl();general.setUrn("w:Observer");var specific=new ConceptImpl();specific.setUrn("w:SpecialObserver");
      assertEquals(8,client.semanticDistance(observable(base,general),observable(base,specific),null,42));
      assertEquals("w:Observer",((Observable)observerPayload.get()[0]).getObserverSemantics().getUrn());assertEquals("w:SpecialObserver",((Observable)observerPayload.get()[1]).getObserverSemantics().getUrn());
      stale.set(true);assertTrue(client.semanticDistance(observable(base,general),observable(base,specific),null,42)<0);
      assertEquals(2,calls.get(),"Observer and base compatibility must be checked atomically, not through separate generations");
      assertTrue(queries.stream().allMatch(q->q.contains("knowledgeRevision=42")&&!q.contains("asserted=true")));
    } finally{server.stop(0);}
  }
  @Test void versionedRetriesReachServerWithoutAddingLocalObserverArithmetic()throws Exception {
    var score=new AtomicInteger(6);var calls=new AtomicInteger();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/reasoner/api/v1/distance/validated",exchange->{exchange.getRequestBody().readAllBytes();calls.incrementAndGet();var body=Integer.toString(score.get()).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort()+"/reasoner",null)) {
      var client=mock(ReasonerClient.class,CALLS_REAL_METHODS);var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      var base=new ConceptImpl();base.setUrn("w:Value");var observer=new ConceptImpl();observer.setUrn("w:Observer");
      assertEquals(6,client.semanticDistance(observable(base,null),observable(base,observer),null,42),"Only the server supplies the complete compatibility score");
      score.set(-50);assertEquals(-50,client.semanticDistance(observable(base,null),observable(base,observer),null,42));assertTrue(client.semanticDistance(base,base,null,-1)<0);assertEquals(2,calls.get());
    } finally{server.stop(0);}
  }
  @Test void unresolvedGenericRequestCannotReuseEarlierPositiveConvenienceScore()throws Exception {
    var calls=new AtomicInteger();var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/reasoner/api/v1/distance",exchange->{
      var payload=mapper.readValue(exchange.getRequestBody().readAllBytes(),org.integratedmodelling.klab.api.knowledge.Semantics[].class);
      calls.incrementAndGet();var requested=(Observable)payload[1];
      var body=(requested.getGenericComponents().isEmpty()?"3":"-10000").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
    });server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort()+"/reasoner",null)) {
      var client=mock(ReasonerClient.class,CALLS_REAL_METHODS);var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      var cache=ReasonerClient.class.getDeclaredField("semanticDistances");cache.setAccessible(true);cache.set(client,com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build());
      var base=new ConceptImpl();base.setUrn("w:Value");var requested=observable(base,null);var candidate=observable(base,null);
      assertEquals(3,client.semanticDistance(candidate,requested,null));assertEquals(3,client.semanticDistance(candidate,requested,null));assertEquals(1,calls.get());
      var unresolved=new ConceptImpl();unresolved.setUrn("w:Unresolved");requested.getGenericComponents().add(unresolved);
      assertTrue(client.semanticDistance(candidate,requested,null)<0);assertEquals(2,calls.get());
    } finally {server.stop(0);}
  }

}
