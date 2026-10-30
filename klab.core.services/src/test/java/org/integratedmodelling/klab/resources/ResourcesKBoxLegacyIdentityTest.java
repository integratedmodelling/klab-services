package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.common.mapper.JacksonMapper;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.repository.EntityDecorator;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

class ResourcesKBoxLegacyIdentityTest {
  @Test
  @SuppressWarnings("unchecked")
  void matchesStoredUrnAndOriginalUrnAfterRewritingWithoutMatchingUnrelatedResources() throws Exception {
    var mapperConstructor = Class.forName(ResourcesKBox.class.getName() + "$KlabJacksonMapper")
        .getDeclaredConstructor();
    mapperConstructor.setAccessible(true);
    var decoratorConstructor = Class.forName(ResourcesKBox.class.getName() + "$ResourceDecorator")
        .getDeclaredConstructor();
    decoratorConstructor.setAccessible(true);
    try (var database = Nitrite.builder().loadModule(NitriteModule.module(
        (JacksonMapper) mapperConstructor.newInstance())).openOrCreate()) {
      var repository = database.getRepository(
          (EntityDecorator<ResourceImpl>) decoratorConstructor.newInstance());
      var stored = new ResourceImpl();
      stored.setUrn("service:staging:namespace:item");
      stored.getMetadata().put(Metadata.IM_ORIGINAL_URN, "local:old:namespace:item");
      repository.update(stored, true);
      var ordinary = new ResourceImpl();
      ordinary.setUrn("service:staging:namespace:ordinary");
      repository.update(ordinary, true);
      // Creating the index must also find resources persisted before this feature was installed.
      ResourcesKBox.ensureLegacyIdentityIndex(repository);
      var kbox = mock(ResourcesKBox.class, CALLS_REAL_METHODS);
      var field = ResourcesKBox.class.getDeclaredField("resources");
      field.setAccessible(true);
      field.set(kbox, repository);

      assertEquals(stored.getUrn(), kbox.getResourceByOriginalUrn(stored.getUrn()).getUrn());
      assertEquals(stored.getUrn(), kbox.getResourceByOriginalUrn("local:old:namespace:item").getUrn());
      assertNull(kbox.getResourceByOriginalUrn("local:other:namespace:item"));
      assertNull(kbox.getResourceByOriginalUrn("service:staging:namespace:different"));
    }
  }
}
