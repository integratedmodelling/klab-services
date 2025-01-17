package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;

public interface WaitStrategy {

  /**
   * Wait until the given deadline, deadlineNanoseconds
   *
   * @param deadlineNanoseconds deadline to wait for, in milliseconds
   */
  public void waitUntil(long deadlineNanoseconds);

  /**
   * Yielding wait strategy.
   *
   * <p>Spins in the loop, until the deadline is reached. Releases the flow control by means of
   * Thread.yield() call. This strategy is less precise than BusySpin one, but is more
   * scheduler-friendly.
   */
  public static class YieldingWait implements WaitStrategy {

    @Override
    public void waitUntil(long deadline) {
      while (deadline >= System.nanoTime()) {
        Thread.yield();
        if (Thread.currentThread().isInterrupted()) {
          throw new KlabIllegalStateException("real-time scheduling interrupted");
        }
      }
    }
  }

  /**
   * BusySpin wait strategy.
   *
   * <p>Spins in the loop until the deadline is reached. In a multi-core environment, will occupy an
   * entire core. Is more precise than Sleep wait strategy, but consumes more resources.
   */
  public static class BusySpinWait implements WaitStrategy {

    @Override
    public void waitUntil(long deadline) {
      while (deadline >= System.nanoTime()) {
        if (Thread.currentThread().isInterrupted()) {
          throw new KlabIllegalStateException("real-time scheduling interrupted");
        }
      }
    }
  }

  /**
   * Sleep wait strategy.
   *
   * <p>Will release the flow control, giving other threads a possibility of execution on the same
   * processor. Uses less resources than BusySpin wait, but is less precise.
   */
  public static class SleepWait implements WaitStrategy {

    @Override
    public void waitUntil(long deadline) {
      long sleepTimeNanos = deadline - System.nanoTime();
      if (sleepTimeNanos > 0) {
        long sleepTimeMillis = sleepTimeNanos / 1000000;
        int sleepTimeNano = (int) (sleepTimeNanos - (sleepTimeMillis * 1000000));
        try {
          Thread.sleep(sleepTimeMillis, sleepTimeNano);
        } catch (InterruptedException e) {
          throw new KlabIllegalStateException("real-time scheduling interrupted");
        }
      }
    }
  }
}
