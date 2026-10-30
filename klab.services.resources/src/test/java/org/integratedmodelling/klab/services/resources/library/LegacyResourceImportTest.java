package org.integratedmodelling.klab.services.resources.library;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyResourceImportTest {
  @TempDir Path directory;

  @Test
  void nullMissingAndEmptyAdapterProduceAnErrorWithoutCallingTheService() throws Exception {
    var service = mock(ResourcesProvider.class);
    var scope = mock(UserScope.class);
    for (var adapter : new String[] {", \"adapterType\": null", "", ", \"adapterType\": \"\""}) {
      var file = directory.resolve("resource.json");
      Files.writeString(file, "{\"urn\":\"legacy:item\",\"type\":\"NUMBER\","
          + "\"geometry\":\"*\",\"version\":\"0.0.1\"" + adapter + "}");
      var result = ResourcesLibrary.importLegacyResource(file.toFile(), service, scope);
      assertNotNull(result);
      assertTrue(result.getNotifications().stream().anyMatch(notification ->
          notification.getLevel() == Notification.Level.Error
              && notification.getMessage().contains("adapterType")));
    }
    verifyNoInteractions(service);
  }

  @Test
  void validManifestUsesIdempotentIngestionAndPreservesActualSourceUrn() throws Exception {
    var service = mock(ResourcesProvider.class);
    var scope = mock(UserScope.class);
    when(service.serviceId()).thenReturn("resources-id");
    var expected = new org.integratedmodelling.klab.api.services.resources.ResourceSet();
    when(service.ingestLegacyResource(any(), eq(scope))).thenReturn(expected);
    var file = directory.resolve("resource.json");
    Files.writeString(file, """
        {"urn":"local:old:namespace:item","type":"NUMBER","geometry":"*",
         "version":"0.0.1","adapterType":"missing-adapter",
         "metadata":{"im:original-urn":"local:unrelated:namespace:item"}}
        """);

    assertSame(expected, ResourcesLibrary.importLegacyResource(file.toFile(), service, scope));
    var resource = org.mockito.ArgumentCaptor.forClass(
        org.integratedmodelling.klab.api.knowledge.Resource.class);
    verify(service).ingestLegacyResource(resource.capture(), eq(scope));
    assertEquals("local:old:namespace:item", resource.getValue().getMetadata().get(
        org.integratedmodelling.klab.api.data.Metadata.IM_ORIGINAL_URN));
    verify(service, never()).ingestResource(any(), any());
  }

  @Test
  void malformedJsonProducesAnErrorResultInsteadOfEscapingToTheLanguageService() throws Exception {
    var file = directory.resolve("resource.json");
    Files.writeString(file, "{invalid JSON");
    var result = ResourcesLibrary.importLegacyResource(file.toFile(), null, null);
    assertNotNull(result);
    assertTrue(result.getNotifications().stream().anyMatch(notification ->
        notification.getLevel() == Notification.Level.Error));
  }
}
