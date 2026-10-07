package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.junit.jupiter.api.Test;

class ResourceAdapterResolutionTest {

  @Test
  void resolvesAdapterIdentifierToItsProvidingComponent() throws Exception {
    var provider = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var registry = mock(ComponentRegistry.class);
    var adapter = mock(Adapter.class);
    var component = mock(Extensions.ComponentDescriptor.class);
    var scope = mock(UserScope.class);
    var componentVersion = Version.create("1.0.0");
    var serviceUrl = URI.create("http://resources.test").toURL();

    doReturn(registry).when(provider).getComponentRegistry();
    doReturn("resources-id").when(provider).serviceId();
    doReturn(serviceUrl).when(provider).getUrl();
    when(registry.getAdapter("random", Version.ANY_VERSION, scope)).thenReturn(adapter);
    when(adapter.getComponentUrn()).thenReturn("klab.component.generators");
    when(adapter.getComponentVersion()).thenReturn(componentVersion);
    when(registry.getComponent("klab.component.generators", componentVersion))
        .thenReturn(component);
    when(component.timestamp()).thenReturn(42L);

    var resolved =
        provider.resolve("random", KlabAsset.KnowledgeClass.RESOURCE_ADAPTER, scope);

    assertFalse(resolved.isEmpty());
    assertEquals(1, resolved.getResults().size());
    var dependency = resolved.getResults().iterator().next();
    assertEquals(KlabAsset.KnowledgeClass.COMPONENT, dependency.getKnowledgeClass());
    assertEquals("klab.component.generators", dependency.getResourceUrn());
    assertEquals(componentVersion, dependency.getResourceVersion());
    assertEquals("resources-id", dependency.getServiceId());
    assertEquals(1, resolved.getServices().size());
    assertEquals(serviceUrl, resolved.getServices().get("resources-id"));
    verify(registry).getAdapter("random", Version.ANY_VERSION, scope);
  }
}
