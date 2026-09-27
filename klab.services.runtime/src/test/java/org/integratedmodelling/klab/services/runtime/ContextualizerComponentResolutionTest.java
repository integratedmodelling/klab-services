package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.base.BaseService;
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
}
