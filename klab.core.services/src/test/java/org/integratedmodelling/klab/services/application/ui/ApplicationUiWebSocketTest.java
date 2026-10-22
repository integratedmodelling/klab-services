package org.integratedmodelling.klab.services.application.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.view.*;
import org.integratedmodelling.klab.services.application.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Real TCP/WebSocket handshake and record serialization, using an in-process retained model. */
class ApplicationUiWebSocketTest {
  @Configuration
  @EnableWebMvc
  @Import(ApplicationUiWebSocket.class)
  static class Server {
    @Bean
    TomcatServletWebServerFactory serverFactory() {
      var factory = new TomcatServletWebServerFactory(0);
      factory.setContextPath("/runtime");
      factory.addContextCustomizers(
          context ->
              context.addServletContainerInitializer(
                  new org.apache.tomcat.websocket.server.WsSci(), Set.of()));
      return factory;
    }

    @Bean
    DispatcherServlet dispatcherServlet() {
      return new DispatcherServlet();
    }

    @Bean
    ServletRegistrationBean<DispatcherServlet> servlet(DispatcherServlet servlet) {
      return new ServletRegistrationBean<>(servlet, "/");
    }
  }

  static class Listener implements WebSocket.Listener {
    final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    final StringBuilder partial = new StringBuilder();

    public void onOpen(WebSocket socket) {
      socket.request(1);
    }

    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        messages.add(partial.toString());
        partial.setLength(0);
      }
      socket.request(1);
      return null;
    }

    String message() throws Exception {
      var message = messages.poll(10, TimeUnit.SECONDS);
      assertNotNull(message);
      return message;
    }
  }

  @Test
  void authenticatesFirstFrameAndReplaysRetainedStateOverRealWebSocket() throws Exception {
    var launcher = mock(UiApplicationLauncher.class);
    var authentication = mock(ServiceAuthorizationManager.class);
    var user = mock(EngineAuthorization.class);
    when(user.isAuthenticated()).thenReturn(true);
    when(user.getScope()).thenReturn(mock(UserScope.class));
    when(user.getFullyQualifiedUsername()).thenReturn("user://hub/alice");
    when(user.getExpiration()).thenReturn(java.time.Instant.now().plusSeconds(300));
    when(user.getToken()).thenReturn("upstream-hub-jwt");
    when(authentication.validateToken(eq("webui_fixture_credential"), anyMap())).thenReturn(user);
    try (var model = new ApplicationUiModel();
        var manager = new ApplicationUiManager(launcher, authentication, 1800);
        var context = new AnnotationConfigServletWebServerApplicationContext()) {
      model.widget(model.root(), "input", "name", Map.of(), "Name", mock(RuntimeAgent.Scope.class));
      var app = mock(ApplicationBase.class);
      when(app.ui()).thenReturn(model);
      when(app.rootScope()).thenReturn(mock(RuntimeAgent.Scope.class));
      when(launcher.resolve(anyString(), any(), any()))
          .thenReturn(
              new UiApplicationLauncher.Published(
                  "test.app", "project", "resources", null, UiPresentation.defaults(), Map.of()));
      when(launcher.start(any(), any())).thenReturn(app);
      context.registerBean(ApplicationUiManager.class, () -> manager);
      context.register(Server.class);
      context.refresh();
      String origin = "http://localhost:" + context.getWebServer().getPort();
      var instance =
          manager.launch(new ApplicationUiManager.Request("test.app", "en", "test", null), user);
      var ticket = manager.ticket(instance.id(), origin, user, "webui_fixture_credential");
      var listener = new Listener();
      try (var client = HttpClient.newHttpClient()) {
        assertThrows(
            ExecutionException.class,
            () ->
                client
                    .newWebSocketBuilder()
                    .header("Origin", "https://attacker.test")
                    .buildAsync(
                        URI.create(origin.replace("http:", "ws:") + "/runtime/ui-bus"),
                        new Listener())
                    .get(10, TimeUnit.SECONDS));
        var socket =
            client
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("Origin", origin)
                .buildAsync(
                    URI.create(origin.replace("http:", "ws:") + "/runtime/ui-bus"), listener)
                .get(10, TimeUnit.SECONDS);
        socket.sendText("{\"ticket\":\"" + ticket.ticket() + "\"}", true).join();
        var mapper = new ObjectMapper();
        var snapshot = mapper.readTree(listener.message());
        assertEquals("snapshot", snapshot.path("type").asText());
        assertEquals("ready", mapper.readTree(listener.message()).path("type").asText());
        var input = snapshot.path("value").path("root").path("children").get(0);
        socket
            .sendText(
                mapper.writeValueAsString(
                    Map.of(
                        "type",
                        "input",
                        "value",
                        Map.of(
                            "sequence",
                            1,
                            "revision",
                            snapshot.path("value").path("revision").asLong(),
                            "nodeId",
                            input.path("id").asText(),
                            "name",
                            "change",
                            "value",
                            "Remembered"))),
                true)
            .join();
        var updated = mapper.readTree(listener.message());
        assertEquals(
            "Remembered",
            updated
                .path("value")
                .path("root")
                .path("children")
                .get(0)
                .path("properties")
                .path("value")
                .asText());
        assertEquals("ack", mapper.readTree(listener.message()).path("type").asText());
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "rebuild").join();
        var nextTicket = manager.ticket(instance.id(), origin, user, "webui_fixture_credential");
        var nextListener = new Listener();
        var rebuilt =
            client
                .newWebSocketBuilder()
                .header("Origin", origin)
                .buildAsync(
                    URI.create(origin.replace("http:", "ws:") + "/runtime/ui-bus"), nextListener)
                .get(10, TimeUnit.SECONDS);
        rebuilt.sendText("{\"ticket\":\"" + nextTicket.ticket() + "\"}", true).join();
        var restored = mapper.readTree(nextListener.message());
        assertEquals(
            "Remembered",
            restored
                .path("value")
                .path("root")
                .path("children")
                .get(0)
                .path("properties")
                .path("value")
                .asText());
        assertEquals(
            1,
            mapper
                .readTree(nextListener.message())
                .path("value")
                .path("lastAcceptedSequence")
                .asLong());
        rebuilt.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
      }
    }
  }
}
