package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher.PlacementKind;
import org.junit.jupiter.api.Test;

class ConfigurationPlacementRecipesTest {
 @Test void dependentIngredientsDoNotEnterRootCohortCapture() {
  var reasoner=mock(Reasoner.class);var recipe=DefaultConfigurationBackendTest.recipe("test:Height");
  when(reasoner.resolveConcept("test:Height")).thenReturn(DefaultConfigurationBackendTest.concept("test:Height",SemanticType.QUALITY));
  assertEquals(List.of(recipe),DefaultConfigurationBackend.placementRecipes(List.of(recipe),PlacementKind.DEPENDENT_BEARER,reasoner));
  assertTrue(DefaultConfigurationBackend.placementRecipes(List.of(recipe),PlacementKind.ROOT_CONTEXT,reasoner).isEmpty());
 }
 @Test void ownerWithIndependentRecipesRoutesEachToItsActualDomain() {
  var reasoner=mock(Reasoner.class);var quality=DefaultConfigurationBackendTest.recipe("test:Height");var wire=DefaultConfigurationBackendTest.recipe("test:Wire");
  when(reasoner.resolveConcept("test:Height")).thenReturn(DefaultConfigurationBackendTest.concept("test:Height",SemanticType.QUALITY));
  when(reasoner.resolveConcept("test:Wire")).thenReturn(DefaultConfigurationBackendTest.concept("test:Wire",SemanticType.RELATIONSHIP));
  var family=List.of(quality,wire);
  assertEquals(List.of(quality),DefaultConfigurationBackend.placementRecipes(family,PlacementKind.DEPENDENT_BEARER,reasoner));
  assertEquals(List.of(wire),DefaultConfigurationBackend.placementRecipes(family,PlacementKind.ROOT_CONTEXT,reasoner));
  assertEquals(2,family.size());
 }
 @Test void unresolvedAndUnsupportedDomainsRemainFailClosed() {
  var reasoner=mock(Reasoner.class);var recipe=DefaultConfigurationBackendTest.recipe("test:Unknown");
  assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->DefaultConfigurationBackend.placementRecipes(List.of(recipe),PlacementKind.ROOT_CONTEXT,reasoner));
  when(reasoner.resolveConcept("test:Unknown")).thenReturn(DefaultConfigurationBackendTest.concept("test:Unknown",SemanticType.NOTHING));
  assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->DefaultConfigurationBackend.placementRecipes(List.of(recipe),PlacementKind.DEPENDENT_BEARER,reasoner));
 }
}
