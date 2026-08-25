package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Method;
import org.integratedmodelling.klab.api.data.Storage;
import org.junit.jupiter.api.Test;

class AbstractExecutorScannerBindingTest {

  @Test
  void componentDoubleScannerParameterAcceptsNativeFloatStorage() throws Exception {
    Method method =
        ComponentContextualizer.class.getDeclaredMethod("run", Storage.DoubleScanner.class);
    var nativeScanner = new FloatScanner(2.5f, 0f);

    var bound =
        AbstractExecutor.adaptObservationArgument(
            method.getParameters()[0], null, nativeScanner);
    var scanner = assertInstanceOf(Storage.DoubleScanner.class, bound);

    assertEquals(2.5, scanner.get());
    scanner.add(7.75);
    assertEquals(7.75f, nativeScanner.values[1]);
  }

  private static final class ComponentContextualizer {
    @SuppressWarnings("unused")
    private void run(Storage.DoubleScanner output) {}
  }

  private static final class FloatScanner implements Storage.FloatScanner {

    private final float[] values;
    private int index;

    private FloatScanner(float... values) {
      this.values = values;
    }

    @Override
    public Storage.Shard shard() {
      return null;
    }

    @Override
    public long size() {
      return values.length;
    }

    @Override
    public boolean hasNext() {
      return index < values.length;
    }

    @Override
    public long nextLong() {
      return index++;
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
}
