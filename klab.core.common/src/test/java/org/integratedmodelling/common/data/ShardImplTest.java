package org.integratedmodelling.common.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.junit.jupiter.api.Test;

class ShardImplTest {

  @Test
  void newShardIsUnassignedUntilStoredInTheKnowledgeGraph() {
    assertTrue(new ShardImpl().getId() < 0);
  }
}
