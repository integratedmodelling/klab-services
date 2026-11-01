package org.integratedmodelling.klab.runtime.computation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Storage;

/** Test-scoped single-tile demo. Caller supplies fresh native scanners and owns/finalizes them. */
final class PythonNdviDemo {
  static void compute(Storage.DoubleScanner red, Storage.DoubleScanner nir,
      Storage.DoubleScanner output, Path worker) throws Exception {
    if (red.size() != nir.size() || red.size() != output.size() || red.size() > 256) {
      throw new IllegalArgumentException("Demo requires equal-sized tiles of at most 256 cells");
    }
    for (var scanner : List.of(red, nir, output)) {
      if (!scanner.shard().getGeometry().encode().equals(red.shard().getGeometry().encode())
          || !scanner.shard().getShardingStrategy().equals(red.shard().getShardingStrategy())) {
        throw new IllegalArgumentException("Demo requires fresh, spatially aligned native scanners with identical layouts");
      }
    }
    var request = Map.of("protocol_version", 1, "operation", "ndvi",
        "red", read(red), "nir", read(nir));
    var values = new PythonProcessRunner().run(worker, new ObjectMapper().writeValueAsString(request),
        Math.toIntExact(red.size()), Duration.ofSeconds(15));
    // Validate the complete tile before writing any returned values.
    for (var value : values) {
      if (!value.isNull() && (value.doubleValue() < -1 || value.doubleValue() > 1)) {
        throw new IllegalArgumentException("NDVI response outside [-1, 1]");
      }
    }
    for (var value : values) {
      output.add(value.isNull() ? Double.NaN : value.doubleValue());
    }
  }

  private static List<Double> read(Storage.DoubleScanner scanner) {
    var values = new ArrayList<Double>();
    while (scanner.hasNext()) {
      double value = scanner.get();
      if (Double.isInfinite(value)) {
        throw new IllegalArgumentException("Infinite reflectance is not missing data");
      }
      values.add(Double.isNaN(value) ? null : value);
    }
    return values;
  }
}
