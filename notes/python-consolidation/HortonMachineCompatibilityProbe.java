import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.hortonmachine.gears.utils.RegionMap;
import org.hortonmachine.gears.utils.coverage.CoverageUtilities;

/** Host-first packaged-component geographic probe, no services or shared databases. */
public class HortonMachineCompatibilityProbe {
  public static void main(String[] args) throws Exception {
    System.out.println("HM=" + CoverageUtilities.class.getProtectionDomain().getCodeSource().getLocation());
    System.out.println("GT=" + DefaultGeographicCRS.class.getProtectionDomain().getCodeSource().getLocation());
    var region = RegionMap.fromBoundsAndGrid(0, 2, 0, 2, 2, 2);
    var coverage = CoverageUtilities.buildCoverage("independent-four-cells",
        new double[][] {{1, 2}, {3, 4}}, region, DefaultGeographicCRS.WGS84, true);
    try {
      var actual = CoverageUtilities.getRegionParamsFromGridCoverage(coverage);
      if (actual.cols != 2 || actual.rows != 2 || actual.west != 0 || actual.east != 2
          || actual.south != 0 || actual.north != 2 || actual.xres != 1 || actual.yres != 1) {
        throw new AssertionError("HM/GT round trip changed independently specified geographic support");
      }
      var data = coverage.getRenderedImage().getData();
      double sum = 0;
      for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
        double expected = y * 2 + x + 1;
        double value = data.getSampleDouble(x, y, 0);
        if (value != expected) throw new AssertionError("HM changed cell orientation or value");
        sum += value;
      }
      if (sum != 10) throw new AssertionError("Independent 1+2+3+4 oracle failed");
      System.out.println("PASS: HM packaged code / host GT grid support, orientation and sum=10");
    } finally {
      coverage.dispose(true);
    }
  }
}
