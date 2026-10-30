package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RuntimeObserverSettingsTest {
  @Test
  void enginePreferenceUsesEngineSettingsInsteadOfRuntimeSettings() throws Exception {
    for (boolean disabled : new boolean[] {false, true}) {
      var scope = mock(ClientContextScope.class);
      var selected = mock(ContextScope.class);
      var engine = mock(EngineImpl.class);
      var settings = mock(Settings.class);
      when(scope.getEngine()).thenReturn(engine);
      when(engine.getSettings()).thenReturn(settings);
      when(settings.get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class)).thenReturn(disabled);
      when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(selected);
      when(selected.getResolutionConstraints()).thenReturn(List.of(
          ResolutionConstraint.of(ResolutionConstraint.Type.DoNotCreateDefaultObserver, true)));
      var submittedScope = disabled ? selected : scope;
      var request = submit(scope, submittedScope);
      assertEquals(disabled, request.getResolutionConstraints().stream()
          .anyMatch(c -> c.getType() == ResolutionConstraint.Type.DoNotCreateDefaultObserver
              && Boolean.TRUE.equals(c.payload(Boolean.class).getFirst())));
      verify(settings).get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class);
    }
  }

  @Test
  void serviceSubmissionPreservesSuppliedConstraintWithoutReadingRuntimeSettings() throws Exception {
    var scope = mock(ContextScope.class);
    when(scope.getResolutionConstraints()).thenReturn(List.of(
        ResolutionConstraint.of(ResolutionConstraint.Type.DoNotCreateDefaultObserver, true)));
    var request = submit(scope, scope);
    assertEquals(ResolutionConstraint.Type.DoNotCreateDefaultObserver,
        request.getResolutionConstraints().getFirst().getType());
    assertEquals(List.of(true), request.getResolutionConstraints().getFirst().payload(Boolean.class));
  }

  @Test
  void clientScopeWithoutEngineSubmitsWithExistingConstraints() throws Exception {
    var scope = mock(ClientContextScope.class);
    assertTrue(submit(scope, scope).getResolutionConstraints().stream()
        .noneMatch(c -> c.getType() == ResolutionConstraint.Type.DoNotCreateDefaultObserver));
  }

  private ResolutionRequest submit(ContextScope scope, ContextScope submittedScope) throws Exception {
    var runtime = mock(RuntimeClient.class);
    doCallRealMethod().when(runtime).submit(any(), any());
    when(runtime.settings()).thenThrow(new KlabIllegalArgumentException(
        "Setting DO_NOT_CREATE_A_DEFAULT_OBSERVER does not belong to RUNTIME settings"));
    var http = mock(Utils.Http.Client.class, RETURNS_SELF);
    var field = BaseServiceClient.class.getDeclaredField("client");
    field.setAccessible(true);
    field.set(runtime, http);
    var agent = mock(Agent.class);
    when(agent.getName()).thenReturn("alice");
    var constraints = new java.util.ArrayList<>(submittedScope.getResolutionConstraints());
    constraints.add(ResolutionConstraint.of(ResolutionConstraint.Type.Provenance, agent));
    when(submittedScope.getResolutionConstraints()).thenReturn(constraints);
    var request = ArgumentCaptor.forClass(ResolutionRequest.class);
    when(http.postAsync(anyString(), request.capture(), eq(Observation.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    assertNull(runtime.submit(new ObservationImpl(), scope).join());
    verify(http).withScope(submittedScope);
    verify(runtime, never()).settings();
    return request.getValue();
  }
}
