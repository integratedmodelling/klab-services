package org.integratedmodelling.klab.runtime.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.storage.StorageImpl;
import org.integratedmodelling.klab.runtime.storage.StorageManagerImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ojalgo.array.BufferArray;

/** Opt-in demonstration of real local storage -> Python NDVI -> real local storage. */
class PythonNdviDemoIT {
  private static final double[] RED = {0.2, 0.3, 0.4, Double.NaN, 0, 0.1};
  private static final double[] NIR = {0.8, 0.3, 0.1, 0.5, 0, 0.9};
  private static final Data.ShardingStrategy LAYOUT =
      new Data.ShardingStrategy(Data.FillCurve.D2_YX, 1, 0, 0, Storage.Type.DOUBLE);
  @TempDir Path directory;

  @BeforeAll
  static void setup() {
    PythonProcessRunner.interpreter();
    ServiceConfiguration.injectInstantiators();
  }

  private Path worker() throws IOException {
    Path script = directory.resolve("NDVI worker.py");
    try (var resource = getClass().getResourceAsStream("/python-bridge/ndvi_worker.py")) {
      assertNotNull(resource);
      Files.copy(resource, script);
    }
    return script;
  }

  @Test
  void computesVegetationIndexThroughStorageAndExportsCsv() throws Exception {
    try (var red = new Tile("red", RED, 0); var nir = new Tile("nir", NIR, 0);
         var result = new Tile("ndvi", new double[6], 0)) {
      var output = result.scanner(false);
      PythonNdviDemo.compute(red.scanner(true), nir.scanner(true), output, worker());
      result.storage.finalizeRun(output);
      // Read back from storage, not from the Python response or output cursor.
      var actual = result.scanner(true);
      double[] expected = {0.6, 0, -0.6, Double.NaN, Double.NaN, 0.8};
      var csv = new StringBuilder("x,y,red_reflectance,nir_reflectance,ndvi\n");
      for (int i = 0; i < expected.length; i++) {
        assertTrue(actual.hasNext());
        double value = actual.get();
        if (Double.isNaN(expected[i])) assertTrue(Double.isNaN(value));
        else assertEquals(expected[i], value, 1e-12);
        csv.append(i % 3).append(',').append(i / 3).append(',')
            .append(text(RED[i])).append(',').append(text(NIR[i])).append(',')
            .append(text(value)).append('\n');
      }
      assertFalse(actual.hasNext());
      // Resolve from compiled test resources rather than the IDE working directory.
      Path target = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
      Path report = target.resolve("ndvi-demo/ndvi.csv");
      Files.createDirectories(report.getParent());
      Files.writeString(report, csv, StandardCharsets.UTF_8);
      System.out.println("\nSynthetic 3x2 NDVI: k.LAB storage -> Python -> k.LAB storage\n" + csv);
      System.out.println("CSV: " + report.toAbsolutePath());
    }
  }

  @Test
  void rejectsMisalignedGridsBeforeWriting() throws Exception {
    try (var red = new Tile("red", RED, 0); var nir = new Tile("nir", NIR, 1);
         var result = new Tile("ndvi", new double[6], 0)) {
      Path script = worker();
      var error = assertThrows(IllegalArgumentException.class, () ->
          PythonNdviDemo.compute(red.scanner(true), nir.scanner(true), result.scanner(false), script));
      assertTrue(error.getMessage().contains("aligned"));
      assertEquals(0, result.scanner(true).get());
    }
  }

  @Test
  void invalidReflectanceDoesNotWritePartialOutput() throws Exception {
    double[] invalid = RED.clone();
    invalid[5] = 1.5;
    try (var red = new Tile("red", invalid, 0); var nir = new Tile("nir", NIR, 0);
         var result = new Tile("ndvi", new double[6], 0)) {
      Path script = worker();
      var error = assertThrows(IOException.class, () ->
          PythonNdviDemo.compute(red.scanner(true), nir.scanner(true), result.scanner(false), script));
      assertTrue(error.getMessage().contains("reflectance"));
      var unchanged = result.scanner(true);
      while (unchanged.hasNext()) assertEquals(0, unchanged.get());
    }
  }

  private static String text(double value) {
    return Double.isNaN(value) ? "null" : Double.toString(value);
  }

  /** Real StorageImpl and numeric buffers; service/persistence infrastructure is mocked. */
  static final class Tile implements AutoCloseable {
    final StorageImpl storage;

    Tile(String name, double[] values, int west) {
      this(name, values, west, 3, 2);
    }

    Tile(String name, double[] values, int west, int width, int height) {
      var concept = new ConceptImpl();
      concept.setUrn("demo:" + name);
      concept.setName(name);
      concept.getType().add(SemanticType.QUALITY);
      var observation = new ObservationImpl();
      observation.setId(-1);
      observation.setUrn("demo:synthetic:" + name);
      observation.setObservable(ObservableImpl.promote(concept, null));
      observation.setGeometry(Geometry.create(
          "T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}S2(" + width + "," + height + "){proj=EPSG:4326,"
              + "shape=EPSG:4326 POLYGON ((" + west + " 0&comma;" + west + " 2&comma;"
              + (west + 3) + " 2&comma;" + (west + 3) + " 0&comma;" + west + " 0))}"));
      var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
      doReturn(mock(RuntimeService.class, RETURNS_DEEP_STUBS)).when(scope).getService(RuntimeService.class);
      when(scope.getConfiguration().getPersistence()).thenReturn(Persistence.EXPLICIT_ACTION);
      var manager = mock(StorageManagerImpl.class);
      when(manager.getDoubleBuffer(anyLong())).thenAnswer(call ->
          (BufferArray) BufferArray.R064.make(call.getArgument(0, Long.class)));
      storage = new StorageImpl(observation, LAYOUT, scope, manager);
      var scanner = scanner(false);
      assertEquals(values.length, scanner.size());
      for (double value : values) scanner.add(value);
      storage.finalizeRun(scanner);
    }

    Storage.DoubleScanner scanner(boolean readOnly) {
      var scanners = storage.scan(Scheduler.Event.initialization(), LAYOUT, Storage.DoubleScanner.class, readOnly);
      assertEquals(1, scanners.size(), "Single-tile demonstration");
      return scanners.getFirst();
    }

    @Override
    public void close() {
      storage.close(null);
    }
  }
}
