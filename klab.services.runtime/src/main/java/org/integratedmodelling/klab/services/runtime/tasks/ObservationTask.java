package org.integratedmodelling.klab.services.runtime.tasks;

import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwin;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ObservationTask implements Future<Observation> {

    AtomicBoolean running = new AtomicBoolean(false);
    AtomicBoolean started = new AtomicBoolean(false);
    AtomicBoolean canceled = new AtomicBoolean(false);
    AtomicReference<Observation> result = new AtomicReference<>(null);
    ContextScope scope;

    public ObservationTask(Dataflow<Observation> dataflow, ContextScope scope, DigitalTwin digitalTwin, boolean start) {
        this.scope = scope;
        if (start) {
            started.set(true);
            this.running.set(true);
            Thread.ofVirtual().unstarted(() -> {
                this.result.set(digitalTwin.runDataflow(dataflow, scope));
                this.running.set(false);
            }).start();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        if (mayInterruptIfRunning) {
            scope.interrupt();
        }
        canceled.set(true);
        return true;
    }

    @Override
    public boolean isCancelled() {
        return canceled.get();
    }

    @Override
    public boolean isDone() {
        return canceled.get() || (started.get() && !running.get());
    }

    @Override
    public Observation get() throws InterruptedException, ExecutionException {
        if (!started.get()) {
            throw new ExecutionException(new KIllegalStateException("get: observation task not started"));
        }
        while (this.result.get() == null && !canceled.get() && running.get()) {
            Thread.sleep(200);
        }
        return result.get();
    }

    @Override
    public Observation get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        long limit = TimeUnit.MILLISECONDS.convert(timeout, unit);
        long time = System.currentTimeMillis();
        if (!started.get()) {
            throw new ExecutionException(new KIllegalStateException("get: observation task not started"));
        }
        while (this.result.get() == null && !canceled.get() && running.get()) {
            Thread.sleep(200);
            if (System.currentTimeMillis() - time > limit) {
                throw new TimeoutException("timeout during observation task");
            }
        }
        return result.get();
    }

}
