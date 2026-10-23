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
    if (source.stripLeading().startsWith("web app"))
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
      doReturn(List.of()).when(registry).getActorDescriptors(anyString(), isNull());
      for (var actorClass : List.of(CoreActorLibrary.Ui.class, CoreActorLibrary.Widget.class, CoreActorLibrary.UiFile.class)) {
        var descriptor = (Extensions.ActorDescriptor) discover.invoke(
            registry, actorClass.getAnnotation(Actor.class), "core.", actorClass);
        doReturn(List.of(descriptor)).when(registry).getActorDescriptors(descriptor.urn, null);
      }
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

  @Test
  void parsedNamedActionsMountTwiceInTaggedGroupWithIndependentStateAndRemoval() throws Exception {
    var app =
        start(
            parse(
                """
        web app tests.ui.components
            "Dynamic component fixture"
            version 1.0
            using core.ui as ui
        action main:
            (ui.text("Components")) #slots
            ui.mount("slots", "left", "card", ("Left", 1))
            ui.mount("slots", "right", "card", ("Right", 2))
            ui.mount("slots", "blank", "blank", ())
            ui.remove("blank")
        @panel
        action card(title, ordinal):
            ui.input(title :maxlength 20 :placeholder "Enter a name" +dense) #field
            ui.text(title) #status
            ui.button("Apply"):
                event -> ui.set("status", ui.get("field"))
        @panel
        action blank:
            ui.text("Temporary")
        """));
    try {
      var right = await(app, n -> "main/right/field".equals(n.properties().get("tag")));
      var left =
          find(
              app.ui().session().snapshot().root(),
              n -> "main/left/field".equals(n.properties().get("tag")));
      assertNotNull(left);
      var tree = app.ui().session().snapshot().root();
      var slots = find(tree, n -> "main/slots".equals(n.properties().get("tag")));
      assertEquals(3, slots.children().size());
      assertTrue(
          slots.children().stream()
              .filter(n -> n.type().equals("panel"))
              .allMatch(n -> n.children().size() == 3));
      app.ui().control(app.ui().root(), "set", "/main/left/field", "Left state");
      app.ui().control(app.ui().root(), "set", "/main/right/field", "Right state");
      app.ui().session().attach(new TestToolkit());
      // Both untagged buttons have isolated invocation scopes; select the left subtree's button.
      var leftPanel =
          find(
              app.ui().session().snapshot().root(),
              n -> "main/left".equals(n.properties().get("tag")));
      var click = find(leftPanel, n -> n.type().equals("button"));
      assertTrue(
          app.ui()
              .session()
              .dispatch(
                  new UiEvent(
                      1, app.ui().session().snapshot().revision(), click.id(), "click", null)));
      await(app, n -> "Left state".equals(n.properties().get("label")));
      app.ui().control(app.ui().root(), "remove", "left", null);
      assertNull(find(app.ui().session().snapshot().root(), n -> n.id().equals(left.id())));
      assertEquals(
          "Right state", app.ui().control(app.ui().root(), "get", "/main/right/field", null));
      assertFalse(app.rootScope().isDone());
      app.ui().control(app.ui().root(), "clear", "slots", null);
      assertTrue(
          find(app.ui().session().snapshot().root(), n -> n.id().equals(slots.id()))
              .children()
              .isEmpty());
      assertFalse(
          app.ui()
              .validInput(
                  new UiEvent(
                      2, app.ui().session().snapshot().revision(), right.id(), "change", "Late")));
    } finally {
      app.stop();
    }
  }

  @Test
  void failedComponentConstructionRollsBackAndDisposesBindingsWithoutPublishingIt() {
    try (var model = new ApplicationUiModel()) {
      model.presentation(new UiPresentation("es", "light", List.of(), Map.of("TITLE", "Tarjeta")));
      model.group(model.root(), "target", Map.of());
      var component =
          model.mount(
              model.root(),
              "target",
              "one",
              "card",
              List.of(org.integratedmodelling.klab.api.lang.Annotation.of("panel")));
      var emitter = mock(RuntimeAgent.Scope.class);
      model.widget(component, "input", "field", Map.of(), "Name", emitter);
      assertNull(find(model.session().snapshot().root(), n -> n.id().equals(component.mount())));
      assertThrows(
          IllegalArgumentException.class,
          () -> model.widget(component, "input", "bad", Map.of("maxlength", -1), "Bad", null));
      model.completeMount(component, false);
      verify(emitter).done();
      var retry =
          model.mount(
              model.root(),
              "target",
              "one",
              "card",
              List.of(
                  org.integratedmodelling.klab.api.lang.Annotation.of("panel", "title", "#TITLE")));
      model.widget(retry, "text", "label", Map.of(), "Committed", null);
      model.completeMount(retry, true);
      assertEquals(
          "Tarjeta",
          find(model.session().snapshot().root(), n -> n.id().equals(retry.mount()))
              .properties()
              .get("title"));
      assertNotNull(
          find(
              model.session().snapshot().root(),
              n -> "Committed".equals(n.properties().get("label"))));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              model.mount(
                  model.root(),
                  "target",
                  "one",
                  "card",
                  List.of(org.integratedmodelling.klab.api.lang.Annotation.of("panel"))));
    }
  }

  @Test
  void importedBehaviorContributesNamedAndNestedComponentActions() throws Exception {
    var cards =
        parse(
            """
        behavior tests.ui.cards
            "Reusable UI actions"
            version 1.0
            using core.ui as ui
        @panel
        static action card(title, ordinal):
            (ui.text(title)) #body
            ui.mount("body", "nested", "detail", ())
            ui.text(title) #status
            ui.button("Update imported"):
                event -> ui.set("status", "Changed from imported action")
        @panel
        action detail:
            ui.text("Nested imported action") #label
        """);
    var base = environment();
    var resolver =
        new AgentCompiler.Resolver() {
          public KActorsBehavior resolveBehavior(String urn, UserScope user) {
            return urn.equals(cards.getUrn()) ? cards : null;
          }

          public AgentCompiler.ResolvedActor resolveActor(String urn, UserScope user) {
            return base.resolver().resolveActor(urn, user);
          }
        };
    var app =
        start(
            parse(
                """
        web app tests.ui.imports
            "Imported UI fixture"
            version 1.0
            using core.ui as ui, tests.ui.cards as cards
        action main:
            (ui.text("Imported")) #target
            ui.mount("target", "one", "cards.card", ("First", 1))
            ui.mount("target", "two", "cards.card", ("Second", 2))
        """),
            AgentCompiler.runtimeEnvironment(resolver, null));
    try {
      await(app, n -> "main/two/nested/label".equals(n.properties().get("tag")));
      assertNotNull(
          find(
              app.ui().session().snapshot().root(),
              n -> "main/one/nested/label".equals(n.properties().get("tag"))));
      app.ui().session().attach(new TestToolkit());
      var panel =
          find(
              app.ui().session().snapshot().root(),
              n -> "main/one".equals(n.properties().get("tag")));
      var button = find(panel, n -> n.type().equals("button"));
      assertTrue(
          app.ui()
              .session()
              .dispatch(
                  new UiEvent(
                      1, app.ui().session().snapshot().revision(), button.id(), "click", null)));
      await(app, n -> "Changed from imported action".equals(n.properties().get("label")));
      app.ui().control(app.ui().root(), "remove", "one", null);
      assertNotNull(
          find(
              app.ui().session().snapshot().root(),
              n -> "main/two/nested/label".equals(n.properties().get("tag"))));
      assertFalse(app.rootScope().isDone());
    } finally {
      app.stop();
    }
  }

  @Test
  void widgetConfigurationAndCapabilitiesAreValidatedBeforeMutation() {
    try (var model = new ApplicationUiModel()) {
      var root = model.root();
      String input =
          (String)
              model.widget(
                  root,
                  "input",
                  "name",
                  Map.of("maxlength", 3, "required", true),
                  "Name",
                  mock(RuntimeAgent.Scope.class));
      assertFalse(model.validInput(new UiEvent(1, 1, input, "change", "long")));
      assertFalse(model.validInput(new UiEvent(1, 1, input, "change", "")));
      assertTrue(model.validInput(new UiEvent(1, 1, input, "change", "Ada")));
      model.control(
          root, "set", "name", ""); // Application resets may leave a required field empty.
      model.control(root, "style", "name", Map.of("readonly", true));
      assertFalse(model.validInput(new UiEvent(1, 1, input, "change", "Ada")));
      assertThrows(
          IllegalArgumentException.class, () -> model.control(root, "set", "name", "long"));
      assertThrows(
          IllegalArgumentException.class, () -> model.group(root, "bad", Map.of("typo", true)));
      assertThrows(
          IllegalArgumentException.class, () -> model.group(root, "bad", Map.of("layout", "grid")));
      assertThrows(
          IllegalArgumentException.class, () -> model.group(root, "bad", Map.of("gap", "auto")));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              model.capabilities(
                  new UiToolkitDescriptor(
                      "limited",
                      1,
                      Set.of("web"),
                      Set.of("container"),
                      Set.of("column"),
                      "limited")));
      model.capabilities(
          new UiToolkitDescriptor(
              "limited",
              1,
              Set.of("web"),
              Set.of("container", "input"),
              Set.of("column"),
              "limited"));
      assertThrows(
          IllegalArgumentException.class,
          () -> model.widget(root, "button", "bad", Map.of(), "Unsupported", null));
      assertThrows(
          IllegalArgumentException.class,
          () -> model.control(root, "style", "name", Map.of("layout", "row")));
    }
  }

  @Test
  void documentedComponentExampleSupportsRepeatedRebuildAndSelfRemoval() throws Exception {
    var example = java.nio.file.Path.of("docs/examples/ui/components.kactor");
    if (!java.nio.file.Files.exists(example))
      example = java.nio.file.Path.of("..", "docs/examples/ui/components.kactor");
    var app = start(parse(java.nio.file.Files.readString(example)));
    try {
      await(app, n -> "main/bob/email".equals(n.properties().get("tag")));
      app.ui().session().attach(new TestToolkit());
      long sequence = 0;
      var oldPanel =
          find(
              app.ui().session().snapshot().root(),
              n -> "main/alice".equals(n.properties().get("tag")));
      var remove =
          find(
              oldPanel,
              n -> n.type().equals("button") && "Remove".equals(n.properties().get("label")));
      assertTrue(
          app.ui()
              .session()
              .dispatch(
                  new UiEvent(
                      ++sequence,
                      app.ui().session().snapshot().revision(),
                      remove.id(),
                      "click",
                      null)));
      app.ui().afterInput().get(5, java.util.concurrent.TimeUnit.SECONDS);
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
      while (find(app.ui().session().snapshot().root(), n -> n.id().equals(oldPanel.id())) != null
          && System.nanoTime() < deadline) Thread.sleep(10);
      assertNull(find(app.ui().session().snapshot().root(), n -> n.id().equals(oldPanel.id())));
      for (int iteration = 0; iteration < 2; iteration++) {
        var previousField =
            find(
                app.ui().session().snapshot().root(),
                n -> "main/alice/email".equals(n.properties().get("tag")));
        String previousId = previousField == null ? "" : previousField.id();
        var reset =
            find(
                app.ui().session().snapshot().root(),
                n ->
                    n.type().equals("button")
                        && "Reset accounts".equals(n.properties().get("label")));
        assertTrue(
            app.ui()
                .session()
                .dispatch(
                    new UiEvent(
                        ++sequence,
                        app.ui().session().snapshot().revision(),
                        reset.id(),
                        "click",
                        null)));
        await(
            app,
            n ->
                "main/alice/email".equals(n.properties().get("tag")) && !n.id().equals(previousId));
        assertFalse(app.rootScope().isDone());
      }
      assertEquals(
          "alice@example.org", app.ui().control(app.ui().root(), "get", "/main/alice/email", null));
    } finally {
      app.stop();
    }
  }

  @Test
  void failingNamedActionDiscardsItsSubtreeWithoutStoppingTheApplication() throws Exception {
    var app =
        start(
            parse(
                fixture().getSourceCode()
                    + """

        @panel
        action broken(name):
            ui.input(name) #temporary
            fail "Deliberate UI construction failure"
        """));
    try {
      await(app, n -> n.type().equals("button"));
      var before = app.ui().session().snapshot();
      assertThrows(
          RuntimeException.class,
          () ->
              app.mountUi(
                  (org.integratedmodelling.klab.runtime.kactors.AgentScope) app.rootScope(),
                  "workspace",
                  "broken",
                  "broken",
                  List.of("Temporary")));
      assertEquals(before, app.ui().session().snapshot());
      assertFalse(app.rootScope().isDone());
      assertNull(
          find(
              app.ui().session().snapshot().root(),
              n -> "Temporary".equals(n.properties().get("label"))));
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
