package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RuntimeContextLifecycleValidationTest {
  @Test
  void missingPersistenceIsRejectedBeforeGraphOrScopeMutation() {
    assertRejected(DigitalTwin.Configuration.builder().name("missing-persistence").build());
  }

  @Test
  void missingConfigurationIsRejectedBeforeGraphOrScopeMutation() {
    assertRejected(null);
  }

  @ParameterizedTest
  @EnumSource(Persistence.class)
  void explicitPersistencePassesAdmissionWithoutInventingAnotherPolicy(Persistence persistence) {
    var runtime = runtime();
    var context = mock(ServiceContextScope.class);
    var configuration = DigitalTwin.Configuration.builder().persistence(persistence).build();
    when(context.getHostServiceId()).thenReturn("runtime");
    when(context.getConfiguration()).thenReturn(configuration);
    var boundary = new IllegalStateException("normal scope identity path reached");
    when(context.getParentScope()).thenThrow(boundary);
    assertSame(boundary, assertThrows(IllegalStateException.class,
        () -> runtime.declareContextScope(context, mock(SessionScope.class), mock(UserScope.class))));
    assertSame(persistence, configuration.getPersistence());
    verify(runtime, never()).getMainKnowledgeGraph();
  }

  private void assertRejected(DigitalTwin.Configuration configuration) {
    var runtime = runtime();
    var context = mock(ServiceContextScope.class);
    var session = mock(SessionScope.class);
    var user = mock(UserScope.class);
    when(context.getHostServiceId()).thenReturn("runtime");
    when(context.getConfiguration()).thenReturn(configuration);
    assertThrows(KlabIllegalArgumentException.class,
        () -> runtime.declareContextScope(context, session, user));
    verify(context, never()).setId(any());
    verify(context, never()).setDigitalTwin(any());
    verify(runtime, never()).getMainKnowledgeGraph();
    verify(runtime, never()).getScopeManager();
    verifyNoInteractions(session, user);
    if (configuration != null) {
      assertNull(configuration.getId());
      assertNull(configuration.getOwner());
    }
  }

  private RuntimeService runtime() {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    doReturn("runtime").when(runtime).serviceId();
    return runtime;
  }
}
