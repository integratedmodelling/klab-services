package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ContextualizerComponentResolutionTest {

  @Test
  void synchronizedComponentIsReturnedAsAResolvedServiceImplementation() throws Exception {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var registry = mock(ComponentRegistry.class);
    var settings = mock(Settings.class);
    var scope = mock(ContextScope.class);
    var resources = mock(ResourcesService.class);
    var contextualizable = mock(Contextualizable.class);
    var call = mock(ServiceCall.class);
    var implementation = mock(Extensions.FunctionDescriptor.class);
    var componentResolution = new ResourceSet();
    componentResolution
        .getResults()
        .add(
            new ResourceSet.Resource(
                "remote-resources",
                "klab.component.generators",
                null,
                Version.create("1.0.0"),
                KlabAsset.KnowledgeClass.COMPONENT,
                42L,
                false));

    when(contextualizable.getServiceCall()).thenReturn(call);
    when(contextualizable.getResourceUrns()).thenReturn(List.of());
    when(call.getUrn()).thenReturn("klab.generators.geospatial.terrain");
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.resolve(
            "klab.generators.geospatial.terrain",
            KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION,
            scope))
        .thenReturn(componentResolution);
    when(registry.getFunctionDescriptor(call, scope)).thenReturn(null);
    when(registry.getFunctionDescriptor(call)).thenReturn(List.of(implementation));
    when(registry.loadComponents(any(ResourceSet.class), eq(scope))).thenReturn(true);
    when(settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class)).thenReturn(true);
    doReturn(registry).when(runtime).getComponentRegistry();
    doReturn("runtime").when(runtime).serviceId();
    var settingsField = BaseService.class.getDeclaredField("settings");
    settingsField.setAccessible(true);
    settingsField.set(runtime, settings);
    var resolved = runtime.resolveContextualizables(List.of(contextualizable), scope);

    assertTrue(
        resolved.getResults().stream()
            .anyMatch(
                resource ->
                    resource.getKnowledgeClass()
                            == KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION
                        && "klab.generators.geospatial.terrain"
                            .equals(resource.getResourceUrn())));
  }

  @Test
  void universalResourceDiscoversAndInstallsItsAdapterComponent() throws Exception {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var registry = mock(ComponentRegistry.class);
    var settings = mock(Settings.class);
    var scope = mock(ContextScope.class);
    var resources = mock(ResourcesService.class);
    var contextualizable = mock(Contextualizable.class);
    var adapter = mock(Adapter.class);
    var urn = Urn.of("klab:random:test:value");

    var resourceResolution =
        ResourceSet.of(
            new ResourceSet.Resource(
                "remote-resources",
                urn.getUrn(),
                null,
                Version.ANY_VERSION,
                KlabAsset.KnowledgeClass.RESOURCE,
                42L,
                false));
    var componentResolution =
        ResourceSet.of(
            new ResourceSet.Resource(
                "remote-resources",
                "klab.component.generators",
                null,
                Version.create("1.0.0"),
                KlabAsset.KnowledgeClass.COMPONENT,
                43L,
                false));

    when(contextualizable.getServiceCall()).thenReturn(null);
    when(contextualizable.getResourceUrns()).thenReturn(List.of(urn));
    when(scope.getData()).thenReturn(Parameters.create());
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.resolve(urn.getUrn(), KlabAsset.KnowledgeClass.RESOURCE, scope))
        .thenReturn(resourceResolution);
    when(resources.resolve("random", KlabAsset.KnowledgeClass.COMPONENT, scope))
        .thenReturn(componentResolution);
    when(registry.getAdapter("random", Version.ANY_VERSION, scope))
        .thenReturn(null, adapter);
    when(adapter.isEmbeddable()).thenReturn(true);
    when(registry.loadComponents(any(ResourceSet.class), eq(scope))).thenReturn(true);
    when(settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class)).thenReturn(true);
    doReturn(registry).when(runtime).getComponentRegistry();
    var settingsField = BaseService.class.getDeclaredField("settings");
    settingsField.setAccessible(true);
    settingsField.set(runtime, settings);

    var resolved = runtime.resolveContextualizables(List.of(contextualizable), scope);

    assertTrue(
        resolved.getResults().stream()
            .anyMatch(
                resource ->
                    resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.COMPONENT
                        && "klab.component.generators".equals(resource.getResourceUrn())));
    assertTrue(
        resolved.getResults().stream()
            .anyMatch(
                resource ->
                    resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.RESOURCE
                        && urn.getUrn().equals(resource.getResourceUrn())));
    verify(resources).resolve("random", KlabAsset.KnowledgeClass.COMPONENT, scope);
    verify(registry).loadComponents(componentResolution, scope);
  }

  @Test
  void universalResourceFallsBackToAdapterDiscoveryWhenProvidersDoNotSynthesizeIt()
      throws Exception {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var registry = mock(ComponentRegistry.class);
    var settings = mock(Settings.class);
    var scope = mock(ContextScope.class);
    var resources = mock(ResourcesService.class);
    var contextualizable = mock(Contextualizable.class);
    var adapter = mock(Adapter.class);
    var descriptor = new AdapterDescriptor();
    descriptor.setName("random");
    descriptor.setEmbeddable(true);
    descriptor.setTimestamp(44L);
    var urn = Urn.of("klab:random:objects:polygons");
    var unavailableResource =
        ResourceSet.empty(Notification.error("Universal resource synthesis is not supported"));
    var componentResolution =
        ResourceSet.of(
            new ResourceSet.Resource(
                "remote-resources",
                "klab.component.generators",
                null,
                Version.create("1.0.0"),
                KlabAsset.KnowledgeClass.COMPONENT,
                43L,
                false));

    when(contextualizable.getServiceCall()).thenReturn(null);
    when(contextualizable.getResourceUrns()).thenReturn(List.of(urn));
    when(scope.getData()).thenReturn(Parameters.create());
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.resolve(urn.getUrn(), KlabAsset.KnowledgeClass.RESOURCE, scope))
        .thenReturn(unavailableResource);
    when(resources.resolve("random", KlabAsset.KnowledgeClass.COMPONENT, scope))
        .thenReturn(componentResolution);
    when(registry.getAdapter("random", Version.ANY_VERSION, scope))
        .thenReturn(null, adapter);
    when(adapter.isEmbeddable()).thenReturn(true);
    when(adapter.getVersion()).thenReturn(Version.create("1.0.0"));
    when(adapter.getAdapterInfo()).thenReturn(descriptor);
    when(registry.loadComponents(any(ResourceSet.class), eq(scope))).thenReturn(true);
    when(settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class)).thenReturn(true);
    doReturn(registry).when(runtime).getComponentRegistry();
    doReturn("runtime").when(runtime).serviceId();
    var settingsField = BaseService.class.getDeclaredField("settings");
    settingsField.setAccessible(true);
    settingsField.set(runtime, settings);

    var resolved = runtime.resolveContextualizables(List.of(contextualizable), scope);

    assertTrue(
        resolved.getResults().stream()
            .anyMatch(
                resource ->
                    resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.COMPONENT
                        && "klab.component.generators".equals(resource.getResourceUrn())));
    assertTrue(
        resolved.getResults().stream()
            .anyMatch(
                resource ->
                    resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.RESOURCE
                        && urn.getUrn().equals(resource.getResourceUrn())
                        && "runtime".equals(resource.getServiceId())));
    verify(resources).resolve("random", KlabAsset.KnowledgeClass.COMPONENT, scope);
    verify(registry).loadComponents(componentResolution, scope);
  }

  @Test
  void compiledDataflowSynthesizesUniversalResourceFromInstalledAdapter() {
    var runtime = mock(RuntimeService.class);
    var registry = mock(ComponentRegistry.class);
    var scope = mock(ServiceContextScope.class);
    var adapter = mock(Adapter.class);
    var urn = Urn.of("klab:random:test:value");

    when(runtime.serviceId()).thenReturn("runtime");
    when(scope.getData()).thenReturn(Parameters.create());
    when(registry.getAdapter("random", Version.ANY_VERSION, scope)).thenReturn(adapter);
    when(adapter.getVersion()).thenReturn(Version.create("1.0.0"));
    when(adapter.resourceType(any(Urn.class))).thenReturn(Artifact.Type.NUMBER);
    var compiled =
        new CompiledDataflow(runtime, mock(Dataflow.class), registry, scope);

    var resource = compiled.resolveResource(List.of(urn.getUrn()), null, scope);

    assertEquals(urn.getUrn(), resource.getUrn());
    assertEquals("random", resource.getAdapterType());
    assertEquals(Artifact.Type.NUMBER, resource.getType());
    assertEquals("runtime", resource.getServiceId());
  }

  @Test
  void universalResourceKeepsNonEmbeddableAdapterOnRemoteProvider() throws Exception {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var registry = mock(ComponentRegistry.class);
    var settings = mock(Settings.class);
    var scope = mock(ContextScope.class);
    var resources = mock(ResourcesService.class);
    var contextualizable = mock(Contextualizable.class);
    var descriptor = new AdapterDescriptor();
    descriptor.setName("remote");
    descriptor.setEmbeddable(false);
    var urn = Urn.of("klab:remote:test:value");
    var remoteResource = mock(Resource.class);
    var resolution =
        ResourceSet.of(
            new ResourceSet.Resource(
                "remote-resources",
                urn.getUrn(),
                null,
                Version.ANY_VERSION,
                KlabAsset.KnowledgeClass.RESOURCE,
                42L,
                false));

    when(contextualizable.getServiceCall()).thenReturn(null);
    when(contextualizable.getResourceUrns()).thenReturn(List.of(urn));
    when(scope.getData()).thenReturn(Parameters.create());
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(scope.findService(eq(ResourcesService.class), any())).thenReturn(Optional.of(resources));
    when(resources.resolve(urn.getUrn(), KlabAsset.KnowledgeClass.RESOURCE, scope))
        .thenReturn(resolution);
    when(resources.info(
            "remote",
            KlabAsset.KnowledgeClass.INFORMATION,
            AdapterDescriptor.class,
            scope))
        .thenReturn(descriptor);
    when(resources.retrieve(urn.getUrn(), Resource.class, scope)).thenReturn(remoteResource);
    when(registry.getAdapter("remote", Version.ANY_VERSION, scope)).thenReturn(null);
    when(registry.loadComponents(any(ResourceSet.class), eq(scope))).thenReturn(true);
    when(settings.get(Setting.LOAD_REMOTE_RUNTIME_COMPONENTS, Boolean.class)).thenReturn(true);
    doReturn(registry).when(runtime).getComponentRegistry();
    var settingsField = BaseService.class.getDeclaredField("settings");
    settingsField.setAccessible(true);
    settingsField.set(runtime, settings);

    var resolved = runtime.resolveContextualizables(List.of(contextualizable), scope);

    assertTrue(
        resolved.getResults().stream()
            .anyMatch(
                resource ->
                    resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.RESOURCE
                        && urn.getUrn().equals(resource.getResourceUrn())));
    verify(resources, never())
        .resolve("remote", KlabAsset.KnowledgeClass.COMPONENT, scope);
  }

  @Test
  void compiledDataflowRetrievesUniversalResourceWhenAdapterIsRemote() {
    var runtime = mock(RuntimeService.class);
    var registry = mock(ComponentRegistry.class);
    var scope = mock(ServiceContextScope.class);
    var resources = mock(ResourcesService.class);
    var remoteResource = mock(Resource.class);
    var urn = Urn.of("klab:remote:test:value");

    when(scope.getData()).thenReturn(Parameters.create());
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(registry.getAdapter("remote", Version.ANY_VERSION, scope)).thenReturn(null);
    when(resources.retrieve(urn.getUrn(), Resource.class, scope)).thenReturn(remoteResource);
    var compiled =
        new CompiledDataflow(runtime, mock(Dataflow.class), registry, scope);

    assertEquals(
        remoteResource,
        compiled.resolveResource(List.of(urn.getUrn()), null, scope));
  }
}
