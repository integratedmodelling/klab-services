package org.integratedmodelling.common.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.junit.jupiter.api.Test;

class DataflowSerializationTest {

  @Test
  void preservesResolverMetadataAcrossTheJsonWireFormat() throws Exception {
    var requirements = new ResourceSet();
    requirements.setWorkspace("resolution-workspace");

    var original = new DataflowImpl();
    original.setName("resolution");
    original.setRequirements(requirements);
    original.setResolvedCoverage(0.625);

    var mapper = JacksonConfiguration.newObjectMapper();
    var decoded = mapper.readValue(mapper.writeValueAsString(original), Dataflow.class);

    var decodedImpl = assertInstanceOf(DataflowImpl.class, decoded);
    assertEquals("resolution", decodedImpl.getName());
    assertEquals("resolution-workspace", decodedImpl.getRequirements().getWorkspace());
    assertEquals(0.625, decodedImpl.getResolvedCoverage());
  }
}
