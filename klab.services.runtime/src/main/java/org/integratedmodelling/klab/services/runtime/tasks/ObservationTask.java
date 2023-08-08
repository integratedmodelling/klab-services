package org.integratedmodelling.klab.services.runtime.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class ObservationTask implements Future<Observation> {

    AtomicBoolean running = new AtomicBoolean(false);

    public ObservationTask(Dataflow<?> dataflow, ContextScope scope, boolean start) {
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
    public Observation get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        // TODO Auto-generated method stub
        return null;
    }

    public Observation runDataflow(Dataflow<Observation> dataflow, ContextScope scope) {

        this.running.set(true);

        for (Actuator actuator : sortComputation(dataflow)) {
            if (!runActuator(actuator, scope)) {
                break;
            }
        }

        this.running.set(false);

        return null;
    }

    /**
     * Run the passed actuator. Dependency order is already guaranteed. The context scope should be
     * updated by sending messages through send(), which in turn should communicate with the actor
     * to guarantee order of execution and integrity, updating the catalog when the actor responds.
     * 
     * @param actuator
     * @param scope
     * @return
     */
    private boolean runActuator(Actuator actuator, ContextScope scope) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Establish the order of execution. Each root actuator should be sorted by dependency and
     * appended in order to the result list.
     * 
     * TODO while doing this we should ensure we have all we need to run the contextualizer calls,
     * using the scope to load components as needed.
     * 
     * @param dataflow
     * @return
     */
    private List<Actuator> sortComputation(Dataflow<Observation> dataflow) {
        List<Actuator> ret = new ArrayList<>();
        // TODO Auto-generated method stub.
        return ret;
    }

}
