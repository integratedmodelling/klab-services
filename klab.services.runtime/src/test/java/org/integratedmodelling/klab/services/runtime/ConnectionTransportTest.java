package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.data.SerializingDataBuilder;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.data.WrappingDataBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConnectionTransportTest {
  @BeforeAll static void setup() { ServiceConfiguration.injectInstantiators(); }

  @Test void endpointReferencesSurviveObservationJsonWithoutRecursiveParticipantGraphs() throws Exception {
    var source = ConnectionExecutionTest.observation(-11, SemanticType.SUBJECT, false, false);
    var target = ConnectionExecutionTest.observation(-12, SemanticType.SUBJECT, false, false);
    var relationship = ConnectionExecutionTest.observation(-20, SemanticType.RELATIONSHIP, false, true);
    relationship.setParticipants(List.of(source, target));
    source.setParticipants(List.of(relationship)); // transport uses references, even for a graph cycle
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writerFor(Observation.class)
        .writeValueAsString(Observation.forTransport(relationship)), Observation.class);
    assertEquals(List.of(-11L, -12L), restored.getParticipants().stream().map(Observation::getId).toList());
    assertTrue(restored.getParticipants().getFirst().getParticipants().isEmpty());
  }

  @Test void remoteConnectorRetainsIdentityAndRestoresTransactionLocalParticipants() {
    var source = ConnectionExecutionTest.observation(-11, SemanticType.SUBJECT, false, false);
    var target = ConnectionExecutionTest.observation(-12, SemanticType.SUBJECT, false, false);
    var collective = ConnectionExecutionTest.observation(-10, SemanticType.RELATIONSHIP, true, false);
    var observable = ConnectionExecutionTest.observation(-20, SemanticType.RELATIONSHIP, false, false).getObservable();
    var builder = new SerializingDataBuilder("connections", collective.getObservable(), null, Geometry.UNIVERSAL, null);
    builder.relationship("link", observable, null, Urn.of("connector:link"), source, target);
    var data = builder.build();
    assertEquals(1, data.children().size());
    assertEquals("connector:link", data.children().getFirst().identity().getUrn());
    var scope = mock(ContextScope.class);
    var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveObservable(observable.getUrn())).thenReturn(observable);
    when(scope.getObservation(-11L)).thenReturn(source);
    when(scope.getObservation(-12L)).thenReturn(target);
    when(scope.observation(observable)).thenAnswer(ignored -> new ObservationBuilderImpl(observable, scope) {
      public Observation register() { return build(); }
      public CompletableFuture<Observation> submit() { return CompletableFuture.completedFuture(build()); }
    });
    var wrapped = new WrappingDataBuilder(data, collective, null, null, "connector", scope);
    var result = wrapped.getObjects().getFirst().getObservation();
    assertFalse(result.isEmpty());
    assertEquals("connector:link", result.getUrn());
    assertEquals(List.of(source, target), result.getParticipants());
    assertFalse(result.getMetadata().containsKey("im:relationship-source-id"));
  }
}
