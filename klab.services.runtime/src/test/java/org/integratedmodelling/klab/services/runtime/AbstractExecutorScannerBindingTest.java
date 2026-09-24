package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Method;
import org.integratedmodelling.klab.api.data.Storage;
import org.junit.jupiter.api.Test;

class AbstractExecutorScannerBindingTest {
  @Test void timeInstantParametersIdentifyTheExecutedEventBoundary() throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var descriptors = org.mockito.Mockito.mock(CompiledDataflow.CallDescriptors.class);
    var executor = org.mockito.Mockito.mock(AbstractExecutor.class,
        org.mockito.Mockito.withSettings().useConstructor(descriptors, null, null, java.util.Map.of())
            .defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS));
    var observation = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    var method = ComponentContextualizer.class.getDeclaredMethod("boundary",
        org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.class);
    for (var boundary : new org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.Boundary[] {
        org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.Boundary.START,
        org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.Boundary.END}) {
      long instant = boundary == org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.Boundary.START ? 1000 : 9000;
      var event = new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.TransitionEvent(
          "boundary:" + boundary, instant, instant, observation, boundary);
      var arguments = executor.matchArguments(null, method, null, null, null, java.util.Map.of(),
          observation, null, null, null, null, null, null, null, event, null);
      assertEquals(instant, assertInstanceOf(
          org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant.class,
          arguments.getFirst()).getMilliseconds());
    }
  }

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
    private void boundary(org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant instant) {}
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
