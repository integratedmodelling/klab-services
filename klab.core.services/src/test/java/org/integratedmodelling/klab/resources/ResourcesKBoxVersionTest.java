package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

class ResourcesKBoxVersionTest {

  @Test
  void unversionedLookupReturnsCurrentAndExplicitLookupUsesHistory() {
    var old = resource("1.0.0");
    var current = resource("1.1.0");
    current.setHistory(List.of(old));

    assertSame(current, ResourcesKBox.selectVersion(current, Version.ANY_VERSION));
    assertSame(current, ResourcesKBox.selectVersion(current, new Version("1.1.0")));
    assertSame(old, ResourcesKBox.selectVersion(current, new Version("1.0.0")));
    assertNull(ResourcesKBox.selectVersion(current, new Version("0.9.0")));
    assertEquals(new Version("1.1.0"), current.getVersion());
  }

  private static ResourceImpl resource(String version) {
    var resource = new ResourceImpl();
    resource.setUrn("local:test:data:resource");
    resource.setVersion(new Version(version));
    return resource;
  }
}
