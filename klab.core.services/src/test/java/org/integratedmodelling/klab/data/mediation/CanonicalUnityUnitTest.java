package org.integratedmodelling.klab.data.mediation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

class CanonicalUnityUnitTest {
  private final UnitServiceImpl units = new UnitServiceImpl();

  @Test void explicitUnityIsOrdinaryUnitAndConvertsDimensionlessRatiosAndPercentages() {
    var unity = units.getUnit("1");
    assertEquals("1", ((UnitImpl) unity).getDefinition());
    assertFalse(unity.isContextual());
    assertTrue(units.isCompatible(unity, units.getUnit("m/m")));
    assertEquals(0.25, units.convert(25, unity, units.getUnit("%")).doubleValue(), 1e-12);
    assertEquals(25, units.convert(0.25, units.getUnit("%"), unity).doubleValue(), 1e-12);
    assertEquals(0.12, units.convert(0.12, unity, units.getUnit("m/m")).doubleValue(), 1e-12);
    assertFalse(units.isCompatible(unity, units.getUnit("m")));
    assertEquals("1", ((UnitImpl) units.getUnit(" 1 ")).getDefinition());
  }

  @Test void explicitUnitlessDefaultRemainsDistinctFromUnknownOrMissingMetadata() {
    var concept = new ConceptImpl();
    concept.setType(EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY, SemanticType.RATIO));
    assertNull(units.getDefaultUnitFor(concept));
    concept.getMetadata().put("odo:unit", "1");
    assertEquals("1", ((UnitImpl) units.getDefaultUnitFor(concept)).getDefinition());
    concept.getMetadata().put("odo:unit", "");
    assertThrows(IllegalArgumentException.class, () -> units.getDefaultUnitFor(concept));
    assertNull(units.getUnit(""));
  }

  @Test void interfaceTransportRetainsUnityAndRehydratesConversion() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    Unit restored = mapper.readValue(mapper.writerFor(Unit.class).writeValueAsBytes(units.getUnit("1")), Unit.class);
    assertEquals("1", ((UnitImpl) restored).getDefinition());
    assertEquals(0.5, units.convert(50, restored, units.getUnit("%")).doubleValue(), 1e-12);
  }

  @Test void dimensionalCompatibilityDoesNotBecomeAQuantityKindProof() {
    // Existing library behavior, not permission to annotate a ratio as an angle.
    assertTrue(units.isCompatible(units.getUnit("1"), units.getUnit("rad")));
    assertTrue(units.isCompatible(units.getUnit("1"), units.getUnit("degree_angle")));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> units.getUnit("not_a_unit"));
  }
}
