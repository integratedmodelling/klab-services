package org.integratedmodelling.klab.runtime.kactors.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.integratedmodelling.klab.api.view.application.*;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.junit.jupiter.api.Test;

class UiWidgetHandleTest {
  private UiNode await(ApplicationUiModel model, Predicate<UiNode> condition) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    UiNode found;
    do {
      found = ApplicationUiRuntimeTest.find(model.session().snapshot().root(), condition);
      if (found != null) return found;
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    fail("Widget condition was not reached: " + model.session().snapshot().root() + "\n" + org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler.getGeneratedSource("tests.ui.handles")); return null;
  }

  private static final class HandleToolkit implements UiToolkit, UiToolkit.View {
    public String id() { return "handles-test"; }
    public Set<String> supportedTypes() { return ApplicationUiModel.TYPES; }
    public View open(java.util.function.Consumer<UiEvent> consumer) { return this; }
    public void render(UiSnapshot snapshot) {}
    public void close() {}
  }

  private void attach(ApplicationUiModel model) {
    model.session().attach(new HandleToolkit());
  }

  @Test
  void assignedButtonReceivesMessagesFromItsOwnReactionAndRetainsState() throws Exception {
    var app = ApplicationUiRuntimeTest.start(ApplicationUiRuntimeTest.parse("""
        web app tests.ui.handles
          "Widget handles"
          version 1.0
          using core.ui as ui
        action main:
          label <- ui.text("Ready")
          btn <- ui.button("Click me")
          btn.events():
            event -> (
              btn.settext("I'm disabled now")
              btn.enabled(false)
              label.settext("Clicked")
            )
          field <- ui.input("Name" :value "Ada")
          field.events():
            event -> label.settext([event.value])
          field.set("Grace")
          ui.text("Constructed") #constructed
        """));
    try {
      var model = app.ui();
      await(model, n -> "main/constructed".equals(n.properties().get("tag")));
      var button = await(model, n -> n.type().equals("button"));
      assertEquals(4, model.session().snapshot().root().children().size(), "handles and subscriptions must not create widgets");
      assertEquals("Ready", await(model, n -> n.type().equals("text")).properties().get("label"), "set must not emit input");
      assertEquals("Grace", await(model, n -> n.type().equals("input")).properties().get("value"));
      attach(model);
      assertTrue(model.session().dispatch(new UiEvent(1, model.session().snapshot().revision(), button.id(), "click", null)));
      var changed = await(model, n -> n.id().equals(button.id()) && Boolean.TRUE.equals(n.properties().get("disabled")));
      assertEquals("I'm disabled now", changed.properties().get("label"));
      await(model, n -> n.type().equals("text") && "Clicked".equals(n.properties().get("label")));
      assertFalse(model.validInput(new UiEvent(2, model.session().snapshot().revision(), button.id(), "click", null)));
      var retained = model.session().resumeState();
      model.session().detach(); attach(model);
      assertEquals(retained, model.session().resumeState());
      var field = await(model, n -> n.type().equals("input"));
      model.session().dispatch(new UiEvent(2, model.session().snapshot().revision(), field.id(), "change", "User value"));
      await(model, n -> n.type().equals("text") && "User value".equals(n.properties().get("label")));
    } finally { app.stop(); }
  }

  @Test
  void emitterCompletionDisposesTheWidgetButSubscriberCompletionDoesNot() throws Exception {
    var app = ApplicationUiRuntimeTest.start(ApplicationUiRuntimeTest.parse("""
        web app tests.ui.handlelifetime
          "Handle lifetimes"
          version 1.0
          using core.ui as ui
        action main:
          ui.text("Ready") #ready
        """));
    try {
      await(app.ui(), n -> "main/ready".equals(n.properties().get("tag")));
      var root = (AgentScope) app.rootScope();
      var construction = root.withId(900001);
      var button = CoreActorLibrary.Ui.button(construction, Metadata.create(), "Transient");
      var first = root.withId(900002);
      button.events(first);
      first.done();
      assertTrue(button.exists());
      var second = root.withId(900003);
      button.events(second);
      construction.done();
      assertFalse(button.exists());
      assertTrue(second.isDone());
      assertThrows(IllegalStateException.class, () -> button.enabled(false));
      assertNull(ApplicationUiRuntimeTest.find(app.ui().session().snapshot().root(), n -> n.type().equals("button")));
      var text = CoreActorLibrary.Ui.reference(root, "ready");
      assertTrue(assertThrows(IllegalArgumentException.class, () -> text.events(root.withId(900004)))
          .getMessage().contains("does not emit"));
    } finally { app.stop(); }
  }

  @Test
  void fluentFileReferencesAreBoundedIsolatedAndExpireWithUploadedBytes() {
    try (var model = new ApplicationUiModel(); var foreign = new ApplicationUiModel()) {
      var scope = mock(AgentScope.class); when(scope.ui()).thenReturn(model.root());
      var upload = CoreActorLibrary.Ui.upload(scope, Metadata.create("tag", "upload"), "CSV");
      var download = CoreActorLibrary.Ui.download(scope, Metadata.create(), "Copy");
      assertThrows(IllegalStateException.class, upload::file);
      String id = (String) model.control(model.root(), "reference", "upload", null);
      var map = model.createFile(id, "data.csv", "text/csv", new byte[] {65, 66});
      upload.set(map);
      var file = upload.file();
      assertEquals("data.csv", file.name()); assertEquals("text/csv", file.type());
      assertEquals(2, file.size()); assertEquals("AB", file.text());
      byte[] copy = file.bytes(); copy[0] = 90; assertEquals("AB", file.text());
      download.source(upload); assertEquals(map, download.get());
      download.source(file); assertEquals(map, download.get());
      var generated = download.content("report.txt", "Generated", "text/plain");
      assertEquals("Generated", generated.text()); assertEquals(generated.metadata(), download.get());
      var otherScope = mock(AgentScope.class); when(otherScope.ui()).thenReturn(foreign.root());
      var other = CoreActorLibrary.Ui.download(otherScope, Metadata.create(), "Other");
      var otherFile = other.content("other.txt", "Other", "text/plain");
      assertThrows(IllegalArgumentException.class, () -> download.source(otherFile));
      assertThrows(IllegalArgumentException.class, () -> download.source(other));
      assertThrows(IllegalArgumentException.class, () -> download.source("C:/secret.txt"));
      assertThrows(IllegalArgumentException.class, () -> upload.content("bad", "x", "text/plain"));
      assertThrows(IllegalArgumentException.class, () -> download.options(List.of("a")));
      assertThrows(IllegalArgumentException.class, () -> download.items(List.of()));
      var malformed = model.createFile(id, "bad.txt", "text/plain", new byte[] {(byte) 0xc3, 0x28});
      upload.set(malformed);
      assertThrows(IllegalArgumentException.class, () -> upload.file().text());
      upload.remove();
      assertFalse(file.exists()); assertThrows(IllegalStateException.class, file::text);
      assertThrows(IllegalStateException.class, () -> download.source(file));
      model.close(); assertFalse(generated.exists()); assertThrows(IllegalStateException.class, generated::metadata);
    }
  }

  @Test
  void documentedFluentApplicationUsesReferencesWithoutUnpackingEventMaps() throws Exception {
    var source = java.nio.file.Files.readString(java.nio.file.Path.of("../docs/examples/ui/widget-handles.kactor"));
    var app = ApplicationUiRuntimeTest.start(ApplicationUiRuntimeTest.parse(source));
    try {
      var model = app.ui();
      await(model, n -> "main/constructed".equals(n.properties().get("tag")));
      var choice = await(model, n -> n.type().equals("select"));
      assertEquals(List.of("eu", "as"), choice.properties().get("options"));
      assertEquals("as", choice.properties().get("value"));
      assertEquals(true, choice.properties().get("readonly"));
      var download = await(model, n -> "Download uploaded file".equals(n.properties().get("label")));
      var report = await(model, n -> "Generated report".equals(n.properties().get("label")));
      assertEquals("report.csv", ((Map<?, ?>) report.properties().get("value")).get("name"));
      var upload = await(model, n -> n.type().equals("upload"));
      var file = model.createFile(upload.id(), "test.csv", "text/csv", new byte[] {65, 66});
      attach(model);
      model.session().dispatch(new UiEvent(1, model.session().snapshot().revision(), upload.id(), "change", file));
      await(model, n -> n.id().equals(download.id()) && file.equals(n.properties().get("value")));
      await(model, n -> "main/status".equals(n.properties().get("tag")) && "AB".equals(n.properties().get("label")));
    } finally { app.stop(); }
  }

  @Test
  void tagReferencesDoNotRetargetReusedTagsAndUpdatesValidateValues() {
    try (var model = new ApplicationUiModel()) {
      var scope = mock(AgentScope.class);
      when(scope.ui()).thenReturn(model.root());
      var old = CoreActorLibrary.Ui.button(scope, Metadata.create("tag", "run"), "Run");
      assertTrue(old.exists());
      old.settext("Changed"); old.enabled(false); old.visible(false);
      assertEquals("Changed", old.gettext());
      var reference = CoreActorLibrary.Ui.reference(scope, "run");
      reference.show(); reference.enable();
      old.remove();
      var replacement = CoreActorLibrary.Ui.button(scope, Metadata.create("tag", "run"), "Replacement");
      assertTrue(assertThrows(IllegalStateException.class, () -> old.settext("Stale")).getMessage().contains("removed"));
      assertThrows(IllegalStateException.class, old::disable);
      assertThrows(IllegalStateException.class, old::remove);
      assertFalse(old.exists()); assertThrows(IllegalStateException.class, old::get); assertTrue(replacement.exists());
      assertEquals("Replacement", replacement.gettext());
      var number = CoreActorLibrary.Ui.number(scope, Metadata.create("min", 0, "max", 10), "Count");
      assertThrows(IllegalArgumentException.class, () -> number.set(11));
      assertThrows(IllegalArgumentException.class, () -> replacement.style(Map.of("onclick", "evil()")));
      when(scope.isDone()).thenReturn(true);
      assertFalse(number.exists());
      assertTrue(assertThrows(IllegalStateException.class, () -> number.set(1)).getMessage().contains("completed"));
      model.close(); assertFalse(replacement.exists());
      assertTrue(assertThrows(IllegalStateException.class, () -> replacement.settext("Closed")).getMessage().contains("stopped"));
    }
  }
}
