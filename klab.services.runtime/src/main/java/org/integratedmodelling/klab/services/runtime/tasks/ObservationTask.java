package org.integratedmodelling.klab.services.runtime.tasks;

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
    AtomicReference<Observation> result = new AtomicReference<>(null);

    public ObservationTask(Dataflow<Observation> dataflow, ContextScope scope, boolean start) {
        if (start) {
            Thread.ofVirtual().unstarted(() -> {
                DigitalTwin digitalTwin = getContextData(scope);
                this.running.set(true);
                result.set(digitalTwin.runDataflow(dataflow, scope));
                this.running.set(false);
            }).start();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isCancelled() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isDone() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Observation get() throws InterruptedException, ExecutionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observation get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        // TODO Auto-generated method stub
        return null;
    }


    public DigitalTwin getContextData(ContextScope scope) {
        var dt = scope.getData().get(DigitalTwin.KEY, DigitalTwin.class);
        if (dt == null) {
            dt = new DigitalTwin(scope);
            scope.getData().put(DigitalTwin.KEY, dt);
        }
        return dt;
    }


}
