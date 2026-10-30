package org.integratedmodelling.klab.services.application.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.runtime.kactors.view.ApplicationUiRuntimeTest;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ServiceUiApplicationLauncherTest {
  @Test
  void sameUrnBehaviorCannotShadowTheSavedProjectApplication() throws Exception {
    var instance = mock(ServiceNetworkedInstance.class);
    var service = mock(BaseService.class);
    when(instance.klabService()).thenReturn(service);
    when(service.serviceId()).thenReturn("runtime-service");
    var resources = mock(ResourcesService.class);
    var scope = mock(UserScope.class);
    var user = mock(EngineAuthorization.class);
    when(user.getScope()).thenReturn(scope);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.serviceId()).thenReturn("resources-service");
    var original = new org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl();
    original.setUrn("staging.vxii.demoapp"); original.setProjectName("klab.staging.vxii");
    original.setBehaviorType(KActorsBehavior.Type.BEHAVIOR);
    original.setSourceCode("behavior staging.vxii.demoapp");
    when(resources.retrieve(original.getUrn(), KActorsBehavior.class, scope)).thenReturn(original);
    var project = new org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl();
    project.setUrn("klab.staging.vxii"); project.setBehaviors(List.of(original));
    when(resources.retrieve(project.getUrn(), org.integratedmodelling.klab.api.knowledge.organization.Project.class, scope)).thenReturn(project);
    var rights = new ResourceInfo(); rights.setPermissions(Set.of(CRUDOperation.READ));
    when(resources.info(project.getUrn(), KnowledgeClass.PROJECT, ResourceInfo.class, scope)).thenReturn(rights);
    var launcher = new ServiceUiApplicationLauncher(instance);
    for (String label : List.of("First saved application", "Second saved application")) {
      var app = ApplicationUiRuntimeTest.parse("web app staging.vxii.demoapp \"Demo\" version 1.0 using core.ui as ui action main: ui.text(\"" + label + "\")");
      ((org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl)app).setProjectName(project.getUrn());
      project.setApps(List.of(app));
      var published = launcher.resolve(original.getUrn(), "en", user);
      assertEquals(KActorsBehavior.Type.APP, published.behavior().getBehaviorType());
      assertEquals(app.getSourceCode(), published.behavior().getSourceCode());
      assertEquals(project.getUrn(), published.project());
    }
    project.setApps(List.of());
    assertThrows(ResponseStatusException.class, () -> launcher.resolve(original.getUrn(), "en", user));
  }

  @Test
  void browserScopesReceiveCallerBoundClientsFromTrustedHostTopology() throws Exception {
    var instance = mock(ServiceNetworkedInstance.class);
    var host =
        mock(
            BaseService.class,
            withSettings()
                .extraInterfaces(org.integratedmodelling.klab.api.services.RuntimeService.class));
    when(instance.klabService()).thenReturn(host);
    when(host.serviceId()).thenReturn("runtime-service");
    var topology = mock(org.integratedmodelling.klab.api.scope.ServiceScope.class);
    when(host.serviceScope()).thenReturn(topology);
    var trusted = mock(ResourcesService.class);
    when(trusted.serviceId()).thenReturn("resources-service");
    when(trusted.getUrl())
        .thenReturn(java.net.URI.create("https://resources.test/resources").toURL());
    when(topology.getService(ResourcesService.class)).thenReturn(trusted);
    var status = mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);
    when(status.isOperational()).thenReturn(true);
    when(status.isAvailable()).thenReturn(true);
    when(trusted.status()).thenReturn(status);
    when(host.status()).thenReturn(status);
    var browser =
        new org.integratedmodelling.klab.services.scopes.ServiceUserScope(
            mock(org.integratedmodelling.klab.api.identities.UserIdentity.class), host);
    browser.setId("webui:fixture");
    var scoped = mock(ResourcesService.class);
    when(scoped.serviceId()).thenReturn("resources-service");
    when(scoped.status()).thenReturn(status);
    var info = new ResourceInfo();
    info.setPermissions(Set.of(CRUDOperation.READ));
    when(scoped.info("test-project", KnowledgeClass.PROJECT, ResourceInfo.class, browser))
        .thenReturn(info);
    when(scoped.parseAsset(any(java.net.URL.class), eq(KActorsBehavior.class), same(browser)))
        .thenReturn(
            ApplicationUiRuntimeTest.parse(
                "web app tests.ui.browser \"Browser\" version 1.0 using core.ui as ui action main:"
                    + " ui.text(\"Hello\")"));
    var launcher =
        new ServiceUiApplicationLauncher(
            instance,
            (advertised, recipient) -> {
              assertSame(browser, recipient);
              assertEquals(trusted.getUrl(), advertised.getUrl());
              return scoped;
            });
    var user = mock(EngineAuthorization.class);
    when(user.getScope()).thenReturn(browser);
    launcher.resolve("runtime-service:app:bundled", "en", user);
    assertSame(scoped, browser.getService(ResourcesService.class));
    assertNotSame(trusted, browser.getService(ResourcesService.class));
    assertFalse(browser.isLocal());
    assertEquals(Set.of(CRUDOperation.READ), browser.getPermissions());
  }

  @Test
  void classpathAliasRequiresItsManifestProjectBeforeSourceParsing() throws Exception {
    var instance = mock(ServiceNetworkedInstance.class);
    var service = mock(BaseService.class);
    when(instance.klabService()).thenReturn(service);
    when(service.serviceId()).thenReturn("runtime-service");
    var scope = mock(UserScope.class);
    var user = mock(EngineAuthorization.class);
    when(user.getScope()).thenReturn(scope);
    var resources = mock(ResourcesService.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.serviceId()).thenReturn("resources-service");
    var info = new ResourceInfo();
    info.setPermissions(Set.of(CRUDOperation.READ));
    when(resources.info("test-project", KnowledgeClass.PROJECT, ResourceInfo.class, scope))
        .thenReturn(info);
    when(resources.parseAsset(any(java.net.URL.class), eq(KActorsBehavior.class), same(scope)))
        .thenAnswer(
            call -> {
              try (var stream = ((java.net.URL) call.getArgument(0)).openStream()) {
                return ApplicationUiRuntimeTest.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
              }
            });
    var launcher = new ServiceUiApplicationLauncher(instance);
    var app = launcher.resolve("runtime-service:app:bundled", "en", user);
    assertEquals("tests.ui.bundled", app.behavior().getUrn());
    assertEquals("test-project", app.project());
    info.setPermissions(Set.of());
    assertThrows(
        ResponseStatusException.class,
        () -> launcher.resolve("runtime-service:app:bundled", "en", user));
    verify(resources, times(1))
        .parseAsset(any(java.net.URL.class), eq(KActorsBehavior.class), same(scope));
  }

  @Test
  void publishedSourceAndProjectMaterialsUseReadRightsAndArePinned() throws Exception {
    var instance = mock(ServiceNetworkedInstance.class);
    var service = mock(BaseService.class);
    when(instance.klabService()).thenReturn(service);
    when(service.serviceId()).thenReturn("runtime-service");
    var resources = mock(ResourcesService.class);
    var scope = mock(UserScope.class);
    var user = mock(EngineAuthorization.class);
    when(user.getScope()).thenReturn(scope);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.serviceId()).thenReturn("resources-service");
    var behavior = ApplicationUiRuntimeTest.fixture();
    when(resources.retrieve("tests.ui.workflow", KActorsBehavior.class, scope))
        .thenReturn(behavior);
    var project = new ResourceInfo();
    project.setPermissions(Set.of(CRUDOperation.READ));
    when(resources.info("test-project", KnowledgeClass.PROJECT, ResourceInfo.class, scope))
        .thenReturn(project);
    when(resources.retrieve(anyString(), eq(ProjectMaterial.class), same(scope)))
        .thenAnswer(
            call -> {
              String urn = call.getArgument(0);
              String text =
                  switch (urn) {
                    case "test-project/ui/i18n/messages.properties" ->
                        "TITLE=Application\nREADY=Ready";
                    case "test-project/ui/i18n/messages_es.properties" -> "TITLE=Aplicación";
                    case "test-project/ui/styles/workflow.css" ->
                        ".summary { color: var(--ui-text); }";
                    default -> null;
                  };
              return text == null
                  ? null
                  : new ProjectMaterial(
                      "test-project",
                      urn.substring("test-project/".length()),
                      text.getBytes(StandardCharsets.UTF_8));
            });
    var launcher = new ServiceUiApplicationLauncher(instance);
    var app = launcher.resolve("tests.ui.workflow", "es-ES", user);
    assertEquals("Aplicación", app.presentation().localize("#TITLE"));
    assertEquals("Ready", app.presentation().localize("READY"));
    assertEquals("es-ES", app.presentation().locale());
    assertEquals("light", app.presentation().theme());
    assertTrue(app.stylesheets().containsKey("styles/workflow"));
    assertNotSame(behavior, app.behavior());
    assertEquals(behavior.getSourceCode(), app.behavior().getSourceCode());
    launcher.authorize(app, user);
    project.setPermissions(Set.of());
    assertThrows(ResponseStatusException.class, () -> launcher.authorize(app, user));
    assertThrows(
        ResponseStatusException.class, () -> launcher.resolve("tests.ui.workflow", "es", user));
  }
}
