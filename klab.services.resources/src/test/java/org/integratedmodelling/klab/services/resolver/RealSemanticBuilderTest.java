package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;

/** Real portable-builder dispatch and semantic mutations; Resources and OWL storage are doubles. */
class RealSemanticBuilderTest {
  static KimConceptImpl syntax(String name, SemanticType... types) {
    var ret = new KimConceptImpl(); ret.setName(name); ret.setType(EnumSet.copyOf(List.of(types)));
    ret.finalizeDefinition(); return ret;
  }
  static ConceptImpl concept(String urn, SemanticType... types) {
    var ret = new ConceptImpl(); ret.setUrn(urn); ret.setName(urn); ret.setNamespace("test");
    ret.setReferenceName(urn); ret.setType(EnumSet.copyOf(List.of(types))); return ret;
  }

  @Test void conceptBackedInherentUsesRealBuilderAndDoesNotMutateResources() throws Exception {
    var scope = mock(ContextScope.class);
    var resources = mock(ResourcesService.class);
    var reasoner = mock(ReasonerService.class);
    var owl = mock(OWL.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.owl()).thenReturn(owl);
    when(reasoner.buildObservable(any(), eq(scope))).thenCallRealMethod();
    when(reasoner.buildConcept(any(), eq(scope))).thenCallRealMethod();
    var predicateSyntax = syntax("data:Normalized", SemanticType.PREDICATE, SemanticType.TRAIT, SemanticType.ATTRIBUTE);
    var qualitySyntax = syntax("geography:Elevation", SemanticType.QUALITY, SemanticType.LENGTH);
    var predicate = concept("data:Normalized", SemanticType.PREDICATE, SemanticType.TRAIT, SemanticType.ATTRIBUTE);
    var quality = concept("geography:Elevation", SemanticType.QUALITY, SemanticType.LENGTH);
    when(resources.declareConcept(predicate.getUrn())).thenReturn(predicateSyntax);
    when(resources.declareConcept(quality.getUrn())).thenReturn(qualitySyntax);
    when(reasoner.resolveConcept(predicate.getUrn())).thenReturn(predicate);
    when(reasoner.resolveConcept(quality.getUrn())).thenReturn(quality);
    when(owl.makeSubclass(eq(predicate), anyString())).thenAnswer(inv ->
        concept(inv.getArgument(1), SemanticType.PREDICATE, SemanticType.TRAIT, SemanticType.ATTRIBUTE));
    doAnswer(inv -> { ((ConceptImpl) inv.getArgument(0)).setUrn(inv.getArgument(1)); return null; })
        .when(owl).setConceptUrn(any(), anyString());
    for (int i = 0; i < 2; i++) {
      var result = new ObservableBuildStrategy(predicate, scope).of(quality).buildObservable();
      assertEquals("data:Normalized of geography:Elevation", result.getUrn());
      assertEquals(Contextualization.TRANSFORMATION, result.getContextualization());
      assertNull(predicateSyntax.getInherent(), "Cached Resources syntax must remain atomic");
    }
    assertEquals(predicate, new ObservableBuildStrategy(predicate, scope).buildConcept());

    // Run the actual strategy through the real builder boundary, not the previous
    // buildObservable stub that could conceal dispatch and shared-syntax defects.
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var requestSyntax = syntax(quality.getUrn(), SemanticType.QUALITY, SemanticType.LENGTH);
    requestSyntax.getTraits().add(predicateSyntax);
    requestSyntax.resetDefinition();
    var requestUrn = requestSyntax.getUrn();
    when(resources.declareConcept(requestUrn)).thenReturn(requestSyntax);
    var request = ObservableImpl.promote(concept(requestUrn, SemanticType.QUALITY, SemanticType.LENGTH), scope);
    when(reasoner.directTraits(any())).thenAnswer(inv ->
        ((Semantics) inv.getArgument(0)).getUrn().equals(requestUrn) ? List.of(predicate) : List.of());
    when(reasoner.directInherent(any())).thenReturn(quality);
    var context = mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    when(context.getObservable()).thenReturn(ObservableImpl.promote(concept("earth:Region", SemanticType.SUBJECT), scope));
    when(scope.getContextObservation()).thenReturn(context);
    var observation = mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    when(observation.getObservable()).thenReturn(request);
    var selector = new org.integratedmodelling.klab.services.reasoner.ObservationReasoner(reasoner, ignored -> null);
    selector.registerStrategy(ObservationPipelineTest.document().getStatements().stream()
        .filter(s -> s.getUrn().equals("quality.split.predicate")).findFirst().orElseThrow());
    for (int i = 0; i < 2; i++) {
      var matches = selector.computeMatchingStrategies(observation, scope, true);
      assertEquals(1, matches.size());
      assertEquals("rawquality", matches.getFirst().getOperations().getLast().getTransformationTarget());
      assertEquals(requestUrn, requestSyntax.getUrn());
      assertEquals(1, requestSyntax.getTraits().size());
    }

  }

  @Test void predicateRemovalDoesNotAlterCachedRequestOrOtherBuilders() {
    var scope = mock(ContextScope.class);
    var resources = mock(ResourcesService.class);
    var reasoner = mock(ReasonerService.class);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.buildObservable(any(), eq(scope))).thenCallRealMethod();
    when(reasoner.owl()).thenReturn(mock(OWL.class));
    var predicate = concept("data:Normalized", SemanticType.PREDICATE, SemanticType.ATTRIBUTE);
    var quality = concept("geography:Elevation", SemanticType.QUALITY, SemanticType.LENGTH);
    var pSyntax = syntax(predicate.getUrn(), SemanticType.PREDICATE, SemanticType.ATTRIBUTE);
    var requestSyntax = syntax(quality.getUrn(), SemanticType.QUALITY, SemanticType.LENGTH);
    requestSyntax.getTraits().add(pSyntax);
    requestSyntax.resetDefinition();
    var requestUrn = requestSyntax.getUrn();
    var request = ObservableImpl.promote(concept(requestUrn, SemanticType.QUALITY, SemanticType.LENGTH), scope);
    when(resources.declareConcept(requestUrn)).thenReturn(requestSyntax);
    when(resources.declareConcept(predicate.getUrn())).thenReturn(pSyntax);
    when(reasoner.resolveConcept(quality.getUrn())).thenReturn(quality);
    for (int i = 0; i < 2; i++) {
      var result = request.builder(scope).without(predicate).buildObservable();
      assertEquals(quality.getUrn(), result.getUrn());
      assertEquals(List.of(pSyntax), requestSyntax.getTraits());
      assertEquals(requestUrn, requestSyntax.getUrn());
    }
    var result = SemanticsBuilder.create(request, reasoner, scope).without(List.of(predicate)).buildObservable();
    assertEquals(quality.getUrn(), result.getUrn());
  }
}
