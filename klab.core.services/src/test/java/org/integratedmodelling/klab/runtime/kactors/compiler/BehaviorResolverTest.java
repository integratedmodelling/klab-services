package org.integratedmodelling.klab.runtime.kactors.compiler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.junit.jupiter.api.Test;

class BehaviorResolverTest {
  private static final String URN = "test.remote";
  private final UserScope scope = mock(UserScope.class);
  private final ResourcesService resources = mock(ResourcesService.class);
  private final AgentCompiler.Resolver resolver = new AgentCompiler.Resolver() {};

  BehaviorResolverTest() {
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(resources.serviceId()).thenReturn("local");
  }

  private ResourceSet resolution(long timestamp) {
    var resource = new ResourceSet.Resource();
    resource.setResourceUrn(URN);
    resource.setServiceId("local");
    resource.setKnowledgeClass(KnowledgeClass.BEHAVIOR);
    resource.setTimestamp(timestamp);
    return ResourceSet.of(resource);
  }

  @Test
  void reusesSemanticBeanButChecksTimestampOnEveryUse() {
    var bean = new KActorsBehaviorImpl();
    when(resources.resolve(URN, KnowledgeClass.BEHAVIOR, scope)).thenReturn(resolution(10));
    when(resources.retrieve(URN, KActorsBehavior.class, scope)).thenReturn(bean);
    assertSame(bean, resolver.resolveBehavior(URN, scope));
    assertSame(bean, new AgentCompiler.Resolver() {}.resolveBehavior(URN, scope));
    verify(resources, times(2)).resolve(URN, KnowledgeClass.BEHAVIOR, scope);
    verify(resources).retrieve(URN, KActorsBehavior.class, scope);
  }

  @Test
  void refreshesOnTimestampChangesIncludingSourceRollback() {
    var first = new KActorsBehaviorImpl();
    var updated = new KActorsBehaviorImpl();
    var rollback = new KActorsBehaviorImpl();
    when(resources.resolve(URN, KnowledgeClass.BEHAVIOR, scope))
        .thenReturn(resolution(10), resolution(20), resolution(5));
    when(resources.retrieve(URN, KActorsBehavior.class, scope)).thenReturn(first, updated, rollback);
    assertSame(first, resolver.resolveBehavior(URN, scope));
    assertSame(updated, resolver.resolveBehavior(URN, scope));
    assertSame(rollback, resolver.resolveBehavior(URN, scope));
    verify(resources, times(3)).retrieve(URN, KActorsBehavior.class, scope);
  }

  @Test
  void missingBehaviorEvictsCacheAndDoesNotMistakeDependencyForResult() {
    var bean = new KActorsBehaviorImpl();
    var missing = new ResourceSet();
    missing.getBehaviors().add(resolution(10).getResults().iterator().next());
    when(resources.resolve(URN, KnowledgeClass.BEHAVIOR, scope))
        .thenReturn(resolution(10), missing, resolution(10));
    when(resources.retrieve(URN, KActorsBehavior.class, scope)).thenReturn(bean);
    assertSame(bean, resolver.resolveBehavior(URN, scope));
    assertNull(resolver.resolveBehavior(URN, scope));
    assertSame(bean, resolver.resolveBehavior(URN, scope));
    verify(resources, times(2)).retrieve(URN, KActorsBehavior.class, scope);
  }

  @Test
  void failedRefreshDoesNotReturnOldBeanAndRetriesRetrieval() {
    var first = new KActorsBehaviorImpl();
    var updated = new KActorsBehaviorImpl();
    when(resources.resolve(URN, KnowledgeClass.BEHAVIOR, scope))
        .thenReturn(resolution(10), resolution(20), resolution(20));
    when(resources.retrieve(URN, KActorsBehavior.class, scope)).thenReturn(first, null, updated);
    assertSame(first, resolver.resolveBehavior(URN, scope));
    assertNull(resolver.resolveBehavior(URN, scope));
    assertSame(updated, resolver.resolveBehavior(URN, scope));
  }

  @Test
  void cachesRemainIsolatedBetweenScopes() {
    var other = mock(UserScope.class);
    when(other.getService(ResourcesService.class)).thenReturn(resources);
    var first = new KActorsBehaviorImpl();
    var second = new KActorsBehaviorImpl();
    when(resources.resolve(eq(URN), eq(KnowledgeClass.BEHAVIOR), any()))
        .thenReturn(resolution(10));
    when(resources.retrieve(URN, KActorsBehavior.class, scope)).thenReturn(first);
    when(resources.retrieve(URN, KActorsBehavior.class, other)).thenReturn(second);
    assertSame(first, resolver.resolveBehavior(URN, scope));
    assertSame(second, resolver.resolveBehavior(URN, other));
  }

  @Test
  void retrievesCanonicalUrnFromOwningServiceAndRefreshesWhenOwnerChanges() {
    var remote = mock(ResourcesService.class);
    when(remote.serviceId()).thenReturn("remote");
    when(scope.findService(eq(ResourcesService.class), any())).thenReturn(Optional.of(remote));
    var localResult = resolution(10);
    var remoteResult = resolution(10);
    remoteResult.getResults().iterator().next().setServiceId("remote");
    String qualified = "workspace/project/" + URN;
    var first = new KActorsBehaviorImpl();
    var second = new KActorsBehaviorImpl();
    when(resources.resolve(qualified, KnowledgeClass.BEHAVIOR, scope))
        .thenReturn(localResult, remoteResult);
    when(resources.retrieve(URN, KActorsBehavior.class, scope)).thenReturn(first);
    when(remote.retrieve(URN, KActorsBehavior.class, scope)).thenReturn(second);
    assertSame(first, resolver.resolveBehavior(qualified, scope));
    assertSame(second, resolver.resolveBehavior(qualified, scope));
    verify(remote).retrieve(URN, KActorsBehavior.class, scope);
  }

  @Test
  void knownJavaActorsBypassResolutionAndTimestampChecksOnEveryUse() {
    var descriptor = new Extensions.ActorDescriptor();
    descriptor.urn = "core.context";
    var actorResolver = new AgentCompiler.Resolver() {
      @Override
      public AgentCompiler.ResolvedActor resolveActor(String urn, UserScope userScope) {
        return descriptor.urn.equals(urn)
            ? new AgentCompiler.ResolvedActor(descriptor, null)
            : null;
      }
    };
    assertNull(actorResolver.resolveBehavior(descriptor.urn, scope));
    assertNull(actorResolver.resolveBehavior(descriptor.urn, scope));
    assertNull(actorResolver.resolveBehavior(AgentCompiler.CORE_AGENT_URN, scope));
    verifyNoInteractions(resources);
    verify(scope, never()).getService(ResourcesService.class);
  }

  @Test
  void ignoresInvalidUrnsAndCoreActorWithoutResourceRequests() {
    assertNull(resolver.resolveBehavior(null, scope));
    assertNull(resolver.resolveBehavior(" ", scope));
    assertNull(resolver.resolveBehavior(AgentCompiler.CORE_AGENT_URN, scope));
    assertNull(resolver.resolveBehavior(URN, null));
    verifyNoInteractions(resources);
  }
}
