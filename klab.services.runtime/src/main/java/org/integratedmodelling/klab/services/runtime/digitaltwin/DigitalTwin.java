package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.contrib.jgrapht.graph.DefaultEdge;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.ObservationGroup;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ProcessImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.runtime.storage.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.ojalgo.concurrent.Parallelism;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * A DigitalTwin is the server-side observation content kept with a context scope, which acts as a "handle" to it. It
 * contains all observations, their storage, the influence diagram between observations with the log of any modification
 * event timestamp, the scheduler, the event manager and the catalog of ID->{observation, actuator, storage, runtime
 * data...}. Also maintains the logical and physical "family tree" of observation and manages the bookkeeping of any
 * runtime assets so that they are known and disposed of properly when the context scope ends. The logical tree skips
 * the instance container built for the instantiators, which is kept in the physical structure.
 * <p>
 * The DigitalTwin is accessed through the {@link ContextScope}. Eventually it may have its own API contract, although
 * all interaction is currently managed through {@link ContextScope}. It is a {@link Closeable} and its close() method
 * should notify every listener, stop all active threads, then free up all internal resources.</p>
 *
 * <p>The digital twin also holds a catalog of the dataflows resolved, keyed by their coverage  and context, so that
 * successive resolutions of instances can reuse a previous dataflow when it is applicable instead of asking the
 * resolver again. This can be configured to be applied only above a certain threshold in the number of instances, or
 * turned off completely, in case speed and space occupation are no issue but maximum dataflow "fit" to the resolved
 * object and its scale must be ensured.</p>
 *
 * <p>Ideas for the DT API and interface:</p>
 * <ul>
 *     <li>Use GraphQL on the main DT GET endpoint for inquiries along the observation structure and possibly the
 *     dataflow. That could be just the URL of the runtime + /{observation|dataflow}/+ the ID of the DT/context,
 *     possibly starting at a non-root observation or actuator (their IDs are the same). This can be the basis for
 *     the remote digital twin API.</li>
 * </ul>
 *
 * @author Ferd
 */
public class DigitalTwin implements Closeable {

    private final ContextScope scope;
    private long MAXIMUM_TASK_TIMEOUT_HOURS = 48l;
    private StorageScope storageScope;

    public boolean validate(ContextScope scope) {
        // check out all calls etc.
        return true;
    }

    public Collection<Observation> getRootObservations() {
        return rootObservations;
    }

    /**
     * Types of the events that can be subscribed to and get communicated. This should evolve into a comprehensive
     * taxonomy of DT events for remote client scopes, debuggers and loggers.
     *
     * @author Ferd
     */
    public enum Event {

    }

    /**
     * Fastest possible way to check if an event must be sent. This does not specify what the listeners are and is just
     * used to wrap the event sending code.
     * <p>
     * TODO this should be a map pointing to a list of scopes for each event. New events should be pushed and
     * processed in a FIFO queue handled by a monitor thread
     */
    private Set<Event> listenedEvents = EnumSet.noneOf(Event.class);

    /*
     * TODO another fast map of event and subscriptions.
     */

    /**
     * The local asset catalog. Most importantly for disposal at end.
     */
    private Set<RuntimeAsset> runtimeAssets = new HashSet<>();


    /**
     * Create an executor for the computation and return it, or - if appropriate - merge the computation into the
     * previous executor and return it.
     *
     * @param actuator
     * @param computation
     * @param previousExecutor
     * @return
     */

    private Executor createExecutor(Actuator actuator, Observation observation, ServiceCall computation,
                                    ContextScope scope,
                                    Executor previousExecutor) {

        var languageService = Configuration.INSTANCE.getService(Language.class);
        var functor = languageService.execute(computation, scope, Object.class);
        var parallelism = getParallelism(scope);

        return switch (functor) {
            case null ->
                    throw new KlabInternalErrorException("function call " + computation.getName() + " produced a null" +
                            " result");
            case Instantiator instantiator -> new Executor(Executor.Type.OBSERVATION_INSTANTIATOR, parallelism) {
                @Override
                public void accept(Observation observation, ContextScope contextScope) {
                    var observable = observation.getObservable().as(DescriptionType.ACKNOWLEDGEMENT);
                    var instances = instantiator.resolve(observation.getObservable(), computation, scope);
                    // TODO this should be external at this point, part of the deferral strategy
                    // create observation, add it and call the resolver back; cache the dataflow if so configured
                    // configuration may only cache the dataflow above a certain number of instances
                    // parallelize the resolution of the observations as needed using virtual threads
                }
            };
            case DoubleValueResolver dresolver ->
                    previousExecutor == null ? new Executor(Executor.Type.VALUE_RESOLVER, parallelism, dresolver) {
                        @Override
                        public void accept(Observation observation, ContextScope contextScope) {
                            executeChain((State) observation, contextScope);
                        }
                    } : previousExecutor.chain(dresolver);
            case IntValueResolver iresolver ->
                    previousExecutor == null ? new Executor(Executor.Type.VALUE_RESOLVER, parallelism, iresolver) {
                        @Override
                        public void accept(Observation observation, ContextScope contextScope) {
                            executeChain((State) observation, contextScope);
                        }
                    } : previousExecutor.chain(iresolver);
            case ConceptValueResolver cresolver ->
                    previousExecutor == null ? new Executor(Executor.Type.VALUE_RESOLVER, parallelism, cresolver) {
                        @Override
                        public void accept(Observation observation, ContextScope contextScope) {
                            executeChain((State) observation, contextScope);
                        }
                    } : previousExecutor.chain(cresolver);
            case BoxingValueResolver bresolver ->
                    previousExecutor == null ? new Executor(Executor.Type.BOXING_VALUE_RESOLVER, parallelism,
                            bresolver) {
                        @Override
                        public void accept(Observation observation, ContextScope contextScope) {
                            executeChain((State) observation, contextScope);
                        }
                    } : previousExecutor.chain(bresolver);
            case Resolver resolver -> new Executor(Executor.Type.OBSERVATION_RESOLVER, parallelism) {
                @Override
                public void accept(Observation observation, ContextScope contextScope) {
                    resolver.resolve(observation, computation, contextScope);
                }
            };
            // TODO add characterizers etc.; also support debugging executors
            default ->
                    throw new IllegalStateException("Unhandled functor of type: " + functor.getClass().getCanonicalName());
        };
    }

    public Parallelism getParallelism(ContextScope scope) {
        // TODO choose parallelism based on context configuration, scale and identity
        return Parallelism.ONE;
    }

    /**
     * Register an actuator and create all support info before execution. Return true if the actuator is new and has
     * computations.
     *
     * @param actuator
     * @param scope
     * @return
     */
    public boolean registerActuator(Actuator actuator, Dataflow dataflow, ContextScope scope,
                                    DirectObservation contextObservation) {

        var data = observationData.get(actuator.getId());
        if (data == null && /* shouldn't happen */ !actuator.isReference()) {
            data = new ObservationData();
            data.actuator = actuator;
            data.observation = createObservation(actuator, contextObservation, scope);
            data.scale = scope.getScale();
            data.contextObservation = contextObservation;

            var customScale = dataflow.getResources().get((actuator.getId() + "_dataflow"), Scale.class);
            if (customScale != null) {
                // FIXME why the heck is this an Object and I have to cast?
                data.scale = data.scale.merge((Scale) customScale, LogicalConnector.INTERSECTION);
            }

            for (Actuator child : actuator.getChildren()) {
                if (child.isInput() && !child.getName().equals(child.getAlias())) {
                    data.localNames.put(child.getName(), child.getAlias());
                }
            }

            Executor executor = null;
            for (var computation : data.actuator.getComputation()) {
                var step = createExecutor(actuator, data.observation, computation, scope, executor);
                if (executor != step) {
                    data.executors.add(step);
                }
                executor = step;
            }

            observationData.put(actuator.getId(), data);

            return true;
        }

        return false;
    }

    class ObservationData {

        Observation observation;
        DirectObservation contextObservation;
        Actuator actuator;
        // the scale of contextualization of the actuator, computed in context based on coverage + context scale
        Scale scale;
        // the last timestamp in the influence graph starting at this
        long lastUpdate = -1;

        // translation table for localized observation names
        Map<String, String> localNames = new HashMap<>();

        // executors in order of application. Implemented by the Executor class.
        List<BiConsumer<Observation, ContextScope>> executors = new ArrayList<>();

        /**
         * TODO create a new scope with localized names in the catalog and the scale of contextualization
         *
         * @param scope
         * @return
         */
        ContextScope contextualize(ContextScope scope) {
            return scope.withContextualizationData(this.contextObservation, this.scale, this.localNames);
        }
    }

    static class InfluenceEdge extends DefaultEdge {

        @Serial
        private static final long serialVersionUID = 5250535576252863277L;

        /*
         * Scheduler timestamps corresponding to this link having caused a change in the target
         * observation. The latest update timestamp is also stored in the ObservationData.
         */
        List<Long> actionTimestamps = new ArrayList<>();
    }

    /**
     * Events that must reach the client side are communicated through this. It's probably the entire scope but we don't
     * store the scope, we pass it to individual methods.
     */
    Channel bus;

    /**
     * The observations that have no parents.
     */
    Set<Observation> rootObservations = new LinkedHashSet<>();

    /**
     * The influence diagram tells us which observation is influenced by changes in which others (info mutuated by
     * actuators at first, then potentially modified through behaviors or messages). Holds for actuators and
     * observations because the IDs are the same. The edges should keep the list of modification timesteps.
     */
    Graph<String, DefaultEdge> influenceDiagram = new DefaultDirectedGraph<>(DefaultEdge.class);

    /**
     * TODO if Javolution or something else eventually provides a fast map, use that here.
     */
    Map<String, ObservationData> observationData = new HashMap<>();

    /*
     * TODO a separate Inspector for debugging and testing (ideally, use a different top
     * contextualizer that talks to the inspector after checking if the inspector is not null,
     * instead of checking at every step).
     */

    public DigitalTwin(ContextScope scope) {
        this.scope = scope;
        this.storageScope = new StorageScope(scope);
    }

    /**
     * Run all the actuators, honoring execution order and parallelism
     *
     * @param dataflow
     * @param scope
     * @return
     */
    public Observation runDataflow(Dataflow<Observation> dataflow, ContextScope scope) {

        if (!validate(scope) || dataflow.isEmpty()) {
            return Observation.empty();
        }

        var executionOrder = sortComputation(dataflow, scope);
        var groups = executionOrder.stream().collect(Collectors.groupingBy(s -> s.getSecond()));
        var lastOrder = executionOrder.getLast().getSecond();
        var parallelism = getParallelism(scope);
        var initializationScope = scope.withGeometry(scope.getScale().initialization());

        for (var i = 0; i <= lastOrder; i++) {
            var actuatorGroup = groups.get(i);
            if (actuatorGroup.size() > 1 && parallelism.getAsInt() > 1) {
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (var actuator : actuatorGroup) {
                        executor.submit(() -> runActuator(actuator.getFirst(), initializationScope));
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
                    if ((error = !runActuator(actuator.getFirst(), initializationScope))) {
                        break;
                    }
                }
                if (error) {
                    break;
                }
            }
        }

        return getObservation(dataflow.getComputation().iterator().next().getId());
    }

    /**
     * Run the passed actuator, building the necessary observations and updating all records. Sound dependency order
     * must be guaranteed by the caller. Notify whatever info we have subscribed to.
     * <p>
     * TODO obs data for an actuator should contain the actual coverage in the context scale
     *
     * @param actuator
     * @param scope
     * @return
     */
    public boolean runActuator(Actuator actuator, ContextScope scope) {

        var data = observationData.get(actuator.getId());

        ContextScope localizedScope = data.contextualize(scope);
        /**
         Run computational chain. Report any errors through the scope and return false on error or exception.
         */
        for (var executor : data.executors) {
            try {
                executor.accept(data.observation, scope);
            } catch (Throwable t) {
                return handleContextualizationException(t, scope);
            }
        }

        while (!actuator.getDeferrals().isEmpty()) {

        }

        return true;
    }

    private boolean handleContextualizationException(Throwable t, ContextScope scope) {
        // TODO specialize handling w.r.t. exception. Not sure anything should ever return true (-> continue
        //  contextualizing to the next executor), but OK for now
        scope.error(t);
        return false;
    }

    private Storage createStorage(Observable observable, ContextScope scope) {
        // TODO use options from the scope for parallelization and choice float/double
        var storage = switch (observable.getDescriptionType()) {
            case QUANTIFICATION -> new DoubleStorage(scope.getScale(), storageScope);
            case CATEGORIZATION -> new KeyedStorage(scope.getScale(), storageScope);
            case VERIFICATION -> new BooleanStorage(scope.getScale(), storageScope);
            default -> throw new KIllegalStateException("Unexpected value: " + observable.getDescriptionType());
        };
        this.runtimeAssets.add(storage);
        return storage;
    }

    private ObservationImpl createObservation(Actuator actuator, DirectObservation parent, ContextScope scope) {

        var ret = switch (actuator.getObservable().getDescriptionType()) {
            case ACKNOWLEDGEMENT -> new DirectObservationImpl(actuator.getObservable(), actuator.getId(), scope);
            case INSTANTIATION, CONNECTION ->
                    new ObservationGroupImpl(actuator.getObservable(), actuator.getId(), scope);
            case CATEGORIZATION, VERIFICATION, QUANTIFICATION ->
                    new StateImpl(actuator.getObservable(), actuator.getId(), scope) {

                        private Storage storage = createStorage(actuator.getObservable(), scope);

                        @Override
                        public Histogram getHistogram() {
                            return storage.getHistogram();
                        }

                        @Override
                        public <T extends Storage> T storage(Class<T> storageClass) {
                            // Just let this throw a cast exception instead of adding painful checks
                            return (T) storage;
                        }
                    };
            case SIMULATION -> new ProcessImpl(actuator.getObservable(), actuator.getId(), scope);
            case CHARACTERIZATION,
                    CLASSIFICATION,
                    COMPILATION -> null; // TODO
            case DETECTION -> new ConfigurationImpl(actuator.getObservable(), actuator.getId(), scope);
            default -> null;
        };

        add(ret);
        if (parent != null) {
            link(ret, parent);
        }

        return ret;
    }


    /**
     * Establish the order of execution and the possible parallelism. Each root actuator should be sorted by dependency
     * and appended in order to the result list along with its order of execution. Successive roots can refer to the
     * previous roots but they must be executed sequentially.
     * <p>
     * The DigitalTwin is asked to register the actuator in the scope and prepare the environment and state for its
     * execution, including defining its contextualization scale in context.
     *
     * @param dataflow
     * @return
     */
    private List<Pair<Actuator, Integer>> sortComputation(Dataflow<Observation> dataflow, ContextScope scope) {
        List<Pair<Actuator, Integer>> ret = new ArrayList<>();
        for (Actuator root : dataflow.getComputation()) {
            int executionOrder = 0;
            Map<String, Actuator> branch = new HashMap<>();
            collectActuators(Collections.singletonList(root), dataflow, scope, null, branch);
            var dependencyGraph = createDependencyGraph(branch);
            TopologicalOrderIterator<Actuator, DefaultEdge> order = new TopologicalOrderIterator<>(
                    dependencyGraph);

            // group by dependency w.r.t. the previous group and assign the execution order based on the
            // group index, so that we know what we can execute in parallel
            Set<Actuator> group = new HashSet<>();
            while (order.hasNext()) {
                Actuator next = order.next();
                if (!next.isReference()) {
                    var data = observationData.get(next.getId());
                    if (data.executors.size() > 0) {
                        ret.add(Pair.of(next, (executionOrder = checkExecutionOrder(executionOrder, next,
                                dependencyGraph,
                                group))));
                    }
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

    private void collectActuators(List<Actuator> actuators, Dataflow dataflow, ContextScope scope,
                                  DirectObservation contextObservation, Map<String, Actuator> ret) {
        var context = contextObservation;
        for (Actuator actuator : actuators) {
            if (registerActuator(actuator, dataflow, scope, contextObservation)) {
                /*
                 * TODO compile a list of all services + versions, validate the actuator, create
                 * any needed notifications and a table of translations for local names
                 */
                if (actuator.getObservable().getDescriptionType() == DescriptionType.ACKNOWLEDGEMENT) {
                    var odata = this.observationData.get(actuator.getId());
                    context = (DirectObservation) odata.observation;
                }
                ret.put(actuator.getId(), actuator);
            }
            collectActuators(actuator.getChildren(), dataflow, scope, context, ret);
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

    // TODO this one should mimic the above but passing an inspector and using it.
    // It should be called instead of the above by the calling logic.
    public boolean runActuatorInspecting(Actuator actuator, ContextScope scope) {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * Call at scope disposal and service shutdown (scope disposal should be scheduled after configured inactivity).
     *
     * @return
     */
    public boolean dispose() {
        return true;
    }

    private Graph<Observation, DefaultEdge> logicalStructure = new DefaultDirectedGraph<Observation, DefaultEdge>(
            DefaultEdge.class);
    private Graph<Observation, DefaultEdge> physicalStructure = new DefaultDirectedGraph<Observation, DefaultEdge>(
            DefaultEdge.class);
    private Map<String, Process> derivedOccurrents = new HashMap<>();

    public Collection<Observation> getLogicalChildren(Observation parent) {
        List<Observation> ret = new ArrayList<>();
        for (DefaultEdge edge : logicalStructure.incomingEdgesOf(parent)) {
            ret.add(logicalStructure.getEdgeSource(edge));
        }
        return ret;
    }

    public Collection<Observation> getPhysicalChildren(Observation parent) {
        List<Observation> ret = new ArrayList<>();
        for (DefaultEdge edge : physicalStructure.incomingEdgesOf(parent)) {
            ret.add(physicalStructure.getEdgeSource(edge));
        }
        return ret;
    }

    public Observation getLogicalParent(Observation child) {
        if (child instanceof ObservationGroup) {
            return getPhysicalParent(child);
        }
        for (DefaultEdge edge : logicalStructure.outgoingEdgesOf(child)) {
            return logicalStructure.getEdgeTarget(edge);
        }
        return null;
    }

    public Observation getPhysicalParent(Observation child) {
        if (!physicalStructure.vertexSet().contains(child)) {
            return null;
        }
        for (DefaultEdge edge : physicalStructure.outgoingEdgesOf(child)) {
            return physicalStructure.getEdgeTarget(edge);
        }
        return null;
    }

    public boolean contains(Observation artifact) {
        return physicalStructure.containsVertex(artifact);
    }

    /**
     * Link observations, using artifact logics, as specified during contextualization. The artifact structure will
     * contains the graph as specified, attributing process states to the parent subject. The logical structure will
     * skip folders and processes, always attributing observations to their parent observations and linking process
     * qualities to subjects.
     *
     * @param childArtifact
     * @param parentArtifact
     */
    public void link(Observation childArtifact, Observation parentArtifact) {

        if (rootObservations.contains(childArtifact)) {
            rootObservations.remove(childArtifact);
        }

        // these are redirected no matter what.
        if (parentArtifact instanceof Process) {

            /*
             * we keep the information about the artifact being owned by a process, so that we can
             * tell the occurrence when it's relevant.
             */
            this.derivedOccurrents.put(childArtifact.getId(), (Process) parentArtifact);

            parentArtifact = getPhysicalParent(parentArtifact);
        }

        /*
         * artifact structure is verbatim
         */
        physicalStructure.addVertex(childArtifact);
        physicalStructure.addVertex(parentArtifact);
        physicalStructure.addEdge(childArtifact, parentArtifact);

        // if we're linking a folder to something, all done
        if (childArtifact instanceof ObservationGroup) {
            return;
        }

        // otherwise link, possibly skipping the non-logical level
        if (parentArtifact instanceof ObservationGroup) {
            parentArtifact = getPhysicalParent(parentArtifact);
        }

        // add process children but not folders
        logicalStructure.addVertex(childArtifact);
        logicalStructure.addVertex(parentArtifact);
        logicalStructure.addEdge(childArtifact, parentArtifact);
    }

    public void add(Observation v) {
        physicalStructure.addVertex(v);
        if (!(v instanceof ObservationGroup)) {
            logicalStructure.addVertex(v);
        }
    }

    public void replace(Observation original, Observation replacement) {

        // TODO deal with groups - really unnecessary at the moment, but incomplete.

        Set<Observation> outgoing = new HashSet<>();
        Set<Observation> incoming = new HashSet<>();
        if (logicalStructure.containsVertex(original)) {
            for (DefaultEdge edge : logicalStructure.outgoingEdgesOf(original)) {
                outgoing.add(logicalStructure.getEdgeTarget(edge));
            }
            for (DefaultEdge edge : logicalStructure.incomingEdgesOf(original)) {
                incoming.add(logicalStructure.getEdgeSource(edge));
            }
            logicalStructure.removeVertex(original);
        }

        logicalStructure.addVertex(replacement);
        for (Observation target : outgoing) {
            logicalStructure.addEdge(replacement, target);
        }
        for (Observation target : incoming) {
            logicalStructure.addEdge(target, replacement);
        }
    }

    public void removeArtifact(Observation object) {
        if (logicalStructure.containsVertex(object)) {
            logicalStructure.removeVertex(object);
        }
        if (physicalStructure.containsVertex(object)) {
            physicalStructure.removeVertex(object);
        }
        derivedOccurrents.remove(object.getId());
    }

    public void swap(Observation original, Observation replacement) {

        if (logicalStructure.containsVertex(original)) {
            List<Observation> sources = new ArrayList<>();
            List<Observation> targets = new ArrayList<>();
            for (DefaultEdge edge : logicalStructure.incomingEdgesOf(original)) {
                sources.add(logicalStructure.getEdgeSource(edge));
            }
            for (DefaultEdge edge : logicalStructure.outgoingEdgesOf(original)) {
                targets.add(logicalStructure.getEdgeTarget(edge));
            }
            logicalStructure.removeVertex(original);
            logicalStructure.addVertex(replacement);
            for (Observation source : sources) {
                logicalStructure.addEdge(source, replacement);
            }
            for (Observation target : targets) {
                logicalStructure.addEdge(replacement, target);
            }
        }
        if (physicalStructure.containsVertex(original)) {
            List<Observation> sources = new ArrayList<>();
            List<Observation> targets = new ArrayList<>();
            for (DefaultEdge edge : physicalStructure.incomingEdgesOf(original)) {
                sources.add(physicalStructure.getEdgeSource(edge));
            }
            for (DefaultEdge edge : physicalStructure.outgoingEdgesOf(original)) {
                targets.add(physicalStructure.getEdgeTarget(edge));
            }
            physicalStructure.removeVertex(original);
            physicalStructure.addVertex(replacement);
            for (Observation source : sources) {
                physicalStructure.addEdge(source, replacement);
            }
            for (Observation target : targets) {
                physicalStructure.addEdge(replacement, target);
            }
        }
    }

    public Process getOwningProcess(Observation artifact) {
        return derivedOccurrents.get(artifact.getId());
    }

    public Observation getObservation(String id) {
        ObservationData data = observationData.get(id);
        return data == null ? Observation.empty() : data.observation;
    }

    @Override
    public void close() throws IOException {
        // TODO notify all listeners of the shutdown
        // TODO free up all observations and dataflows
        // TODO disconnect gracefully from any linked remote DT
        // TODO disconnect any active service connection
        // TODO ensure history is up to date and consistent
        // TODO log everything
        // free up storage
        storageScope.close();
    }


}
