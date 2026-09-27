package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;

class ComponentRegistryDiscoveryTest {

  @Test
  void scopedLookupInstallsACompletelyMissingServiceCallProvider() {
    var host = mock(BaseService.class);
    when(host.serviceId()).thenReturn("runtime");
    var registry = spy(new ComponentRegistry(host, null, null, List.of()));
    var scope = mock(UserScope.class);
    var resources = mock(ResourcesService.class);
    var descriptor = mock(Extensions.FunctionDescriptor.class);
    var installed = new AtomicBoolean();
    var resolution = new ResourceSet();
    resolution
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

    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.resolve(
            "generator.contextualizer", KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION, scope))
        .thenReturn(resolution);
    doAnswer(invocation -> installed.get() ? List.of(descriptor) : null)
        .when(registry)
        .getFunctionDescriptor(eq("generator.contextualizer"), isNull(Version.class));
    doAnswer(
            invocation -> {
              assertSame(resolution, invocation.getArgument(0));
              installed.set(true);
              return true;
            })
        .when(registry)
        .loadComponents(resolution, scope);

    assertSame(
        descriptor,
        registry
            .getFunctionDescriptor("generator.contextualizer", null, scope)
            .getFirst());
    verify(resources)
        .resolve(
            "generator.contextualizer",
            KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION,
            scope);
    verify(registry).loadComponents(resolution, scope);
  }
}
