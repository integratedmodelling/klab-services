package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URL;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class WorldviewAuthorityTransportTest {
  Worldview.AuthorityBinding binding(URL url) {
    return new Worldview.AuthorityBinding("TAXA", "test:Species", "test", 20, 40, "source-hash",
        new Extensions.AuthorityDescriptor("provider.taxa", Version.create("1.0.0"), true,
            true, List.of("GENUS"), List.of()), "component.taxa", Version.create("2.0.0"), url);
  }
  WorldviewImpl worldview() {
    var worldview = new WorldviewImpl(); worldview.setUrn("test");
    worldview.setAuthorityBindings(List.of(binding(null))); return worldview;
  }
  Reasoner reasoner(String url, boolean local) throws Exception {
    var reasoner = mock(Reasoner.class);
    when(reasoner.getUrl()).thenReturn(new URL(url)); when(reasoner.isLocal()).thenReturn(local);
    var capabilities = new ReasonerCapabilitiesImpl();
    capabilities.setWorldviewUrn("test"); capabilities.setConsistent(true);
    capabilities.setAuthorityBindings(List.of(binding(new URL(url))));
    when(reasoner.capabilities(any())).thenReturn(capabilities);
    return reasoner;
  }
  @Test void worldviewAndHostCapabilitiesRoundTrip() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var worldview = worldview();
    worldview.setAuthorityBindings(List.of(binding(new URL("http://localhost:8091"))));
    var json = mapper.writeValueAsString(worldview);
    assertEquals(worldview.getAuthorityBindings(), mapper.readValue(json, Worldview.class).getAuthorityBindings());
    assertFalse(json.contains("configurationId")); assertFalse(json.contains("parameters"));
    var capabilities = new ReasonerCapabilitiesImpl();
    capabilities.setAuthorityBindings(worldview.getAuthorityBindings());
    assertEquals(capabilities.getAuthorityBindings(), mapper.readValue(
        mapper.writeValueAsString(capabilities), ReasonerCapabilitiesImpl.class).getAuthorityBindings());
    var legacy = mapper.readTree(json);
    ((com.fasterxml.jackson.databind.node.ObjectNode) legacy).remove("authorityBindings");
    assertTrue(mapper.treeToValue(legacy, Worldview.class).getAuthorityBindings().isEmpty());
  }
  @Test void localHostWinsRegardlessOfDiscoveryOrderAndRemoteIsFallback() throws Exception {
    var local = reasoner("http://localhost:8091", true);
    var remote = reasoner("https://remote.example/reasoner", false);
    var scope = mock(UserScope.class); var worldview = worldview();
    when(scope.getServices(Reasoner.class)).thenReturn(List.of(remote, local));
    worldview.refreshAuthorityHosts(scope);
    assertEquals(local.getUrl(), worldview.getAuthorityBindings().getFirst().reasonerUrl());
    when(scope.getServices(Reasoner.class)).thenReturn(List.of(remote));
    worldview.refreshAuthorityHosts(scope);
    assertEquals(remote.getUrl(), worldview.getAuthorityBindings().getFirst().reasonerUrl());
    when(scope.getServices(Reasoner.class)).thenReturn(List.of());
    worldview.refreshAuthorityHosts(scope);
    assertNull(worldview.getAuthorityBindings().getFirst().reasonerUrl());
  }
  @Test void unconfiguredAndOtherWorldviewHostsCannotWin() throws Exception {
    var local = reasoner("http://localhost:8091", true);
    ((ReasonerCapabilitiesImpl)local.capabilities(null)).setAuthorityBindings(List.of());
    var remote = reasoner("https://remote.example/reasoner", false);
    var scope = mock(UserScope.class);
    when(scope.getServices(Reasoner.class)).thenReturn(List.of(local, remote));
    var worldview = worldview(); worldview.refreshAuthorityHosts(scope);
    assertEquals(remote.getUrl(), worldview.getAuthorityBindings().getFirst().reasonerUrl());
    ((ReasonerCapabilitiesImpl)remote.capabilities(null)).setWorldviewUrn("other");
    worldview.refreshAuthorityHosts(scope);
    assertNull(worldview.getAuthorityBindings().getFirst().reasonerUrl());
  }
  @Test void differentConfigurationRevisionCannotWin() throws Exception {
    var local = reasoner("http://localhost:8091", true);
    var original = binding(local.getUrl());
    ((ReasonerCapabilitiesImpl)local.capabilities(null)).setAuthorityBindings(List.of(
        new Worldview.AuthorityBinding(original.localId(), original.rootIdentity(),
            original.sourceOntology(), original.sourceOffset(), original.sourceLength(), "old-source",
            original.provider(), original.componentUrn(), original.componentVersion(), original.reasonerUrl())));
    var scope = mock(UserScope.class); when(scope.getServices(Reasoner.class)).thenReturn(List.of(local));
    var worldview = worldview(); worldview.refreshAuthorityHosts(scope);
    assertNull(worldview.getAuthorityBindings().getFirst().reasonerUrl());
  }
  @Test void synchronizationReplacesAndRemovesBindings() {
    var scope = mock(UserScope.class); var resources = mock(ResourcesService.class);
    when(scope.findService(eq(ResourcesService.class), any())).thenReturn(Optional.of(resources));
    when(scope.getServices(Reasoner.class)).thenReturn(List.of());
    var resourceSet = ResourceSet.of(new ResourceSet.Resource("resources", "test", null,
        Version.create("1.0.0"), KlabAsset.KnowledgeClass.WORLDVIEW, 1, false));
    var snapshot = worldview();
    when(resources.retrieve("test", Worldview.class, scope)).thenReturn(snapshot);
    var client = new WorldviewImpl(); client.setUrn("test");
    client.refreshAuthorityBindings(resourceSet, scope);
    assertEquals(snapshot.getAuthorityBindings(), client.getAuthorityBindings());
    when(resources.retrieve("test", Worldview.class, scope)).thenThrow(new IllegalStateException("offline"));
    client.refreshAuthorityBindings(resourceSet, scope);
    assertTrue(client.isEmpty()); assertTrue(client.getAuthorityBindings().isEmpty());
    doReturn(snapshot).when(resources).retrieve("test", Worldview.class, scope);
    client.refreshAuthorityBindings(resourceSet, scope);
    assertFalse(client.isEmpty()); assertEquals(1, client.getAuthorityBindings().size());
    snapshot.setAuthorityBindings(List.of()); client.refreshAuthorityBindings(resourceSet, scope);
    assertTrue(client.getAuthorityBindings().isEmpty());
  }
}
