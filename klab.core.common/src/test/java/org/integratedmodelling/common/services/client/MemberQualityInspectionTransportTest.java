package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberQualityInspectionTransportTest {
  @ParameterizedTest @ValueSource(strings={"typed","empty","unavailable","failure","malformed"})
  void actualHttpPreservesKnownEmptyVersusUnavailable(String mode) throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var request=new AtomicReference<String>();
    var expected=mode.equals("typed")?MemberQualityConsumerInspection.known(Set.of("gradients")):
        mode.equals("empty")?MemberQualityConsumerInspection.known(Set.of()):MemberQualityConsumerInspection.unavailable();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
      byte[] body=mode.equals("malformed")?"{\"unexpected\":true}".getBytes(StandardCharsets.UTF_8):mapper.writeValueAsBytes(expected);
      exchange.getResponseHeaders().set("Content-Type","application/json");
      exchange.sendResponseHeaders(mode.equals("failure")?500:200,body.length);
      try(var output=exchange.getResponseBody()){output.write(body);}
    });server.start();
    try(var http=org.integratedmodelling.common.utils.Utils.Http.getClient("http://127.0.0.1:"+server.getAddress().getPort(),null)) {
      var client=mock(RuntimeClient.class,CALLS_REAL_METHODS);var field=BaseServiceClient.class.getDeclaredField("client");field.setAccessible(true);field.set(client,http);
      var result=client.inspectMemberQualityInputs(new ServiceCallImpl("test.mapper"),mock(UserScope.class));
      assertEquals(expected,result);assertEquals(mode.equals("typed")||mode.equals("empty"),result.available());
      assertEquals("test.mapper",mapper.readValue(request.get(),ServiceCall.class).getUrn());
    } finally {server.stop(0);}
  }
}
