package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.junit.jupiter.api.Test;

class ScannerAdaptersTest {

  @Test
  void floatStorageIsExposedThroughPrimitiveDoubleScannerWithoutBoxing() {
    var nativeScanner = new FloatArrayScanner(1.25f, 2.5f);
    var adapted = ScannerAdapters.adaptType(nativeScanner, Storage.DoubleScanner.class);

    assertSame(nativeScanner.shard(), adapted.shard());
    assertEquals(nativeScanner.size(), adapted.size());
    assertEquals(1.25, adapted.peek());
    assertEquals(1.25, adapted.get());
    adapted.add(9.75);

    assertEquals(9.75f, nativeScanner.values[1]);
    assertEquals(2, nativeScanner.index);
  }

  @Test
  void doubleStorageIsExposedThroughPrimitiveFloatScannerWithoutBoxing() {
    var nativeScanner = new DoubleArrayScanner(1.25, 2.5);
    var adapted = ScannerAdapters.adaptType(nativeScanner, Storage.FloatScanner.class);

    assertEquals(1.25f, adapted.get());
    adapted.add(9.75f);

    assertEquals(9.75, nativeScanner.values[1]);
    assertEquals(2, nativeScanner.index);
  }

  @Test
  void compatibleScannerIsNotWrapped() {
    var nativeScanner = new FloatArrayScanner(1.25f);

    assertSame(
        nativeScanner,
        ScannerAdapters.adaptType(nativeScanner, Storage.FloatScanner.class));
    assertSame(nativeScanner, ScannerAdapters.adaptType(nativeScanner, Storage.Scanner.class));
  }

  @Test
  void incompatibleFamiliesFailBeforeReflectiveInvocation() {
    var scanner = new FloatArrayScanner(1.25f);

    assertThrows(
        KlabIllegalArgumentException.class,
        () -> ScannerAdapters.adaptType(scanner, Storage.BooleanScanner.class));
  }

  private abstract static class ArrayScanner implements Storage.Scanner {

    private final Storage.Shard shard;
    protected int index;

    private ArrayScanner(Storage.Type nativeType) {
      shard = mock(Storage.Shard.class);
      when(shard.getNativeType()).thenReturn(nativeType);
    }

    @Override
    public Storage.Shard shard() {
      return shard;
    }

    @Override
    public boolean hasNext() {
      return index < size();
    }

    @Override
    public long nextLong() {
      return index++;
    }
  }

  private static final class FloatArrayScanner extends ArrayScanner
      implements Storage.FloatScanner {

    private final float[] values;

    private FloatArrayScanner(float... values) {
      super(Storage.Type.FLOAT);
      this.values = values;
    }

    @Override
    public long size() {
      return values.length;
    }

    @Override
    public float get() {
      return values[index++];
    }

    @Override
    public float peek() {
      return values[index];
    }

    @Override
    public void add(float value) {
      values[index++] = value;
    }
  }

  private static final class DoubleArrayScanner extends ArrayScanner
      implements Storage.DoubleScanner {

    private final double[] values;

    private DoubleArrayScanner(double... values) {
      super(Storage.Type.DOUBLE);
      this.values = values;
    }

    @Override
    public long size() {
      return values.length;
    }

    @Override
    public double get() {
      return values[index++];
    }

    @Override
    public double peek() {
      return values[index];
    }

    @Override
    public void add(double value) {
      values[index++] = value;
    }
  }
}
