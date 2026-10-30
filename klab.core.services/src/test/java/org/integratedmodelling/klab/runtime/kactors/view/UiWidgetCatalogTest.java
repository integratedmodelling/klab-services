package org.integratedmodelling.klab.runtime.kactors.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.view.application.*;
import org.junit.jupiter.api.Test;

class UiWidgetCatalogTest {
  private UiEvent event(ApplicationUiModel model, String id, String name, Object value) {
    return new UiEvent(model.session().lastAcceptedSequence() + 1, model.session().snapshot().revision(), id, name, value);
  }

  private void attach(ApplicationUiModel model) {
    model.session().attach(new UiToolkit() {
      public String id() { return "catalog-test"; }
      public Set<String> supportedTypes() { return ApplicationUiModel.TYPES; }
      public View open(java.util.function.Consumer<UiEvent> input) {
        return new View() { public void render(UiSnapshot snapshot) {} public void close() {} };
      }
    });
  }

  @Test
  void validatesChoicesRangesDatesCollectionsAndPlayback() {
    assertFalse(UiWidgetMetadata.value("multiselect", Map.of("options", List.of("a", "b")), List.of("a", "a")));
    assertFalse(UiWidgetMetadata.value("multiselect", Map.of("options", List.of("a")), List.of("unknown")));
    assertTrue(UiWidgetMetadata.value("range", Map.of("min", 0, "max", 10), Map.of("min", 2, "max", 7)));
    assertFalse(UiWidgetMetadata.value("range", Map.of("min", 0, "max", 10), Map.of("min", 7, "max", 2)));
    assertFalse(UiWidgetMetadata.value("date", Map.of(), "2025-02-29"));
    assertTrue(UiWidgetMetadata.value("date", Map.of(), "2024-02-29"));
    assertFalse(UiWidgetMetadata.value("time", Map.of(), "24:10"));
    assertFalse(UiWidgetMetadata.value("color", Map.of(), "red;position:fixed"));
    assertFalse(UiWidgetMetadata.value("pagination", Map.of("max", 5), 1.5));
    var table = Map.<String, Object>of("items", List.of(Map.of("id", "a", "label", "A")), "columns", List.of("label"));
    assertTrue(UiWidgetMetadata.eventValue("table", table, "select", "a"));
    assertFalse(UiWidgetMetadata.eventValue("table", table, "select", "forged"));
    assertFalse(UiWidgetMetadata.eventValue("table", table, "sort", Map.of("column", "forged", "descending", true)));
    assertFalse(UiWidgetMetadata.eventValue("table", table, "page", 2));
    assertFalse(UiWidgetMetadata.eventValue("audio", Map.of(), "play", Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> UiWidgetMetadata.validate("tree",
        Map.of("items", List.of(Map.of("id", "a", "label", "A"), Map.of("id", "a", "label", "B")))));
    for (var source : List.of("javascript:alert(1)", "data:text/html,x", "//evil.test/a", "file:///etc/passwd", "https://user:password@example.org/a"))
      assertFalse(UiWidgetMetadata.safeSource(source), source);
  }

  @Test
  void formsSubmitAuthoritativeValuesAndResetAuthoredDefaults() throws Exception {
    try (var model = new ApplicationUiModel()) {
      var form = model.group(model.root(), "details", Map.of("form", true));
      var fieldScope = mock(RuntimeAgent.Scope.class);
      String field = (String) model.widget(form, "input", "name", Map.of("field", "name", "required", true), "Name", fieldScope);
      var submitScope = mock(RuntimeAgent.Scope.class);
      String submit = (String) model.widget(form, "submit", "save", Map.of(), "Save", submitScope);
      var resetScope = mock(RuntimeAgent.Scope.class);
      String reset = (String) model.widget(form, "reset", "reset", Map.of(), "Reset", resetScope);
      attach(model);
      assertFalse(model.validInput(event(model, submit, "submit", null)));
      assertTrue(model.session().dispatch(event(model, field, "change", "Ada")));
      model.afterInput().get(5, TimeUnit.SECONDS);
      assertFalse(model.validInput(event(model, submit, "submit", Map.of("name", "Forged"))));
      assertTrue(model.validInput(event(model, submit, "submit", null)));
      model.session().dispatch(event(model, submit, "submit", null));
      model.afterInput().get(5, TimeUnit.SECONDS);
      verify(submitScope, timeout(5000)).doFire(Map.of("event", "submit", "value", Map.of("name", "Ada")));
      model.session().dispatch(event(model, reset, "reset", null));
      model.afterInput().get(5, TimeUnit.SECONDS);
      assertEquals("", model.control(form, "get", "name", null));
      assertFalse(model.validInput(event(model, submit, "submit", null)));
      assertTrue(model.session().dispatch(event(model, field, "change", "Ada")));
      model.afterInput().get(5, TimeUnit.SECONDS);
      assertTrue(model.validInput(event(model, field, "change", "")));
      model.session().dispatch(event(model, field, "change", ""));
      model.afterInput().get(5, TimeUnit.SECONDS);
      assertFalse(model.validInput(event(model, submit, "submit", null)));
      model.control(model.root(), "disable", "details", null);
      assertFalse(model.validInput(event(model, field, "change", "blocked")));
    }
  }

  @Test
  void filesAreBoundedBoundToUploadsAndDisposedWithTheirNode() {
    try (var model = new ApplicationUiModel(); var other = new ApplicationUiModel()) {
      String upload = (String) model.widget(model.root(), "upload", "upload", Map.of("maxbytes", 8, "accept", ".csv"), "Upload", mock(RuntimeAgent.Scope.class));
      var handle = model.createFile(upload, "data.csv", "text/csv", new byte[] {1, 2});
      assertTrue(model.validInput(event(model, upload, "change", handle)));
      assertThrows(IllegalArgumentException.class, () -> model.createFile(upload, "data.csv", "text/csv", new byte[9]));
      assertThrows(IllegalArgumentException.class, () -> model.createFile(upload, "data.exe", "application/octet-stream", new byte[1]));
      assertThrows(IllegalArgumentException.class, () -> model.createFile(upload, "../data.csv", "text/csv", new byte[1]));
      assertThrows(IllegalArgumentException.class, () -> other.file(handle.get("id").toString()));
      byte[] copy = model.file(handle.get("id").toString()).bytes(); copy[0] = 9;
      assertEquals(1, model.file(handle.get("id").toString()).bytes()[0]);
      assertFalse(model.validInput(event(model, upload, "change", Map.of("id", handle.get("id"), "size", 0))));
      model.control(model.root(), "remove", "upload", null);
      assertThrows(IllegalArgumentException.class, () -> model.file(handle.get("id").toString()));
    }
  }

  @Test
  void realKactorsSourceRegistersExtendedVerbsAndFormOptions() throws Exception {
    var example = java.nio.file.Path.of("../docs/examples/ui/widgets.kactor");
    var app = ApplicationUiRuntimeTest.start(ApplicationUiRuntimeTest.parse(java.nio.file.Files.readString(example)));
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (ApplicationUiRuntimeTest.find(app.ui().session().snapshot().root(), n -> "main/complete".equals(n.properties().get("tag"))) == null
          && System.nanoTime() < deadline) Thread.sleep(10);
      assertNotNull(ApplicationUiRuntimeTest.find(app.ui().session().snapshot().root(), n -> "main/complete".equals(n.properties().get("tag"))));
      for (String type : List.of("iconbutton", "link", "switch", "radio", "multiselect", "textarea",
          "slider", "range", "date", "time", "color", "submit", "reset", "list", "table", "tree",
          "tabs", "stepper", "pagination", "breadcrumbs", "markdown", "image", "icon", "separator",
          "badge", "spinner", "upload", "download", "audio", "video"))
        assertNotNull(ApplicationUiRuntimeTest.find(app.ui().session().snapshot().root(), n -> n.type().equals(type)), type);
      var model = app.ui();
      attach(model);
      var submit = ApplicationUiRuntimeTest.find(model.session().snapshot().root(), n -> n.type().equals("submit"));
      assertTrue(model.validInput(event(model, submit.id(), "submit", null)));
      model.session().dispatch(event(model, submit.id(), "submit", null));
      model.afterInput().get(5, TimeUnit.SECONDS);
      long reactionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!ApplicationUiRuntimeTest.find(model.session().snapshot().root(), n -> "main/summary".equals(n.properties().get("tag")))
          .properties().get("label").toString().contains("Ada") && System.nanoTime() < reactionDeadline) Thread.sleep(10);
      assertTrue(ApplicationUiRuntimeTest.find(model.session().snapshot().root(), n -> "main/summary".equals(n.properties().get("tag")))
          .properties().get("label").toString().contains("Ada"));
      var upload = ApplicationUiRuntimeTest.find(model.session().snapshot().root(), n -> n.type().equals("upload"));
      var handle = model.createFile(upload.id(), "sample.csv", "text/csv", new byte[] {65});
      model.session().dispatch(event(model, upload.id(), "change", handle));
      model.afterInput().get(5, TimeUnit.SECONDS);
      reactionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (model.control(model.root(), "get", "copy", null) == null && System.nanoTime() < reactionDeadline) Thread.sleep(10);
      assertEquals(handle, model.control(model.root(), "get", "copy", null));
      var report = (Map<?, ?>) model.control(model.root(), "get", "report", null);
      assertEquals("report.csv", report.get("name"));
      assertTrue(new String(model.file(report.get("id").toString()).bytes(), java.nio.charset.StandardCharsets.UTF_8).contains("sample,42"));
    } finally { app.stop(); }
  }
}
