package org.integratedmodelling.klab.runtime.kactors.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.integratedmodelling.klab.api.view.application.UiEvent;
import org.integratedmodelling.klab.api.view.application.UiNode;
import org.integratedmodelling.klab.api.view.application.UiSnapshot;
import org.integratedmodelling.klab.api.view.application.UiToolkit;
import org.junit.jupiter.api.Test;

class ApplicationUiSessionTest {
  private UiNode button() {
    return new UiNode("run", "button", Map.of("label", "Run"), Set.of("click"), List.of());
  }

  private UiEvent click(long sequence, long revision) {
    return new UiEvent(sequence, revision, "run", "click", null);
  }

  @Test
  void serverAcceptedFieldValueSurvivesRendererRebuild() {
    var owner = new AtomicReference<ApplicationUiSession>();
    var toolkit = new RecordingToolkit();
    try (var session =
        new ApplicationUiSession(
            toolkit,
            event ->
                owner
                    .get()
                    .present(
                        new UiNode(
                            "name",
                            "input",
                            Map.of("value", event.value()),
                            Set.of("change"),
                            List.of())))) {
      owner.set(session);
      session.present(
          new UiNode("name", "input", Map.of("value", ""), Set.of("change"), List.of()));
      toolkit.input.accept(new UiEvent(1, 1, "name", "change", "Alice"));
      session.detach();
      var rebuilt = new RecordingToolkit();
      session.attach(rebuilt);
      var resume = session.resumeState();
      assertEquals(1, resume.lastAcceptedSequence());
      assertEquals(2, resume.snapshot().revision());
      assertEquals("Alice", rebuilt.snapshots.getFirst().root().properties().get("value"));
      assertEquals(resume.snapshot(), rebuilt.snapshots.getFirst());
    }
  }

  @Test
  void rejectsStaleDuplicateUndeclaredAndUnknownInput() {
    var delivered = new ArrayList<UiEvent>();
    var toolkit = new RecordingToolkit();
    try (var session = new ApplicationUiSession(toolkit, delivered::add)) {
      session.present(button());
      assertFalse(session.dispatch(new UiEvent(1, 1, "missing", "click", null)));
      assertFalse(session.dispatch(new UiEvent(1, 1, "run", "change", null)));
      assertFalse(session.dispatch(click(1, 2)));
      assertTrue(session.dispatch(click(1, 1)));
      assertFalse(session.dispatch(click(1, 1)));
      session.present(button());
      assertFalse(session.dispatch(click(2, 1)));
      assertTrue(session.dispatch(click(2, 2)));
      assertEquals(2, delivered.size());
    }
  }

  @Test
  void reconnectReplaysServerStateAndRejectsObsoleteRenderer() {
    var delivered = new ArrayList<UiEvent>();
    var first = new RecordingToolkit();
    try (var session = new ApplicationUiSession(first, delivered::add)) {
      session.present(button());
      first.input.accept(click(1, 1));
      session.detach();
      assertEquals(1, first.closes);
      session.present(
          new UiNode("run", "button", Map.of("label", "Continue"), Set.of("click"), List.of()));
      assertFalse(session.dispatch(click(2, 2)));
      var second = new RecordingToolkit();
      session.attach(second);
      assertEquals(session.snapshot(), second.snapshots.getFirst());
      assertEquals("Continue", second.snapshots.getFirst().root().properties().get("label"));
      assertEquals(1, session.lastAcceptedSequence());
      first.input.accept(click(2, 2));
      second.input.accept(click(1, 2));
      assertEquals(1, delivered.size());
      second.input.accept(click(2, 2));
      assertEquals(2, delivered.size());
    }
  }

  @Test
  void hiddenOrDisabledAncestorBlocksInput() {
    for (String state : List.of("hidden", "disabled")) {
      try (var session = new ApplicationUiSession(new RecordingToolkit(), event -> fail())) {
        session.present(
            new UiNode("root", "container", Map.of(state, true), Set.of(), List.of(button())));
        assertFalse(session.dispatch(click(1, 1)));
      }
    }
  }

  @Test
  void invalidTreeDoesNotReplacePreviousSnapshot() {
    try (var session = new ApplicationUiSession(new RecordingToolkit(), event -> {})) {
      var previous = session.present(button());
      assertThrows(
          IllegalArgumentException.class,
          () ->
              session.present(
                  new UiNode(
                      "root", "container", Map.of(), Set.of(), List.of(button(), button()))));
      assertThrows(
          IllegalArgumentException.class,
          () -> session.present(new UiNode("run", "unsupported", Map.of(), Set.of(), List.of())));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              session.present(
                  new UiNode("run", "button", Map.of("hidden", "false"), Set.of(), List.of())));
      assertEquals(previous, session.snapshot());
    }
  }

  @Test
  void stateIsDeeplyCopiedAndOnlyJsonValuesAreAccepted() {
    var values = new ArrayList<Object>();
    values.add(null);
    values.add("initial");
    var properties = new HashMap<String, Object>();
    properties.put("values", values);
    var node = new UiNode("run", "button", properties, Set.of(), List.of());
    values.clear();
    properties.clear();
    assertEquals(2, ((List<?>) node.properties().get("values")).size());
    assertThrows(UnsupportedOperationException.class, () -> node.properties().clear());
    assertThrows(IllegalArgumentException.class, () -> UiNode.immutableValue(new Object()));
    assertThrows(IllegalArgumentException.class, () -> UiNode.immutableValue(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> UiNode.immutableValue(Map.of(1, "bad")));
  }

  @Test
  void callbackFailureDoesNotRedeliverTheSameInput() {
    try (var session =
        new ApplicationUiSession(
            new RecordingToolkit(),
            event -> {
              throw new IllegalStateException("actor rejected input");
            })) {
      session.present(button());
      assertThrows(IllegalStateException.class, () -> session.dispatch(click(1, 1)));
      assertFalse(session.dispatch(click(1, 1)));
    }
  }

  @Test
  void renderFailurePreservesLatestServerStateForReconnect() {
    var toolkit = new RecordingToolkit();
    try (var session = new ApplicationUiSession(toolkit, event -> {})) {
      session.present(button());
      toolkit.failRender = true;
      assertThrows(IllegalStateException.class, () -> session.present(button()));
      assertEquals(2, session.snapshot().revision());
      assertEquals(1, toolkit.closes);
      var replacement = new RecordingToolkit();
      session.attach(replacement);
      assertEquals(2, replacement.snapshots.getFirst().revision());
    }
  }

  @Test
  void failedAttachCanBeRetriedAndCloseIsTerminal() {
    var session = new ApplicationUiSession(event -> {});
    session.present(button());
    var failing = new RecordingToolkit();
    failing.failRender = true;
    assertThrows(IllegalStateException.class, () -> session.attach(failing));
    assertEquals(1, failing.closes);
    var replacement = new RecordingToolkit();
    session.attach(replacement);
    assertThrows(IllegalStateException.class, () -> session.attach(new RecordingToolkit()));
    session.close();
    session.close();
    assertNull(session.snapshot());
    assertEquals(1, replacement.closes);
    assertFalse(session.dispatch(click(1, 1)));
    assertThrows(IllegalStateException.class, () -> session.present(button()));
    assertThrows(IllegalStateException.class, () -> session.attach(new RecordingToolkit()));
  }

  private static class RecordingToolkit implements UiToolkit {
    private final List<UiSnapshot> snapshots = new ArrayList<>();
    private Consumer<UiEvent> input;
    private int closes;
    private boolean failRender;

    public String id() {
      return "headless-test";
    }

    public Set<String> supportedTypes() {
      return Set.of("container", "button", "input");
    }

    public View open(Consumer<UiEvent> input) {
      this.input = input;
      return new View() {
        public void render(UiSnapshot snapshot) {
          if (failRender) throw new IllegalStateException("renderer unavailable");
          snapshots.add(snapshot);
        }

        public void close() {
          closes++;
        }
      };
    }
  }
}
