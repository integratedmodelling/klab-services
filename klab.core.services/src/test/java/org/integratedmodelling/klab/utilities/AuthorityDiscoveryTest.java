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
  @Test void startupInstallsFromAnAuthorizedResourcesProvider() {
    var scope = serviceScope();
    var resources = resources(true, "imod");
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    var resolution = component();
    when(resources.resolve("test.authority", KnowledgeClass.COMPONENT, null)).thenReturn(resolution);
    var target = target();
    var registry = target.getComponentRegistry();
    var authority = mock(Authority.class);
    when(registry.getAuthority(eq("test.authority"), any(), same(scope)))
        .thenReturn(null);
    when(registry.getAuthority(any(org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding.class),same(scope))).thenReturn(authority);
    when(registry.loadComponents(any(), same(scope))).thenReturn(true);

    var worldview = worldview();
    assertNotEquals(worldview.getUrn(), worldview.getWorldviewId());
    assertSame(authority, Utils.Resources.resolveAuthority("test.authority", scope, target, worldview));
    verify(registry).loadComponents(argThat(result -> result.getResults().size() == 1), same(scope));
    verify(resources).resolve("test.authority", KnowledgeClass.COMPONENT, null);
  }

  @Test void reloadKeepsInstalledAuthorityWithoutRediscovery() {
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
    assertSame(old, Utils.Resources.resolveAuthority("test.authority", scope, target, worldview()));
    verify(resources,never()).resolve(anyString(),any(),any());
    verify(registry,never()).loadComponents(any(),any());
  }

  @Test void generalResourcesAreQueriedAndFailedInstallationIsNotUsed() {
    var scope = serviceScope();
    var ordinary = resources(false, "imod");
    var otherWorldview = resources(true, "other");
    var provider = resources(true, "imod");
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(ordinary, otherWorldview, provider));
    when(provider.resolve("test.authority", KnowledgeClass.COMPONENT, null)).thenReturn(component());
    var target = target();
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
    verify(ordinary).resolve(anyString(), any(), any());
    verify(otherWorldview).resolve(anyString(), any(), any());
  }

  private org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding binding() {
    return new org.integratedmodelling.klab.api.knowledge.Worldview.AuthorityBinding(
        "TAXA", "test:Species", "test", 0, 10, "source-hash",
        new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor(
            "test.authority", Version.create("1.0.0"), true, true, List.of(), List.of()),
        "test.component", Version.create("1.0.0"), null);
  }

  @Test void pinnedAuthorityUsesInstalledExactProviderWithoutRediscovery() {
    var scope = serviceScope();
    var target = target(); var binding = binding(); var provider = mock(Authority.class);
    when(target.getComponentRegistry().getAuthority(binding, scope)).thenReturn(provider);
    assertSame(provider, Utils.Resources.resolveAuthority(binding, scope, target, worldview()));
    verify(scope, never()).getServices(ResourcesService.class);
  }

  @Test void pinnedAuthorityRejectsCompatibleButDifferentComponentVersion() {
    var scope = serviceScope(); var source = resources(true, "imod");
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(source));
    when(source.resolve("test.authority", KnowledgeClass.COMPONENT, null))
        .thenReturn(ResourceSet.of(new ResourceSet.Resource("resources", "test.component", null,
            Version.create("1.1.0"), KnowledgeClass.COMPONENT, 1, false)));
    var target = target();
    assertNull(Utils.Resources.resolveAuthority(binding(), scope, target, worldview()));
    verify(target.getComponentRegistry(), never()).loadComponents(any(), any());
  }

  @Test void pinnedAuthorityInstallsExactComponentAndUsesExactLookup() {
    var scope = serviceScope(); var source = resources(true, "imod");
    when(scope.getServices(ResourcesService.class)).thenReturn(List.of(source));
    when(source.resolve("test.authority", KnowledgeClass.COMPONENT, null)).thenReturn(component());
    var target = target(); var binding = binding(); var provider = mock(Authority.class);
    when(target.getComponentRegistry().loadComponents(any(), same(scope))).thenReturn(true);
    when(target.getComponentRegistry().getAuthority(binding, scope)).thenReturn(null, provider);
    assertSame(provider, Utils.Resources.resolveAuthority(binding, scope, target, worldview()));
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
    when(capabilities.getComponents()).thenReturn(List.of(descriptor()));
    when(resources.serviceId()).thenReturn("resources");
    when(resources.getUrl()).thenReturn(url());
    var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(resources.status()).thenReturn(status);
    return resources;
  }

  private ResourceSet component() {
    var result=ResourceSet.of(new ResourceSet.Resource("resources", "test.component", null,
        Version.create("1.0.0"), KnowledgeClass.COMPONENT, 1, false));
    result.getServices().put("resources",url());return result;
  }

  private ServiceScope serviceScope() {
    var scope=mock(ServiceScope.class);var owner=mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);
    when(owner.getUsername()).thenReturn("owner");when(owner.getId()).thenReturn("owner-id");when(owner.getGroups()).thenReturn(List.of());when(scope.getIdentity()).thenReturn(owner);return scope;
  }
  private java.net.URL url() {
    try{return java.net.URI.create("https://resources.invalid").toURL();}catch(java.net.MalformedURLException impossible){throw new AssertionError(impossible);}
  }
  private org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor descriptor() {
    var rights=new org.integratedmodelling.klab.api.authentication.ResourcePrivileges();rights.setPublic(true);
    return new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor(
        "test.component",Version.create("1.0.0"),"fixture",new java.io.File("fixture.jar"),"hash",null,rights,List.of(),List.of(),List.of(binding().provider()),
        java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),null,1,null,null,0);
  }
}
