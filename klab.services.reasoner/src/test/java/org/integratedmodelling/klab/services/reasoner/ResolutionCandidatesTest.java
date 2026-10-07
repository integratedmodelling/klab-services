package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.junit.jupiter.api.Test;

class ResolutionCandidatesTest {
  @Test void classificationSurvivesInternalAndIncompatibleAncestors() { exercise(true); }
  @Test void characterizationSurvivesInternalAndIncompatibleAncestors() { exercise(false); }

  @Test void normalizationDiscoversTransformerWithFoundationalQualityBearer() {
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
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
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
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
      assertEquals(java.util.Set.of(request, generalized), java.util.Set.copyOf(reasoner.resolving(request)));
    }
  }

  private static ConceptImpl concept(String urn, SemanticType type) {
    var result = new ConceptImpl();
    result.setUrn(urn);
    result.setNamespace(urn.substring(0, urn.indexOf(':')));
    result.getType().add(type);
    return result;
  }
}
