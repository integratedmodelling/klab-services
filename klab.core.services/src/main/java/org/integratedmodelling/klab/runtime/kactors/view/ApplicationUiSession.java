package org.integratedmodelling.klab.runtime.kactors.view;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.view.application.UiEvent;
import org.integratedmodelling.klab.api.view.application.UiNode;
import org.integratedmodelling.klab.api.view.application.UiSnapshot;
import org.integratedmodelling.klab.api.view.application.UiToolkit;

/**
 * In-process UI testing ground. A caller must authorize and bind the owning agent before creating
 * this session. There is deliberately no public network endpoint or AMQP dependency here. Mutations
 * and input are serialized; callbacks should enqueue actor work without blocking.
 */
public final class ApplicationUiSession implements AutoCloseable {
  private Set<String> supportedTypes = Set.of();
  private UiToolkit.View view;
  private Consumer<UiEvent> input;
  private Map<String, UiNode> nodes = Map.of();
  private UiSnapshot snapshot;
  private long lastSequence;
  private long attachment;
  private boolean closed;

  public ApplicationUiSession(UiToolkit toolkit, Consumer<UiEvent> input) {
    this(input);
    attach(toolkit);
  }

  /** Create an application UI state owner without a renderer. */
  public ApplicationUiSession(Consumer<UiEvent> input) {
    this.input = Objects.requireNonNull(input);
  }

  /**
   * Attach a renderer and replay the current state without executing the application again. Only
   * one renderer is supported in this initial scaffold. Detach it before replacing it.
   */
  public synchronized void attach(UiToolkit toolkit) {
    ensureOpen();
    Objects.requireNonNull(toolkit);
    if (view != null) throw new IllegalStateException("UI already has a renderer");
    var supported = Set.copyOf(toolkit.supportedTypes());
    if (snapshot != null) validateTypes(snapshot.root(), supported);
    long token = ++attachment;
    var candidate = Objects.requireNonNull(toolkit.open(event -> dispatchAttached(token, event)));
    try {
      if (snapshot != null) candidate.render(snapshot);
    } catch (RuntimeException | Error failure) {
      try {
        candidate.close();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
    supportedTypes = supported;
    view = candidate;
  }

  /** Release the renderer, keeping the tree, sequence and application event handler alive. */
  public synchronized void detach() {
    ++attachment;
    var previous = view;
    view = null;
    supportedTypes = Set.of();
    if (previous != null) previous.close();
  }

  /** A reconnect handshake must return this cursor alongside the latest snapshot. */
  public synchronized long lastAcceptedSequence() {
    return lastSequence;
  }

  /** Atomically capture the view and input cursor for a reconnect handshake. */
  public synchronized ResumeState resumeState() {
    ensureOpen();
    return new ResumeState(snapshot, lastSequence);
  }

  /** Snapshot is null if the application has not presented its first tree yet. */
  public record ResumeState(UiSnapshot snapshot, long lastAcceptedSequence) {}

  private synchronized void dispatchAttached(long token, UiEvent event) {
    if (token == attachment) dispatch(event);
  }

  /** Replace the entire tree atomically from the event dispatcher's perspective. */
  public synchronized UiSnapshot present(UiNode root) {
    ensureOpen();
    var index = new HashMap<String, UiNode>();
    index(Objects.requireNonNull(root), index);
    if (view != null) validateTypes(root, supportedTypes);
    var replacement =
        new UiSnapshot(
            UiSnapshot.PROTOCOL_VERSION,
            snapshot == null ? 1 : Math.incrementExact(snapshot.revision()),
            root);
    nodes = Map.copyOf(index);
    snapshot = replacement;
    try {
      if (view != null) view.render(replacement);
    } catch (RuntimeException | Error failure) {
      try {
        detach();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      throw failure;
    }
    return replacement;
  }

  /**
   * Accept only fresh, declared input from an interactive node in the current tree. Hidden or
   * disabled ancestors also block input. False means the event was rejected without delivery.
   * Sequence is consumed before delivery: callback failures must not repeat side effects.
   */
  public synchronized boolean dispatch(UiEvent event) {
    Objects.requireNonNull(event);
    if (closed
        || view == null
        || snapshot == null
        || event.revision() != snapshot.revision()
        || event.sequence() <= lastSequence) return false;
    var node = nodes.get(event.nodeId());
    if (node == null
        || !node.events().contains(event.name())
        || !interactive(snapshot.root(), event.nodeId(), true)) return false;
    lastSequence = event.sequence();
    input.accept(event);
    return true;
  }

  /** Latest immutable snapshot, or null before the first presentation or after closing. */
  public synchronized UiSnapshot snapshot() {
    return snapshot;
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    nodes = Map.of();
    snapshot = null;
    input = null;
    detach();
  }

  private void index(UiNode node, Map<String, UiNode> index) {
    if (index.putIfAbsent(node.id(), node) != null) {
      throw new IllegalArgumentException("Duplicate UI node ID " + node.id());
    }
    for (String state : Set.of("hidden", "disabled", "invisible")) {
      if (node.properties().containsKey(state)
          && !(node.properties().get(state) instanceof Boolean)) {
        throw new IllegalArgumentException("UI state must be boolean: " + state);
      }
    }
    node.children().forEach(child -> index(child, index));
  }

  private void validateTypes(UiNode node, Set<String> supported) {
    if (!supported.contains(node.type())) {
      throw new IllegalArgumentException("Toolkit does not support UI type " + node.type());
    }
    node.children().forEach(child -> validateTypes(child, supported));
  }

  private boolean interactive(UiNode node, String id, boolean enabled) {
    enabled =
        enabled
            && !Boolean.TRUE.equals(node.properties().get("disabled"))
            && !Boolean.TRUE.equals(node.properties().get("hidden"))
            && !Boolean.TRUE.equals(node.properties().get("invisible"));
    if (node.id().equals(id)) return enabled;
    for (var child : node.children()) {
      if (interactive(child, id, enabled)) return true;
    }
    return false;
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("UI session is closed");
  }
}
