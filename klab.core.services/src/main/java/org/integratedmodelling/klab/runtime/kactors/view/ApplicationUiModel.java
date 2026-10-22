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
  private UiToolkitDescriptor capabilities;

  public synchronized void capabilities(UiToolkitDescriptor descriptor) {
    descriptor.validate(root.freeze());
    capabilities = descriptor;
  }

  private static final class Node {
    final String id, type;
    String action;
    boolean component, pending;
    RuntimeAgent.Scope lifetime;
    final Map<String, Object> properties = new LinkedHashMap<>();
    final List<Node> children = new ArrayList<>();
    Set<String> events = Set.of();

    Node(String id, String type) {
      this.id = id;
      this.type = type;
    }

    UiNode freeze() {
      return new UiNode(
          id,
          type,
          properties,
          events,
          children.stream().filter(n -> !n.pending).map(Node::freeze).toList());
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
    var options = new LinkedHashMap<>(UiLayout.normalize(metadata));
    options.putIfAbsent("layout", "column");
    var node = add(parent, "container", tag, options);
    publish();
    return new UiExecution(this, node.id, parent.namespace());
  }

  public synchronized UiExecution action(
      UiExecution parent, String name, List<Annotation> annotations) {
    if (closed || !nodes.containsKey(parent.mount())) return parent;
    var current = nodes.get(parent.mount());
    if (current.component && name.equals(current.action)) return parent;
    if (annotations.stream().filter(annotation -> SURFACES.contains(annotation.getName())).count()
        > 1) throw new IllegalArgumentException("An action can declare only one UI surface");
    for (var annotation : annotations) {
      String surface = annotation.getName();
      if (SURFACES.contains(surface)) {
        String localId = Objects.toString(annotation.get("id"), name);
        if (!localId.matches("[a-z][a-z0-9_-]*") || localId.equals("main"))
          throw new IllegalArgumentException("Invalid or reserved UI mount ID " + localId);
        boolean inline = Set.of("panel", "window", "toolbar", "menu").contains(surface);
        String namespace =
            inline && !parent.namespace().equals("main")
                ? parent.namespace() + "/" + localId
                : localId;
        String alias = inline ? parent.namespace() + "/" + localId : "main/" + localId;
        String key = namespace + "/surface";
        var old = tags.get(key);
        if (old != null && !name.equals(nodes.get(old).action))
          throw new IllegalArgumentException("Duplicate UI mount ID " + namespace);
        var publicTag = tags.get(alias);
        if (publicTag != null && !publicTag.equals(old))
          throw new IllegalArgumentException("Duplicate application tag " + namespace);
        if (old != null) remove(nodes.get(old));
        var options = surfaceOptions(annotation);
        var node =
            add(
                new UiExecution(this, inline ? parent.mount() : root.id, namespace),
                surface,
                "surface",
                options);
        node.action = name;
        tags.put(alias, node.id);
        if (Set.of("dialog", "modal", "drawer").contains(surface)) node.events = Set.of("close");
        publish();
        return new UiExecution(this, node.id, namespace);
      }
    }
    return parent;
  }

  /** Build an isolated, initially unpublished component. Completion commits the entire subtree. */
  public synchronized UiExecution mount(
      UiExecution caller,
      String target,
      String instance,
      String action,
      List<Annotation> annotations) {
    if (closed || !nodes.containsKey(caller.mount()))
      throw new IllegalStateException("UI caller disposed");
    if (instance == null || !instance.matches("[a-z][a-z0-9_-]*"))
      throw new IllegalArgumentException("Invalid component instance tag " + instance);
    var id =
        tags.get(target.startsWith("/") ? target.substring(1) : caller.namespace() + "/" + target);
    var container = nodes.get(id);
    if (container == null
        || !Set.of(
                "container",
                "panel",
                "window",
                "toolbar",
                "menu",
                "drawer",
                "header",
                "footer",
                "dialog",
                "modal")
            .contains(container.type))
      throw new IllegalArgumentException("UI mount target must be a tagged container: " + target);
    var surfaces = annotations.stream().filter(a -> SURFACES.contains(a.getName())).toList();
    if (surfaces.size() != 1 || !Set.of("panel", "window").contains(surfaces.getFirst().getName()))
      throw new IllegalArgumentException("Mounted actions must declare @panel or @window");
    String namespace = caller.namespace() + "/" + instance;
    if (tags.containsKey(namespace + "/surface"))
      throw new IllegalArgumentException("Duplicate component instance " + instance);
    var node =
        add(
            new UiExecution(this, id, caller.namespace()),
            surfaces.getFirst().getName(),
            instance,
            surfaceOptions(surfaces.getFirst()));
    node.component = true;
    node.pending = true;
    node.action = action;
    tags.put(namespace + "/surface", node.id);
    return new UiExecution(this, node.id, namespace);
  }

  public synchronized void completeMount(UiExecution component, boolean success) {
    var node = nodes.get(component.mount());
    if (node == null) return; // The target may have been removed during asynchronous construction.
    if (success) node.pending = false;
    else remove(node);
    publish();
  }

  public synchronized void bindMount(UiExecution component, RuntimeAgent.Scope lifetime) {
    var node = nodes.get(component.mount());
    if (node == null) lifetime.done();
    else node.lifetime = lifetime;
  }

  private Map<String, Object> surfaceOptions(Annotation annotation) {
    var options = new LinkedHashMap<String, Object>();
    annotation.forEach(
        (key, value) ->
            options.put(
                key,
                Set.of("title", "label", "description", "tooltip").contains(key)
                        && value instanceof String text
                        && text.matches("#[A-Z][A-Z0-9_]*")
                    ? presentation.localize(text)
                    : value));
    return options;
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
    var normalized = UiLayout.normalize(options);
    UiWidgetMetadata.validate(type, normalized);
    if (type.equals("progress") && !UiWidgetMetadata.value(type, normalized, value))
      throw new IllegalArgumentException("Progress must be in [0,1]");
    var node = add(cursor, type, tag, normalized);
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
      case "set" -> {
        if (!UiWidgetMetadata.configuredValue(node.type, node.properties, value))
          throw new IllegalArgumentException("Invalid UI value for " + tag);
        node.properties.put(
            node.type.equals("text") ? "label" : "value", UiNode.immutableValue(value));
      }
      case "show", "hide", "close" -> node.properties.put("hidden", !command.equals("show"));
      case "enable", "disable" -> node.properties.put("disabled", command.equals("disable"));
      case "remove" -> remove(node);
      case "clear" -> {
        if (!Set.of(
                "container",
                "panel",
                "window",
                "toolbar",
                "menu",
                "drawer",
                "header",
                "footer",
                "dialog",
                "modal")
            .contains(node.type))
          throw new IllegalArgumentException("UI clear requires a container");
        for (var child : List.copyOf(node.children)) remove(child);
      }
      case "style" -> {
        if (!(value instanceof Map<?, ?> map))
          throw new IllegalArgumentException("UI style requires a map");
        var candidate = new LinkedHashMap<>(node.properties);
        candidate.putAll(UiLayout.normalize(map));
        UiWidgetMetadata.validate(node.type, candidate);
        if (capabilities != null)
          capabilities.validate(new UiNode(node.id, node.type, candidate, node.events, List.of()));
        node.properties.clear();
        node.properties.putAll(candidate);
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
            .filter(candidate -> candidate.type.equals("modal") && visible(root, candidate.id))
            .reduce((first, last) -> last)
            .orElse(null);
    if (modal != null && !contains(modal, node.id)) return false;
    Object value = event.value();
    if (Boolean.TRUE.equals(node.properties.get("readonly"))
        || Boolean.TRUE.equals(node.properties.get("loading"))) return false;
    return switch (node.type) {
      case "dialog", "modal", "drawer" ->
          event.name().equals("close")
              && value == null
              && !Boolean.FALSE.equals(node.properties.get("dismissible"));
      case "button" -> value == null;
      case "checkbox", "number", "input" ->
          UiWidgetMetadata.value(node.type, node.properties, value);
      case "select" ->
          value instanceof String s
              && node.properties.get("options") instanceof List<?> list
              && list.contains(s)
              && UiWidgetMetadata.value(node.type, node.properties, value);
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
    var normalized = UiLayout.normalize(properties);
    if (capabilities != null)
      capabilities.validate(new UiNode("candidate", type, normalized, Set.of(), List.of()));
    var mount = nodes.get(parent.mount());
    if (mount == null) throw new IllegalStateException("UI mount has been removed");
    String key = tag == null ? null : parent.namespace() + "/" + tag;
    if (key != null && tags.containsKey(key))
      throw new IllegalArgumentException("Duplicate UI tag " + tag);
    var node = new Node("n" + ids.incrementAndGet(), type);
    node.properties.putAll(normalized);
    if (key != null) node.properties.put("tag", key);
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

  private boolean visible(Node node, String id) {
    if (node.pending
        || Boolean.TRUE.equals(node.properties.get("hidden"))
        || Boolean.TRUE.equals(node.properties.get("invisible"))) return false;
    return node.id.equals(id) || node.children.stream().anyMatch(child -> visible(child, id));
  }

  private void remove(Node node) {
    for (var child : List.copyOf(node.children)) remove(child);
    nodes.values().forEach(parent -> parent.children.remove(node));
    nodes.remove(node.id);
    tags.values().removeIf(node.id::equals);
    var emitter = emitters.remove(node.id);
    if (emitter != null) emitter.done();
    if (node.lifetime != null) node.lifetime.done();
  }

  private void publish() {
    if (!closed) {
      var tree = root.freeze();
      var previous = session.snapshot();
      if (previous == null || !previous.root().equals(tree)) session.present(tree);
    }
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    events.shutdownNow();
    reactions.shutdownNow();
    emitters.values().forEach(RuntimeAgent.Scope::done);
    nodes.values().stream()
        .map(n -> n.lifetime)
        .filter(Objects::nonNull)
        .forEach(RuntimeAgent.Scope::done);
    emitters.clear();
    nodes.clear();
    tags.clear();
    session.close();
  }
}
