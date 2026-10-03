package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import org.integratedmodelling.klab.api.data.Storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ojalgo.array.BufferArray;

/** Opt-in measurement; correctness assertions only, never speed thresholds. */
class ShardIOBenchmarkIT {
  @TempDir Path directory;

  @Test void compareDurableWritesAndWarmReadsWithScalarReference() throws Exception {
    int rounds = Integer.getInteger("klab.shard.io.rounds", 5);
    int warmups = Integer.getInteger("klab.shard.io.warmups", 2);
    assertTrue(rounds >= 3 && warmups >= 1);
    var report = new StringBuilder();
    report.append("Storage I/O benchmark; scalar reference at 721f37365 vs current production path\n");
    report.append("java=").append(System.getProperty("java.version"))
        .append(",os=").append(System.getProperty("os.name"))
        .append(",arch=").append(System.getProperty("os.arch"))
        .append(",processors=").append(Runtime.getRuntime().availableProcessors())
        .append(",maxHeap=").append(Runtime.getRuntime().maxMemory())
        .append(",fileStore=").append(Files.getFileStore(directory).type())
        .append(",warmups=").append(warmups).append(",rounds=").append(rounds).append('\n');
    report.append("In-memory BufferArray source/target; durable writes include FileDescriptor.sync; reads are warm-cache.\n");
    report.append("Allocation, fixture filling, byte comparison and value checks are outside timing.\n");
    report.append("type,values,payloadMiB,scalarWriteMs,bulkWriteMs,writeRatio,scalarReadMs,bulkReadMs,readRatio\n");
    for (var type : Storage.Type.values()) {
      for (int size : new int[] {262144, 4194304}) {
        try (var source = (BufferArray) StorageManagerImpl.bufferFactory(type).make(size);
             var target = (BufferArray) StorageManagerImpl.bufferFactory(type).make(size)) {
          for (int i = 0; i < size; i++) {
            switch (type) {
              case DOUBLE, FLOAT -> source.set(i, (i % 1001 - 500) * .125);
              case LONG -> source.set(i, Long.MAX_VALUE - i);
              case INTEGER, KEYED -> source.set(i, i * 17);
              case BOOLEAN -> source.set(i, i % 2);
            }
          }
          var scalar = directory.resolve(type + "-scalar.dat").toFile();
          var bulk = directory.resolve(type + "-bulk.dat").toFile();
          double[][] elapsed = new double[4][rounds];
          for (int run = -warmups; run < rounds; run++) {
            // Alternate paired order to reduce systematic cache/JIT/order advantage.
            for (int pass = 0; pass < 2; pass++) {
              boolean reference = ((run + warmups + pass) & 1) == 0;
              long start = System.nanoTime();
              if (reference) ScalarShardIOReference.write(source, scalar, type);
              else StorageManagerImpl.writeBufferArray(source, bulk, type);
              double writeMillis = (System.nanoTime() - start) / 1e6;
              start = System.nanoTime();
              if (reference) ScalarShardIOReference.read(target, scalar, type);
              else StorageManagerImpl.readBufferArray(target, bulk, type);
              double readMillis = (System.nanoTime() - start) / 1e6;
              if (run >= 0) {
                elapsed[reference ? 0 : 1][run] = writeMillis;
                elapsed[reference ? 2 : 3][run] = readMillis;
              }
              for (int i = 0; i < size; i++) {
                switch (type) {
                  case DOUBLE, FLOAT -> assertEquals((i % 1001 - 500) * .125, target.doubleValue(i));
                  case LONG -> assertEquals(Long.MAX_VALUE - i, target.longValue(i));
                  case INTEGER, KEYED -> assertEquals(i * 17, target.intValue(i));
                  case BOOLEAN -> assertEquals(i % 2, target.byteValue(i));
                }
              }
            }
            assertEquals(-1, Files.mismatch(scalar.toPath(), bulk.toPath()), "Identical version-1 bytes");
          }
          double sw = median(elapsed[0]), bw = median(elapsed[1]);
          double sr = median(elapsed[2]), br = median(elapsed[3]);
          String row = String.format(Locale.ROOT, "%s,%d,%.1f,%.3f,%.3f,%.2f,%.3f,%.3f,%.2f%n",
              type, size, size * (double) type.size() / (1024 * 1024), sw, bw, sw / bw, sr, br, sr / br);
          report.append(row);
          report.append("# samples_ms ").append(type).append('/').append(size)
              .append(" scalarWrite=").append(Arrays.toString(elapsed[0]))
              .append(" bulkWrite=").append(Arrays.toString(elapsed[1]))
              .append(" scalarRead=").append(Arrays.toString(elapsed[2]))
              .append(" bulkRead=").append(Arrays.toString(elapsed[3])).append('\n');
          System.out.print(row);
        }
      }
    }
    var target = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
    var file = target.resolve("shard-io-benchmark.txt");
    Files.writeString(file, report);
    System.out.println(report);
    System.out.println("Report: " + file);
  }

  private static double median(double[] values) {
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    int middle = sorted.length / 2;
    return sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
  }
}
