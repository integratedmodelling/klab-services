package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.common.services.ResourcesCapabilitiesImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class ResourcesCapabilitiesSerializationTest {

  @Test
  void capabilitiesWithLibraryExporterAreValidAndRoundTrip() {
    var serviceInfo = new ServiceInfoImpl();
    serviceInfo.setName("export");
    serviceInfo.setDescription("Test exporter");

    var function = new Extensions.FunctionDescriptor();
    function.serviceInfo = serviceInfo;
    function.methodCall = 1;
    function.staticMethod = true;

    var library =
        new Extensions.LibraryDescriptor(
            "test.library",
            "Test library",
            null,
            null,
            null,
            List.of(Pair.of(serviceInfo, function)),
            null);
    var component =
        new Extensions.ComponentDescriptor(
            "test.component",
            null,
            "Test component",
            null,
            null,
            null,
            null,
            List.of(library),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0L,
            null,
            null,
            0L);
    var capabilities = new ResourcesCapabilitiesImpl();
    capabilities.setComponents(List.of(component));

    String json = Utils.Json.asString(capabilities);
    assertFalse(json.isBlank());

    var roundTrip = Utils.Json.parseObject(json, ResourcesCapabilitiesImpl.class);
    assertEquals(
        "export",
        roundTrip
            .getComponents()
            .getFirst()
            .libraries()
            .getFirst()
            .exporters()
            .getFirst()
            .getFirst()
            .getName());
  }
}
