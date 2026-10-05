package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.base.EmailManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class CoreActorLibraryEmailTest {
  private final RuntimeAgent.Scope scope = mock(RuntimeAgent.Scope.class);
  private final ServiceUserScope serviceScope = mock(ServiceUserScope.class);
  private final BaseService service = mock(BaseService.class);
  private final EmailManager manager = mock(EmailManager.class);

  CoreActorLibraryEmailTest() {
    when(scope.getScope()).thenReturn(serviceScope);
    when(serviceScope.getService()).thenReturn(service);
    when(service.getEmailManager()).thenReturn(manager);
  }

  @Test void unavailableEmailIsOrdinaryFalseAndDoesNotUseConnectedPeers() {
    when(scope.getScope()).thenReturn(mock(UserScope.class));
    assertFalse(CoreActorLibrary.Email.configured(scope));
    assertEquals(Map.of("available", false, "enabled", false, "configured", false, "missing", List.of()),
        CoreActorLibrary.Email.status(scope));
    assertFalse(CoreActorLibrary.Email.send(scope, "to@example.org", "Subject", "Body").join());
    assertFalse(CoreActorLibrary.Email.sendHtml(null, "to@example.org", "Subject", "Body").join());
    verifyNoInteractions(manager);
    verify(scope.getScope(), never()).getService(any());
  }

  @Test void statusExposesOnlyAvailabilityAndConfigurationNames() {
    when(manager.getConfigurationStatus()).thenReturn(
        new EmailManager.ConfigurationStatus(true, List.of(Setting.EMAIL_PASSWORD)));
    assertEquals(Map.of("available", true, "enabled", true, "configured", false,
        "missing", List.of("EMAIL_PASSWORD")), CoreActorLibrary.Email.status(scope));
    when(manager.isConfigured()).thenReturn(true);
    assertTrue(CoreActorLibrary.Email.configured(scope));
  }

  @Test void suppliesTextAndHtmlResultsAndPreservesNonConfiguration() {
    when(manager.send("to@example.org", "Subject", "Body", false)).thenReturn(true);
    when(manager.send("to@example.org", "Subject", "<b>Body</b>", true)).thenReturn(true);
    assertTrue(CoreActorLibrary.Email.send(scope, "to@example.org", "Subject", "Body").join());
    assertTrue(CoreActorLibrary.Email.sendHtml(scope, "to@example.org", "Subject", "<b>Body</b>").join());
    when(manager.send("to@example.org", "Subject", "Body", false)).thenReturn(false);
    assertFalse(CoreActorLibrary.Email.send(scope, "to@example.org", "Subject", "Body").join());
  }

  @Test void deliveryFailureCompletesSupplierExceptionally() {
    var failure = new KlabIOException("SMTP unavailable");
    when(manager.send(anyString(), anyString(), anyString(), eq(false))).thenThrow(failure);
    var thrown = assertThrows(CompletionException.class,
        () -> CoreActorLibrary.Email.send(scope, "to@example.org", "Subject", "Body").join());
    assertSame(failure, thrown.getCause());
  }

  @Test void smtpWorkDoesNotBlockCallingAction() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(manager.send(anyString(), anyString(), anyString(), eq(false))).thenAnswer(call -> {
      assertTrue(Thread.currentThread().isVirtual());
      entered.countDown();
      assertTrue(release.await(5, TimeUnit.SECONDS));
      return true;
    });
    var result = CoreActorLibrary.Email.send(scope, "to@example.org", "Subject", "Body");
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertFalse(result.isDone());
    } finally { release.countDown(); }
    assertTrue(result.get(5, TimeUnit.SECONDS));
  }

  @Test void registryDiscoversStaticEmailVerbsWithSupplierResultTypes() throws Exception {
    var registry = mock(ComponentRegistry.class, CALLS_REAL_METHODS);
    var instances = ComponentRegistry.class.getDeclaredField("globalInstances");
    instances.setAccessible(true);
    // Discovery needs an actor instance, not a fully bootstrapped service configuration singleton.
    var actorInstances = new java.util.HashMap<Class<?>, Object>();
    actorInstances.put(CoreActorLibrary.Email.class, new CoreActorLibrary.Email());
    instances.set(registry, actorInstances);
    var discover = ComponentRegistry.class.getDeclaredMethod("createActorDescriptor", Actor.class, String.class, Class.class);
    discover.setAccessible(true);
    var descriptor = (Extensions.ActorDescriptor) discover.invoke(registry,
        CoreActorLibrary.Email.class.getAnnotation(Actor.class), "core.", CoreActorLibrary.Email.class);
    assertEquals("core.email", descriptor.urn);
    assertEquals(4, descriptor.verbs.size());
    for (var verb : descriptor.verbs) {
      var name = verb.serviceInfo.getName();
      assertTrue(name.startsWith("core.email."));
      assertSame(CoreActorLibrary.Email.class, registry.implementation(verb).implementation);
    }
    for (var method : CoreActorLibrary.Email.class.getDeclaredMethods()) {
      var verb = method.getAnnotation(Verb.class);
      if (verb == null) continue;
      assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
      assertEquals(verb.name().startsWith("send") ? Verb.Type.SUPPLIER : Verb.Type.FUNCTION,
          verb.executionType());
      if (verb.name().startsWith("send")) assertEquals(Boolean.class, verb.returns());
    }
  }
}
