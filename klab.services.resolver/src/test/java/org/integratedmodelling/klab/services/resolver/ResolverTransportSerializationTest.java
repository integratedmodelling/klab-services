package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.client.ResolverClient;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.runtime.scale.CoverageImpl;
import org.integratedmodelling.klab.runtime.scale.ScaleImpl;
import org.junit.jupiter.api.Test;

class ResolverTransportSerializationTest {

  @Test
  void requestPayloadContainsOnlyPlainGeometries() throws Exception {
    var scale = new ScaleImpl(Geometry.UNIVERSAL);
    var coverage = new CoverageImpl(scale, 1.0);
    var observation = new ObservationImpl();
    observation.setGeometry(scale);

    var request = new ResolutionRequest();
    request.setObservation(ResolverClient.forTransport(observation));
    request
        .getResolutionConstraints()
        .add(
            ResolverClient.forTransport(
                ResolutionConstraint.of(ResolutionConstraint.Type.Geometry, coverage)));
    request
        .getResolutionConstraints()
        .add(
            ResolverClient.forTransport(
                ResolutionConstraint.of(
                    ResolutionConstraint.Type.Parameters,
                    Parameters.create("nested", java.util.List.of(coverage)))));

    assertPlain(request.getObservation().getGeometry());
    assertPlain(
        (Geometry)
            request
            .getResolutionConstraints()
            .getFirst()
            .payload(Geometry.class)
            .getFirst());
    assertPlain(
        (Geometry)
            request
                .getResolutionConstraints()
                .get(1)
                .payload(Parameters.class)
                .getFirst()
                .getList("nested", Geometry.class)
                .getFirst());

    var mapper = JacksonConfiguration.newObjectMapper();
    var decoded =
        mapper.readValue(mapper.writeValueAsString(request), ResolutionRequest.class);
    assertPlain(decoded.getObservation().getGeometry());
    assertPlain(
        decoded.getResolutionConstraints().getFirst().payload(Geometry.class).getFirst());
    assertPlain(
        (Geometry)
            decoded
                .getResolutionConstraints()
                .get(1)
                .payload(Parameters.class)
                .getFirst()
                .getList("nested", Geometry.class)
                .getFirst());
  }

  private static void assertPlain(Geometry geometry) {
    assertFalse(geometry instanceof Scale);
    assertFalse(geometry instanceof Coverage);
  }
}
