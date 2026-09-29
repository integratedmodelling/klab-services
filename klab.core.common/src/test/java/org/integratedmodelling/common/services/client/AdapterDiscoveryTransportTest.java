package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdapterDiscoveryTransportTest {
  @ParameterizedTest
  @ValueSource(ints = {200, 404})
  void discoversRemoteAdapterFromAdvertisedComponentWhenResolutionIsUnavailable(int status)
      throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "{\"empty\":true}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    var url = URI.create("http://127.0.0.1:" + server.getAddress().getPort()).toURL();
    try (var http = Utils.Http.getClient(url.toString(), null)) {
      var remote = mock(ResourcesClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true);
      field.set(remote, http);
      doReturn("remote").when(remote).serviceId();
      doReturn("remote-resources").when(remote).serviceName();
      doReturn(url).when(remote).getUrl();
      doReturn(false).when(remote).isLocal();
      var scope = mock(UserScope.class);
      var adapter = new AdapterDescriptor();
      adapter.setName("random");
      adapter.setVersion(Version.create("1.0.0"));
      adapter.setEmbeddable(true);
      var component = mock(Extensions.ComponentDescriptor.class);
      when(component.id()).thenReturn("klab.component.generators");
      when(component.version()).thenReturn(Version.create("2.0.0"));
      when(component.timestamp()).thenReturn(42L);
      when(component.mavenCoordinates()).thenReturn("org.test:generators:2.0.0");
      when(component.adapters()).thenReturn(List.of(adapter));
      var capabilities = new ResourcesCapabilitiesImpl();
      capabilities.setComponents(List.of(component));
      doReturn(capabilities).when(remote).capabilities(scope);
      var local = mock(ResourcesService.class);
      when(local.serviceId()).thenReturn("local");
      when(local.resolve("random", KnowledgeClass.RESOURCE_ADAPTER, scope))
          .thenReturn(ResourceSet.empty());
      doReturn(List.of(local, remote)).when(scope).getServices(ResourcesService.class);

      var result = new ResourcesMerger(scope).resolve("random", KnowledgeClass.RESOURCE_ADAPTER, scope);
      assertFalse(result.isEmpty(), () -> result.getNotifications().stream()
          .map(org.integratedmodelling.klab.api.services.runtime.Notification::getMessage)
          .toList().toString());
      var requirement = result.getResults().iterator().next();
      assertEquals("klab.component.generators", requirement.getResourceUrn());
      assertEquals("remote", requirement.getServiceId());
      assertEquals(KnowledgeClass.COMPONENT, requirement.getKnowledgeClass());
      assertEquals(Version.create("2.0.0"), requirement.getResourceVersion());
      assertEquals(42L, requirement.getTimestamp());
      assertEquals("org.test:generators:2.0.0", requirement.getMetadata().get(
          Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY));
      assertEquals(url, result.getServices().get("remote"));

      // Adapter compatibility is independent of its containing component's version.
      assertFalse(remote.resolve("random@1.0.0", KnowledgeClass.RESOURCE_ADAPTER, scope).isEmpty());
      var incompatible = remote.resolve("random@2.0.0", KnowledgeClass.RESOURCE_ADAPTER, scope);
      assertTrue(incompatible == null || incompatible.isEmpty());
      adapter.setEmbeddable(false);
      var nonEmbeddable = remote.resolve("random", KnowledgeClass.RESOURCE_ADAPTER, scope);
      assertTrue(nonEmbeddable == null || nonEmbeddable.isEmpty());
    } finally {
      server.stop(0);
    }
  }
}
