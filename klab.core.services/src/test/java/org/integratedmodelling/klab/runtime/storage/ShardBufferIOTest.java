package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.integratedmodelling.klab.api.data.Storage;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.ojalgo.array.BufferArray;
import org.ojalgo.structure.Access1D;
import org.ojalgo.structure.Mutate1D;

class ShardBufferIOTest {
  @TempDir Path directory;

  @TestFactory Stream<DynamicTest> versionOneBytesAndCrossReadersMatchAtBlockBoundaries() {
    return Arrays.stream(Storage.Type.values()).flatMap(type -> {
      int blockValues = ShardBufferIO.BLOCK_BYTES / type.size();
      return IntStream.of(0, 1, blockValues - 1, blockValues, blockValues + 1, 3 * blockValues + 7)
          .mapToObj(size -> DynamicTest.dynamicTest(type + " v1 values=" + size, () -> {
            var original = directory.resolve(type + "-" + size + "-reference.dat");
            var candidate = directory.resolve(type + "-" + size + "-bulk.dat");
            try (var source = array(type, size); var restored = array(type, size)) {
              fill(source, type);
              ScalarShardIOReference.write(source, original.toFile(), type);
              StorageManagerImpl.writeBufferArray(source, candidate.toFile(), type);
              assertEquals(-1, Files.mismatch(original, candidate), "Exact header and payload compatibility");
              assertEquals(24L + (long) size * type.size(), Files.size(candidate));
              StorageManagerImpl.readBufferArray(restored, original.toFile(), type);
              assertValues(source, restored, type);
              ScalarShardIOReference.read(restored, candidate.toFile(), type);
              assertValues(source, restored, type);
            }
            // The durable files must not be retained as mappings or open stream handles.
            Files.delete(original);
            Files.delete(candidate);
          }));
    });
  }

  @TestFactory Stream<DynamicTest> headerlessNativeEndianFilesRemainReadable() {
    return Arrays.stream(Storage.Type.values()).flatMap(type ->
        IntStream.of(0, 1, 5, ShardBufferIO.BLOCK_BYTES / type.size() + 3)
            .mapToObj(size -> DynamicTest.dynamicTest(type + " legacy values=" + size, () -> {
              try (var source = array(type, size); var target = array(type, size)) {
                fill(source, type);
                var file = directory.resolve(type + "-legacy-" + size);
                Files.write(file, payload(source, type, ByteOrder.nativeOrder()));
                StorageManagerImpl.readBufferArray(target, file.toFile(), type);
                assertValues(source, target, type);
              }
            })));
  }

  @TestFactory Stream<DynamicTest> payloadDecoderHandlesBothOrdersAndShortReads() {
    return Arrays.stream(Storage.Type.values()).flatMap(type ->
        Stream.of(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN).map(order ->
            DynamicTest.dynamicTest(type + " fragmented " + order, () -> {
              int count = ShardBufferIO.BLOCK_BYTES / type.size() + 3;
              try (var source = array(type, count); var target = array(type, count)) {
                fill(source, type);
                try (var input = new DataInputStream(new ByteArrayInputStream(payload(source, type, order)) {
                  @Override public synchronized int read(byte[] b, int offset, int length) {
                    return super.read(b, offset, Math.min(length, 7));
                  }
                })) {
                  ShardBufferIO.read(target, input, type, order);
                  assertEquals(-1, input.read());
                }
                assertValues(source, target, type);
              }
            })));
  }

  @Test void legacyNanPayloadsAndSignedZerosArePreservedByReading() throws Exception {
    long[] doubles = {0x7ff8000000000042L, 0xfff8000000000012L, Long.MIN_VALUE, 0};
    int[] floats = {0x7fc00042, 0xffc00012, Integer.MIN_VALUE, 0};
    var bytes = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
    for (long bits : doubles) bytes.putLong(bits);
    var file = directory.resolve("legacy-double");
    Files.write(file, bytes.array());
    try (var target = array(Storage.Type.DOUBLE, doubles.length)) {
      StorageManagerImpl.readBufferArray(target, file.toFile(), Storage.Type.DOUBLE);
      for (int i = 0; i < doubles.length; i++) assertEquals(doubles[i], Double.doubleToRawLongBits(target.doubleValue(i)));
    }
    bytes = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder());
    for (int bits : floats) bytes.putInt(bits);
    file = directory.resolve("legacy-float");
    Files.write(file, bytes.array());
    try (var target = array(Storage.Type.FLOAT, floats.length)) {
      StorageManagerImpl.readBufferArray(target, file.toFile(), Storage.Type.FLOAT);
      for (int i = 0; i < floats.length; i++) assertEquals(floats[i], Float.floatToRawIntBits(target.floatValue(i)));
    }
  }

  @Test void badHeadersAndLengthsFailBeforeDestinationMutation() throws Exception {
    try (var source = array(Storage.Type.DOUBLE, 128); var target = array(Storage.Type.DOUBLE, 128)) {
      fill(source, Storage.Type.DOUBLE);
      var file = directory.resolve("invalid.dat");
      ScalarShardIOReference.write(source, file.toFile(), Storage.Type.DOUBLE);
      byte[] valid = Files.readAllBytes(file);
      for (int field : new int[] {0, 4, 8, 12, 16}) {
        byte[] invalid = valid.clone();
        ByteBuffer.wrap(invalid).putInt(field, -1);
        rejectWithoutMutation(file, invalid, target);
      }
      for (int length : new int[] {0, 3, 7, 23, valid.length - 1, valid.length + 1})
        rejectWithoutMutation(file, Arrays.copyOf(valid, length), target);
    }
  }

  @Test void eofInALaterBlockDoesNotDecodeThePartialBlock() throws Exception {
    int firstBlock = ShardBufferIO.BLOCK_BYTES / Long.BYTES;
    try (var source = array(Storage.Type.LONG, firstBlock + 4);
         var target = array(Storage.Type.LONG, firstBlock + 4)) {
      fill(source, Storage.Type.LONG);
      for (long i = 0; i < target.count(); i++) target.set(i, 42L);
      byte[] truncated = Arrays.copyOf(payload(source, Storage.Type.LONG, ByteOrder.BIG_ENDIAN),
          ShardBufferIO.BLOCK_BYTES + 3);
      try (var input = new DataInputStream(new ByteArrayInputStream(truncated))) {
        assertThrows(EOFException.class, () -> ShardBufferIO.read(target, input, Storage.Type.LONG, ByteOrder.BIG_ENDIAN));
      }
      for (int i = 0; i < firstBlock; i++) assertEquals(source.longValue(i), target.longValue(i));
      for (int i = firstBlock; i < target.count(); i++) assertEquals(42L, target.longValue(i));
      // Earlier complete blocks have changed: this is explicitly not whole-array rollback.
    }
  }

  @Test void payloadIoExceptionsPropagateAndCallerOwnsStreams() throws Exception {
    var failure = new IOException("injected transfer failure");
    try (var source = array(Storage.Type.DOUBLE, 10)) {
      var output = new OutputStream() {
        public void write(int b) throws IOException { throw failure; }
        public void close() { fail("Payload codec must not close caller output"); }
      };
      assertSame(failure, assertThrows(IOException.class, () -> ShardBufferIO.write(source, output, Storage.Type.DOUBLE)));
      var input = new DataInputStream(new InputStream() {
        public int read() throws IOException { throw failure; }
        public int read(byte[] b, int off, int len) throws IOException { throw failure; }
        public void close() { fail("Payload codec must not close caller input"); }
      });
      assertSame(failure, assertThrows(IOException.class,
          () -> ShardBufferIO.read(source, input, Storage.Type.DOUBLE, ByteOrder.BIG_ENDIAN)));
    }
  }

  @Test void transferStorageIsReusedAndBoundedEvenForAHugeLogicalArray() throws Exception {
    var source = mock(Access1D.class);
    var target = mock(Mutate1D.class);
    when(source.count()).thenReturn(1L << 40);
    when(target.count()).thenReturn(1L << 40);
    var stop = new IOException("stop after inspecting two blocks");
    var output = new OutputStream() {
      byte[] first;
      int calls;
      public void write(int value) { fail("Scalar stream writes must not occur"); }
      public void write(byte[] bytes, int offset, int length) throws IOException {
        assertEquals(ShardBufferIO.BLOCK_BYTES, bytes.length);
        assertEquals(ShardBufferIO.BLOCK_BYTES, length);
        if (calls++ == 0) first = bytes;
        else { assertSame(first, bytes); throw stop; }
      }
    };
    assertSame(stop, assertThrows(IOException.class, () -> ShardBufferIO.write(source, output, Storage.Type.DOUBLE)));
    var input = new DataInputStream(new InputStream() {
      byte[] first;
      int calls;
      public int read() { fail("Scalar stream reads must not occur"); return -1; }
      public int read(byte[] bytes, int offset, int length) throws IOException {
        assertEquals(ShardBufferIO.BLOCK_BYTES, bytes.length);
        assertEquals(ShardBufferIO.BLOCK_BYTES, length);
        if (calls++ == 0) { first = bytes; Arrays.fill(bytes, (byte) 0); return length; }
        assertSame(first, bytes);
        throw stop;
      }
    });
    assertSame(stop, assertThrows(IOException.class,
        () -> ShardBufferIO.read(target, input, Storage.Type.DOUBLE, ByteOrder.BIG_ENDIAN)));
  }

  @Test void payloadLengthOverflowIsRejectedBeforeAllocationOrTransfer() {
    var source = mock(Access1D.class);
    var target = mock(Mutate1D.class);
    when(source.count()).thenReturn(Long.MAX_VALUE);
    when(target.count()).thenReturn(Long.MAX_VALUE);
    var output = mock(OutputStream.class);
    var input = new DataInputStream(InputStream.nullInputStream());
    assertThrows(ArithmeticException.class, () -> ShardBufferIO.write(source, output, Storage.Type.LONG));
    assertThrows(ArithmeticException.class, () -> ShardBufferIO.read(target, input, Storage.Type.LONG, ByteOrder.BIG_ENDIAN));
    verifyNoInteractions(output);
  }

  @Test void concurrentTransfersHaveIndependentBuffersAndFiles() throws Exception {
    try (var threads = Executors.newFixedThreadPool(4)) {
      var jobs = Arrays.stream(Storage.Type.values()).<java.util.concurrent.Callable<Void>>map(type -> () -> {
        int size = 2 * ShardBufferIO.BLOCK_BYTES / type.size() + 11;
        try (var source = array(type, size); var target = array(type, size)) {
          fill(source, type);
          var file = directory.resolve("concurrent-" + type);
          StorageManagerImpl.writeBufferArray(source, file.toFile(), type);
          StorageManagerImpl.readBufferArray(target, file.toFile(), type);
          assertValues(source, target, type);
        }
        return null;
      }).toList();
      for (var future : threads.invokeAll(jobs, 30, TimeUnit.SECONDS)) future.get();
    }
  }

  static BufferArray array(Storage.Type type, long size) {
    return (BufferArray) StorageManagerImpl.bufferFactory(type).make(size);
  }

  private static void rejectWithoutMutation(Path file, byte[] bytes, BufferArray target) throws Exception {
    for (long i = 0; i < target.count(); i++) target.set(i, 42.0);
    Files.write(file, bytes);
    assertThrows(IOException.class, () -> StorageManagerImpl.readBufferArray(target, file.toFile(), Storage.Type.DOUBLE));
    for (long i = 0; i < target.count(); i++) assertEquals(42.0, target.doubleValue(i));
  }

  static void fill(BufferArray values, Storage.Type type) {
    double[] doubles = {Double.NaN, -0.0, 0, 1.25, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
        Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE, -Double.MAX_VALUE,
        Double.longBitsToDouble(0x7ff8000000000042L), Double.longBitsToDouble(0xfff8000000000012L)};
    float[] floats = {Float.NaN, -0.0f, 0, 1.25f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
        Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE, -Float.MAX_VALUE,
        Float.intBitsToFloat(0x7fc00042), Float.intBitsToFloat(0xffc00012)};
    long[] longs = {Long.MIN_VALUE, Long.MAX_VALUE, (1L << 53) + 1, -((1L << 53) + 1), 0, -1, 1};
    int[] ints = {0, Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 1, 42};
    byte[] bytes = {0, 1, -1, Byte.MIN_VALUE, Byte.MAX_VALUE};
    for (long i = 0; i < values.count(); i++) {
      switch (type) {
        case DOUBLE -> values.set(i, doubles[(int) (i % doubles.length)]);
        case FLOAT -> values.set(i, floats[(int) (i % floats.length)]);
        case LONG -> values.set(i, longs[(int) (i % longs.length)]);
        case INTEGER, KEYED -> values.set(i, ints[(int) (i % ints.length)]);
        case BOOLEAN -> values.set(i, bytes[(int) (i % bytes.length)]);
      }
    }
  }

  private static byte[] payload(BufferArray source, Storage.Type type, ByteOrder order) {
    var bytes = ByteBuffer.allocate(Math.toIntExact(source.count() * type.size())).order(order);
    for (long i = 0; i < source.count(); i++) {
      switch (type) {
        case DOUBLE -> bytes.putDouble(source.doubleValue(i));
        case FLOAT -> bytes.putFloat(source.floatValue(i));
        case LONG -> bytes.putLong(source.longValue(i));
        case INTEGER, KEYED -> bytes.putInt(source.intValue(i));
        case BOOLEAN -> bytes.put(source.byteValue(i));
      }
    }
    return bytes.array();
  }

  private static void assertValues(BufferArray source, BufferArray target, Storage.Type type) {
    for (long i = 0; i < source.count(); i++) {
      switch (type) {
        case DOUBLE -> assertEquals(Double.doubleToLongBits(source.doubleValue(i)), Double.doubleToLongBits(target.doubleValue(i)));
        case FLOAT -> assertEquals(Float.floatToIntBits(source.floatValue(i)), Float.floatToIntBits(target.floatValue(i)));
        case LONG -> assertEquals(source.longValue(i), target.longValue(i));
        case INTEGER, KEYED -> assertEquals(source.intValue(i), target.intValue(i));
        case BOOLEAN -> assertEquals(source.byteValue(i), target.byteValue(i));
      }
    }
  }
}
