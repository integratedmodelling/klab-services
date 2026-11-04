import java.sql.DriverManager;
import org.h2gis.functions.factory.H2GISFunctions;

/** Disposable in-memory dependency probe; no service, filesystem database or shared state. */
public class H2GisCompatibilityProbe {
  public static void main(String[] args) throws Exception {
    for (var type : new Class<?>[] {org.h2.Driver.class, H2GISFunctions.class}) {
      System.out.println(type.getName() + " loaded-from="
          + type.getProtectionDomain().getCodeSource().getLocation());
    }
    try (var connection = DriverManager.getConnection("jdbc:h2:mem:closeout_dependency_probe")) {
      System.out.println("H2=" + connection.getMetaData().getDatabaseProductVersion());
      H2GISFunctions.load(connection);
      try (var statement = connection.createStatement();
          var rows = statement.executeQuery("SELECT ST_AREA(ST_GEOMFROMTEXT('POLYGON((0 0,4 0,4 3,0 3,0 0))'))")) {
        if (!rows.next() || rows.getDouble(1) != 12.0 || rows.wasNull()) {
          throw new AssertionError("Independent planar rectangle oracle: 4 * 3 = 12");
        }
        System.out.println("PASS: planar area=12 coordinate-units squared (not a geographic-area claim)");
      }
    }
  }
}
