package org.integratedmodelling.klab.utilities;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;

class AuthorityDiscoveryTest {
  @Test void startupInstallsFromTheWorldviewProvider() {
    var scope = mock(ServiceScope.class);
    var resources = resources(true, "imod");
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    var resolution = component();
    when(resources.resolve("test.authority", KnowledgeClass.COMPONENT, null)).thenReturn(resolution);
    var target = target();
    var registry = target.getComponentRegistry();
    var authority = mock(Authority.class);
    when(registry.getAuthority(eq("test.authority"), any(), same(scope)))
        .thenReturn(null, authority);
    when(registry.loadComponents(any(), same(scope))).thenReturn(true);

    var worldview = worldview();
    assertNotEquals(worldview.getUrn(), worldview.getWorldviewId());
    assertSame(authority, Utils.Resources.resolveAuthority("test.authority", scope, target, worldview));
    verify(registry).loadComponents(argThat(result -> result.getResults().size() == 1), same(scope));
    verify(resources).resolve("test.authority", KnowledgeClass.COMPONENT, null);
  }

  @Test void reloadRetainsUserScopeAndRefreshesInstalledDependencies() {
    var scope = mock(UserScope.class);
    var resources = resources(true, "imod");
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    when(resources.resolve("test.authority", KnowledgeClass.COMPONENT, scope)).thenReturn(component());
    var target = target();
    var registry = target.getComponentRegistry();
    var old = mock(Authority.class);
    var refreshed = mock(Authority.class);
    when(registry.getAuthority(eq("test.authority"), any(), same(scope))).thenReturn(old, refreshed);
    when(registry.loadComponents(any(), same(scope))).thenReturn(true);
    assertSame(refreshed, Utils.Resources.resolveAuthority("test.authority", scope, target, worldview()));
    verify(resources).resolve("test.authority", KnowledgeClass.COMPONENT, scope);
  }

  @Test void unrelatedResourcesAreNotQueriedAndFailedInstallationIsNotUsed() {
    var scope = mock(ServiceScope.class);
    var ordinary = resources(false, "imod");
    var otherWorldview = resources(true, "other");
    var provider = resources(true, "imod");
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(ordinary, otherWorldview, provider));
    when(provider.resolve("test.authority", KnowledgeClass.COMPONENT, null)).thenReturn(component());
    var target = target();
    when(target.getComponentRegistry().getAuthority(anyString(), any(), same(scope)))
        .thenReturn(mock(Authority.class));
    when(target.getComponentRegistry().loadComponents(any(), same(scope))).thenAnswer(invocation -> {
      ResourceSet result = invocation.getArgument(0);
      result.getNotifications().add(
          org.integratedmodelling.klab.api.services.runtime.Notification.error(
              "Component registry has not been initialized"));
      return false;
    });
    var failure = assertThrows(
        org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> Utils.Resources.resolveAuthority("test.authority", scope, target, worldview()));
    assertTrue(failure.getMessage().contains("test.authority"));
    assertTrue(failure.getMessage().contains("Component registry has not been initialized"));
    verify(ordinary, never()).resolve(anyString(), any(), any());
    verify(otherWorldview, never()).resolve(anyString(), any(), any());
  }

  private BaseService target() {
    var target = mock(BaseService.class);
    when(target.serviceType()).thenReturn(KlabService.Type.REASONER);
    when(target.getComponentRegistry()).thenReturn(mock(ComponentRegistry.class));
    return target;
  }

  private WorldviewImpl worldview() {
    var worldview = new WorldviewImpl();
    worldview.setUrn("imod");
    worldview.setWorldviewId("_uu_wv:loaded-instance");
    return worldview;
  }

  private ResourcesService resources(boolean provider, String worldview) {
    var resources = mock(ResourcesService.class);
    var capabilities = mock(ResourcesService.Capabilities.class);
    when(resources.capabilities(any(Scope.class))).thenReturn(capabilities);
    when(capabilities.isWorldviewProvider()).thenReturn(provider);
    when(capabilities.getAdoptedWorldview()).thenReturn(worldview);
    return resources;
  }

  private ResourceSet component() {
    return ResourceSet.of(new ResourceSet.Resource("resources", "test.component", null,
        Version.create("1.0.0"), KnowledgeClass.COMPONENT, 1, false));
  }
}
