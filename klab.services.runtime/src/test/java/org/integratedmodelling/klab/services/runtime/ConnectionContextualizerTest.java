package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.Test;

class ConnectionContextualizerTest {
  public static void connect(Data.Builder builder, Observable observable, Geometry geometry,
      ContextScope scope, Observation source, Observation target) {
    assertNotNull(source); assertNotNull(target);
    builder.relationship("link", Observable.promote(observable.getSemantics().singular()), geometry,
        Urn.of("connector:link"), source, target);
  }

  @Test void localConnectorReceivesNamedEndpointInputsAndEmitsIndividualObservations() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var source = ConnectionExecutionTest.observation(-11, SemanticType.SUBJECT, false, false);
    var target = ConnectionExecutionTest.observation(-12, SemanticType.SUBJECT, false, false);
    var collective = ConnectionExecutionTest.observation(-10, SemanticType.RELATIONSHIP, true, false);
    collective.setGeometry(Geometry.UNIVERSAL);
    var scope = mock(ContextScope.class);
    when(scope.observation(any(Observable.class))).thenAnswer(call -> new ObservationBuilderImpl(call.getArgument(0, Observable.class), scope) {
      public Observation register() { return build(); }
      public CompletableFuture<Observation> submit() { return CompletableFuture.completedFuture(build()); }
    });
    var registry = mock(ComponentRegistry.class);
    var descriptor = new Extensions.FunctionDescriptor(); descriptor.staticMethod = true;
    descriptor.serviceInfo = mock(ServiceInfo.class);
    var sourceInput = mock(ServiceInfo.Argument.class); when(sourceInput.getName()).thenReturn("source");
    var targetInput = mock(ServiceInfo.Argument.class); when(targetInput.getName()).thenReturn("target");
    when(descriptor.serviceInfo.listInputs()).thenReturn(List.of(sourceInput, targetInput));
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method = getClass().getMethod("connect", Data.Builder.class, Observable.class,
        Geometry.class, ContextScope.class, Observation.class, Observation.class);
    when(registry.implementation(descriptor)).thenReturn(implementation);
    var executor = new ContextualizerExecutor(registry,
        new CompiledDataflow.CallDescriptors(null, descriptor, null, null), collective,
        Map.of("source", source, "target", target), new ServiceCallImpl(), scope);
    var outcomes = new ContextualizationScopeImpl(collective, null);
    assertTrue(executor.run(null, Map.of(), scope, outcomes));
    assertEquals(1, outcomes.getOutcomes().size());
    var result = outcomes.getOutcomes().getFirst();
    assertFalse(result.isEmpty()); assertEquals("connector:link", result.getUrn());
    assertEquals(List.of(source, target), result.getParticipants());
  }
}
