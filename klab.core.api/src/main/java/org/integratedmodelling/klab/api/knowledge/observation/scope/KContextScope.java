package org.integratedmodelling.klab.api.knowledge.observation.scope;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.Relationship;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;

/**
 * The scope for a context and any observations made within it.
 * 
 * @author Ferd
 *
 */
public interface KContextScope extends KSessionScope {

    /**
     * Return the observer for this context. The original observation scope has the session user as
     * observer.
     * 
     * @return
     */
    Identity getObserver();

    /**
     * Return a child scope with the passed observer instead of ours.
     * 
     * @param scenarios
     * @return
     */
    KContextScope withObserver(Identity observer);

    /**
     * The context observation for this scope. When an observation scope is created, this is null
     * and must be set using {@link #within(DirectObservation)} on the parent scope.
     * 
     * @return
     */
    DirectObservation getContextObservation();

    /**
     * Return a scope focused on a specific root observation as the context for its
     * {@link #observe(Object...)} calls.
     * 
     * @param observation
     * @return a new scope focused on the passed observation.
     */
    KContextScope within(DirectObservation observation);

    /**
     * Return a new observation scope that sets the passed scenarios for any future observation.
     * 
     * @param scenarios
     * @return
     */
    KContextScope withScenarios(String... scenarios);

    /**
     * Make an observation. Must be called on a context scope, possibly focused on a given root
     * observation using {@link #within(DirectObservation)}. If no root observation is present in
     * the scope, the arguments must fully specify a subject, either through an
     * {@link IAcknowledgement} or a subject observable + a scale. If the parent session was focused
     * on a scale, this is available through {@link #getGeometry()} and the context can decide to
     * use it as a scale for the root subject.
     * <p>
     * In case the observable specifies a relationship, k.LAB will attempt to instantiate it,
     * observing its source/target endpoints as well, unless two subject observations are passed, in
     * which case a specified relationship will be instantiated between them using them as source
     * and target respectively. In the latter case, each relationship will be resolved but
     * configuration detection will only happen upon exiting the scope where observe() is called.
     * <p>
     * If the observation is at root level, or connecting two root-level subject through a
     * relationship, the overall geometry of the context will be automatically adjusted.
     * 
     * @param observables either a {@link Observable} (with a {@link Geometry} if root subject) or
     *        a {@link IAcknowledgement} for a pre-specified root subject.
     * @return a future for the observation being contextualized.
     */
    Future<Observation> observe(Object... observables);

    /**
     * <p>
     * getProvenance.
     * </p>
     *
     * @return the provenance graph. Null in an empty context.
     */
    Provenance getProvenance();

    /**
     * There is one report per root context. Actuators will add sections to it as models are
     * computed, based on the documentation templates associated with models and their parts. The
     * report can be compiled and rendered at any time.
     * 
     * @return
     */
    Report getReport();

    /**
     * During a contextualization there normally is a dataflow being run. This will only be null
     * only in special situations, e.g. when expressions are passed a convenience context in order
     * to be evaluated outside of contextualization.
     * 
     * @return
     */
    Dataflow<?> getDataflow();

    /**
     * Return the parent observation of the passed observation. The runtime context maintains the
     * logical structure graph (ignores grouping of artifacts).
     * 
     * @param observation
     * @return the parent, or null if root subject
     */
    DirectObservation getParentOf(Observation observation);

    /**
     * Return all children of the passed observation, using the logical structure (i.e. skipping
     * observation groups). The runtime context maintains the structure graph.
     * 
     * @param observation an observation. {@link State States} have no children but no error should
     *        be raised.
     * @return the parent, or an empty collection if no children
     */
    Collection<Observation> getChildrenOf(Observation observation);

    /**
     * Inspect the network graph of the current context, returning all relationships that have the
     * passed subject as target.
     *
     * @param observation a
     *        {@link org.integratedmodelling.klab.api.DirectObservation.IDirectObservation} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Relationship> getOutgoingRelationships(DirectObservation observation);

    /**
     * Inspect the network graph of the current context, returning all relationships that have the
     * passed subject as target.
     *
     * @param observation a
     *        {@link org.integratedmodelling.klab.api.DirectObservation.IDirectObservation} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Relationship> getIncomingRelationships(DirectObservation observation);

    /**
     * Return the currently known observations as a map indexed by observable.
     * 
     * @return
     */
    Map<Observable, Observation> getCatalog();

}