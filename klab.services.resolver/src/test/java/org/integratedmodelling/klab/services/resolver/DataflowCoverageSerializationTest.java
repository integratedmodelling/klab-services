package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.runtime.scale.CoverageImpl;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;
import org.junit.jupiter.api.Test;

class DataflowCoverageSerializationTest {

  @Test
  void roundTripsGeometryProjectionWithoutLeakingCoverageImplementation() throws Exception {
    var original = new DataflowImpl();
    var resolverCoverage = new CoverageImpl(new ScaleImpl(Geometry.UNIVERSAL), 1.0);
    original.setCoverage(resolverCoverage.as(Geometry.class));

    var mapper = JacksonConfiguration.newObjectMapper();
    var decoded = mapper.readValue(mapper.writeValueAsString(original), Dataflow.class);

    var decodedImpl = assertInstanceOf(DataflowImpl.class, decoded);
    assertNotNull(decodedImpl.getCoverage());
    assertFalse(decodedImpl.getCoverage() instanceof Coverage);
  }
}
