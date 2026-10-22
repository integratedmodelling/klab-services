package org.integratedmodelling.klab.runtime.kactors.view;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.view.application.*;

/**
 * Server-owned live UI tree and emitter bindings. Renderer lifetime is independent of this model.
 */
public final class ApplicationUiModel implements AutoCloseable {
  public static final Set<String> TYPES =
      Set.of(
          "container",
          "text",
          "button",
          "checkbox",
          "input",
          "select",
          "number",
          "progress",
          "header",
          "footer",
          "drawer",
          "panel",
          "window",
          "dialog",
          "modal",
          "toolbar",
          "menu",
          "notification");
  private static final Set<String> SURFACES =
      Set.of(
          "header",
          "footer",
          "drawer",
          "panel",
          "window",
          "dialog",
          "modal",
          "toolbar",
          "menu",
          "notification");

  public static boolean isSurfaceAnnotation(String name) {
    return SURFACES.contains(name);
  }

  private final AtomicLong ids = new AtomicLong();
  private final Map<String, Node> nodes = new LinkedHashMap<>();
  private final Map<String, String> tags = new HashMap<>();
  private final Map<String, RuntimeAgent.Scope> emitters = new HashMap<>();
  private final ThreadPoolExecutor events =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(256),
          Thread.ofVirtual().name("kactors-ui-", 0).factory());
  private final ThreadPoolExecutor reactions =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(256),
          Thread.ofVirtual().name("kactors-ui-reaction-", 0).factory());
  private final ApplicationUiSession session = new ApplicationUiSession(this::enqueue);
  private final Node root;
  private UiPresentation presentation = UiPresentation.defaults();
  private boolean closed;

  private static final class Node {
    final String id, type;
    String action;
    final Map<String, Object> properties = new LinkedHashMap<>();
    final List<Node> children = new ArrayList<>();
    Set<String> events = Set.of();

    Node(String id, String type) {
      this.id = id;
      this.type = type;
    }

    UiNode freeze() {
      return new UiNode(id, type, properties, events, children.stream().map(Node::freeze).toList());
    }
  }

  public ApplicationUiModel() {
    root = new Node("root", "container");
    nodes.put(root.id, root);
    root.properties.put("layout", "column");
    presentation(UiPresentation.current());
  }

  public ApplicationUiSession session() {
    return session;
  }

  public UiExecution root() {
    return new UiExecution(this, root.id, "main");
  }

  public synchronized void presentation(UiPresentation presentation) {
    this.presentation = Objects.requireNonNull(presentation);
    root.properties.put("locale", presentation.locale());
    root.properties.put("theme", presentation.theme());
    root.properties.put("stylesheets", presentation.stylesheets());
    publish();
  }

  public synchronized String localize(String key) {
    return presentation.localize(key);
  }

  public synchronized UiExecution group(
      UiExecution parent, String tag, Map<String, Object> metadata) {
    if (closed || !nodes.containsKey(parent.mount())) return parent;
    var node = add(parent, "container", tag, metadata);
    node.properties.putIfAbsent("layout", "column");
    publish();
    return new UiExecution(this, node.id, parent.namespace());
  }

  public synchronized UiExecution action(
      UiExecution parent, String name, List<Annotation> annotations) {
    if (closed || !nodes.containsKey(parent.mount())) return parent;
    if (annotations.stream().filter(annotation -> SURFACES.contains(annotation.getName())).count()
        > 1) throw new IllegalArgumentException("An action can declare only one UI surface");
    for (var annotation : annotations) {
      String surface = annotation.getName();
      if (SURFACES.contains(surface)) {
        String namespace = Objects.toString(annotation.get("id"), name);
        if (!namespace.matches("[a-z][a-z0-9_-]*") || namespace.equals("main"))
          throw new IllegalArgumentException("Invalid or reserved UI mount ID " + namespace);
        String key = namespace + "/surface";
        var old = tags.get(key);
        if (old != null && !name.equals(nodes.get(old).action))
          throw new IllegalArgumentException("Duplicate UI mount ID " + namespace);
        var publicTag = tags.get("main/" + namespace);
        if (publicTag != null && !publicTag.equals(old))
          throw new IllegalArgumentException("Duplicate application tag " + namespace);
        if (old != null) remove(nodes.get(old));
        var options = new LinkedHashMap<String, Object>();
        annotation.forEach(
            (k, v) ->
                options.put(
                    k,
                    Set.of("title", "label", "description", "tooltip").contains(k)
                            && v instanceof String text
                            && text.matches("#[A-Z][A-Z0-9_]*")
                        ? presentation.localize(text)
                        : v));
        var node = add(new UiExecution(this, root.id, namespace), surface, "surface", options);
        node.action = name;
        tags.put("main/" + namespace, node.id);
        if (Set.of("dialog", "modal", "drawer").contains(surface)) node.events = Set.of("close");
        return new UiExecution(this, node.id, namespace);
      }
    }
    return parent;
  }

  public synchronized Object widget(
      UiExecution cursor,
      String type,
      String tag,
      Map<String, Object> options,
      Object value,
      RuntimeAgent.Scope emitter) {
    if (closed || !nodes.containsKey(cursor.mount())) {
      if (emitter != null) emitter.done();
      return null;
    }
    var node = add(cursor, type, tag, options);
    if (Set.of("text", "button", "checkbox", "input", "number", "select").contains(type)) {
      node.properties.putIfAbsent("label", value == null ? "" : value);
    }
    if (Set.of("checkbox", "input", "number", "select").contains(type)) {
      node.properties.putIfAbsent(
          "value", type.equals("checkbox") ? false : type.equals("number") ? 0 : "");
    }
    if (type.equals("progress")) node.properties.put("value", value);
    if (emitter != null) {
      node.events = Set.of(type.equals("button") ? "click" : "change");
      emitters.put(node.id, emitter);
    }
    publish();
    return node.id;
  }

  public synchronized Object control(UiExecution cursor, String command, String tag, Object value) {
    // Delayed reactions from a replaced mount must not address tags in its successor.
    if (closed || !nodes.containsKey(cursor.mount())) return null;
    var id = tags.get(tag.startsWith("/") ? tag.substring(1) : cursor.namespace() + "/" + tag);
    if (id == null) throw new IllegalArgumentException("Unknown UI tag " + tag);
    var node = nodes.get(id);
    switch (command) {
      case "get" -> {
        return node.properties.get("value");
      }
      case "set" ->
          node.properties.put(
              node.type.equals("text") ? "label" : "value", UiNode.immutableValue(value));
      case "show", "hide", "close" -> node.properties.put("hidden", !command.equals("show"));
      case "enable", "disable" -> node.properties.put("disabled", command.equals("disable"));
      case "remove" -> remove(node);
      case "style" -> {
        if (!(value instanceof Map<?, ?> map))
          throw new IllegalArgumentException("UI style requires a map");
        node.properties.putAll(UiLayout.normalize(map));
      }
      default -> throw new IllegalArgumentException("Unknown UI control " + command);
    }
    publish();
    return null;
  }

  /** Validate against server-owned node types, independently of client rendering. */
  public synchronized boolean validInput(UiEvent event) {
    var node = nodes.get(event.nodeId());
    if (closed || node == null || !node.events.contains(event.name())) return false;
    // Modal interaction is enforced on the server as well as by the renderer.
    var modal =
        nodes.values().stream()
            .filter(
                candidate ->
                    candidate.type.equals("modal")
                        && !Boolean.TRUE.equals(candidate.properties.get("hidden")))
            .reduce((first, last) -> last)
            .orElse(null);
    if (modal != null && !contains(modal, node.id)) return false;
    Object value = event.value();
    return switch (node.type) {
      case "dialog", "modal", "drawer" ->
          event.name().equals("close")
              && value == null
              && !Boolean.FALSE.equals(node.properties.get("dismissible"));
      case "button" -> value == null;
      case "checkbox" -> value instanceof Boolean;
      case "number" ->
          value instanceof Number n
              && Double.isFinite(n.doubleValue())
              && (!(node.properties.get("min") instanceof Number min)
                  || n.doubleValue() >= min.doubleValue())
              && (!(node.properties.get("max") instanceof Number max)
                  || n.doubleValue() <= max.doubleValue());
      case "input" -> value instanceof String s && s.length() <= 16384;
      case "select" ->
          value instanceof String s
              && node.properties.get("options") instanceof List<?> list
              && list.contains(s);
      default -> false;
    };
  }

  private void enqueue(UiEvent event) {
    events.execute(() -> receive(event));
  }

  /** Complete after preceding accepted inputs have updated state and scheduled their reactions. */
  public CompletableFuture<Void> afterInput() {
    var barrier = new CompletableFuture<Void>();
    events.execute(() -> barrier.complete(null));
    return barrier;
  }

  private synchronized void receive(UiEvent event) {
    if (!validInput(event)) return;
    var node = nodes.get(event.nodeId());
    if (Set.of("dialog", "modal", "drawer").contains(node.type)) {
      node.properties.put("hidden", true);
      publish();
      return;
    }
    var emitter = emitters.get(node.id);
    if (emitter == null || emitter.isDone()) return;
    if (!node.type.equals("button")) node.properties.put("value", event.value());
    publish();
    var payload = new LinkedHashMap<String, Object>();
    payload.put("event", event.name());
    payload.put("value", event.value());
    reactions.execute(
        () -> {
          if (!emitter.isDone()) emitter.doFire(Collections.unmodifiableMap(payload));
        });
  }

  private Node add(UiExecution parent, String type, String tag, Map<?, ?> properties) {
    if (closed) throw new IllegalStateException("Application UI stopped");
    if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported UI type " + type);
    var mount = nodes.get(parent.mount());
    if (mount == null) throw new IllegalStateException("UI mount has been removed");
    String key = tag == null ? null : parent.namespace() + "/" + tag;
    if (key != null && tags.containsKey(key))
      throw new IllegalArgumentException("Duplicate UI tag " + tag);
    var node = new Node("n" + ids.incrementAndGet(), type);
    node.properties.putAll(UiLayout.normalize(properties));
    boolean row = "row".equals(mount.properties.get("layout"));
    if (Boolean.TRUE.equals(node.properties.get("fill"))
        || Boolean.TRUE.equals(node.properties.get(row ? "hfill" : "vfill")))
      node.properties.putIfAbsent("grow", 1);
    if (Boolean.TRUE.equals(node.properties.get("fill"))
        || Boolean.TRUE.equals(node.properties.get(row ? "vfill" : "hfill")))
      node.properties.putIfAbsent("self", "stretch");
    nodes.put(node.id, node);
    if (key != null) tags.put(key, node.id);
    mount.children.add(node);
    return node;
  }

  private boolean contains(Node parent, String id) {
    if (parent.id.equals(id)) return true;
    return parent.children.stream().anyMatch(child -> contains(child, id));
  }

  private void remove(Node node) {
    for (var child : List.copyOf(node.children)) remove(child);
    nodes.values().forEach(parent -> parent.children.remove(node));
    nodes.remove(node.id);
    tags.values().removeIf(node.id::equals);
    var emitter = emitters.remove(node.id);
    if (emitter != null) emitter.done();
  }

  private void publish() {
    if (!closed) session.present(root.freeze());
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    events.shutdownNow();
    reactions.shutdownNow();
    emitters.values().forEach(RuntimeAgent.Scope::done);
    emitters.clear();
    nodes.clear();
    tags.clear();
    session.close();
  }
}
