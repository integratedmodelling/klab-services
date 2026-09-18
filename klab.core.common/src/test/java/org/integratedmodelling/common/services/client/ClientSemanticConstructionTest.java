package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.common.configuration.CommonConfiguration;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ClientSemanticConstructionTest {
  @Test
  void clientConfigurationBuildsTransportableUnusableSentinelsWithoutServices() throws Exception {
    var previous = Klab.INSTANCE.getConfiguration();
    try {
      Klab.INSTANCE.setConfiguration(new CommonConfiguration());
      var nothing = Concept.nothing();
      assertEquals("owl:Nothing", nothing.getUrn());
      assertTrue(nothing.is(SemanticType.NOTHING));
      var observable = Observable.nothing(null);
      assertEquals("owl:Nothing", observable.getUrn());
      var mapper = JacksonConfiguration.newObjectMapper();
      var returned =
          mapper.readValue(
              mapper.writerFor(Observable.class).writeValueAsString(observable), Observable.class);
      assertTrue(returned.is(SemanticType.NOTHING));
      var builder =
          mock(
              ObservationBuilderImpl.class,
              withSettings()
                  .useConstructor(returned, mock(ContextScope.class))
                  .defaultAnswer(CALLS_REAL_METHODS));
      assertTrue(builder.build().isEmpty());
      assertInstanceOf(
          ObservableBuildStrategy.class,
          Klab.INSTANCE.getConfiguration().getObservableBuilder(nothing, mock(ContextScope.class)));
    } finally {
      Klab.INSTANCE.setConfiguration(previous);
    }
  }
}
