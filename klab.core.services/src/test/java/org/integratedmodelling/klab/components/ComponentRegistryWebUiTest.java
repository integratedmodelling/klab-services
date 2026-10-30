package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.extension.KlabComponent;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

class ComponentRegistryWebUiTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

  @Test
  void retainedToolkitBlocksArchiveReplacementBeforeCopying() throws Exception {
    var fixture = fixture();
    when(fixture.wrapper().getPluginState()).thenReturn(PluginState.STARTED);
    when(fixture.component().uiToolkits())
        .thenReturn(
            List.of(
                new org.integratedmodelling.klab.api.view.application.UiToolkitDescriptor(
                    "test-kit",
                    1,
                    java.util.Set.of("web"),
                    java.util.Set.of("container"),
                    java.util.Set.of("column"),
                    "catalog-browser")));
    var pluginFolder = java.nio.file.Files.createDirectory(directory.resolve("plugins"));
    var pluginPath = ComponentRegistry.class.getDeclaredField("pluginPath");
    pluginPath.setAccessible(true);
    pluginPath.set(fixture.registry(), pluginFolder.toFile());
    var source = directory.resolve("replacement.jar");
    var manifest = new java.util.jar.Manifest();
    var attributes = manifest.getMainAttributes();
    attributes.putValue("Manifest-Version", "1.0");
    attributes.putValue("Plugin-Id", "test.component");
    attributes.putValue("Plugin-Version", "2.0.0");
    try (var jar =
        new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(source), manifest)) {}
    try (var lease = fixture.registry().acquireUiToolkit("test-kit")) {
      assertThrows(
          IllegalStateException.class,
          () -> fixture.registry().installComponent(source.toFile(), null));
      assertFalse(java.nio.file.Files.exists(pluginFolder.resolve("replacement.jar")));
      assertTrue(java.nio.file.Files.exists(source));
    }
  }

  @Test
  void toolkitSelectionPinsComponentVersionAndRejectsDuplicateOrUndeclaredModules()
      throws Exception {
    var fixture = fixture();
    when(fixture.wrapper().getPluginState()).thenReturn(PluginState.STARTED);
    var descriptor =
        new org.integratedmodelling.klab.api.view.application.UiToolkitDescriptor(
            "test-kit",
            1,
            java.util.Set.of("web"),
            java.util.Set.of("container", "text"),
            java.util.Set.of("column"),
            "catalog-browser");
    when(fixture.component().uiToolkits()).thenReturn(List.of(descriptor));
    var one = fixture.registry().acquireUiToolkit("test-kit");
    var two = fixture.registry().acquireUiToolkit("test-kit");
    assertEquals(descriptor, one.descriptor());
    assertEquals("public/ui/components/test.component/1.0.0/catalog.js", one.moduleUrl());
    assertThrows(
        IllegalStateException.class,
        () -> fixture.registry().unloadComponent("test.component", Version.create("1.0.0")));
    one.close();
    one.close();
    assertThrows(
        IllegalStateException.class,
        () -> fixture.registry().removeComponent("test.component", Version.create("1.0.0")));
    two.close();
    var counts = ComponentRegistry.class.getDeclaredField("uiToolkitUsers");
    counts.setAccessible(true);
    assertTrue(((Map<?, ?>) counts.get(fixture.registry())).isEmpty());
    when(fixture.component().uiToolkits()).thenReturn(List.of(descriptor, descriptor));
    assertThrows(
        IllegalArgumentException.class, () -> fixture.registry().acquireUiToolkit("test-kit"));
    when(fixture.component().webUiModules()).thenReturn(Map.of());
    when(fixture.component().uiToolkits()).thenReturn(List.of(descriptor));
    assertThrows(
        IllegalArgumentException.class, () -> fixture.registry().acquireUiToolkit("test-kit"));
    when(fixture.component().uiToolkits()).thenReturn(List.of());
    assertEquals("quasar", fixture.registry().acquireUiToolkit("quasar").descriptor().id());
    assertThrows(
        IllegalArgumentException.class, () -> fixture.registry().acquireUiToolkit("missing"));
  }

  @Test
  void installedComponentContributesModulesPanelsAndPages() throws Exception {
    var fixture = fixture();
    doAnswer(
            invocation -> {
              WebUiConfiguration.Builder builder = invocation.getArgument(0);
              builder
                  .panel("catalog-panel", "Catalog", "", "catalog-browser", 20, false)
                  .page("catalog", "Catalog", "", "catalog-browser", 20, true);
              return null;
            })
        .when(fixture.component())
        .configureWebUi(any());

    var builder = WebUiConfiguration.builder(KlabService.Type.RESOURCES);
    fixture.registry().configureWebUi(builder);
    var configuration = builder.build();

    assertEquals(1, configuration.panels().size());
    assertEquals("catalog", configuration.pages().getFirst().name());
    assertEquals(
        "public/ui/components/test.component/1.0.0/catalog.js",
        configuration.modules().get("catalog-browser"));
  }

  @Test
  void servesOnlyAnExplicitlyDeclaredModuleFromThePluginClassLoader() throws Exception {
    var fixture = fixture();
    var source = "export default {}".getBytes(StandardCharsets.UTF_8);
    var loader = mock(PluginClassLoader.class);
    when(fixture.wrapper().getPluginClassLoader()).thenReturn(loader);
    when(loader.getResourceAsStream("META-INF/klab/webui/catalog.js"))
        .thenReturn(new ByteArrayInputStream(source));

    var module = fixture.registry().getWebUiResource("test.component", "1.0.0", "catalog.js");
    var undeclared = fixture.registry().getWebUiResource("test.component", "1.0.0", "other.js");

    assertTrue(module.isPresent());
    assertArrayEquals(source, module.get().content());
    assertTrue(undeclared.isEmpty());
  }

  private Fixture fixture() throws Exception {
    var version = Version.create("1.0.0");
    var service = mock(BaseService.class);
    when(service.serviceId()).thenReturn("resources-service");
    var component = mock(KlabComponent.class);
    when(component.getName()).thenReturn("test.component");
    when(component.getVersion()).thenReturn(version);
    when(component.webUiModules()).thenReturn(Map.of("catalog-browser", "catalog.js"));
    var wrapper = mock(PluginWrapper.class);
    var pluginDescriptor = mock(PluginDescriptor.class);
    when(pluginDescriptor.getPluginId()).thenReturn("test.component");
    when(wrapper.getDescriptor()).thenReturn(pluginDescriptor);
    when(wrapper.getPlugin()).thenReturn(component);
    when(wrapper.getPluginState()).thenReturn(PluginState.RESOLVED);
    var manager = mock(PluginManager.class);
    when(manager.getPlugins()).thenReturn(List.of(wrapper));
    when(manager.getPlugin("test.component")).thenReturn(wrapper);
    var registry = new ComponentRegistry(service, null, null, List.of(descriptor(version)));
    Field managerField = ComponentRegistry.class.getDeclaredField("componentManager");
    managerField.setAccessible(true);
    managerField.set(registry, manager);
    return new Fixture(registry, component, wrapper);
  }

  private Extensions.ComponentDescriptor descriptor(Version version) {
    return new Extensions.ComponentDescriptor(
        "test.component",
        version,
        "Test component",
        null,
        null,
        null,
        ResourcePrivileges.PUBLIC,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "resources-service",
        1L,
        Extensions.ComponentImportType.FILE,
        Extensions.ComponentUpdateStatus.UP_TO_DATE,
        1L);
  }

  private record Fixture(
      ComponentRegistry registry, KlabComponent component, PluginWrapper wrapper) {}
}
