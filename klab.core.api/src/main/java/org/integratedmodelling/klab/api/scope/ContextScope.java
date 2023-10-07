package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Relationship;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * The scope for a context and any observations made within it. The context scope is the "digital twin" of the
 * observations made in it. A scope is also passed around during resolution and carries scope info (namespace, project,
 * scenarios) that is relevant to the resolver.
 * <p>
 * The context scope has a URL and can be connected to another to become part of a larger scope.
 *
 * @author Ferd
 */
public interface ContextScope extends SessionScope, AutoCloseable {

    /**
     * Context scopes have a URL that enables communication with clients but also allows other contexts to connect to
     * them and become part of federated contexts to form distributed digital twins. When connected, contexts share
     * events and state through messaging, with visibility defined by their authenticated agreement.
     *
     * @return
     */
    URL getUrl();

    /**
     * Return the observer for this context. The original observation scope has the session user as observer.
     *
     * @return
     */
    Identity getObserver();

    /**
     * If this scope is focused on a specific subject, return it.
     *
     * @return the context observation or null
     */
    DirectObservation getContextObservation();

    /**
     * Return a child scope with the passed observer instead of ours.
     *
     * @param observer
     * @return
     */
    ContextScope withObserver(Identity observer);

    /**
     * Return a new observation scope that sets the passed scenarios for any future observation.
     *
     * @param scenarios
     * @return
     */
    ContextScope withScenarios(String... scenarios);

    /**
     * Return a new context scope with the passed namespace of resolution. Used by the resolver or to fine-tune
     * resolution.
     *
     * @param namespace
     * @return
     */
    ContextScope withResolutionNamespace(String namespace);

    /**
     * Create a context with the passed geometry to replace the one currently active. Any observations made in it must
     * be consistent with the overall geometry and context observations; if observations of direct observables are made,
     * the parent's geometry should be updated to reflect it.
     *
     * <p>This does not reinitialize the catalog, as it's meant to focus on a subset of an overall scale.</p>
     *
     * @param geometry
     * @return
     */
    ContextScope withGeometry(Geometry geometry);

    /**
     * Return a scope focused on a specific context observation. The catalog will be reinitialized to empty, and there
     * is no guarantee that passing the same observation as the current context will not do so.
     *
     * @param contextObservation
     * @return
     */
    ContextScope withContextObservation(DirectObservation contextObservation);

    /**
     * Add another context to this one to build a higher-level one. Authentication details will define what is seen and
     * done.
     *
     * @param remoteContext
     * @return
     */
    ContextScope connect(URL remoteContext);

    /**
     * Make an observation. Must be called on a context scope, possibly focused on a given root observation using
     * {@link #withContextObservation(DirectObservation)}}. If no root observation is present in the scope, the
     * arguments must fully specify a subject, either through an
     * {@link org.integratedmodelling.klab.api.knowledge.Instance} or a URN specifying a subject observable + a scale.
     * If the parent session was focused on a scale, this is available through {@link #getScale()} and the context can
     * decide to use it as a scale for the root subject.
     * <p>
     * Observables will be routinely specified through URNs, which will be validated as any observable object -
     * concepts/observables, resource URNs, model/acknowledgement URNs, or full URLs specifying a context/observation in
     * an externally hosted runtime to link to the current context. Passing descriptors for concepts, observables,
     * acknowledgements or models should not cause errors.
     * <p>
     * In case the observable specifies a relationship, k.LAB will attempt to instantiate it, observing its
     * source/target endpoints as well, unless two subject observations are passed, in which case a specified
     * relationship will be instantiated between them using them as source and target respectively. In the latter case,
     * each relationship will be resolved but configuration detection will only happen upon exiting the scope where
     * observe() is called.
     * <p>
     * If the observation is at root level, or connecting two root-level subject through a relationship, the overall
     * geometry of the context will be automatically adjusted.
     *
     * @param observables either a {@link Observable} (with a {@link Geometry} if root subject) or a
     *                    {@link org.integratedmodelling.klab.api.knowledge.Instance} for a pre-specified root subject.
     * @return a future for the observation being contextualized.
     */
    Future<Observation> observe(Object... observables);

    /**
     * Return all observations affected by the passed one in this scope, either through model dependencies or behaviors.
     * "Affected" is any kind of reaction, not necessarily implied by semantics.
     *
     * @param observation
     * @return
     */
    Collection<Observation> affects(Observation observation);

    /**
     * Return all observations that the passed one affects in this scope, either through model dependencies or
     * behaviors. "Affected" is any kind of reaction, not necessarily implied by semantics.
     *
     * @param observation
     * @return
     */
    Collection<Observation> affected(Observation observation);

    /**
     * Start the scheduling if the context occurs; do nothing if not. This can be called again at each observation, with
     * intelligent "replay" of any transitions that need to be seen again.
     */
    void runTransitions();

    /**
     * <p>
     * getProvenance.
     * </p>
     *
     * @return the provenance graph. Empty in an empty context, never null. Each observation in the same context will
     * report the same provenance graph for now (TODO may turn into provenance as the "root" node or have a focus
     * field).
     */
    Provenance getProvenance();

    /**
     * There is one report per root context. Actuators will add sections to it as models are computed, based on the
     * documentation templates associated with models and their parts. The report can be compiled and rendered at any
     * time. Each observation in the same context will report the same report.
     *
     * @return
     */
    Report getReport();

    /**
     * There is one dataflow per context, and it's never null. It starts empty and incorporates all the dataflows
     * created by the resolver when new observations are made. Each dataflow is inserted in the main one at the
     * appropriate position, so that running the dataflow again will recreate the exact same context.
     *
     * @return
     */
    Dataflow<Observation> getDataflow();

    /**
     * Return the parent observation of the passed observation. The runtime context maintains the logical structure
     * graph (ignores grouping of artifacts).
     *
     * @param observation
     * @return the parent, or null if root subject
     */
    DirectObservation getParentOf(Observation observation);

    /**
     * Return all children of the passed observation, using the logical structure (i.e. skipping observation groups).
     * The runtime context maintains the structure graph.
     *
     * @param observation an observation. {@link State States} have no children but no error should be raised.
     * @return the parent, or an empty collection if no children
     */
    Collection<Observation> getChildrenOf(Observation observation);

    /**
     * Inspect the network graph of the current context, returning all relationships that have the passed subject as
     * target.
     *
     * @param observation a {@link DirectObservation} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Relationship> getOutgoingRelationships(DirectObservation observation);

    /**
     * Inspect the network graph of the current context, returning all relationships that have the passed subject as
     * target.
     *
     * @param observation a {@link DirectObservation} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Relationship> getIncomingRelationships(DirectObservation observation);

    /**
     * Return the currently known observations as a map indexed by observable.
     *
     * @return
     */
    Map<Observable, Observation> getCatalog();

    /**
     * Retrieve the observation recognized in this context with the passed name.
     *
     * @param <T>
     * @param localName
     * @param cls
     * @return
     */
    <T extends Observation> T getObservation(String localName, Class<T> cls);

    /**
     * When resolving, the resolution namespace that provides the resolution scope must be provided. In other situations
     * this will be null.
     *
     * @return
     */
    String getResolutionNamespace();

    /**
     * Same as {@link #getResolutionNamespace()}, reporting the project in scope during resolution.
     *
     * @return
     */
    String getResolutionProject();

    /**
     * Any scenarios set during the resolution.
     *
     * @return
     */
    Collection<String> getResolutionScenarios();

    /**
     * A context is born "empty" and since k.LAB 0.12 does not have a root observation, but when used in resolution may
     * acquire a root observation which serves as context for the resolution.
     *
     * @return
     */
    DirectObservation getResolutionObservation();

    /**
     * Create a new scope if necessary where the catalog uses the passed local names and the scale, if not null, is the
     * passed one. Called before each actuator's functors are executed and passed to the functor executor.
     *
     * @param scale      may be null, meaning that the original scale is unchanged
     * @param localNames if empty, the catalog remains the same
     * @return a localized context or this one if nothing needs to change
     */
    ContextScope withContextualizationData(Scale scale, Map<String, String> localNames);
}