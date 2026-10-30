package org.integratedmodelling.klab.services.application.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.view.application.UiEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** A dedicated same-origin application bus. Authentication happens in the first frame. */
@Configuration
@EnableWebSocket
public class ApplicationUiWebSocket implements WebSocketConfigurer {
  private final ApplicationUiManager manager;
  private final ObjectMapper mapper = JacksonConfiguration.newObjectMapper();
  private final ScheduledExecutorService deadlines =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofVirtual().name("kactors-ui-socket-deadlines").factory());
  private final Map<String, Peer> peers = new ConcurrentHashMap<>();

  public ApplicationUiWebSocket(ApplicationUiManager manager) {
    this.manager = manager;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Spring's default origin policy is same-origin. No wildcard or SockJS bridge.
    registry.addHandler(new Handler(), "/ui-bus");
  }

  private final class Peer implements AutoCloseable {
    final WebSocketSession socket;
    final ThreadPoolExecutor output =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),
            Thread.ofVirtual().name("kactors-ui-output-", 0).factory());
    volatile ApplicationUiManager.Connection connection;

    Peer(WebSocketSession socket) {
      this.socket = socket;
    }

    void send(Map<String, Object> envelope) {
      try {
        byte[] json = mapper.writeValueAsBytes(envelope);
        if (json.length > 262144) {
          deadlines.execute(this::close);
          return;
        }
        output.execute(
            () -> {
              try {
                if (socket.isOpen()) socket.sendMessage(new TextMessage(json));
              } catch (IOException error) {
                close();
              }
            });
      } catch (IOException | RejectedExecutionException error) {
        // render() may hold the model/session monitor: never close the socket synchronously here.
        if (!deadlines.isShutdown()) deadlines.execute(this::close);
      }
    }

    public void close() {
      output.shutdownNow();
      try {
        socket.close(CloseStatus.POLICY_VIOLATION);
      } catch (IOException ignored) {
      }
    }
  }

  private final class Handler extends TextWebSocketHandler {
    @Override
    public void afterConnectionEstablished(WebSocketSession socket) {
      socket.setTextMessageSizeLimit(65536);
      var peer = new Peer(socket);
      peers.put(socket.getId(), peer);
      deadlines.schedule(
          () -> {
            if (peer.connection == null) peer.close();
          },
          5,
          TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
      var peer = peers.get(socket.getId());
      if (peer == null) return;
      try {
        if (message.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
          throw new IllegalArgumentException();
        var frame = mapper.readTree(message.getPayload());
        if (peer.connection == null) {
          String ticket = frame.path("ticket").asText();
          if (ticket.length() > 100) throw new IllegalArgumentException();
          peer.connection =
              manager.attach(
                  ticket, socket.getHandshakeHeaders().getOrigin(), peer::send, peer::close);
          if (!socket.isOpen()) peer.connection.close();
        } else if ("ping".equals(frame.path("type").asText())) peer.connection.ping();
        else if ("input".equals(frame.path("type").asText()))
          peer.connection.event(mapper.treeToValue(frame.path("value"), UiEvent.class));
        else throw new IllegalArgumentException();
      } catch (Exception denied) {
        peer.close();
      }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
      var peer = peers.remove(socket.getId());
      if (peer != null) {
        if (peer.connection != null) peer.connection.close();
        peer.close();
      }
    }

    @Override
    public void handleTransportError(WebSocketSession socket, Throwable error) {
      var peer = peers.get(socket.getId());
      if (peer != null) peer.close();
    }
  }

  @jakarta.annotation.PreDestroy
  public void dispose() {
    deadlines.shutdownNow();
    peers.values().forEach(Peer::close);
  }
}
