package org.integratedmodelling.klab.services.runtime.tasks;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.integratedmodelling.contrib.jgrapht.graph.DefaultEdge;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.runtime.DataflowInfo;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwin;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

public class ObservationTask implements Future<Observation> {

    private long MAXIMUM_TASK_TIMEOUT_HOURS = 48l;

    AtomicBoolean running = new AtomicBoolean(false);
    AtomicReference<Observation> result = new AtomicReference<>(null);

    public ObservationTask(Dataflow<Observation> dataflow, ContextScope scope, boolean start) {
        if (start) {
            Thread.ofVirtual().unstarted(() -> result.set(runDataflow(dataflow, scope))).start();
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

    /**
     * Run all the actuators, honoring execution order and parallelism
     *
     * @param dataflow
     * @param scope
     * @return
     */
    public Observation runDataflow(Dataflow<Observation> dataflow, ContextScope scope) {

        var dt = getContextData(scope);
        var info = new DataflowInfo();
        if (!info.validate(scope)) {
            return Observation.empty();
        }

        var executionOrder = sortComputation(dataflow, info);
        var groups = executionOrder.stream().collect(Collectors.groupingBy(s -> s.getSecond()));
        var lastOrder = executionOrder.getLast().getSecond();
        var parallelism = dt.getParallelism(scope);
        this.running.set(true);
        for (int i = 0; i <= lastOrder; i++) {
            var actuatorGroup = groups.get(i);
            if (actuatorGroup.size() > 1 && parallelism.getAsInt() > 1) {
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (var actuator : actuatorGroup) {
                        executor.submit(() -> dt.runActuator(actuator.getFirst(), scope));
                    }
                    // ehm.
                    if (!executor.awaitTermination(MAXIMUM_TASK_TIMEOUT_HOURS, TimeUnit.HOURS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    scope.error(e);
                    break;
                }
            } else {
                boolean error = false;
                for (var actuator : actuatorGroup) {
                    if ((error = !dt.runActuator(actuator.getFirst(), scope))) {
                        break;
                    }
                }
                if (error) {
                    break;
                }
            }
        }
        this.running.set(false);

        return dt.getObservation(dataflow.getComputation().iterator().next().getId());
    }

    public DigitalTwin getContextData(ContextScope scope) {
        var dt = scope.getData().get(DigitalTwin.KEY, DigitalTwin.class);
        if (dt == null) {
            dt = new DigitalTwin(scope);
            scope.getData().put(DigitalTwin.KEY, dt);
        }
        return dt;
    }

    /**
     * Establish the order of execution and the possible parallelism. Each root actuator should be sorted by dependency
     * and appended in order to the result list along with its order of execution. Successive roots can refer to the
     * previous roots but they must be executed sequentially.
     * <p>
     * TODO while doing this we should ensure we have all we need to run the
     * contextualizer calls, using the scope to load components as needed.
     * <p>
     * We should also collect all the observables being used, so we have a blueprint
     * to produce what we need only, and build the influence graph which could
     * simply use strings given that the actuator and observation IDs are identical.
     * Actuators and observations should be quickly available through the ID.
     *
     * @param dataflow
     * @return
     */
    private List<Pair<Actuator, Integer>> sortComputation(Dataflow<Observation> dataflow, DataflowInfo info) {
        List<Pair<Actuator, Integer>> ret = new ArrayList<>();
        for (Actuator root : dataflow.getComputation()) {
            int executionOrder = 0;
            Map<String, Actuator> branch = new HashMap<>();
            collectActuators(Collections.singletonList(root), branch);
            var dependencyGraph = createDependencyGraph(branch);
            TopologicalOrderIterator<Actuator, DefaultEdge> order = new TopologicalOrderIterator<>(
                    dependencyGraph);

            // group by dependency w.r.t. the previous group and assign the execution order based on the
            // group index, so that we know what we can execute in parallel
            Set<Actuator> group = new HashSet<>();
            while (order.hasNext()) {
                Actuator next = order.next();
                if (!info.containsActuator(next.getId())) {
                    ret.add(Pair.of(next, (executionOrder = checkExecutionOrder(executionOrder, next, dependencyGraph,
                            group))));
                    info.notifyActuator(next);
                }
            }
        }

        return ret;
    }

    /**
     * If the actuator depends on any in the currentGroup, empty the group and increment the order; otherwise, add it to
     * the group and return the same order.
     *
     * @param executionOrder
     * @param current
     * @param dependencyGraph
     * @param currentGroup
     * @return
     */
    private int checkExecutionOrder(int executionOrder, Actuator current, Graph<Actuator, DefaultEdge> dependencyGraph,
                                    Set<Actuator> currentGroup) {
        boolean dependency = false;
        for (Actuator previous : currentGroup) {
            for (var edge : dependencyGraph.incomingEdgesOf(current)) {
                if (currentGroup.contains(dependencyGraph.getEdgeSource(edge))) {
                    dependency = true;
                    break;
                }
            }
        }

        if (dependency) {
            currentGroup.clear();
            return executionOrder + 1;
        }

        currentGroup.add(current);

        return executionOrder;
    }

    private void collectActuators(List<Actuator> actuators, Map<String, Actuator> ret) {
        for (Actuator actuator : actuators) {
            if (!actuator.isReference()) {
                /*
                 * TODO compile a list of all services + versions, validate the actuator, create
                 * any needed notifications and a table of translations for local names
                 */
                ret.put(actuator.getId(), actuator);
            }
            collectActuators(actuator.getChildren(), ret);
        }
    }

    /**
     * Build and return the dependency graph for the passed actuators. Save externally if appropriate - caching does
     * create issues in contextualization and scheduling.
     *
     * @return
     */
    public Graph<Actuator, DefaultEdge> createDependencyGraph(Map<String, Actuator> actuators) {
        Graph<Actuator, DefaultEdge> ret = new DefaultDirectedGraph<>(DefaultEdge.class);
        for (Actuator actuator : actuators.values()) {
            ret.addVertex(actuator);
            for (Actuator child : actuator.getChildren()) {
                var ref = actuators.get(child.getId());
                if (ref != null) {
                    ret.addVertex(ref);
                    ret.addEdge(child, actuator);
                }
            }
        }
        return ret;
    }

}
