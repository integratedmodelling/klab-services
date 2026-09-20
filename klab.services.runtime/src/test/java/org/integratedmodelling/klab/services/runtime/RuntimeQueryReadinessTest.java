package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class RuntimeQueryReadinessTest {
  @Test void unavailableContextTwinAndGraphAreRetryable() {
    var runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);
    var scope=mock(ContextScope.class);
    assertUnavailable(() -> runtime.queryKnowledgeGraph(null,null));
    assertUnavailable(() -> runtime.queryKnowledgeGraph(null,scope));
    when(scope.getDigitalTwin()).thenReturn(mock(DigitalTwin.class));
    assertUnavailable(() -> runtime.queryKnowledgeGraph(null,scope));
  }
  private void assertUnavailable(org.junit.jupiter.api.function.Executable call) {
    assertEquals(KnowledgeGraph.QueryException.Code.BACKEND_UNAVAILABLE,
        assertThrows(KnowledgeGraph.QueryException.class,call).getCode());
  }
}
