package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.knowledge.ModelCandidateSources;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.junit.jupiter.api.Test;

class FrozenModelQueryFailureTest {
  @Test void supplierDeadlineIncludesCapabilityNegotiationAndIsNotRetriedWithinAttempt() throws Exception {
    var entered=new java.util.concurrent.CountDownLatch(1);
    var interrupted=new java.util.concurrent.CountDownLatch(1);
    var client=mock(ResourcesClient.class,CALLS_REAL_METHODS);
    doReturn("slow").when(client).serviceId();doReturn(1).when(client).candidateTimeoutSeconds();
    doAnswer(call->{entered.countDown();try {new java.util.concurrent.CountDownLatch(1).await();}
      catch(InterruptedException expected){interrupted.countDown();Thread.currentThread().interrupt();}
      return null;}).when(client).capabilities(any());
    try(var session=ModelCandidateSources.openSession()) {
      assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),()->{
        // Keep the attempt session on this thread, including both dependency requests.
        ModelCandidateSources.inSession(session,()->{
          for(int i=0;i<2;i++)assertThrows(Utils.Http.Client.RequestFailure.class,()->client.query(
              Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope(client,true)));
          return null;
        });
      });
      assertEquals(0,entered.getCount());assertTrue(interrupted.await(2,java.util.concurrent.TimeUnit.SECONDS));
      verify(client,times(1)).capabilities(any());
    }
  }
  static ContextScope scope(ResourcesService service,boolean bound) {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");when(scope.getHostServiceId()).thenReturn("private-runtime");
    doReturn(List.of(service)).when(scope).getServices(ResourcesService.class);
    doReturn(List.of(service)).when(scope).getServices(KlabService.class);
    if(bound)when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","private-runtime",true,null));
    return scope;
  }
  static ResourcesClient client(Utils.Http.Client http) throws Exception {
    var client=mock(ResourcesClient.class,CALLS_REAL_METHODS);doReturn("supplier").when(client).serviceId();
    doReturn(URI.create("https://user:secret-password@supplier.example/resources?token=secret-query#secret-fragment").toURL()).when(client).getUrl();
    var advertised=new org.integratedmodelling.common.services.ResourcesCapabilitiesImpl();advertised.setModelCandidateProtocol(1);
    doReturn(advertised).when(client).capabilities(any());
    var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);return client;
  }
  @Test void supplierRequestPreservesUserButNeverRequiresPrivateRuntimeOrCycle() throws Exception {
    var failure=new AtomicReference<Throwable>();var calls=new AtomicInteger();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{try {
      assertEquals(ServicesAPI.RESOURCES.MODEL_CANDIDATES,exchange.getRequestURI().getPath());
      for(var name:List.of(ServicesAPI.SCOPE_HEADER,ServicesAPI.SERVICE_ID_HEADER,ServicesAPI.TRANSACTION_ID_HEADER,
          ServicesAPI.RESOLUTION_CYCLE_HEADER,ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER,ServicesAPI.CONSEQUENCE_HEADER))
        assertNull(exchange.getRequestHeaders().getFirst(name),name);
      assertEquals("test-user",exchange.getRequestHeaders().getFirst(ServicesAPI.USERNAME_HEADER));
      assertEquals("user-credential",exchange.getRequestHeaders().getFirst("Authorization"));
      var text=new String(exchange.getRequestBody().readAllBytes());assertFalse(text.contains("private-runtime"));assertFalse(text.contains("cycle"));
      var request=Utils.Json.parseObject(text,ModelCandidateRequest.class);assertEquals(1,request.version());assertTrue(request.namespaces().isEmpty());assertTrue(request.projects().isEmpty());calls.incrementAndGet();
    }catch(Throwable problem){failure.set(problem);}
      var bytes=Utils.Json.asString(ResourceSet.empty()).getBytes();exchange.sendResponseHeaders(200,bytes.length);
      try(var out=exchange.getResponseBody()){out.write(bytes);}});server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=client(http);var scope=scope(client,true);var user=mock(UserIdentity.class);
      when(user.getUsername()).thenReturn("test-user");when(user.getId()).thenReturn("user-credential");
      when(user.getData()).thenReturn(Parameters.create());when(user.getGroups()).thenReturn(List.of());when(scope.getUser()).thenReturn(user);
      try(var snapshot=ModelCandidateSources.openSession()) {
        for(int i=0;i<2;i++)assertTrue(client.query(Parameters.create("observable","geography:Elevation"),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope).getFirst().isEmpty());
      }
      assertNull(failure.get(),()->String.valueOf(failure.get()));assertEquals(1,calls.get());
    }finally{server.stop(0);}
  }
  @Test void oldSupplierFallsBackOnlyToAuthorizedUserSourceListing() throws Exception {
    var paths=new ArrayList<String>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{paths.add(exchange.getRequestURI().getPath());
      assertNull(exchange.getRequestHeaders().getFirst(ServicesAPI.SCOPE_HEADER));exchange.getRequestBody().readAllBytes();
      if(exchange.getRequestURI().getPath().equals(ServicesAPI.RESOURCES.MODEL_CANDIDATES)){exchange.sendResponseHeaders(500,-1);exchange.close();return;}
      var bytes="[]".getBytes();exchange.sendResponseHeaders(200,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}});server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=client(http);doReturn(new org.integratedmodelling.common.services.ResourcesCapabilitiesImpl()).when(client).capabilities(any());assertTrue(client.query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope(client,true)).getFirst().isEmpty());
      assertEquals(1,paths.size());assertTrue(paths.getFirst().contains("/list/"));
      assertFalse(paths.stream().anyMatch(p->p.contains("semantic/cycle")));
    }finally{server.stop(0);}
  }
  @Test void failedSupplierDoesNotDiscardHealthyCandidatesOrCertifyExhaustiveAbsence() throws Exception {
    var healthy=mock(ResourcesService.class);when(healthy.serviceId()).thenReturn("healthy");
    var failed=mock(ResourcesService.class);when(failed.serviceId()).thenReturn("failed");var scope=scope(healthy,true);
    doReturn(List.of(healthy,failed)).when(scope).getServices(ResourcesService.class);
    var found=ResourceSet.of(new ResourceSet.Resource("healthy","ns.model","project",null,KlabAsset.KnowledgeClass.MODEL,1,false));
    when(healthy.query(any(),any(),any(),same(scope))).thenReturn(List.of(found));
    when(failed.query(any(),any(),any(),same(scope))).thenThrow(new IllegalStateException("no graph access"));
    var results=new ResourcesMerger(scope).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope);
    assertEquals(1,results.stream().mapToInt(r->r.getResults().size()).sum());
    assertTrue(results.stream().flatMap(r->r.getNotifications().stream()).anyMatch(n->n.getMessage().contains(ModelCandidateSources.INCOMPLETE)));
    doReturn(List.of()).when(scope).getServices(ResourcesService.class);doReturn(List.of()).when(scope).getServices(KlabService.class);
    assertTrue(new ResourcesMerger(scope).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope)
        .stream().flatMap(r->r.getNotifications().stream()).anyMatch(n->n.getMessage().contains(ModelCandidateSources.INCOMPLETE)));
  }
  @Test void emptyRemote403RetainsProviderAndStatusWithoutLeakingUrlCredentials() throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(403,-1);exchange.close();});server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=client(http);var scope=scope(client,true);
      var error=assertThrows(Utils.Http.Client.RequestFailure.class,()->client.query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope));
      assertEquals(403,error.getStatus());assertTrue(error.getMessage().contains("HTTP 403"));
      assertTrue(error.getMessage().contains("supplier.example/resources"));assertFalse(error.getMessage().contains("secret-"));
    }finally{server.stop(0);}
  }
  @Test void sourceCatalogMayExceedTheSeparateDiagnosticResponseLimit() throws Exception {
    var source=new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    source.setUrn("large");source.setSourceCode("x".repeat(1_100_000));
    var syntax=new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();syntax.setUrn("large.model");
    syntax.setScope(KlabStatement.Scope.PUBLIC);source.getStatements().add(syntax);
    var response=ModelCandidateSources.capture(List.of(source),ModelCandidateRequest.from(null),"supplier",null);
    var bytes=Utils.Json.asString(response).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(bytes.length>1024*1024);
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,bytes.length);
      try(var out=exchange.getResponseBody()){out.write(bytes);}});server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=client(http);var captured=client.query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope(client,true)).getFirst();
      assertEquals(1,captured.getResults().size());
      assertEquals(1_100_000,ModelCandidateSources.read(captured.getResults().iterator().next()).getSourceCode().length());
    }finally{server.stop(0);}
  }
}
