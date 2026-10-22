package org.integratedmodelling.klab.runtime.kactors.view;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.api.view.application.*;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.runtime.kactors.compiler.runtime.AgentRegistry;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.kActors.Behavior;
import org.junit.jupiter.api.Test;

public class ApplicationUiRuntimeTest {
  public static KActorsBehavior fixture() throws Exception {
    String source;
    try (var stream = ApplicationUiRuntimeTest.class.getResourceAsStream("/ui/workflow.kactor")) {
      source = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
    }
    return parse(source);
  }

  public static KActorsBehavior parse(String source) {
    var parser =
        new KActorsStandaloneSetup()
            .createInjectorAndDoEMFRegistration()
            .getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source));
    var syntaxErrors = new ArrayList<String>();
    parsed
        .getSyntaxErrors()
        .forEach(
            error ->
                syntaxErrors.add(
                    error.getStartLine()
                        + ": "
                        + error.getSyntaxErrorMessage().getMessage()
                        + " at "
                        + error.getText()));
    assertFalse(parsed.hasSyntaxErrors(), syntaxErrors.toString());
    var syntax =
        new BehaviorSyntaxImpl((Behavior) parsed.getRootASTElement(), null) {
          protected void logWarning(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {}

          protected void logError(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            fail(message);
          }
        };
    var notifications = new ArrayList<Notification>();
    var behavior = LanguageAdapter.INSTANCE.adaptBehavior(syntax, "test-project", notifications, 1);
    assertEquals(KActorsBehavior.Platform.WEB, behavior.getPlatform());
    assertTrue(
        notifications.stream()
            .noneMatch(n -> n.getLevel().severity >= Notification.Level.Error.severity),
        notifications.toString());
    return behavior;
  }

  public static AgentCompiler.Environment environment() {
    try {
      var registry = mock(ComponentRegistry.class, org.mockito.Answers.CALLS_REAL_METHODS);
      var global = ComponentRegistry.class.getDeclaredField("globalInstances");
      global.setAccessible(true);
      global.set(registry, new HashMap<>());
      var discover =
          ComponentRegistry.class.getDeclaredMethod(
              "createActorDescriptor", Actor.class, String.class, Class.class);
      discover.setAccessible(true);
      var descriptor =
          (Extensions.ActorDescriptor)
              discover.invoke(
                  registry,
                  CoreActorLibrary.Ui.class.getAnnotation(Actor.class),
                  "core.",
                  CoreActorLibrary.Ui.class);
      doReturn(List.of()).when(registry).getActorDescriptors(anyString(), isNull());
      doReturn(List.of(descriptor)).when(registry).getActorDescriptors("core.ui", null);
      return AgentCompiler.runtimeEnvironment(AgentCompiler.componentResolver(registry), null);
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
  }

  public static ApplicationBase start(KActorsBehavior behavior) {
    return start(behavior, environment());
  }

  private static ApplicationBase start(
      KActorsBehavior behavior, AgentCompiler.Environment environment) {
    var request = new AgentImpl();
    request.setBehaviorUrn(behavior.getUrn());
    var handle =
        AgentRegistry.INSTANCE.getOrCreateAgent(
            request,
            behavior,
            mock(SessionScope.class),
            environment.validator(),
            environment.resolver(),
            RuntimeAgent.CompilationOptions.INCLUDE_JAVA_CODE);
    assertTrue(handle.isViable(), () -> handle.getNotifications().toString());
    var runtime = (ApplicationBase) AgentRegistry.INSTANCE.getRuntimeAgent(handle.getUrn());
    assertTrue(handle.start(), () -> handle.getNotifications().toString());
    return runtime;
  }

  public static UiNode find(UiNode node, Predicate<UiNode> predicate) {
    if (predicate.test(node)) return node;
    for (var child : node.children()) {
      var found = find(child, predicate);
      if (found != null) return found;
    }
    return null;
  }

  private UiNode await(ApplicationBase app, Predicate<UiNode> predicate) throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    do {
      var snapshot = app.ui().session().snapshot();
      var found = snapshot == null ? null : find(snapshot.root(), predicate);
      if (found != null) return found;
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    fail("UI did not reach expected state: " + app.ui().session().snapshot());
    return null;
  }

  @Test
  void cachedApplicationClassInitializesSurfacesFromFreshSemanticSource() throws Exception {
    var shared = environment();
    var first = start(fixture(), shared);
    try {
      await(first, node -> node.type().equals("footer"));
      var fresh = fixture();
      assertNull(
          fresh.getStatements().stream()
              .filter(action -> action.getUrn().equals("footer"))
              .findFirst()
              .orElseThrow()
              .getActionType());
      var second = start(fresh, shared);
      try {
        assertSame(first.getClass(), second.getClass());
        await(second, node -> node.type().equals("footer"));
      } finally {
        second.stop();
      }
    } finally {
      first.stop();
    }
  }

  @Test
  void parsedApplicationCreatesWidgetsFiresReactionsAndReconnectsWithoutRebuildingAgent()
      throws Exception {
    var presentation =
        new UiPresentation(
            "es",
            "dark",
            List.of("dark"),
            Map.of(
                "READY",
                "Preparado",
                "RUN",
                "Ejecutar",
                "NAME",
                "Nombre",
                "TITLE",
                "Mi aplicación"));
    var app =
        presentation.constructing(
            () -> {
              try {
                return start(fixture());
              } catch (Exception error) {
                throw new RuntimeException(error);
              }
            });
    try {
      var button =
          await(
              app,
              n -> n.type().equals("button") && n.properties().get("label").equals("Ejecutar"));
      await(app, n -> n.type().equals("footer"));
      assertNotNull(
          find(
              app.ui().session().snapshot().root(),
              n -> n.properties().containsValue("Mi aplicación")));
      var field = await(app, n -> n.type().equals("input"));
      assertEquals(
          "workspace",
          find(
                  app.ui().session().snapshot().root(),
                  n ->
                      "container".equals(n.type())
                          && "column".equals(n.properties().get("layout"))
                          && n.properties().containsKey("class"))
              .properties()
              .get("class"));
      var toolkit = new TestToolkit();
      app.ui().session().attach(toolkit);
      var revision = app.ui().session().snapshot().revision();
      toolkit.input.accept(new UiEvent(1, revision, field.id(), "change", "Remember me"));
      app.ui().afterInput().get(5, java.util.concurrent.TimeUnit.SECONDS);
      toolkit.input.accept(
          new UiEvent(2, app.ui().session().snapshot().revision(), button.id(), "click", null));
      await(app, n -> n.type().equals("text") && n.properties().containsValue("Remember me"));
      var retained = app.ui().session().resumeState();
      app.ui().session().detach();
      app.ui().session().attach(new TestToolkit());
      assertEquals(retained, app.ui().session().resumeState());
      assertEquals(
          "Remember me",
          find(retained.snapshot().root(), n -> n.id().equals(field.id()))
              .properties()
              .get("value"));
      var details =
          find(
              retained.snapshot().root(),
              n -> n.type().equals("button") && n.properties().get("label").equals("Details"));
      app.ui()
          .session()
          .dispatch(new UiEvent(3, retained.snapshot().revision(), details.id(), "click", null));
      var dialog =
          await(
              app,
              n -> n.type().equals("dialog") && !Boolean.TRUE.equals(n.properties().get("hidden")));
      assertEquals("Mi aplicación", dialog.properties().get("title"));
      app.ui()
          .session()
          .dispatch(
              new UiEvent(4, app.ui().session().snapshot().revision(), dialog.id(), "close", null));
      await(
          app,
          n -> n.id().equals(dialog.id()) && Boolean.TRUE.equals(n.properties().get("hidden")));
    } finally {
      app.stop();
    }
  }

  @Test
  void translationsUseUtf8AndExplicitFallbackOnly() {
    var files =
        Map.of(
            "i18n/messages.properties",
            "TITLE=Base\nRUN=Run",
            "i18n/messages_es.properties",
            "TITLE=Aplicación",
            "i18n/messages_es_ES.properties",
            "RUN=Ejecutar");
    var values =
        UiPresentation.translations(
            "i18n/messages",
            "es-ES",
            name ->
                files.containsKey(name)
                    ? new ByteArrayInputStream(files.get(name).getBytes(StandardCharsets.UTF_8))
                    : null);
    assertEquals(Map.of("TITLE", "Aplicación", "RUN", "Ejecutar"), values);
    assertEquals(
        "#MISSING", new UiPresentation("es", "light", List.of(), values).localize("#MISSING"));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiPresentation.translations("../secrets", "es", name -> null));
  }

  @Test
  void modalInputAndFillAxesAreServerControlled() {
    try (var model = new ApplicationUiModel()) {
      var outer = model.root();
      var rootButton =
          (String)
              model.widget(
                  outer, "button", "outside", Map.of(), "Outside", mock(RuntimeAgent.Scope.class));
      var row = model.group(outer, "row", Map.of("layout", "row"));
      model.group(row, "wide", Map.of("hfill", true));
      model.group(row, "tall", Map.of("vfill", true));
      var tree = model.session().snapshot().root();
      assertEquals(
          1, find(tree, n -> n.properties().containsKey("hfill")).properties().get("grow"));
      assertEquals(
          "stretch", find(tree, n -> n.properties().containsKey("vfill")).properties().get("self"));
      var modal =
          model.action(
              outer,
              "confirm",
              List.of(
                  org.integratedmodelling.klab.api.lang.Annotation.of(
                      "modal", "dismissible", false)));
      var inside =
          (String)
              model.widget(
                  modal, "button", "inside", Map.of(), "Inside", mock(RuntimeAgent.Scope.class));
      long revision = model.session().snapshot().revision();
      assertFalse(model.validInput(new UiEvent(1, revision, rootButton, "click", null)));
      assertTrue(model.validInput(new UiEvent(1, revision, inside, "click", null)));
      assertFalse(model.validInput(new UiEvent(1, revision, modal.mount(), "close", null)));
      model.control(outer, "hide", "confirm", null);
      assertTrue(model.validInput(new UiEvent(1, revision, rootButton, "click", null)));
    }
  }

  @Test
  void replacingASurfaceDiscardsDelayedUpdatesAndReleasesOldEmitters() {
    try (var model = new ApplicationUiModel()) {
      var declaration =
          List.<org.integratedmodelling.klab.api.lang.Annotation>of(
              org.integratedmodelling.klab.api.lang.Annotation.of("panel"));
      var old = model.action(model.root(), "settings", declaration);
      var oldGroup = model.group(old, "group", Map.of("layout", "column"));
      var emitter = mock(RuntimeAgent.Scope.class);
      model.widget(oldGroup, "input", "name", Map.of(), "Name", emitter);
      var replacement = model.action(model.root(), "settings", declaration);
      model.widget(replacement, "text", "status", Map.of(), "New state", null);
      verify(emitter).done();
      model.control(oldGroup, "set", "status", "Stale state");
      assertEquals(
          "New state",
          find(model.session().snapshot().root(), node -> node.type().equals("text"))
              .properties()
              .get("label"));
      var lateEmitter = mock(RuntimeAgent.Scope.class);
      assertNull(model.widget(oldGroup, "button", "late", Map.of(), "Late", lateEmitter));
      verify(lateEmitter).done();
      model.widget(model.root(), "text", "outside", Map.of(), "Application state", null);
      model.control(replacement, "set", "/main/outside", "Updated explicitly");
      assertNotNull(
          find(
              model.session().snapshot().root(),
              node -> node.properties().containsValue("Updated explicitly")));
    }
  }

  @Test
  void onlySurfaceAnnotationsParticipateInAutomaticUiStartup() throws Exception {
    var source =
        fixture().getSourceCode()
            + "\n"
            + "@custom(initial=true)\n"
            + "action unexpected:\n"
            + "    fail \"Not an initial UI action\"\n";
    var app = start(parse(source));
    try {
      await(app, node -> node.type().equals("button"));
      assertFalse(app.rootScope().isDone());
    } finally {
      app.stop();
    }
  }

  private static class TestToolkit implements UiToolkit {
    java.util.function.Consumer<UiEvent> input;

    public String id() {
      return "headless";
    }

    public Set<String> supportedTypes() {
      return ApplicationUiModel.TYPES;
    }

    public View open(java.util.function.Consumer<UiEvent> input) {
      this.input = input;
      return new View() {
        public void render(UiSnapshot state) {}

        public void close() {}
      };
    }
  }
}
