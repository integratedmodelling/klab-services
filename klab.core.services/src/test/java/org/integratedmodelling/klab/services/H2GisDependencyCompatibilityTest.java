package org.integratedmodelling.klab.services;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import org.h2gis.functions.factory.H2GISFunctions;
import org.junit.jupiter.api.Test;

/** Exercises the host's resolved driver, not a test-selected alternate classpath. */
class H2GisDependencyCompatibilityTest {
  @Test void resolvedHostDriverInitializesSpatialExtensionAndComputesRectangleArea() throws Exception {
    try (var connection = DriverManager.getConnection("jdbc:h2:mem:host_h2gis_compatibility")) {
      assertTrue(connection.getMetaData().getDatabaseProductVersion().startsWith("1.4.197"));
      H2GISFunctions.load(connection);
      try (var statement = connection.createStatement();
          var result = statement.executeQuery("SELECT ST_AREA(ST_GEOMFROMTEXT('POLYGON((0 0,4 0,4 3,0 3,0 0))'))")) {
        assertTrue(result.next());
        assertEquals(12.0, result.getDouble(1), 0.0);
        assertFalse(result.wasNull());
        assertFalse(result.next());
      }
    }
  }
}
