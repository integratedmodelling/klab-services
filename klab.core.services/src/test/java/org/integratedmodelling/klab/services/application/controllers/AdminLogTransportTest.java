package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.services.logging.LogQuery;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminLogTransportTest {
  @TempDir Path root;
  @Test void productionClientControllerAndRegisteredFileRoundTripSafeDiagnostics() throws Exception {
    var file=root.resolve("reasoner.log");Files.writeString(file,
        "[2026-10-23 13:31:46.123] - 12 ERROR [main] --- org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics: AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE phase=COMPONENT_EXPORT component=klab.authority.taxa@1.0.0-SNAPSHOT reason=EXCEPTION httpStatus=500 token=secret\n"
        +"Caused by: java.lang.IllegalArgumentException: No asset secret\n");
    var principal=mock(EngineAuthorization.class);var scope=mock(ServiceUserScope.class);
    when(principal.isAuthenticated()).thenReturn(true);when(principal.getScope()).thenReturn(scope);
    when(scope.isAuthorized(CRUDOperation.ADMINISTER)).thenReturn(true);
    var user=mock(UserIdentity.class);when(scope.getUser()).thenReturn(user);when(user.getId()).thenReturn("private-identity");
    var controller=new AdminLogController(new MockEnvironment().withProperty("logging.file.name",file.toString()));
    controller.instance=mock(ServiceNetworkedInstance.class);
    var service=mock(BaseService.class);doReturn(service).when(controller.instance).klabService();
    when(service.serviceId()).thenReturn("target-service");
    var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new KlabErrorHandler()).build();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    var failure=new AtomicReference<Throwable>();
    server.createContext("/admin/logs/",exchange->{
      try {
        var request=exchange.getRequestMethod().equals("POST")?post(exchange.getRequestURI()):get(exchange.getRequestURI());
        var response=mvc.perform(request.principal(principal).contentType("application/json")
            .content(exchange.getRequestBody().readAllBytes())).andReturn().getResponse();
        byte[] bytes=response.getContentAsByteArray();exchange.getResponseHeaders().set("Content-Type",response.getContentType());
        exchange.sendResponseHeaders(response.getStatus(),bytes.length);
        try(var out=exchange.getResponseBody()) {out.write(bytes);}
      } catch(Throwable error) {failure.set(error);exchange.sendResponseHeaders(500,-1);exchange.close();}
    });
    server.start();
    try(var http=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=mock(BaseServiceClient.class,CALLS_REAL_METHODS);
      var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      assertEquals("vetted-diagnostics-v1",client.logCapabilities().redactionPolicy());
      var page=client.queryLogs(LogQuery.firstPage());assertEquals("target-service",page.serviceId());
      assertEquals(1,page.records().size());assertEquals("500",page.records().getFirst().diagnostics().get("httpStatus"));
      assertTrue(page.records().getFirst().stackTrace().contains("java.lang.IllegalArgumentException"));
      assertFalse(page.toString().contains("secret"));assertFalse(page.toString().contains("private-identity"));
      assertNull(failure.get());
      when(scope.isAuthorized(CRUDOperation.ADMINISTER)).thenReturn(false);
      var denied=assertThrows(Utils.Http.Client.RequestFailure.class,()->client.queryLogs(LogQuery.firstPage()));
      assertEquals(403,denied.getStatus());assertFalse(denied.getMessage().contains("secret"));
      assertNull(failure.get());
    } finally {server.stop(0);controller.close();}
  }
}
