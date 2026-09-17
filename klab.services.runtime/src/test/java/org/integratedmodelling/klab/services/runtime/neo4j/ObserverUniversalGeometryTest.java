package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.junit.jupiter.api.Test;

class ObserverUniversalGeometryTest {
  @Test void firstObservationReplacesUniversalPerceivedGeometry() {
    var observed = Geometry.create("S2(2,2)");
    var perceived = KnowledgeGraphNeo4j.mergePerceivedGeometry(Geometry.UNIVERSAL, observed);
    assertEquals(observed.encode(), perceived.encode());
    assertFalse(perceived.isUniversal());
    assertSame(perceived, KnowledgeGraphNeo4j.mergePerceivedGeometry(perceived, Geometry.UNIVERSAL));
  }
}
