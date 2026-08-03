package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsArgumentsImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.rest.AgentInstantiationRequest;
import org.junit.jupiter.api.Test;

class AgentInstantiationRequestSerializationTest {

  @Test
  void serializesBehaviorAsAnObject() throws Exception {
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("test.behavior");
    var action = new KActorsActionImpl();
    action.getArguments().add(new KActorsActionImpl.ArgumentImpl("message"));
    behavior.getStatements().add(action);
    var navigableBehavior =
        mock(KActorsBehavior.class, withSettings().extraInterfaces(NavigableAsset.class));
    when(((NavigableAsset) navigableBehavior).getDelegate()).thenReturn(behavior);

    var request = new AgentInstantiationRequest();
    request.setBehavior(navigableBehavior);
    request.setCompileOnly(true);
    request.setDoNotStart(true);
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
    assertTrue(
        restored.getBehavior().getStatements().getFirst().getArguments().getFirst()
            instanceof KActorsActionImpl.ArgumentImpl);
    assertEquals(
        "message",
        restored.getBehavior().getStatements().getFirst().getArguments().getFirst().getName());
    assertTrue(restored.isCompileOnly());
    assertTrue(restored.isDoNotStart());
    assertEquals(42L, restored.getObservationId());
    assertEquals("test-agent", restored.getSuggestedName());
  }

  @Test
  void statementValuedCallArgumentsRemainPojoBeansAcrossJson() throws Exception {
    var nested = new KActorsStatementImpl.VerbImpl();
    nested.setRecipient("self");
    nested.setMessage("produce");
    nested.setArguments(new KActorsArgumentsImpl());
    var argument = new KActorsStatementImpl.CallArgumentImpl();
    argument.setFunction(nested);
    argument.setAdaptedBehaviorUrn("test.product");
    var nestedSwitch = new KActorsStatementImpl.SwitchImpl();
    nestedSwitch.setFunction(nested);
    var switchArgument = new KActorsStatementImpl.CallArgumentImpl();
    switchArgument.setSwitch(nestedSwitch);
    var outer = new KActorsStatementImpl.VerbImpl();
    outer.setRecipient("self");
    outer.setMessage("consume");
    outer.setArguments(new KActorsArgumentsImpl());
    outer.getArguments().putUnnamed(argument);
    outer.getArguments().putUnnamed(switchArgument);
    var action = new KActorsActionImpl();
    action.setUrn("main");
    action.getCode().add(outer);
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("test.arguments");
    behavior.getStatements().add(action);

    var mapper = JacksonConfiguration.newObjectMapper();
    var json = mapper.writeValueAsString(behavior);
    var restored = mapper.readValue(json, KActorsBehavior.class);

    var restoredCall =
        (KActorsStatement.Verb) restored.getStatements().getFirst().getCode().getFirst();
    var restoredArgument =
        (KActorsStatement.CallArgument) restoredCall.getArguments().getUnnamedArguments().getFirst();
    assertEquals("produce", restoredArgument.getFunction().getMessage());
    assertEquals("test.product", restoredArgument.getAdaptedBehaviorUrn());
    var restoredSwitchArgument =
        (KActorsStatement.CallArgument) restoredCall.getArguments().getUnnamedArguments().get(1);
    assertEquals("produce", restoredSwitchArgument.getSwitch().getFunction().getMessage());
  }

  @Test
  void objectValuedEmptyCollectionsRemainCollectionsAcrossJson() throws Exception {
    var value = new KActorsValueImpl();
    value.setType(ValueType.LIST);
    value.setStatedValue(new ArrayList<>());

    var mapper = JacksonConfiguration.newObjectMapper();
    var restored =
        mapper.readValue(mapper.writeValueAsString(value), KActorsValue.class);

    assertEquals(ValueType.LIST, restored.getType());
    assertTrue(restored.getValue(Object.class) instanceof ArrayList<?>);
    assertTrue(((ArrayList<?>) restored.getValue(Object.class)).isEmpty());
  }
}
