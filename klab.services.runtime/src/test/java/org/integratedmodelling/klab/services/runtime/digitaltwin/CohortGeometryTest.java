package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumSet;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CohortGeometryTest {

  @BeforeAll
  static void configureKlab() {
    ServiceConfiguration.injectInstantiators();
  }

  @Test
  void occurrentCohortGeometryUnionsTemporalBoundaries() {
    var first = Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}");
    var second = Geometry.create("T0(1){tend=20,tstart=10,ttype=PHYSICAL}");

    var merged =
        DigitalTwinImpl.TransactionImpl.mergeCohortGeometry(
            EnumSet.of(SemanticType.EVENT), first, second);

    assertEquals(0, merged.getTime().getStart().getMilliseconds());
    assertEquals(20, merged.getTime().getEnd().getMilliseconds());
  }

  @Test
  void functionalRelationshipCohortsAreOccurrent() {
    var first = Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}");
    var second = Geometry.create("T0(1){tend=20,tstart=10,ttype=PHYSICAL}");

    var merged =
        DigitalTwinImpl.TransactionImpl.mergeCohortGeometry(
            EnumSet.of(SemanticType.RELATIONSHIP, SemanticType.FUNCTIONAL), first, second);

    assertEquals(0, merged.getTime().getStart().getMilliseconds());
    assertEquals(20, merged.getTime().getEnd().getMilliseconds());
  }

  @Test
  void continuantCohortGeometryDoesNotRetainTime() {
    var first = Geometry.create("T0(1){tend=5,tstart=0,ttype=PHYSICAL}");
    var second = Geometry.create("T0(1){tend=20,tstart=10,ttype=PHYSICAL}");

    var merged =
        DigitalTwinImpl.TransactionImpl.mergeCohortGeometry(
            EnumSet.of(SemanticType.SUBJECT), first, second);

    assertNull(merged.getTime());
  }
}
