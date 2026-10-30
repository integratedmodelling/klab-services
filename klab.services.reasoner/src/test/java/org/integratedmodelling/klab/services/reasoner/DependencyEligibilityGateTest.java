package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.junit.jupiter.api.Test;

/** Public production entry must establish canonical eligibility before invoking distance. */
class DependencyEligibilityGateTest {
  private final ReasonerService service=mock(ReasonerService.class);
  private final SemanticMatcher matcher=mock(SemanticMatcher.class);
  private final Concept target=mock(Concept.class);
  private final Concept canonical=mock(Concept.class);
  private final Observable requested=mock(Observable.class);
  private final Concept requestedConcept=mock(Concept.class);
  DependencyEligibilityGateTest() throws Exception {
    var field=ReasonerService.class.getDeclaredField("semanticMatcher");field.setAccessible(true);field.set(service,matcher);
    when(requested.asConcept()).thenReturn(requestedConcept);when(requestedConcept.getUrn()).thenReturn("test:Compound");
    when(requested.getGenericComponents()).thenReturn(Set.of());
    when(service.resolveConcept("test:Compound")).thenReturn(canonical);when(service.satisfiable(canonical)).thenReturn(true);
    when(canonical.getType()).thenReturn(EnumSet.of(SemanticType.QUALITY));
    when(service.semanticDistance(target,requested,null)).thenCallRealMethod();
    when(matcher.semanticDistance(target,requested,null)).thenReturn(17);
  }
  @Test void unresolvedCanonicalCompoundCannotReachMatcherEvenIfCallerClaimsConcrete() {
    when(canonical.isAbstract()).thenReturn(true);when(requested.isAbstract()).thenReturn(false);
    assertTrue(service.semanticDistance(target,requested,null)<0);verifyNoInteractions(matcher);
  }
  @Test void leftoverGenericComponentsAndSelectorsCannotReachMatcher() {
    when(requested.getGenericComponents()).thenReturn(Set.of(mock(Concept.class)));
    assertTrue(service.semanticDistance(target,requested,null)<0);verifyNoInteractions(matcher);
    when(requested.getGenericComponents()).thenReturn(Set.of());when(canonical.is(SemanticType.ANY)).thenReturn(true);
    assertTrue(service.semanticDistance(target,requested,null)<0);verifyNoInteractions(matcher);
  }
  @Test void unsatisfiableOrUnavailableDeclarationsCannotReachMatcher() {
    when(service.satisfiable(canonical)).thenReturn(false);
    assertTrue(service.semanticDistance(target,requested,null)<0);verifyNoInteractions(matcher);
    when(service.resolveConcept("test:Compound")).thenReturn(null);
    assertTrue(service.semanticDistance(target,requested,null)<0);verifyNoInteractions(matcher);
  }
  @Test void eligibleConcreteRequestReachesMatcherOnce() {
    assertEquals(17,service.semanticDistance(target,requested,null));verify(matcher).semanticDistance(target,requested,null);
  }
  @Test void classifierCategoryRetainsDocumentedAbstractPredicateException() {
    when(canonical.isAbstract()).thenReturn(true);when(canonical.is(SemanticType.PREDICATE)).thenReturn(true);
    assertEquals(17,service.semanticDistance(target,requested,null));verify(matcher).semanticDistance(target,requested,null);
  }
}
