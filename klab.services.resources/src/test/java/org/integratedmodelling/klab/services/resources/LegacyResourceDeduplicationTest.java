package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.junit.jupiter.api.Test;

class LegacyResourceDeduplicationTest {
  @Test
  void existingResourceIsReturnedBeforeAdapterValidationWithoutIngestion() {
    var provider = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var kbox = mock(ResourcesKBox.class);
    var scope = mock(UserScope.class);
    doReturn(kbox).when(provider).getResourcesKbox();
    doReturn("resources-id").when(provider).serviceId();
    var source = resource("local:old:namespace:item", "0.0.1");
    var stored = resource("service:staging:namespace:item", "1.0.0");
    when(kbox.getResourceByOriginalUrn(source.getUrn())).thenReturn(stored);

    var result = provider.ingestLegacyResource(source, scope);

    assertEquals(stored.getUrn(), result.getResources().getFirst().getResourceUrn());
    assertEquals(stored.getVersion(), result.getResources().getFirst().getResourceVersion());
    assertEquals(CRUDOperation.READ, result.getResources().getFirst().getOperation());
    assertTrue(result.getNotifications().getFirst().getMessage().contains("Skipped legacy import"));
    verify(provider, never()).ingestResource(any(), any());
    verify(provider, never()).getComponentRegistry();
  }

  @Test
  void rerunningAfterSuccessfulImportDoesNotCreateAnotherResource() {
    var provider = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var kbox = mock(ResourcesKBox.class);
    var scope = mock(UserScope.class);
    var existing = new AtomicReference<ResourceImpl>();
    doReturn(kbox).when(provider).getResourcesKbox();
    doReturn("resources-id").when(provider).serviceId();
    var source = resource("local:old:namespace:item", "0.0.1");
    var stored = resource("service:staging:namespace:item", "0.0.1");
    when(kbox.getResourceByOriginalUrn(source.getUrn())).thenAnswer(call -> existing.get());
    var created = new ResourceSet();
    doAnswer(call -> { existing.set(stored); return created; })
        .when(provider).ingestResource(source, scope);

    assertSame(created, provider.ingestLegacyResource(source, scope));
    assertEquals(stored.getUrn(), provider.ingestLegacyResource(source, scope)
        .getResources().getFirst().getResourceUrn());
    verify(provider, times(1)).ingestResource(source, scope);
  }

  private static ResourceImpl resource(String urn, String version) {
    var resource = new ResourceImpl();
    resource.setUrn(urn);
    resource.setVersion(Version.create(version));
    return resource;
  }
}
