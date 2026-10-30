package org.integratedmodelling.klab.components;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class OwnedBootstrapRegistryTest {
  @TempDir Path directory;
  @Test void freshRegistryUsesOnlyExplicitStagingCatalogAndRejectsReuse() throws Exception {
    var host=mock(BaseService.class);when(host.serviceId()).thenReturn("bootstrap-owned");when(host.serviceType()).thenReturn(KlabService.Type.REASONER);
    var options=mock(StartupOptions.class);var root=directory.resolve("owned/components");
    var registry=ComponentRegistry.ownedBootstrap(host,options,root);
    try {
      assertTrue(Files.isRegularFile(root.resolve("catalog.json")));assertEquals("[]",Files.readString(root.resolve("catalog.json")));
      assertTrue(Files.isDirectory(root.resolve("plugins")));
      assertThrows(IllegalArgumentException.class,()->ComponentRegistry.ownedBootstrap(host,options,root));
    } finally {registry.closeOwnedBootstrap();}
  }
}
