package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.identities.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class AcceptedSemanticRuntimePreflightTest {
  private final AtomicLong clock=new AtomicLong();
  private final AtomicReference<String> dependency=new AtomicReference<>("dependency-1");
  private final SemanticEnvironmentFactory factory=mock(SemanticEnvironmentFactory.class);
  private final SemanticEnvironment state=mock(SemanticEnvironment.class);
  private final ServiceScope scope=mock(ServiceScope.class);
  private final UserIdentity owner=mock(UserIdentity.class);
  private final List<Notification> errors=List.of(Notification.error("invalid source"));
  AcceptedSemanticRuntimePreflightTest() throws Exception {
    when(owner.getUsername()).thenReturn("owner");when(owner.getId()).thenReturn("test-owner-context");when(owner.getGroups()).thenReturn(List.of());when(scope.getIdentity()).thenReturn(owner);
    when(factory.dependencyRevision()).thenAnswer(i->dependency.get());
    when(factory.capture(any())).thenAnswer(i->{Worldview w=i.getArgument(0);return new CandidatePublicationCoordinator.Input<>(
        SemanticEnvironmentFactory.sources(w),SemanticEnvironmentFactory.sourceOrder(w),dependency.get(),
        SemanticEnvironmentFactory.CONFIGURATION_REVISION,w,CandidatePublicationCoordinator.BeanStatus.VALID,List.of());});
    when(factory.build(any(),any())).thenReturn(new CandidatePublicationCoordinator.Result<>(state,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()));
  }
  private WorldviewImpl world(String source) {
    var ontology=new KimOntologyImpl();ontology.setUrn("w");ontology.setProjectName("fixture");ontology.setSourceCode(source);
    var w=new WorldviewImpl();w.setUrn("w");w.setWorldviewId("w");w.setEmpty(false);w.setOntologies(new ArrayList<>(List.of(ontology)));return w;
  }
  private AcceptedSemanticRuntime runtime(){return new AcceptedSemanticRuntime(factory,clock::get);}

  @Test void repeatedSamePrincipalRequestsWithDifferentCredentialsDoNotRebuild() throws Exception {
    try (var runtime = runtime()) {
      var source = world("unchanged");
      runtime.submit(source, scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var generation = runtime.status().acceptedGeneration();
      for (int i = 0; i < 5; i++) {
        when(owner.getId()).thenReturn("credential-" + i);
        assertFalse(runtime.shouldRetryPreflight(source, scope));
        assertFalse(runtime.beginPreflight(source, scope).retry());
      }
      assertEquals(generation, runtime.status().acceptedGeneration());
      verify(factory, times(1)).build(any(), any());
      var group = mock(Group.class); when(group.getName()).thenReturn("changed-membership");
      when(owner.getGroups()).thenReturn(List.of(group));
      assertTrue(runtime.shouldRetryPreflight(source, scope));
    }
  }

  @Test void acceptedMetadataIncludesOwnedComponentsAndRetainsThemAfterRejection() throws Exception {
    var actor = mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class);
    var descriptor = mock(org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor.class);
    when(state.actor()).thenReturn(actor);
    when(actor.semanticCapabilitiesSnapshot()).thenReturn(SemanticCapabilitiesSnapshot.initial());
    when(actor.capturedAuthorityComponents()).thenReturn(List.of(descriptor));
    try (var runtime = runtime()) {
      assertTrue(runtime.metadata().components().isEmpty());
      runtime.submit(world("accepted")); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var accepted = runtime.metadata();
      assertTrue(accepted.generation() > 0);
      assertEquals(List.of(descriptor), accepted.components());
      runtime.rejectPreflight(runtime.beginPreflight(world("rejected"), scope), errors, scope);
      assertEquals(accepted, runtime.metadata());
    }
  }

  @Test void blockedCorePreflightExplainsUnavailableValidationWithoutStartingWorker() throws Exception {
    var source = world("first");
    var diagnostic = "CORE_ALIAS_TARGET_UNAVAILABLE: odo:PhysicalDomain";
    doReturn(new CandidatePublicationCoordinator.Input<>(
        SemanticEnvironmentFactory.sources(source), SemanticEnvironmentFactory.sourceOrder(source),
        dependency.get(), SemanticEnvironmentFactory.CONFIGURATION_REVISION, source,
        CandidatePublicationCoordinator.BeanStatus.INVALID, List.of(diagnostic))).when(factory).capture(any());
    try (var runtime = runtime()) {
      runtime.submit(source);
      var request = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(
          source.getOntologies().getFirst(), "1");
      var response = runtime.validate(request,
          org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(request));
      assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.FAILED,
          response.getStatus());
      assertTrue(response.getReason().contains(diagnostic));
      assertTrue(response.getReason().contains("blocked"));
      assertEquals(0, response.getKnowledgeRevision());
      assertFalse(response.valid());
      assertEquals(1, response.getNotifications().size());
      assertNotNull(response.getNotifications().getFirst().getLexicalContext());
      verify(factory, never()).build(any(), any());
    }
  }

  @Test void unchangedRejectedInputDoesNotInvalidateOrAdvanceGenerationBeforeBackoff() {
    try(var runtime=runtime()) {
      var source=world("first");var first=runtime.beginPreflight(source,scope);assertTrue(runtime.rejectPreflight(first,errors,scope));
      var generation=runtime.status().inputGeneration();
      for(int i=0;i<5;i++) {assertFalse(runtime.shouldRetryPreflight(source,scope));assertFalse(runtime.beginPreflight(source,scope).retry());}
      assertEquals(generation,runtime.status().inputGeneration());assertEquals(List.of("invalid source"),runtime.status().diagnostics());
    }
  }

  @Test void transientFailureRetriesWithCappedBackoff() {
    try(var runtime=runtime()) {
      var source=world("first");
      for(long seconds:List.of(10L,20L,40L,60L,60L)) {
        var attempt=runtime.beginPreflight(source,scope);assertTrue(attempt.retry());assertTrue(runtime.rejectPreflight(attempt,errors,scope));
        clock.addAndGet(Duration.ofSeconds(seconds).toNanos()-1);assertFalse(runtime.shouldRetryPreflight(source,scope));
        clock.incrementAndGet();assertTrue(runtime.shouldRetryPreflight(source,scope));
      }
    }
  }

  @Test void changedSourceProviderRevisionAndOwnerAccessRetryImmediately() {
    try(var runtime=runtime()) {
      var source=world("first");assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source,scope),errors,scope));
      assertTrue(runtime.shouldRetryPreflight(world("second"),scope));
      source.getMetadata().put("authority.catalog.revision","provider-2");assertTrue(runtime.shouldRetryPreflight(source,scope));
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source,scope),errors,scope));
      dependency.set("dependency-2");assertTrue(runtime.shouldRetryPreflight(source,scope));
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source,scope),errors,scope));
      var group=mock(Group.class);when(group.getName()).thenReturn("NEW_ACCESS");when(owner.getGroups()).thenReturn(List.of(group));
      assertTrue(runtime.shouldRetryPreflight(source,scope));
    }
  }

  @Test void explicitInvalidationRefreshesWithoutSourceEdit() {
    try(var runtime=runtime()) {
      var source=world("first");runtime.rejectPreflight(runtime.beginPreflight(source,scope),errors,scope);
      runtime.invalidate();assertTrue(runtime.shouldRetryPreflight(source,scope));
    }
  }

  @Test void incidentalNotificationTimestampsDoNotChangeInputIdentity() {
    try(var runtime=runtime()) {
      var source=world("first");source.getNotifications().add(Notification.warning("provider pending"));
      runtime.rejectPreflight(runtime.beginPreflight(source,scope),errors,scope);
      source.getNotifications().clear();source.getNotifications().add(Notification.warning("provider pending"));
      assertFalse(runtime.shouldRetryPreflight(source,scope));
    }
  }

  @Test void preflightOwnsDeepSnapshot() {
    try(var runtime=runtime()) {
      var source=world("first");var attempt=runtime.beginPreflight(source,scope);
      ((KimOntologyImpl)source.getOntologies().getFirst()).setSourceCode("mutated");source.getMetadata().put("authority.catalog.revision","mutated");
      assertEquals("first",attempt.snapshot().getOntologies().getFirst().getSourceCode());
      assertNull(attempt.snapshot().getMetadata().get("authority.catalog.revision"));
      assertTrue(runtime.rejectPreflight(attempt,errors,scope));
    }
  }

  @Test void delayedRejectionCannotCancelNewCandidateOrOverwriteStatus() throws Exception {
    try(var runtime=runtime()) {
      var a=runtime.beginPreflight(world("A"),scope);var b=runtime.beginPreflight(world("B"),scope);
      assertTrue(runtime.submit(b.snapshot(),b,scope));assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      var before=runtime.status();assertFalse(runtime.rejectPreflight(a,errors,scope));
      assertEquals(before,runtime.status());assertTrue(before.acceptedGeneration()>0);
    }
  }

  @Test void rejectedInputLeavesAcceptedKnowledgeReadable() throws Exception {
    try(var runtime=runtime()) {
      runtime.submit(world("accepted"));assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long accepted=runtime.status().acceptedGeneration();runtime.rejectPreflight(runtime.beginPreflight(world("rejected"),scope),errors,scope);
      assertEquals(accepted,runtime.status().acceptedGeneration());try(var lease=runtime.lease()){assertEquals(accepted,lease.generation());assertSame(state,lease.state());}
    }
  }

  @Test void closedRuntimeDoesNotRetryOrAcceptDelayedResults() {
    var runtime=runtime();var source=world("first");var attempt=runtime.beginPreflight(source,scope);runtime.close();
    assertFalse(runtime.shouldRetryPreflight(source,scope));assertFalse(runtime.beginPreflight(source,scope).retry());
    assertFalse(runtime.rejectPreflight(attempt,errors,scope));assertFalse(runtime.submit(source,attempt,scope));
  }

  private void bootstrapEnabled() {
    when(factory.authorityBootstrapEnabled()).thenReturn(true);
    when(factory.publicationCurrent(any())).thenReturn(true);
    when(factory.capture(any(),same(scope))).thenAnswer(i->factory.capture(i.getArgument(0)));
  }
  @Test void optInBootstrapDoesNotRepeatSubmittedInputOnEveryPoll() throws Exception {
    bootstrapEnabled();try(var runtime=runtime()) {
      var source=world("first");assertTrue(runtime.submit(source,runtime.beginPreflight(source,scope),scope));
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));long generation=runtime.status().inputGeneration();
      for(int i=0;i<5;i++) {assertFalse(runtime.shouldRetryPreflight(source,scope));assertFalse(runtime.beginPreflight(source,scope).retry());}
      assertEquals(generation,runtime.status().inputGeneration());assertTrue(runtime.shouldRetryPreflight(world("changed"),scope));
      verify(factory,times(1)).capture(any(),same(scope));
    }
  }
  @Test void bootstrapPendingAndFailureKeepPreviousAcceptedGenerationAvailable() throws Exception {
    bootstrapEnabled();var entered=new java.util.concurrent.CountDownLatch(1);var finish=new java.util.concurrent.CountDownLatch(1);
    when(factory.build(any(),any())).thenAnswer(i->{
      CandidatePublicationCoordinator.Input<Worldview> input=i.getArgument(0);
      if(input.payload().getOntologies().getFirst().getSourceCode().equals("first"))
        return new CandidatePublicationCoordinator.Result<>(state,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of());
      entered.countDown();assertTrue(finish.await(5,java.util.concurrent.TimeUnit.SECONDS));throw new IllegalStateException("bootstrap failed");
    });
    try(var runtime=runtime()) {
      runtime.submit(world("first"),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));long accepted=runtime.status().acceptedGeneration();assertTrue(accepted>0);
      runtime.submit(world("changed"),scope);assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(accepted,runtime.status().acceptedGeneration());try(var lease=runtime.lease()){assertSame(state,lease.state());}
      finish.countDown();assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));assertEquals(CandidatePublicationCoordinator.LogicalStatus.FAILED,runtime.status().logicalStatus());
      assertEquals(accepted,runtime.status().acceptedGeneration());try(var lease=runtime.lease()){assertSame(state,lease.state());}
      verify(state,never()).close();assertFalse(runtime.shouldRetryPreflight(world("changed"),scope));
    } finally {finish.countDown();}
  }
  @Test void bootstrapPublicationGuardRejectsChangedOwnerAndPreservesAcceptedState() throws Exception {
    bootstrapEnabled();var replacement=mock(SemanticEnvironment.class);
    when(factory.build(any(),any())).thenReturn(new CandidatePublicationCoordinator.Result<>(state,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()),
        new CandidatePublicationCoordinator.Result<>(replacement,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()));
    try(var runtime=runtime()) {
      runtime.submit(world("first"),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));long accepted=runtime.status().acceptedGeneration();
      when(factory.publicationCurrent(any())).thenReturn(false);runtime.submit(world("changed"),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(accepted,runtime.status().acceptedGeneration());verify(replacement).close();verify(state,never()).close();
    }
  }
  @Test void bootstrapRejectsUnscopedSubmitAndExplicitRetryClearsCachedAttempt() throws Exception {
    bootstrapEnabled();try(var runtime=runtime()) {
      assertThrows(SecurityException.class,()->runtime.submit(world("first")));
      runtime.submit(world("first"),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertFalse(runtime.shouldRetryPreflight(world("first"),scope));runtime.invalidate();
      verify(factory).invalidateBootstrap();assertTrue(runtime.shouldRetryPreflight(world("first"),scope));
    }
  }

  @Test void failedBootstrapRetriesAfterDelayAndClearsFailedProviderCapture() throws Exception {
    bootstrapEnabled();
    when(factory.build(any(),any())).thenThrow(new IllegalStateException("provider temporarily unavailable"));
    try (var runtime=runtime()) {
      var source=world("first");
      runtime.submit(source,scope);
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertFalse(runtime.shouldRetryPreflight(source,scope));
      clock.addAndGet(Duration.ofSeconds(60).toNanos());
      assertTrue(runtime.shouldRetryPreflight(source,scope));
      var retry=runtime.beginPreflight(source,scope);
      assertTrue(retry.retry());
      verify(factory).invalidateBootstrap();
      assertTrue(runtime.submit(source,retry,scope));
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      verify(factory,times(2)).capture(any(),same(scope));
    }
  }

  @Test void pollingWithoutBootstrapAlsoDeduplicatesSubmittedSources() throws Exception {
    try (var runtime=runtime()) {
      var source=world("first");
      assertTrue(runtime.submit(source,runtime.beginPreflight(source,scope),scope));
      assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertFalse(runtime.shouldRetryPreflight(source,scope));
      assertTrue(runtime.shouldRetryPreflight(world("changed"),scope));
    }
  }

  @Test void providerOnlyTakeoverPreservesAcceptedGenerationUntilReplacementSucceeds() throws Exception {
    bootstrapEnabled();
    var selection=new AtomicReference<>("remote-artifact");
    var replacement=mock(SemanticEnvironment.class);
    var failReplacement=new AtomicBoolean(true);
    var source=world("unchanged worldview");
    var version=org.integratedmodelling.klab.api.data.Version.create("1.0.0");
    var descriptor=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor(
        "klab.authority.taxa",version,true,true,List.of(),List.of());
    source.getAuthorityBindings().add(new Worldview.AuthorityBinding("TAXA","w:Identity","w",0,1,"hash",descriptor,"klab.authority.taxa",version,null));
    String metadata=org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA;
    when(factory.refreshAuthoritySelection(any(),same(scope))).thenAnswer(call->{
      var owned=BeanIngestionPreflight.snapshot((Worldview)call.getArgument(0));
      owned.getMetadata().put(metadata,selection.get()); return owned;
    });
    when(factory.authoritySelectionRevision(any(),same(scope))).thenAnswer(call -> selection.get());
    when(factory.build(any(),any())).thenAnswer(call->{
      CandidatePublicationCoordinator.Input<Worldview> input=call.getArgument(0);
      boolean local="local-artifact".equals(input.payload().getMetadata().get(metadata));
      if(local && failReplacement.get()) throw new IllegalStateException("local provider failed configuration");
      return new CandidatePublicationCoordinator.Result<>(local?replacement:state,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of());
    });
    try(var runtime=runtime()) {
      runtime.submit(source,scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      long first=runtime.status().acceptedGeneration(); assertTrue(first>0);
      assertFalse(runtime.shouldRetryPreflight(source,scope));
      selection.set("local-artifact"); assertTrue(runtime.shouldRetryPreflight(source,scope));
      runtime.submit(source,scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(first,runtime.status().acceptedGeneration());
      try(var lease=runtime.lease()){ assertSame(state,lease.state()); }
      verify(state,never()).close();
      assertFalse(runtime.shouldRetryPreflight(source,scope));
      failReplacement.set(false); clock.addAndGet(Duration.ofSeconds(60).toNanos());
      runtime.submit(source,scope); assertTrue(runtime.awaitIdle(Duration.ofSeconds(5)));
      assertEquals(first+1,runtime.status().acceptedGeneration());
      try(var lease=runtime.lease()){ assertSame(replacement,lease.state()); }
      long generation=runtime.status().inputGeneration();
      for(int i=0;i<5;i++) {
        assertFalse(runtime.shouldRetryPreflight(source,scope));
        assertFalse(runtime.beginPreflight(source,scope).retry());
      }
      assertEquals(generation,runtime.status().inputGeneration());
      verify(factory,times(3)).build(any(),any());
      assertNull(source.getMetadata().get(metadata));
    }
  }

  @Test void pollingUsesFreshSelectionWithoutCopyingAndMatchesOwnedPreflightKeys() throws Exception {
    bootstrapEnabled();
    var selection = new AtomicReference<>("provider-one");
    var source = world("unchanged");
    var version = org.integratedmodelling.klab.api.data.Version.create("1.0.0");
    var descriptor = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.AuthorityDescriptor(
        "fixture.provider", version, true, true, List.of(), List.of());
    source.getAuthorityBindings().add(new Worldview.AuthorityBinding("FIXTURE", "w:Identity", "w", 0, 1,
        "hash", descriptor, "fixture.provider", version, null));
    String metadata = org.integratedmodelling.klab.components.AuthorityComponentResolver.SELECTION_METADATA;
    when(factory.authoritySelectionRevision(same(source), same(scope))).thenAnswer(call -> selection.get());
    when(factory.refreshAuthoritySelection(same(source), same(scope))).thenAnswer(call -> {
      var copy = BeanIngestionPreflight.snapshot(source);
      copy.getMetadata().put(metadata, selection.get());
      return copy;
    });
    try (var runtime = runtime()) {
      // The existing owned path defines the identity; the polling fast path must agree with it.
      var attempt = runtime.beginPreflight(source, scope);
      assertTrue(runtime.rejectPreflight(attempt, errors, scope));
      clearInvocations(factory);
      for (int i = 0; i < 4; i++) assertFalse(runtime.shouldRetryPreflight(source, scope));
      verify(factory, times(4)).authoritySelectionRevision(same(source), same(scope));
      verify(factory, never()).refreshAuthoritySelection(any(), any());
      assertNull(source.getMetadata().get(metadata));

      selection.set("provider-two");
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      var replacement = runtime.beginPreflight(source, scope);
      assertNotEquals(attempt.key(), replacement.key());
      assertEquals("provider-two", replacement.snapshot().getMetadata().get(metadata));
      assertTrue(runtime.rejectPreflight(replacement, errors, scope));
      assertFalse(runtime.shouldRetryPreflight(source, scope));
      when(factory.authoritySelectionRevision(same(source), same(scope)))
          .thenThrow(new IllegalArgumentException("unavailable selection"))
          .thenAnswer(call -> selection.get());
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      assertFalse(runtime.shouldRetryPreflight(source, scope));

      // Every other key dimension still invalidates independently of the unchanged selection.
      source.getMetadata().put("authority.catalog.revision", "catalog-two");
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source, scope), errors, scope));
      dependency.set("dependency-two");
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source, scope), errors, scope));
      var second = new KimOntologyImpl(); second.setUrn("second"); second.setSourceCode("second");
      source.getOntologies().add(second);
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source, scope), errors, scope));
      Collections.reverse(source.getOntologies());
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source, scope), errors, scope));
      source.getAuthorityBindings().set(0, new Worldview.AuthorityBinding("FIXTURE", "w:Identity", "w", 0, 1,
          "changed-configuration", descriptor, "fixture.provider", version, null));
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      assertTrue(runtime.rejectPreflight(runtime.beginPreflight(source, scope), errors, scope));
      var group = mock(Group.class); when(group.getName()).thenReturn("new-access");
      when(owner.getGroups()).thenReturn(List.of(group));
      assertTrue(runtime.shouldRetryPreflight(source, scope));
      assertNull(source.getMetadata().get(metadata));
    }
  }

}
