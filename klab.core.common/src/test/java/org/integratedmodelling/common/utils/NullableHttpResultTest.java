package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.junit.jupiter.api.Test;

class NullableHttpResultTest {
  @Test void nullableSemanticResultAcceptsEmptyBodyWithoutReportingParseFailure() throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange -> {exchange.sendResponseHeaders(200,-1);exchange.close();});
    server.start();
    var scope=org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.Scope.class);
    try(var client=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),scope)) {
      assertNull(client.post("/inherent",Map.of(),Concept.class));
      assertEquals("",client.post("/empty",Map.of(),String.class));
      org.mockito.Mockito.verifyNoInteractions(scope);
    } finally {server.stop(0);}
  }
}
