package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class RuntimeSubmissionSynchronizationTest {
  @Test
  void synchronizesOnlyPersistedSubmissionResults() throws Exception {
    var client = mock(RuntimeClient.class);
    var scope = mock(ContextScope.class);
    var twin = mock(ClientDigitalTwin.class);
    var graph = mock(ClientKnowledgeGraph.class);
    when(scope.getDigitalTwin()).thenReturn(twin);
    when(twin.getKnowledgeGraph()).thenReturn(graph);
    var method = RuntimeClient.class.getDeclaredMethod(
        "synchronizeSubmission", Observation.class, ContextScope.class);
    method.setAccessible(true);
    assertNull(method.invoke(client, null, scope));
    for (long id : new long[] {Observation.QUERY_ID, 0, -20}) {
      var response = new ObservationImpl();
      response.setId(id);
      assertSame(response, method.invoke(client, response, scope));
    }
    verifyNoInteractions(graph);
    var committed = new ObservationImpl();
    committed.setId(42);
    assertSame(committed, method.invoke(client, committed, scope));
    verify(graph).ingest(committed);
  }
}
