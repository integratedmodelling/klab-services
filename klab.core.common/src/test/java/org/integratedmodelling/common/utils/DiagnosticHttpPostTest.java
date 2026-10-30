package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Map;
import org.integratedmodelling.klab.api.services.logging.LogPage;
import org.junit.jupiter.api.Test;

class DiagnosticHttpPostTest {
  @Test void preservesHttpStatusWithoutResponseOrRequestSecrets() throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      exchange.getRequestBody().readAllBytes();
      byte[] body="Authorization: Bearer secret-response".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(410,body.length);
      try(var out=exchange.getResponseBody()){out.write(body);}
    });server.start();
    try(var client=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var failure=assertThrows(Utils.Http.Client.RequestFailure.class,()->client.postWithStatus("/",
          Map.of("password","secret-request"),LogPage.class));
      assertEquals(410,failure.getStatus());assertFalse(failure.toString().contains("secret"));assertNull(failure.getCause());
    } finally {server.stop(0);}
  }
  @Test void boundsChunkedResponseAndRedactsMalformedJsonCause() throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/large",exchange->{
      exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,0);
      try(var out=exchange.getResponseBody()) {
        byte[] chunk=new byte[64*1024];for(int i=0;i<40;i++)out.write(chunk);
      } catch(java.io.IOException cancelled) { /* Expected bounded client cancellation. */ }
    });
    server.createContext("/invalid",exchange->{
      exchange.getRequestBody().readAllBytes();byte[] body="not-json secret-response".getBytes();
      exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
    });server.start();
    try(var client=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      for(String endpoint:new String[]{"/large","/invalid"}) {
        var failure=assertThrows(Utils.Http.Client.RequestFailure.class,()->client.postWithStatus(endpoint,Map.of(),LogPage.class));
        assertEquals(0,failure.getStatus());
        var buffer=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(buffer));
        assertFalse(buffer.toString().contains("secret-response"));
      }
    } finally {server.stop(0);}
  }
  @Test void preservesNonstandardUpstreamStatusWithoutBodyDisclosure() throws Exception {
    var status=new java.util.concurrent.atomic.AtomicInteger(499);
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      exchange.getRequestBody().readAllBytes();byte[] bytes="secret-response".getBytes();
      exchange.sendResponseHeaders(status.get(),bytes.length);
      try(var out=exchange.getResponseBody()){out.write(bytes);}
    });server.start();
    try(var client=Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      for(int code:new int[]{499,599}) {
        status.set(code);
        var failure=assertThrows(Utils.Http.Client.RequestFailure.class,()->client.postWithStatus("/",Map.of(),LogPage.class));
        assertEquals(code,failure.getStatus());assertFalse(failure.toString().contains("secret"));
      }
    } finally {server.stop(0);}
  }
}
