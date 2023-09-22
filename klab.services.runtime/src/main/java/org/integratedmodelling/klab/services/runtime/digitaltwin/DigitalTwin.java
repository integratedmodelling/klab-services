package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.contrib.jgrapht.graph.DefaultEdge;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.ObservationGroup;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ProcessImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.*;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.extension.Contextualizer;
import org.integratedmodelling.klab.runtime.storage.StorageScope;
import org.integratedmodelling.klab.runtime.storage.StorageManager;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.ojalgo.concurrent.Parallelism;

import java.io.Serial;
import java.util.*;
import java.util.function.BiFunction;

/**
 * A DigitalTwin is the server-side observation content kept with a context scope, which acts as a "handle" to it. It
 * contains all observations, their storage, the influence diagram between observations with the log of any modification
 * event timestamp, the scheduler, the event manager and the catalog of ID->{observation, actuator, storage, runtime
 * data...}. Also maintains the logical and physical "family tree" of observation and manages the bookkeeping of any
 * runtime assets so that they are known and disposed of properly when the context scope ends. The logical tree skips
 * the instance container built for the instantiators, which is kept in the physical structure.
 * <p>
 * The DigitalTwin is accessed through the {@link ContextScope}. Eventually it may have its own API contract, although
 * all interaction is currently managed through {@link ContextScope}.</p>
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
public class DigitalTwin {

    /**
     * Key to locate this in the context scope data. The first contextualization creates it, and it remains the same
     * object throughout the context lifetime.
     */
    public static final String KEY = "klab.context.data";

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

    StorageScope storageScope;

    /**
     * The local asset catalog. Most importantly for disposal at end.
     */
    private Set<RuntimeAsset> runtimeAssets = new HashSet<>();

    /**
     * <p>Holder of atomic "executors" that implement one or more contextualizers (sequential scalar ones are merged into
     * chains to avoid storage of intermediate products unless requested).</p>
     *
     * <p>Has two separate and equivalent call sets, one with debugging and one without, to avoid constant checking in
     * time-critical contextualizers.</p>
     *
     * <p>It implements a bifuction that takes the localized observations (with the name that each is known as in the
     * actuator) and returns an observation, normally the same that corresponds to "self" in the observation map, but
     * possibly a different one for observation resolvers</p>
     */
    class Executor implements BiFunction<Map<String, Observation>, ContextScope, Observation> {

        enum Type {
            DOUBLE_VALUE_RESOLVER, INT_VALUE_RESOLVER, CONCEPT_VALUE_RESOLVER, BOXING_VALUE_RESOLVER,
            OBSERVATION_RESOLVER, OBSERVATION_INSTANTIATOR, OBSERVATION_CHARACTERIZER, OBSERVABLE_CLASSIFIER,
            OBJECT_CLASSIFIER
        }

        Type type;
        Parallelism parallelism = Parallelism.ONE;

        @Override
        public Observation apply(Map<String, Observation> observations, ContextScope scope) {


            /**
             * Decide the parallelization strategy based on the scope data
             */

            return null;
        }
    }


    /**
     * Create an executor for the computation and return it, or merge the computation into the previous executor and
     * return it.
     *
     * @param actuator
     * @param computation
     * @param previousExecutor
     * @return
     */

    private Executor createExecutor(Actuator actuator, ServiceCall computation, Executor previousExecutor) {
        // TODO
        return null;
    }

    class ObservationData {

        Observation observation;
        Actuator actuator;
        // the last timestamp in the influence graph starting at this
        long lastUpdate = -1;

        // executors in order of application. Implemented by the Executor class.
        List<BiFunction<Map<String, Observation>, ContextScope, Observation>> contextualizers = new ArrayList<>();
    }

    static class InfluenceEdge extends DefaultEdge {

        @Serial
        private static final long serialVersionUID = 5250535576252863277L;

        /*
         * Scheduler timestamps corresponding to this link having caused modifications in the target
         * observation.
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
    List<Observation> rootObservations = new ArrayList<>();

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
        this.storageScope = new StorageScope(scope);
    }


    /**
     * Run the passed actuator, building the necessary observations and updating all records. Sound dependency order
     * must be guaranteed by the caller. Notify whatever info we have subscribed to.
     *
     * @param actuator
     * @param scope
     * @return
     */
    public boolean runActuator(Actuator actuator, ContextScope scope) {

        var data = observationData.get(actuator.getId());
        if (data == null && /* shouldn't happen */ !actuator.isReference()) {
            data = new ObservationData();
            data.actuator = actuator;
            data.observation = createObservation(actuator, scope);

            Executor executor = null;
            for (var computation : data.actuator.getComputation()) {
                Executor step = createExecutor(actuator, computation, executor);
                if (executor != step) {
                    data.contextualizers.add(step);
                }
                executor = step;
            }

            observationData.put(actuator.getId(), data);
        }

        /**
         If not done already: initialize by
         1. Separate chains of scalar functions and merge into chained contextualizers
         2. Add individual non-scalar contextualizers
         3. Define run strategy and naming
         */

        /**
         Run computational chain
         */

        return false;
    }

    private Storage createStorage(Observable observable, ContextScope scope) {
        // TODO use options from the scope for parallelization and choice float/double
        var storage = switch (observable.getDescriptionType()) {
            case QUANTIFICATION -> StorageManager.INSTANCE.getDoubleStorage(scope, Parallelism.CORES);
            case CATEGORIZATION -> StorageManager.INSTANCE.getKeyedStorage(scope, Parallelism.CORES);
            case VERIFICATION -> StorageManager.INSTANCE.getBooleanStorage(scope, Parallelism.CORES);
            default -> throw new KIllegalStateException("Unexpected value: " + observable.getDescriptionType());
        };
        this.runtimeAssets.add(storage);
        return storage;
    }

    private ObservationImpl createObservation(Actuator actuator, ContextScope scope) {

        var ret = switch (actuator.getObservable().getDescriptionType()) {
            case ACKNOWLEDGEMENT -> new DirectObservationImpl(actuator.getObservable(), actuator.getId(), scope);
            case INSTANTIATION, CONNECTION ->
                    new ObservationGroupImpl(actuator.getObservable(), actuator.getId(), scope);
            case CATEGORIZATION, VERIFICATION, QUANTIFICATION ->
                    new StateImpl(actuator.getObservable(), actuator.getId(), scope) {

                        private Storage storage = createStorage(actuator.getObservable(), scope);

                        @Override
                        public <T extends Storage> T getStorage(Class<T> storageClass) {
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
        if (scope.getContextObservation() != null) {
            link(ret, scope.getContextObservation());
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

}
