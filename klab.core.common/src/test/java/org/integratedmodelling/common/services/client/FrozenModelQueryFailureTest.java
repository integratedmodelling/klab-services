package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.junit.jupiter.api.Test;

class FrozenModelQueryFailureTest {
  static ContextScope scope(ResourcesService service,boolean bound) {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("context");when(scope.getHostServiceId()).thenReturn("runtime");
    doReturn(List.of(service)).when(scope).getServices(ResourcesService.class);doReturn(List.of(service)).when(scope).getServices(KlabService.class);
    if(bound)when(scope.getResolutionCycleBinding()).thenReturn(new ResolutionCycleBinding(1,"cycle","context","runtime",true,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",7,"recipe","lease")));
    return scope;
  }
  @Test void actualClientCatalogFailurePropagatesThroughMergerInsteadOfBecomingNoModel() throws Exception {
    var status=new AtomicInteger(404);var path=new java.util.concurrent.atomic.AtomicReference<String>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      path.set(exchange.getRequestURI().getPath());exchange.getRequestBody().readAllBytes();var bytes=(status.get()==200?"[]":"{\"detail\":\"MODEL_CATALOG_VIEW_UNAVAILABLE\"}").getBytes();
      exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status.get(),bytes.length);
      try(var output=exchange.getResponseBody()){output.write(bytes);}
    });server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=mock(ResourcesClient.class,CALLS_REAL_METHODS);doReturn("resources").when(client).serviceId();
      var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      var bound=scope(client,true);var merger=new ResourcesMerger(bound);
      var failure=assertThrows(CompletionException.class,()->merger.query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,bound));
      assertInstanceOf(Utils.Http.Client.RequestFailure.class,failure.getCause());
      assertEquals(404,((Utils.Http.Client.RequestFailure)failure.getCause()).getStatus());
      assertEquals("/api/v1/semantic/cycle/models/MODEL",path.get());
      var ordinary=scope(client,false);
      assertTrue(new ResourcesMerger(ordinary).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,ordinary).isEmpty());
      assertEquals("/api/v1/query/MODEL",path.get());
      status.set(200);assertTrue(merger.query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,bound).isEmpty());
    }finally{server.stop(0);}
  }
  @Test void oneFailedProviderDoesNotPermitPartialFrozenModelDiscovery() {
    var first=mock(ResourcesService.class);var second=mock(ResourcesService.class);
    when(first.serviceId()).thenReturn("first");when(second.serviceId()).thenReturn("second");
    var scope=scope(first,true);doReturn(List.of(first,second)).when(scope).getServices(ResourcesService.class);
    when(first.query(any(),eq(KlabAsset.KnowledgeClass.MODEL),eq(ResourceSet.class),same(scope))).thenReturn(List.of(new ResourceSet()));
    when(second.query(any(),eq(KlabAsset.KnowledgeClass.MODEL),eq(ResourceSet.class),same(scope))).thenThrow(new IllegalStateException("Unavailable catalog view"));
    assertThrows(CompletionException.class,()->new ResourcesMerger(scope).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,scope));
  }
  @Test void advertisedDiscoveryFailureCannotSilentlyUseOnlyHealthyTypedProviders() {
    var service=mock(ResourcesService.class);when(service.serviceId()).thenReturn("healthy");var bound=scope(service,true);
    doThrow(new IllegalStateException("advertisements unavailable")).when(bound).getServices(KlabService.class);
    assertThrows(IllegalStateException.class,()->new ResourcesMerger(bound).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,bound));
    verify(service,never()).query(any(),any(),any(),any());
    var ordinary=scope(service,false);doThrow(new IllegalStateException("unsupported view")).when(ordinary).getServices(KlabService.class);
    when(service.query(any(),any(),any(),same(ordinary))).thenReturn(List.of());
    assertTrue(new ResourcesMerger(ordinary).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,ordinary).isEmpty());
  }
  @Test void zeroProvidersIsUnavailableRatherThanSuccessfulEmptyFrozenDiscovery() {
    var service=mock(ResourcesService.class);var bound=scope(service,true);
    doReturn(List.of()).when(bound).getServices(ResourcesService.class);doReturn(List.of()).when(bound).getServices(KlabService.class);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,
        ()->new ResourcesMerger(bound).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,bound));
    var ordinary=scope(service,false);
    doReturn(List.of()).when(ordinary).getServices(ResourcesService.class);doReturn(List.of()).when(ordinary).getServices(KlabService.class);
    assertTrue(new ResourcesMerger(ordinary).query(Parameters.create(),KlabAsset.KnowledgeClass.MODEL,ResourceSet.class,ordinary).isEmpty());
  }
}
