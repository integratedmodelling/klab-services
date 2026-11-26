package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.junit.jupiter.api.Test;

class ResolutionCandidatesTest {
  @Test void batchMatchesExactAndGeneralizedGroupsWithOneHeadExpansion() {
    var reasoner = reasoner();
    var requested = concept("test:Specific", SemanticType.QUALITY);
    var generalized = concept("test:General", SemanticType.QUALITY);
    var unrelated = concept("test:Unrelated", SemanticType.QUALITY);
    var substantial = concept("test:Subject", SemanticType.SUBJECT);
    doAnswer(call -> call.getArgument(0)).when(reasoner).coreObservable(any());
    doReturn(Set.of("test:General")).when(reasoner).matchingResolutionHeads(
        requested, Set.of("test:General", "test:Unrelated"));

    List<List<Concept>> candidates = List.of(
        List.of(unrelated, requested),
        List.of(substantial),
        List.of(generalized),
        List.of(unrelated),
        List.<Concept>of());
    assertEquals(Set.of(0, 2), reasoner.matchingCandidateOutputs(requested, candidates));
    verify(reasoner).matchingResolutionHeads(requested, Set.of("test:General", "test:Unrelated"));
  }

  @Test void ordinaryQualityBuildsOnlyGeneralizationsThatCanMatchAnOfferedOutput() {
    var reasoner = reasoner();
    var base = concept("test:Weight", SemanticType.QUALITY);
    var tree = concept("test:Tree", SemanticType.SUBJECT);
    var plant = concept("test:Plant", SemanticType.SUBJECT);
    var object = concept("test:Object", SemanticType.SUBJECT);
    var request = concept("test:Weight of test:Tree", SemanticType.QUALITY);
    var offered = concept("test:Weight of test:Plant", SemanticType.QUALITY);
    var other = concept("test:Weight of test:Object", SemanticType.QUALITY);
    doReturn(null).when(reasoner).serviceScope();
    doReturn(tree).when(reasoner).directInherent(request);
    doReturn(List.of(plant, object)).when(reasoner).allParents(tree);
    var stripping = mock(SemanticsBuilder.class, RETURNS_SELF);
    var application = mock(SemanticsBuilder.class, RETURNS_SELF);
    when(stripping.buildConcept()).thenReturn(base);
    var current = new java.util.concurrent.atomic.AtomicReference<Concept>();
    when(application.of(any())).thenAnswer(i -> {
      current.set(i.getArgument(0));
      return application;
    });
    when(application.canBuildResolutionHead(any())).thenAnswer(i -> plant.equals(current.get()));
    when(application.buildConcept()).thenAnswer(i -> plant.equals(current.get()) ? offered : other);
    try (var builders = mockStatic(SemanticsBuilder.class)) {
      builders.when(() -> SemanticsBuilder.create(request, reasoner, null)).thenReturn(stripping);
      builders.when(() -> SemanticsBuilder.create(base, reasoner, null)).thenReturn(application);
      var candidates = Set.of(offered.getUrn());
      var matched = reasoner.matchingResolutionHeads(request, candidates);
      verify(application, times(1)).buildConcept();
      assertEquals(Set.of(offered.getUrn()), matched);
      assertEquals(matched, reasoner.resolving(request).stream().map(Concept::getUrn)
          .filter(candidates::contains).collect(java.util.stream.Collectors.toSet()));
      verify(application, times(3)).buildConcept();
    }
  }

  @Test void classificationSurvivesInternalAndIncompatibleAncestors() { exercise(true); }
  @Test void characterizationSurvivesInternalAndIncompatibleAncestors() { exercise(false); }

  @Test void normalizationDiscoversTransformerWithFoundationalQualityBearer() {
    var reasoner = reasoner();
    var request = concept("data:Normalized of geography:Elevation", SemanticType.PREDICATE);
    var head = concept("data:Normalized", SemanticType.PREDICATE);
    var elevation = concept("geography:Elevation", SemanticType.QUALITY);
    // imod:Quality is represented by this foundational concept in the loaded worldview.
    var quality = concept("odo:Quality", SemanticType.QUALITY);
    quality.getType().add(SemanticType.OBSERVABLE);
    var structural = concept("odo:PhysicalProperty", SemanticType.ABSTRACT);
    var invalid = concept("odo:InvalidObservable", SemanticType.OBSERVABLE);
    var nothing = concept("owl:Nothing", SemanticType.NOTHING);
    var transformer = concept("data:Normalized of odo:Quality", SemanticType.PREDICATE);
    doReturn(null).when(reasoner).serviceScope();
    doReturn(elevation).when(reasoner).directInherent(request);
    doReturn(List.of()).when(reasoner).allParents(head);
    doReturn(List.of(structural, invalid, quality)).when(reasoner).allParents(elevation);

    var stripping = mock(SemanticsBuilder.class, RETURNS_SELF);
    var application = mock(SemanticsBuilder.class, RETURNS_SELF);
    when(stripping.buildConcept()).thenReturn(head);
    Concept[] bearer = new Concept[1];
    when(application.of(any())).thenAnswer(call -> {
      bearer[0] = call.getArgument(0);
      assertNotEquals(structural, bearer[0]);
      return application;
    });
    when(application.buildConcept()).thenAnswer(call ->
        bearer[0].equals(quality) ? transformer : bearer[0].equals(invalid) ? nothing : request);
    try (var builders = mockStatic(SemanticsBuilder.class)) {
      builders.when(() -> SemanticsBuilder.create(request, reasoner, null)).thenReturn(stripping);
      builders.when(() -> SemanticsBuilder.create(head, reasoner, null)).thenReturn(application);
      assertEquals(java.util.Set.of(request, transformer),
          java.util.Set.copyOf(reasoner.resolving(request)));
    }
  }

  private void exercise(boolean collective) {
    var reasoner = reasoner();
    var request = concept("test:Environment of test:Region", SemanticType.PREDICATE);
    var head = concept("test:Environment", SemanticType.PREDICATE);
    var internalHead = concept("odo:Attribute", SemanticType.PREDICATE);
    var region = concept("test:Region", SemanticType.SUBJECT);
    var internalBearer = concept("odo:Subject", SemanticType.SUBJECT);
    var incompatible = concept("test:Incompatible", SemanticType.SUBJECT);
    var location = concept("test:Location", SemanticType.SUBJECT);
    var generalized = concept("test:Environment of test:Location", SemanticType.PREDICATE);
    doReturn(null).when(reasoner).serviceScope();
    doReturn(collective ? region.collective() : region).when(reasoner).directInherent(request);
    doReturn(List.of(internalHead)).when(reasoner).allParents(head);
    doReturn(List.of(internalBearer, incompatible, location)).when(reasoner).allParents(region);

    var stripping = mock(SemanticsBuilder.class, RETURNS_SELF);
    var application = mock(SemanticsBuilder.class, RETURNS_SELF);
    when(stripping.buildConcept()).thenReturn(head);
    Concept[] bearer = new Concept[1];
    when(application.of(any())).thenAnswer(call -> {
      bearer[0] = call.getArgument(0);
      assertNotEquals("odo", bearer[0].getNamespace(), "Internal OWL classes cannot be parsed as k.IM");
      assertEquals(collective, bearer[0].isCollective());
      return application;
    });
    when(application.buildConcept()).thenAnswer(call -> {
      if (bearer[0].singular().equals(incompatible))
        throw new KlabValidationException("Outside the predicate's applies-to domain");
      return bearer[0].singular().equals(region) ? request : generalized;
    });
    try (var builders = mockStatic(SemanticsBuilder.class)) {
      builders.when(() -> SemanticsBuilder.create(request, reasoner, null)).thenReturn(stripping);
      builders.when(() -> SemanticsBuilder.create(head, reasoner, null)).thenReturn(application);
      var offered = Set.of(generalized.getUrn(), "test:Unrelated");
      when(application.canBuildResolutionHead(offered)).thenAnswer(call -> bearer[0].singular().equals(location));
      assertEquals(Set.of(generalized.getUrn()), reasoner.matchingResolutionHeads(request, offered));
      verify(application, times(1)).buildConcept();
      assertEquals(Set.of(generalized.getUrn()), reasoner.matchingResolutionHeads(request, offered));
      verify(application, times(1)).buildConcept();
      assertEquals(java.util.Set.of(request, generalized), java.util.Set.copyOf(reasoner.resolving(request)));
    }
  }

  private static ReasonerService reasoner() {
    var reasoner=mock(ReasonerService.class,CALLS_REAL_METHODS);
    try {
      var heads=ReasonerService.class.getDeclaredField("resolvingHeads");heads.setAccessible(true);
      heads.set(reasoner,com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build());
      var matching=ReasonerService.class.getDeclaredField("matchingHeads");matching.setAccessible(true);
      matching.set(reasoner,com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build());
      var epoch=ReasonerService.class.getDeclaredField("resolvingRefinements");epoch.setAccessible(true);
      epoch.set(reasoner,new java.util.concurrent.atomic.AtomicLong());
    } catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    doReturn(1L).when(reasoner).knowledgeRevision();return reasoner;
  }

  @Test void cachedHeadsAreDetachedAndInvalidatedBySemanticRevisionAndDerivedRefinements() {
    var reasoner=reasoner();var request=concept("test:Classified",SemanticType.PREDICATE);
    var parent=concept("test:Parent",SemanticType.PREDICATE);
    doReturn(List.of(request,parent)).when(reasoner).computeResolving(any());
    var first=reasoner.resolving(request);first.clear();
    assertEquals(List.of(request,parent),reasoner.resolving(request));
    verify(reasoner,times(1)).computeResolving(any());
    doReturn(2L).when(reasoner).knowledgeRevision();reasoner.resolving(request);
    verify(reasoner,times(2)).computeResolving(any());
    reasoner.derivedRefinementsChanged();reasoner.resolving(request);
    verify(reasoner,times(3)).computeResolving(any());
  }

  @Test void cachedHeadsCannotBypassUnavailableSemanticClosureAndFailuresAreNotRetained() throws Exception {
    var reasoner=reasoner();var request=concept("test:Predicate",SemanticType.PREDICATE);
    doThrow(new IllegalStateException("temporarily unavailable"))
        .doReturn(List.of(request)).when(reasoner).computeResolving(any());
    assertThrows(IllegalStateException.class,()->reasoner.resolving(request));
    assertEquals(List.of(request),reasoner.resolving(request));
    var owl=mock(org.integratedmodelling.klab.services.reasoner.owl.OWL.class);
    var field=ReasonerService.class.getDeclaredField("owl");field.setAccessible(true);field.set(reasoner,owl);
    doThrow(new IllegalStateException("closure unavailable")).when(owl).requireAvailableClosure();
    assertThrows(IllegalStateException.class,()->reasoner.resolving(request));
    verify(reasoner,times(2)).computeResolving(any());
  }

  private static ConceptImpl concept(String urn, SemanticType type) {
    var result = new ConceptImpl();
    result.setUrn(urn);
    result.setNamespace(urn.substring(0, urn.indexOf(':')));
    result.getType().add(type);
    return result;
  }
}
