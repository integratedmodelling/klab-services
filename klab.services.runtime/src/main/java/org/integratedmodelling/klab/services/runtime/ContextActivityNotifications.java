package org.integratedmodelling.klab.services.runtime;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.SSLContext;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Explicit finite, best-effort sender-only hints; not federation routing or durable state. */
final class ContextActivityNotifications implements AutoCloseable {
  static final String PROPERTY = "klab.runtime.activityNotifications";
  private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private final String runtimeId;
  private final Map<String, Route> routes;
  private final Map<String, Publisher> publishers = new HashMap<>();
  private boolean closed;
  @FunctionalInterface interface ConnectionOpener { Connection open(ConnectionFactory factory) throws Exception; }
  private ConnectionOpener opener = ConnectionFactory::newConnection;

  static ContextActivityNotifications parse(String runtimeId, byte[] input, ConnectionOpener opener) {
    var notifications = parse(runtimeId, input);
    notifications.opener = Objects.requireNonNull(opener);
    return notifications;
  }

  // Secrets remain private: no record/toString, getters, HTTP DTOs or diagnostic export.
  private static final class Route {
    String context, owner, host, username, password;
    int port;
    boolean tls;
  }

  static ContextActivityNotifications configured(String runtimeId) {
    var path = System.getProperty(PROPERTY);
    if (path == null) return new ContextActivityNotifications(runtimeId, Map.of());
    try {
      var file = Path.of(path);
      if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw invalid();
      try (var input = Files.newInputStream(file)) { return parse(runtimeId, input.readNBytes(65537)); }
    } catch (Exception failure) { throw invalid(); }
  }

  static ContextActivityNotifications parse(String runtimeId, byte[] input) {
    try {
      if (runtimeId == null || runtimeId.isBlank() || input == null || input.length == 0 || input.length > 65536) throw invalid();
      var root = JSON.readTree(input); fields(root, "runtimeId", "contexts");
      if (!runtimeId.equals(text(root, "runtimeId"))) throw invalid();
      var entries = root.get("contexts");
      if (!entries.isArray() || entries.isEmpty() || entries.size() > 16) throw invalid();
      var routes = new HashMap<String, Route>();
      var usernames = new HashSet<String>(); var passwords = new HashSet<String>();
      for (var entry : entries) {
        fields(entry, "context", "owner", "host", "port", "tls", "username", "password");
        var route = new Route();
        route.context = text(entry, "context"); route.owner = text(entry, "owner");
        route.host = text(entry, "host"); route.username = text(entry, "username"); route.password = text(entry, "password");
        if (route.context.length() > 255 || !route.context.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
            || route.owner.length() > 255 || route.owner.codePoints().anyMatch(Character::isISOControl)
            || !route.username.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
            || Set.of("all", "guest", "anonymous").contains(route.username.toLowerCase(Locale.ROOT))
            || !usernames.add(route.username) || !passwords.add(route.password)
            || route.password.length() < 24 || route.password.length() > 256
            || route.password.codePoints().anyMatch(c -> c < 33 || c > 126)
            || !route.host.matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")
            || !entry.get("tls").isBoolean() || !entry.get("port").isIntegralNumber()
            || !entry.get("port").canConvertToInt()) throw invalid();
        route.port = entry.get("port").intValue(); route.tls = entry.get("tls").booleanValue();
        if (route.port < 1024 || route.port > 65535
            || (!route.tls && !Set.of("localhost", "127.0.0.1").contains(route.host))
            || routes.putIfAbsent(route.context, route) != null) throw invalid();
      }
      return new ContextActivityNotifications(runtimeId, Map.copyOf(routes));
    } catch (Exception failure) { throw invalid(); }
  }

  private ContextActivityNotifications(String runtimeId, Map<String, Route> routes) {
    this.runtimeId = runtimeId; this.routes = routes;
  }

  synchronized ServiceContextScope.ActivityNotificationSink forContext(String context, String owner) {
    var route = routes.get(context);
    if (closed || route == null || !route.owner.equals(owner)) return null;
    return publishers.computeIfAbsent(context, ignored -> new Publisher(route));
  }

  synchronized void closeContext(String context) {
    var publisher = publishers.get(context);
    if (publisher != null) publisher.close();
  }

  @Override public synchronized void close() {
    closed = true;
    for (var publisher : publishers.values()) publisher.close();
  }

  private final class Publisher implements ServiceContextScope.ActivityNotificationSink {
    private final Route route;
    private final ThreadPoolExecutor executor;
    private Connection connection;
    private Channel channel;
    private volatile boolean terminal;

    Publisher(Route route) {
      this.route = route;
      // Created on demand, maximum16 publishers,128 queued <=8KiB snapshots each.
      executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), task -> {
        var thread = new Thread(task, "context-activity-hints"); thread.setDaemon(true); return thread;
      }, new ThreadPoolExecutor.AbortPolicy());
      // Establish the exact exchange without inventing an event. A receiver may
      // attach only after this asynchronous, one-attempt initialization succeeds.
      executor.execute(() -> publish(null));
    }

    @Override public void accept(Message message) {
      if (terminal || message == null || message.getMessageClass() != Message.MessageClass.DigitalTwin
          || (message.getMessageType() != Message.MessageType.ActivityStarted && message.getMessageType() != Message.MessageType.ActivityFinished)) return;
      try {
        var dispatch = message.getDispatchId();
        if (dispatch == null || !(dispatch.equals(route.context) || dispatch.startsWith(route.context + ".") || dispatch.startsWith(route.context + "#"))) return;
        var activity = message.getPayload(Activity.class);
        if (activity == null || !runtimeId.equals(activity.getServiceId())) return;
        // Immutable bounded hint snapshot, not the mutable full provenance/metadata object.
        var hint = new ActivityImpl();
        hint.setServiceId(runtimeId); hint.setUrn(bounded(activity.getUrn()));
        hint.setId(activity.getId()); hint.setStart(activity.getStart()); hint.setEnd(activity.getEnd());
        hint.setType(activity.getType()); hint.setOutcome(activity.getOutcome());
        hint.setTransientId(activity.getTransientId());
        var envelope = new MessageImpl();
        envelope.setDispatchId(route.context); envelope.setMessageClass(message.getMessageClass());
        envelope.setMessageType(message.getMessageType()); envelope.setQueue(Message.Queue.Events);
        envelope.setId(message.getId()); envelope.setTimestamp(message.getTimestamp());
        envelope.setPayload(hint); envelope.setPayloadClass(ActivityImpl.class.getCanonicalName());
        var body = Utils.Json.asString(envelope).getBytes(StandardCharsets.UTF_8);
        if (body.length > 8192) return;
        executor.execute(() -> publish(body));
      } catch (RuntimeException ignored) {
        // Queue overflow and malformed hint data drop hints; never block/alter the Job result.
      }
    }

    private void publish(byte[] body) {
      if (terminal) return;
      try {
        if (connection == null) {
          var factory = new ConnectionFactory();
          factory.setHost(route.host); factory.setPort(route.port); factory.setVirtualHost(route.context);
          factory.setUsername(route.username); factory.setPassword(route.password);
          factory.setAutomaticRecoveryEnabled(false); factory.setTopologyRecoveryEnabled(false);
          factory.setConnectionTimeout(2000); factory.setHandshakeTimeout(2000);
          factory.setChannelRpcTimeout(2000); factory.setRequestedHeartbeat(10);
          if (route.tls) { factory.useSslProtocol(SSLContext.getDefault()); factory.enableHostnameVerification(); }
          var opened = opener.open(factory);
          synchronized (this) {
            if (terminal) { opened.abort(1000); return; }
            connection = opened;
          }
          channel = opened.createChannel();
          channel.exchangeDeclare(route.context, BuiltinExchangeType.FANOUT, false, false, null);
        }
        if (body != null) channel.basicPublish(route.context, "", new AMQP.BasicProperties.Builder().contentType("application/json").deliveryMode(1).build(), body);
      } catch (Exception failure) { close(); } // Terminal: no automatic reconnect/replay or credential diagnostics.
    }

    @Override public synchronized void close() {
      terminal = true;
      executor.shutdownNow();
      if (connection != null) { connection.abort(1000); connection = null; }
    }
  }

  private static String bounded(String value) {
    if (value != null && value.length() > 1024) throw new IllegalArgumentException();
    return value;
  }
  private static void fields(JsonNode node, String... names) {
    if (node == null || !node.isObject() || node.size() != names.length) throw invalid();
    for (var name : names) if (!node.has(name)) throw invalid();
  }
  private static String text(JsonNode node, String name) {
    var value = node.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) throw invalid();
    return value.textValue();
  }
  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Invalid private Runtime activity-notification profile");
  }
}
