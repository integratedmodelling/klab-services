package org.integratedmodelling.klab.services.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.contrib.jgrapht.graph.DefaultEdge;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.ObservationGroup;
import org.integratedmodelling.klab.api.knowledge.observation.impl.DirectObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationGroupImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.StateImpl;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.extension.Contextualizer;
import org.integratedmodelling.klab.services.runtime.storage.DoubleStorage;
import org.integratedmodelling.klab.services.runtime.storage.IntStorage;
import org.integratedmodelling.klab.services.runtime.storage.KeyStorage;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

/**
 * The actual observation content kept in each local context (inside the context agent or the scope
 * itself if local). Contains the influence diagram between observations with the log of any
 * modification event timestamp, the scheduler, the event bus, any inspectors and probes, and the
 * catalog of ID->{observation, actuator, storage, runtime data...}. Also maintains the logical and
 * physical "family trees" of observation. The logical one skips the instance container built for
 * the instantiators, which is kept in the physical structure.
 * <p>
 * Essentially the implementation of the "digital twin" accessed through the {@link ContextScope}.
 * Eventually it can have its own API although the {@link ContextScope} should have all it needs to
 * interact with it.
 * 
 * @author Ferd
 *
 */
public class DigitalTwin {

    /**
     * Key to locate this in the context scope data. The first contextualization creates it and it
     * remains the same object throughout the context lifetime.
     */
    public static final String KEY = "klab.context.data";

    /**
     * Types of the events that can be subscribed to and get communicated.
     * 
     * @author Ferd
     *
     */
    enum Event {

    }

    /**
     * Each contextualizer is stored here along with the call that generated it and a classification
     * for speed.
     * 
     * @author Ferd
     *
     */
    class ContextualizerData {

        enum Type {
            DOUBLE_VALUE_RESOLVER, INT_VALUE_RESOLVER, CONCEPT_VALUE_RESOLVER, BOXING_VALUE_RESOLVER, OBSERVATION_RESOLVER, OBSERVATION_INSTANTIATOR
        }

        Type type;
        Contextualizer contextualizer;
        ServiceCall serviceCall;
        boolean parallel; // for value resolvers, if true execution can be parallel
    }

    class ObservationData {

        Observation observation;
        Actuator actuator;
        // the last timestamp in the influence graph starting at this
        long lastUpdate;

        // these are in there to enable quick access if we know the type in advance
        DoubleStorage dStorage;
        IntStorage iStorage;
        KeyStorage kStorage;

        // in order of application
        List<ContextualizerData> contextualizers = new ArrayList<>();
    }

    class InfluenceEdge extends DefaultEdge {

        private static final long serialVersionUID = 5250535576252863277L;

        /*
         * Scheduler timestamps corresponding to this link having caused modifications in the target
         * observation.
         */
        List<Long> actionTimestamps = new ArrayList<>();
    }

    /**
     * Events that must reach the client side are communicated through this. It's probably the
     * entire scope but we don't store the scope, we pass it to individual methods.
     */
    Channel bus;

    /**
     * The observations that have no parents.
     */
    List<Observation> rootObservations = new ArrayList<>();

    /**
     * The influence diagram tells us which observation is influenced by changes in which others
     * (info mutuated by actuators at first, then potentially modified through behaviors or
     * messages). Holds for actuators and observations because the IDs are the same. The edges
     * should keep the list of modification timesteps.
     */
    Graph<String, DefaultEdge> influenceDiagram = new DefaultDirectedGraph<>(DefaultEdge.class);

    /**
     * TODO if Javolution or something else eventually provides a fast map, use that here.
     */
    Map<String, ObservationData> observationData = new HashMap<>();

    /*
     * TODO another fast map of event and subscriptions.
     */

    /*
     * TODO a separate Inspector for debugging and testing (ideally, use a different top
     * contextualizer that talks to the inspector after checking if the inspector is not null,
     * instead of checking at every step).
     */

    /**
     * Run the passed actuator, building the necessary observations and updating all records. Sound
     * dependency order must be guaranteed by the caller. Notify whatever info we have subscribed
     * to.
     * 
     * @param actuator
     * @param scope
     * @return
     */
    public boolean runActuator(Actuator actuator, ContextScope scope) {

        ObservationData data = observationData.get(actuator.getId());
        if (data == null && /* shouldn't happen */ !actuator.isReference()) {
            data = new ObservationData();
            data.actuator = actuator;
            data.observation = createObservation(actuator, scope);
            createStorage(data.observation, scope);
            observationData.put(actuator.getId(), data);
        }

        return false;
    }

    private void createStorage(Observation observation, ContextScope scope) {
        // TODO create the appropriate storage for the type and the scope

    }

    private ObservationImpl createObservation(Actuator actuator, ContextScope scope) {

        DirectObservation context = scope.getContextObservation();
        ObservationImpl ret = null;

        switch(actuator.getObservable().getDescriptionType()) {
        case ACKNOWLEDGEMENT:
            ret = new DirectObservationImpl(actuator.getObservable(), actuator.getId(), scope);
            break;
        case CHARACTERIZATION:
            break;
        case CLASSIFICATION:
            break;
        case COMPILATION:
            break;
        case DETECTION:
            break;
        case INSTANTIATION:
        case CONNECTION:
            ret = new ObservationGroupImpl(actuator.getObservable(), actuator.getId(), scope);
            break;
        case CATEGORIZATION:
        case VERIFICATION:
        case QUANTIFICATION:
            ret = new StateImpl(actuator.getObservable(), actuator.getId(), scope);
            break;
        case SIMULATION:
            break;
        default:
            break;
        }

        add(ret);
        if (context != null) {
            link(ret, context);
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
     * Call at scope disposal and service shutdown (scope disposal should be scheduled after
     * configured inactivity).
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
     * Link observations, using artifact logics, as specified during contextualization. The artifact
     * structure will contains the graph as specified, attributing process states to the parent
     * subject. The logical structure will skip folders and processes, always attributing
     * observations to their parent observations and linking process qualities to subjects.
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
