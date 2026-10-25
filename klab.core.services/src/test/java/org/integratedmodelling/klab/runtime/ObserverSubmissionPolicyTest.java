package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ObserverSubmissionPolicy;
import org.integratedmodelling.common.services.client.ResolverClient;
import org.integratedmodelling.common.services.client.scope.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.DefaultObserver;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.junit.jupiter.api.Test;

class ObserverSubmissionPolicyTest {
  @Test
  void engineBuilderCarriesSettingOnPrivateSubmissionScope() throws Exception {
    for (boolean disabled : new boolean[] {false, true}) {
      var settings = mock(Settings.class);
      when(settings.get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class)).thenReturn(disabled);
      var engine = mock(EngineImpl.class);
      when(engine.getSettings()).thenReturn(settings);
      var user = mock(UserIdentity.class);
      when(user.getId()).thenReturn("alice");
      when(user.getUsername()).thenReturn("alice");
      when(user.getData()).thenReturn(Parameters.create());
      var runtime = mock(RuntimeService.class);
      when(runtime.serviceId()).thenReturn("runtime");
      when(runtime.getUrl()).thenReturn(URI.create("http://localhost:8183").toURL());
      var session = new ClientSessionScope(new ClientUserScope(user, engine), "session", runtime);
      var root = new ClientContextScope(session, runtime,
          DigitalTwin.Configuration.builder().id("session.twin").name("Twin").build());
      var concept = new ConceptImpl();
      concept.setType(EnumSet.of(SemanticType.SUBJECT));
      concept.setUrn("test:Subject");
      var observable = new ObservableImpl();
      observable.setSemantics(concept);
      observable.setUrn("test:Subject");
      var pending = new CompletableFuture<Observation>();
      when(runtime.submit(any(), any())).thenAnswer(call -> {
        var submittedScope = call.getArgument(1, ContextScope.class);
        assertEquals(disabled, Boolean.TRUE.equals(submittedScope.getConstraint(
            ResolutionConstraint.Type.DoNotCreateDefaultObserver, Boolean.class)));
        return pending;
      });

      assertSame(pending, root.observation(observable).identity("test", "subject")
          .geometry(Geometry.UNIVERSAL).submit());
      assertNull(root.getConstraint(ResolutionConstraint.Type.DoNotCreateDefaultObserver, Boolean.class));
      verify(runtime).submit(any(), any());
    }
  }

  @Test
  void constraintSurvivesTransportAndFalseSettingDoesNotEraseSubmissionOptOut() {
    var constraint = ResolutionConstraint.of(ResolutionConstraint.Type.DoNotCreateDefaultObserver, true);
    var restored = Utils.Json.parseObject(Utils.Json.asString(ResolverClient.forTransport(constraint)),
        org.integratedmodelling.klab.api.services.resolver.objects.ResolutionConstraintImpl.class);
    assertEquals(constraint.getType(), restored.getType());
    assertEquals(java.util.List.of(true), restored.payload(Boolean.class));
    var scope = mock(ContextScope.class);
    var settings = mock(Settings.class);
    when(settings.get(Setting.DO_NOT_CREATE_A_DEFAULT_OBSERVER, Boolean.class)).thenReturn(false);
    assertSame(scope, ObserverSubmissionPolicy.apply(scope, settings));
    verify(scope, never()).withResolutionConstraints(any(ResolutionConstraint[].class));
  }

  @Test
  void onlyUnobservedUserRootSubmissionsPrepareDefaults() {
    var scope = mock(ContextScope.class);
    var observation = new ObservationImpl();
    assertTrue(DefaultObserverPreparation.shouldPrepare(observation, scope));
    clearInvocations(scope);
    when(scope.getConstraint(ResolutionConstraint.Type.DoNotCreateDefaultObserver, Boolean.class)).thenReturn(true);
    assertFalse(DefaultObserverPreparation.shouldPrepare(observation, scope));
    verify(scope, never()).getObserver();
    when(scope.getConstraint(ResolutionConstraint.Type.DoNotCreateDefaultObserver, Boolean.class)).thenReturn(false);
    when(scope.getObserver()).thenReturn(mock(Observation.class));
    assertFalse(DefaultObserverPreparation.shouldPrepare(observation, scope));
    when(scope.getObserver()).thenReturn(null);
    when(scope.getContextObservation()).thenReturn(mock(Observation.class));
    assertFalse(DefaultObserverPreparation.shouldPrepare(observation, scope));
    when(scope.getContextObservation()).thenReturn(null);
    when(scope.getTransactionId()).thenReturn("transaction");
    assertFalse(DefaultObserverPreparation.shouldPrepare(observation, scope));
    when(scope.getTransactionId()).thenReturn(null);
    observation.getMetadata().put(DefaultObserver.AUTOMATIC, true);
    assertFalse(DefaultObserverPreparation.shouldPrepare(observation, scope));
    observation.getMetadata().clear();
    observation.setId(Observation.QUERY_ID);
    assertFalse(DefaultObserverPreparation.shouldPrepare(observation, scope));
    observation.setId(12);
    assertFalse(DefaultObserverPreparation.shouldPrepare(observation, scope));
  }
}
