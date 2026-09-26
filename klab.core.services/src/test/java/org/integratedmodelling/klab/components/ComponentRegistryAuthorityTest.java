package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;

class ComponentRegistryAuthorityTest {

  @org.integratedmodelling.klab.api.services.reasoner.Authority(
      urn = "test.authority",
      embeddable = true,
      subAuthorities = {"SUB"})
  public static class TestAuthority
      implements org.integratedmodelling.klab.api.knowledge.Authority {

    @Override
    public String getURN() {
      return "test.authority";
    }

    @Override
    public Identity getIdentity(String identityId, String catalog) {
      return null;
    }

    @Override
    public Capabilities getCapabilities() {
      return null;
    }

    @Override
    public Codelist getCodelist() {
      return null;
    }

    @Override
    public int getSemanticDistance(Identity a, Identity b) {
      return 0;
    }

    @Override
    public void document(String identityId, String mediaType, OutputStream destination) {}

    @Override
    public List<Identity> search(String query, String catalog) {
      return List.of();
    }

    @Override
    public Configuration setup(Parameters<String> options) {
      return null;
    }
  }

  @Test
  void resourcesAdvertiseAuthorityWithoutHostingIt() throws Exception {
    var registry = registry(KlabService.Type.RESOURCES);
    var descriptors = discover(registry);

    assertEquals(1, descriptors.size());
    assertEquals("test.authority", descriptors.getFirst().urn());
    assertTrue(descriptors.getFirst().embeddable());
    assertEquals(List.of("SUB"), descriptors.getFirst().subAuthorities());
    assertNull(registry.getAuthority("test.authority", Version.ANY_VERSION, null));
  }

  @Test
  void reasonerHostsDiscoveredAuthority() throws Exception {
    var registry = registry(KlabService.Type.REASONER);
    discover(registry);

    assertInstanceOf(
        TestAuthority.class,
        registry.getAuthority("test.authority", Version.ANY_VERSION, null));
  }

  private ComponentRegistry registry(KlabService.Type serviceType) {
    var service = mock(BaseService.class);
    when(service.serviceId()).thenReturn(serviceType.name().toLowerCase());
    when(service.serviceType()).thenReturn(serviceType);
    return new ComponentRegistry(service, null, null, List.of());
  }

  private List<Extensions.AuthorityDescriptor> discover(ComponentRegistry registry)
      throws Exception {
    Method method =
        ComponentRegistry.class.getDeclaredMethod(
            "registerAuthority",
            org.integratedmodelling.klab.api.services.reasoner.Authority.class,
            Class.class,
            String.class,
            Version.class,
            List.class);
    method.setAccessible(true);
    var descriptors = new ArrayList<Extensions.AuthorityDescriptor>();
    method.invoke(
        registry,
        TestAuthority.class.getAnnotation(
            org.integratedmodelling.klab.api.services.reasoner.Authority.class),
        TestAuthority.class,
        "test.component",
        Version.create("1.0.0"),
        descriptors);
    return descriptors;
  }
}
