package org.integratedmodelling.klab.services.runtime;

import static org.mockito.Mockito.*;

/** Supplies the runtime-owned controller to fixtures which mock service/persistence infrastructure. */
public final class ShardExecutionTestSupport {
  private ShardExecutionTestSupport() {}

  public static ShardExecution controller(int limit) {
    return new ShardExecution(() -> limit);
  }

  public static RuntimeService runtime(int limit) {
    var runtime = mock(RuntimeService.class);
    when(runtime.shardExecution()).thenReturn(controller(limit));
    return runtime;
  }
}
