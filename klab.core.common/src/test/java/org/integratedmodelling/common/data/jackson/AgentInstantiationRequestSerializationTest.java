package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.rest.AgentInstantiationRequest;
import org.junit.jupiter.api.Test;

class AgentInstantiationRequestSerializationTest {

  @Test
  void serializesBehaviorAsAnObject() throws Exception {
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("test.behavior");
    behavior.getStatements().add(new KActorsActionImpl());
    var navigableBehavior =
        mock(KActorsBehavior.class, withSettings().extraInterfaces(NavigableAsset.class));
    when(((NavigableAsset) navigableBehavior).getDelegate()).thenReturn(behavior);

    var request = new AgentInstantiationRequest();
    request.setBehavior(navigableBehavior);
    request.setCompileOnly(true);
    request.setObservationId(42L);
    request.setSuggestedName("test-agent");

    var mapper = JacksonConfiguration.newObjectMapper();
    var json = mapper.writeValueAsString(request);
    var tree = mapper.readTree(json);
    var restored = mapper.readValue(json, AgentInstantiationRequest.class);

    assertTrue(tree.get("behavior").isObject());
    assertEquals("test.behavior", tree.get("behavior").get("urn").asText());
    assertEquals("test.behavior", restored.getBehavior().getUrn());
    assertEquals(1, restored.getBehavior().getStatements().size());
    assertTrue(restored.isCompileOnly());
    assertEquals(42L, restored.getObservationId());
    assertEquals("test-agent", restored.getSuggestedName());
  }
}
