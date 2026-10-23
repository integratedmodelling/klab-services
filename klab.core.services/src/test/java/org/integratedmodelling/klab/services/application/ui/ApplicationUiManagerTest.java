package org.integratedmodelling.klab.services.application.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.view.application.*;
import org.integratedmodelling.klab.runtime.kactors.ApplicationBase;
import org.integratedmodelling.klab.runtime.kactors.view.*;
import org.integratedmodelling.klab.services.application.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApplicationUiManagerTest {
  private EngineAuthorization user(String name) {
    var user = mock(EngineAuthorization.class);
    when(user.isAuthenticated()).thenReturn(true);
    when(user.getScope()).thenReturn(mock(UserScope.class));
    when(user.getFullyQualifiedUsername()).thenReturn("user://hub/" + name);
    when(user.getToken()).thenReturn(name + "-upstream-hub-jwt");
    when(user.getExpiration()).thenReturn(Instant.now().plusSeconds(300));
    return user;
  }

  private ApplicationUiManager.Request request(String key, String resume) {
    return new ApplicationUiManager.Request("examples.workflow", "es", key, resume);
  }

  @Test
  void isolatesUsersAndMakesLaunchRetriesIdempotent() {
    var launcher = mock(UiApplicationLauncher.class);
    var releases = new AtomicInteger();
    when(launcher.selectToolkit())
        .thenAnswer(
            invocation ->
                new UiToolkitLease(UiToolkitLease.QUASAR, null, releases::incrementAndGet));
    var authentication = mock(ServiceAuthorizationManager.class);
    var alice = user("alice");
    var bob = user("bob");
    try (var model = new ApplicationUiModel();
        var manager = manager(launcher, authentication, model)) {
      var first = manager.launch(request("one", null), alice);
      assertEquals("quasar", first.toolkit().id());
      assertEquals(first.id(), manager.launch(request("one", null), alice).id());
      assertEquals(first.id(), manager.launch(request("two", first.id()), alice).id());
      verify(launcher, times(1)).start(any(), same(alice));
      assertEquals(
          404,
          assertThrows(ResponseStatusException.class, () -> manager.state(first.id(), bob))
              .getStatusCode()
              .value());
      assertEquals(
          404,
          assertThrows(
                  ResponseStatusException.class,
                  () -> manager.ticket(first.id(), "https://runtime.test", bob, "webui_bob"))
              .getStatusCode()
              .value());
      assertThrows(ResponseStatusException.class, () -> manager.stop(first.id(), bob));
      assertThrows(
          ResponseStatusException.class,
          () ->
              manager.launch(
                  new ApplicationUiManager.Request("another.app", "es", "one", null), alice));
      assertEquals(0, releases.get()); // Detached applications retain their toolkit lease.
    }
    assertEquals(1, releases.get());
  }

  @Test
  void ticketsAreOriginBoundSingleUseAndOldConnectionsCannotDeliverInput() throws Exception {
    var launcher = mock(UiApplicationLauncher.class);
    when(launcher.selectToolkit())
        .thenAnswer(
            invocation ->
                org.integratedmodelling.klab.runtime.kactors.view.UiToolkitLease.quasar());
    var authentication = mock(ServiceAuthorizationManager.class);
    var alice = user("alice");
    when(authentication.validateToken(eq("webui_alice"), anyMap())).thenReturn(alice);
    try (var model = new ApplicationUiModel();
        var manager = manager(launcher, authentication, model)) {
      var scope = mock(RuntimeAgent.Scope.class);
      model.widget(model.root(), "input", "name", Map.of(), "Name", scope);
      var state = manager.launch(request("one", null), alice);
      var wrong = manager.ticket(state.id(), "https://runtime.test", alice, "webui_alice");
      assertThrows(
          ResponseStatusException.class,
          () -> manager.attach(wrong.ticket(), "https://attacker.test", frame -> {}, () -> {}));
      assertThrows(
          ResponseStatusException.class,
          () -> manager.attach(wrong.ticket(), "https://runtime.test", frame -> {}, () -> {}));
      var frames = new ArrayList<Map<String, Object>>();
      var disconnected = new AtomicInteger();
      var grant = manager.ticket(state.id(), "https://runtime.test", alice, "webui_alice");
      var first =
          manager.attach(
              grant.ticket(), "https://runtime.test", frames::add, disconnected::incrementAndGet);
      assertEquals("snapshot", frames.getFirst().get("type"));
      assertEquals("ready", frames.getLast().get("type"));
      assertThrows(
          ResponseStatusException.class,
          () -> manager.attach(grant.ticket(), "https://runtime.test", frames::add, () -> {}));
      var node = model.session().snapshot().root().children().getFirst();
      var event =
          new UiEvent(1, model.session().snapshot().revision(), node.id(), "change", "Retained");
      first.event(event);
      model.afterInput().get(5, TimeUnit.SECONDS);
      var secondGrant = manager.ticket(state.id(), "https://runtime.test", alice, "webui_alice");
      var second =
          manager.attach(secondGrant.ticket(), "https://runtime.test", frame -> {}, () -> {});
      assertEquals(1, disconnected.get());
      assertThrows(ResponseStatusException.class, () -> first.event(event));
      assertEquals(
          "Retained",
          model.session().snapshot().root().children().getFirst().properties().get("value"));
      long cursor = model.session().lastAcceptedSequence();
      second.event(
          new UiEvent(
              2,
              model.session().snapshot().revision(),
              node.id(),
              "change",
              Map.of("unexpected", "object")));
      assertEquals(cursor, model.session().lastAcceptedSequence());
      second.close();
      var resume = manager.state(state.id(), alice);
      assertEquals(cursor, resume.state().lastAcceptedSequence());
      assertEquals(
          "Retained",
          resume.state().snapshot().root().children().getFirst().properties().get("value"));
    }
  }

  @Test
  void expiredCredentialsAndRevokedProjectReadCannotResumeOrAttach() {
    var launcher = mock(UiApplicationLauncher.class);
    when(launcher.selectToolkit())
        .thenAnswer(
            invocation ->
                org.integratedmodelling.klab.runtime.kactors.view.UiToolkitLease.quasar());
    var authentication = mock(ServiceAuthorizationManager.class);
    var alice = user("alice");
    when(authentication.validateToken(anyString(), anyMap())).thenReturn(alice);
    try (var model = new ApplicationUiModel();
        var manager = manager(launcher, authentication, model)) {
      var state = manager.launch(request("one", null), alice);
      var ticket = manager.ticket(state.id(), "https://runtime.test", alice, "webui_alice");
      doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
          .when(launcher)
          .authorize(any(), same(alice));
      assertThrows(
          ResponseStatusException.class, () -> manager.launch(request("two", state.id()), alice));
      assertThrows(
          ResponseStatusException.class,
          () -> manager.attach(ticket.ticket(), "https://runtime.test", frame -> {}, () -> {}));
      when(alice.getExpiration()).thenReturn(Instant.now().minusSeconds(1));
      assertThrows(ResponseStatusException.class, () -> manager.state(state.id(), alice));
    }
  }

  @Test
  void opaqueCredentialRevocationCannotFallBackToAStillValidUpstreamToken() {
    var launcher = mock(UiApplicationLauncher.class);
    when(launcher.selectToolkit())
        .thenAnswer(
            invocation ->
                org.integratedmodelling.klab.runtime.kactors.view.UiToolkitLease.quasar());
    var authentication = mock(ServiceAuthorizationManager.class);
    var alice = user("alice");
    when(authentication.validateToken(eq("webui_alice"), anyMap())).thenReturn(alice);
    try (var model = new ApplicationUiModel();
        var manager = manager(launcher, authentication, model)) {
      var state = manager.launch(request("one", null), alice);
      var grant = manager.ticket(state.id(), "https://runtime.test", alice, "webui_alice");
      var connection =
          manager.attach(grant.ticket(), "https://runtime.test", frame -> {}, () -> {});
      connection.ping();
      when(authentication.validateToken(eq("webui_alice"), anyMap())).thenReturn(null);
      assertEquals(
          401,
          assertThrows(ResponseStatusException.class, connection::ping).getStatusCode().value());
      verify(authentication, never()).validateToken(eq(alice.getToken()), anyMap());
    }
  }

  @Test
  void fileTransfersRequireOwnershipAndProjectReadAndStopDisposesFiles() {
    var launcher = mock(UiApplicationLauncher.class);
    when(launcher.selectToolkit()).thenAnswer(invocation -> UiToolkitLease.quasar());
    var alice = user("alice");
    var bob = user("bob");
    try (var model = new ApplicationUiModel();
        var manager = manager(launcher, mock(ServiceAuthorizationManager.class), model)) {
      String upload = (String) model.widget(model.root(), "upload", "file", Map.of("maxbytes", 10), "File", mock(RuntimeAgent.Scope.class));
      var state = manager.launch(request("files", null), alice);
      var handle = manager.upload(state.id(), upload, "data.txt", "text/plain", new byte[] {65}, alice);
      assertEquals("QQ==", manager.file(state.id(), handle.get("id").toString(), alice).get("content"));
      assertThrows(ResponseStatusException.class, () -> manager.file(state.id(), handle.get("id").toString(), bob));
      assertThrows(ResponseStatusException.class, () -> manager.upload(state.id(), upload, "data.txt", "text/plain", new byte[1], bob));
      doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(launcher).authorize(any(), same(alice));
      assertThrows(ResponseStatusException.class, () -> manager.file(state.id(), handle.get("id").toString(), alice));
      doNothing().when(launcher).authorize(any(), same(alice));
      manager.stop(state.id(), alice);
      assertThrows(ResponseStatusException.class, () -> manager.file(state.id(), handle.get("id").toString(), alice));
    }
  }

  private ApplicationUiManager manager(
      UiApplicationLauncher launcher,
      ServiceAuthorizationManager authentication,
      ApplicationUiModel model) {
    var application =
        new UiApplicationLauncher.Published(
            "examples.workflow", "project", "resources", null, UiPresentation.defaults(), Map.of());
    when(launcher.resolve(anyString(), any(), any())).thenReturn(application);
    var app = mock(ApplicationBase.class);
    when(app.ui()).thenReturn(model);
    when(app.rootScope()).thenReturn(mock(RuntimeAgent.Scope.class));
    when(launcher.start(any(), any())).thenReturn(app);
    return new ApplicationUiManager(launcher, authentication, 1800);
  }
}
