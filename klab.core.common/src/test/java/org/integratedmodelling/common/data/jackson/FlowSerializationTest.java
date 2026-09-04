package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.FlowImpl;
import org.junit.jupiter.api.Test;

class FlowSerializationTest {

  @Test
  void flowTimestampsRoundTripAcrossTheSharedTransportMapper() {
    var created = Instant.parse("2026-09-04T12:00:00Z");
    var updated = Instant.parse("2026-09-04T12:01:00Z");
    var flow = Flow.create();
    flow.setId("flow-1");
    flow.setCreatedAt(created);
    flow.setUpdatedAt(updated);
    var state = Flow.State.create();
    state.setId("state-1");
    state.setCreatedAt(created);
    state.setUpdatedAt(updated);
    flow.getStates().put(state.getId(), state);
    var transaction = new FlowImpl.TransactionImpl();
    transaction.setId("transaction-1");
    transaction.setTimestamp(updated);
    flow.getHistory().add(transaction);

    var reconstructed = Utils.Json.parseObject(Utils.Json.asString(flow), Flow.class);

    assertNotNull(reconstructed);
    assertEquals(created, reconstructed.getCreatedAt());
    assertEquals(updated, reconstructed.getUpdatedAt());
    assertEquals(created, reconstructed.getStates().get("state-1").getCreatedAt());
    assertEquals(updated, reconstructed.getHistory().getFirst().getTimestamp());
  }
}
