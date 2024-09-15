package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.api.utils.Utils;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;

/**
 * The scope for a context and any observations made within it. The context scope is the handle to the
 * "digital twin" of the observations made in it and contains all the methods that give access to the
 * observation, dataflow and provenance graphs. These are available from the GraphQL API served by the
 * {@link org.integratedmodelling.klab.api.services.RuntimeService}.
 * <p>
 * The context scope always has an observer that can be switched to another when making observations. The
 * observer is an observation of an agent that also specifies an observed geometry in addition to its own. The
 * calling client should explicitly provide an observer; if not, the digital twin should provide one by
 * default, using information from the parent scopes (session user identity, including any roles specified in
 * the user groups subscribed to). Observations of the same observable with different observers are different
 * observations.
 * <p>
 * A context scope is used to add observations, which will trigger resolution, through
 * {@link #observe(Observation)} and carries information (parent observation, observer, namespace, project,
 * scenarios) that is relevant to the resolver. Scopes can be specialized to customize resolution before
 * {@link #observe(Observation)} is called, and their status is passed across network boundaries, encoded in
 * the scope header added to requests that trigger resolution. If resolution in the runtime fails, the
 * resulting observation will be in an unresolved state, unusable to make any further resolution, and the
 * context is inconsistent unless all the inconsistent observations are substantials that can simply be
 * stated. In the runtime service API, the GraphQL endpoint is used to submit observations to a digital twin
 * for resolution through a mutation.
 * <p>
 * The context scope carries the URL of the digital twin (a GraphQL endpoint) and can be connected to another
 * to form larger, multi-observer, distributed scopes.
 *
 * @author Ferd
 */
public interface ContextScope extends SessionScope, AutoCloseable {

    @Override
    default Type getType() {
        return Type.CONTEXT;
    }


    /**
     * Context scopes have a URL that enables communication with clients but also allows other contexts to
     * connect to them and become part of federated contexts to form distributed digital twins. When
     * connected, contexts share events and state through messaging, with visibility defined by their
     * authenticated agreement.
     *
     * @return
     */
    URL getUrl();

    /**
     * Return the observer for this context. This should normally not be null even if the context is not
     * focused on an observation except at context initialization; the system should provide a default
     * observer built from session data if non-observer observations are made without an explicit observer.
     * The scale of the observer implies the default context of observation.
     *
     * @return
     */
    Observation getObserver();

    /**
     * A context scope is inconsistent if resolution has failed for one or more <em>dependent</em>
     * observations, such as qualities and processes. These can be retrieved through
     * {@link #getInconsistencies(boolean)} passing true as parameter.
     *
     * @return
     */
    boolean isConsistent();

    /**
     * Return all observations in this scope for which resolution has failed. Optionally only return dependent
     * observations, whose presence makes the context and the associated digital twin inconsistent.
     *
     * @param dependentOnly if true, only dependent observations are returned
     * @return the requested inconsistent observations in this scope
     */
    Collection<Observation> getInconsistencies(boolean dependentOnly);

    /**
     * Return all the known observation perspectives for the passed observable. These are the different
     * observations of the same observable made by different observers. The observer in the scope and in the
     * passed observable will be ignored when matching.
     *
     * @param observable
     * @param <T>
     * @return zero or more observations of the same observable. If > 1, the resulting observations are
     * guaranteed to have different observers.
     */
    <T extends Observation> Collection<T> getPerspectives(Observable observable);

    /**
     * Return the observer that has made the observation passed. It should never be null. This is done by
     * inspecting the observation graph.
     *
     * @param observation
     * @return
     */
    Observation getObserverOf(Observation observation);

    /**
     * Return the consistent observations made in the root context using {@link #observe(Observation)}. The
     * resulting collection must iterate in order of observation, established by provenance. All the root
     * observations must be direct, so they cannot be inconsistent even if they are unresolved.
     *
     * @return
     */
    Collection<Observation> getRootObservations();

    /**
     * If this scope is focused on a specific subject, return it.
     *
     * @return the context observation or null
     */
    Observation getContextObservation();

    /**
     * Return a child scope with the passed observer instead of ours.
     *
     * @param observer
     * @return
     */
    ContextScope withObserver(Observation observer);

    //    /**
    //     * Return a new observation scope that sets the passed scenarios for any future observation.
    //     *
    //     * @param scenarios
    //     * @return
    //     * @deprecated use resolution constraints
    //     */
    //    ContextScope withScenarios(String... scenarios);

    //    /**
    //     * Return a new context scope with the passed namespace of resolution. Used by the resolver or to
    //     * fine-tune resolution, prioritizing models in the passed namespace and the enclosing project.
    //     *
    //     * @param namespace
    //     * @return
    //     * @deprecated use resolution constraints
    //     */
    //    ContextScope withResolutionNamespace(String namespace);


    /**
     * Return a scope focused on a specific context observation. The focus determines the observations found
     * and made, and filters the dataflow and provenance returned.
     *
     * @param contextObservation
     * @return a scope focused on the passed observation.
     */
    ContextScope within(DirectObservation contextObservation);

    //    /**
    //     * Contextualize to a specific subclass of a trait, which is observed within the current context
    //     * observation, and defaults to the passed value overall if the resolution fails. The trait
    //     becomes a
    //     * constraint in the iteration of the scale, making the iterators skip any state where the trait
    //     may be
    //     * different from the one set through this method.
    //     * <p>
    //     * Using this method will trigger resolution and computation for each new base trait subsuming
    //     * abstractTrait. Implementations may make its resolution lazy or not.
    //     *
    //     * @param abstractTrait subsuming value, may be concrete but will be observed if absent, so if
    //     concrete
    //     *                      the base trait will be observed instead.
    //     * @param concreteTrait the fill value, may be abstract as long as it's subsumed by abstractTrait
    //     * @return a new context scope that only considers the passed incarnation of the trait.
    //     * @deprecated use resolution constraints
    //     */
    //    ContextScope withContextualizedPredicate(Concept abstractTrait, Concept concreteTrait);

    /**
     * Return a new context with source and target set to create and resolve a relationship.
     *
     * @param source
     * @param target
     * @return
     */
    ContextScope between(Observation source, Observation target);

    /**
     * Add another context to this one to build a higher-level one. Authentication details will define what is
     * seen and done.
     *
     * @param remoteContext
     * @return
     */
    ContextScope connect(URL remoteContext);

    /**
     * Submit an observation to the digital twin for resolution. If no context observation or observer is
     * present in the scope, the arguments must fully specify a non-collective <em>substantial</em> with its
     * own geometry. Otherwise, the geometry of reference is the observation geometry of the observer if the
     * observation is of a collective substantial, and that of the context observation, which must be present,
     * if the observation is of a dependent.  If the observation is a non-collective relationship, the scope
     * must be the result of a {@link #between(Observation, Observation)} call to specify the source and
     * target.
     * <p>
     * If the passed observation contains an observation geometry and the semantics is of an agent, the
     * runtime will build an observation suitable to be an observer. At the API level, no automatic linking is
     * done in the DT except for what the scope defines, so setting it as the observer for other observations
     * must be done through the scope using the with- methods.
     * <p>
     * After this is called, resolution will started in the runtime service chosen at context creation, using
     * any {@link ResolutionConstraint}s set into the scope. The ID tracked by the returned {@link Task} is
     * the ID of the observation. If resolution fails, the task will fail and the observation will be returned
     * in an unresolved state, recorded but invisible to the DT unless unresolved observations are queried.
     * Unresolved observations are those that the resolver wasn't able to explain with a model: this only
     * applies to dependents (qualities and processes) as substantials can be simply acknowledged as long as
     * their scale is given. If any dependents are unresolved, the context will be in an inconsistent state.
     *
     * @param observation an unresolved observation to be resolved by the runtime and added to the digital
     *                    twin. The
     *                    {@link
     *                    org.integratedmodelling.klab.api.digitaltwin.DigitalTwin#createObservation(Scope,
     *                    Object...)} method can be used to construct it from existing knowledge.
     * @return a {@link org.integratedmodelling.klab.api.scope.ReactiveScope.Task}, which is a {@link Future}
     * producing the resolved observation. The associated tracking ID, available immediately, can be used for
     * inquiries beyond the future's own API, such as retrieval of notifications or addressing of
     * observations, reports, provenance or dataflows in the knowledge graph contained in the digital twin.
     */
    Task<Observation, Long> observe(Observation observation);

    /**
     * Return all observations affected by the passed one in this scope, either through model dependencies or
     * behaviors. "Affected" is any kind of reaction, not necessarily implied by semantics.
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
     * Start the scheduling if the context occurs; do nothing if not, or if there are no new transitions to
     * calculate. This can be called multiple times, normally after each observation, with intelligent
     * "replay" of any transitions that need to be seen again.
     */
    void runTransitions();

    /**
     * Return the portion of the provenance graph that pertains to this scope. This may be empty in an empty
     * context, never null. Provenance will be relative to the context observation this scope focuses on. The
     * full provenance graph will be returned by calling this method on the result of
     * {@link #getRootContextScope()}.
     *
     * @return the provenance graph for this scope
     */
    Provenance getProvenance();

    /**
     * Return the compiled report that pertains to this scope. The result may be a subgraph of the root report
     * available from the root context scope. There is one report per root context. Actuators will add
     * sections to it as models are computed, based on the documentation templates associated with models and
     * their parts. The report can be compiled and rendered at any time. Each observation in the same context
     * will report the same report.
     * <p>
     * TODO pass reporting options
     *
     * @return
     */
    Report getReport();

    /**
     * Return the dataflow that pertains to this scope. The result may be a subgraph of the root context
     * scope's dataflow. There is one dataflow per context, and it's never null. It starts empty and
     * incorporates all the dataflows created by the resolver when new observations are made. Each dataflow is
     * inserted in the main one at the appropriate position, so that running the dataflow again will recreate
     * the exact same context. The dataflow returned pertains to the observation that the scope is focused
     * on.
     * <p>
     * Note that the dataflow will not recompute observations, so the partial dataflow may not be a complete
     * strategy for the observation as it may reuse information already available in upstream scopes.
     *
     * @return
     */
    Dataflow<Observation> getDataflow();

    /**
     * Return the root context scope with the overall observer and the full observation graph.
     *
     * @return
     */
    ContextScope getRootContextScope();

    /**
     * Return the parent observation of the passed observation. The runtime context maintains the logical
     * structure graph (ignores grouping of artifacts).
     *
     * @param observation
     * @return the parent, or null if root subject
     */
    Observation getParentOf(Observation observation);

    /**
     * Return all children of the passed observation, using the logical structure (i.e. skipping observation
     * groups). The runtime context maintains the structure graph.
     *
     * @param observation an observation. {@link State States} have no children but no error should be
     *                    raised.
     * @return the parent, or an empty collection if no children
     */
    Collection<Observation> getChildrenOf(Observation observation);

    //    /**
    //     * Return mappings for any predicates contextualized with
    //     * {@link #withContextualizedPredicate(Concept, Concept)} at context creation.
    //     *
    //     * @deprecated merge into resolution constraints
    //     * @return the contextualized predicates mapping
    //     */
    //    Map<Concept, Concept> getContextualizedPredicates();

    /**
     * Inspect the network graph of the current context, returning all relationships that have the passed
     * subject as target.
     *
     * @param observation a {@link DirectObservation} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Observation> getOutgoingRelationshipsOf(Observation observation);

    /**
     * Inspect the network graph of the current context, returning all relationships that have the passed
     * subject as target.
     *
     * @param observation a {@link DirectObservation} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Observation> getIncomingRelationshipsOf(Observation observation);

    /**
     * Return the observations in this scope as a map indexed by observable.
     *
     * @return
     */
    Map<Observable, Observation> getObservations();

    /**
     * Retrieve the observation that is recognized with the passed name in this context.
     *
     * @param <T>
     * @param localName
     * @param cls
     * @return
     */
    <T extends Observation> T getObservation(String localName, Class<T> cls);

    //    /**
    //     * When resolving, the resolution namespace that provides the resolution scope must be provided.
    //     In other
    //     * situations this will be null.
    //     *
    //     * @return
    //     * @deprecated use resolution constraints
    //     */
    //    String getResolutionNamespace();

    //    /**
    //     * Same as {@link #getResolutionNamespace()}, reporting the project in scope during resolution.
    //     *
    //     * @return
    //     * @deprecated use resolution constraints
    //     */
    //    String getResolutionProject();

    //    /**
    //     * Any scenarios set during the resolution.
    //     *
    //     * @return
    //     * @deprecated use resolution constraints
    //     */
    //    Collection<String> getResolutionScenarios();
    //
    //    /**
    //     * A context is born "empty" and since k.LAB 0.12 does not have a root observation, but when used in
    //     * resolution may acquire a root observation which serves as context for the resolution.
    //     *
    //     * @return
    //     * @deprecated use the context but the geometry of resolution is the observer's for substantials,
    //     and the
    //     * context observation's for dependents
    //     */
    //    DirectObservation getResolutionObservation();

    //    /**
    //     * Create a new scope if necessary where the catalog uses the passed local names and the scale,
    //     if not
    //     * null, is the passed one. Called before each actuator's functors are executed and passed to the
    //     functor
    //     * executor.
    //     *
    //     * @param scale      may be null, meaning that the original scale is unchanged
    //     * @param localNames if empty, the catalog remains the same
    //     * @return a localized context or this one if nothing needs to change
    //     * @deprecated revise, at best this should be non-API and only at service side
    //     */
    //    ContextScope withContextualizationData(Observation contextObservation, Scale scale, Map<String,
    //            String> localNames);

    /**
     * Set resolution constraints here. Returns a new scope with all the constraints added to the ones in
     * this. Pass nothing (null array) to reset the constraints and return a new scope with no constraints.
     */
    ContextScope withResolutionConstraints(ResolutionConstraint... resolutionConstraints);

    /**
     * Return all the raw resolution constraints. Used when calling REST endpoints, the
     * <code>getConstraint[s](...)</code> methods should be used when resolving.
     *
     * @return
     */
    List<ResolutionConstraint> getResolutionConstraints();

    /**
     * Return the single value of a resolution constraint, or null if absent.
     *
     * @param type
     * @param resultClass
     * @param <T>
     * @return
     */
    <T> T getConstraint(ResolutionConstraint.Type type, Class<T> resultClass);

    /**
     * Return the single value of a resolution constraint, or a default value if absent.
     *
     * @param type
     * @param defaultValue
     * @param <T>
     * @return
     */
    <T> T getConstraint(ResolutionConstraint.Type type, T defaultValue);

    /**
     * Return all the existing value of a resolution constraint in the scope, or the empty list if no
     * constraint is there.
     *
     * @param type
     * @param resultClass
     * @param <T>
     * @return
     */
    <T> List<T> getConstraints(ResolutionConstraint.Type type, Class<T> resultClass);

    /**
     * A data structure incorporating the results of parsing a scope token string into all its possible
     * components. The scope token is added to requests that need a scope below UserScope through the
     * {@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} HTTP request header. Empty means the
     * passed token was null. Tokens must appear in the header content in the order below. All fields may be
     * null, including the arrays, if not passed in the token.
     *
     * @param type            the scope type, based on the path length
     * @param scopeId         the ID with which the scope should be registered
     * @param observationPath if there is a focal observation ID, the path to the observation
     * @param observerId      if there is an observer field after #, the path to the observer
     */
    record ScopeData(Scope.Type type, String scopeId, String[] observationPath, String observerId
                     /*, String[] scenarioUrns, Map<String, String> traitIncarnations,
                     String resolutionNamespace*/) {
        public boolean empty() {
            return scopeId() == null;
        }
    }

    /**
     * Obtain the properly formatted scope token for the
     * {@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} to use in a request. The root context
     * scope must have been registered by the runtime service, which is done automatically by client scopes..
     *
     * @param scope
     * @return
     */
    public static String getScopeId(ContextScope scope) {

        StringBuffer ret = new StringBuffer(512);

        ret.append(scope.getId());

        if (scope.getContextObservation() != null) {

            var cobs = new ArrayList<Observation>();
            ContextScope rootContext = scope;
            cobs.add(scope.getContextObservation());
            while (rootContext.getParentScope() instanceof ContextScope parentContext) {
                if (parentContext.getContextObservation() == null) {
                    break;
                } else {
                    cobs.add(parentContext.getContextObservation());
                }
                rootContext = parentContext;
            }

            for (var obs : cobs.reversed()) {
                ret.append("." + obs.getId());
            }
        }

        if (scope.getObserver() != null) {
            ret.append("#").append(scope.getObserver().getId());
        }

        //        if (!scope.getResolutionScenarios().isEmpty()) {
        //            ret.append("@").append(Utils.Strings.join(scope.getResolutionScenarios(), ","));
        //        }

        //        if (!scope.getContextualizedPredicates().isEmpty()) {
        //            ret.append("&");
        //            StringBuffer buf = new StringBuffer();
        //            for (Concept key : scope.getContextualizedPredicates().keySet()) {
        //                buf.append((buf.isEmpty() ? "" : ",") + key.getUrn() + "=" + scope
        //                .getContextualizedPredicates().get(key).getUrn());
        //            }
        //            ret.append(buf);
        //        }

        //        if (scope.getResolutionNamespace() != null) {
        //            ret.append("$").append(scope.getResolutionNamespace());
        //        }

        return ret.toString();
    }


    /**
     * Parse a scope token into the corresponding data structure
     *
     * @param scopeToken
     * @return
     */
    static ScopeData parseScopeId(String scopeToken) {

        Scope.Type type = Scope.Type.USER;
        String scopeId = null;
        String[] observationPath = null;
        String observerId = null;
        //        String[] scenarioUrns = null;
        //        String resolutionNamespace = null;
        //        Map<String, String> traitIncarnations = null;

        if (scopeToken != null) {

            //            if (scopeToken.contains("$")) {
            //                String[] split = scopeToken.split("\\@");
            //                scopeToken = split[0];
            //                resolutionNamespace = split[1];
            //            }

            //            if (scopeToken.contains("&")) {
            //                String[] split = scopeToken.split("\\@");
            //                scopeToken = split[0];
            //                traitIncarnations = new LinkedHashMap<>();
            //                for (var pair : split[1].split(",")) {
            //                    String[] pp = pair.split("=");
            //                    traitIncarnations.put(pp[0], pp[1]);
            //                }
            //            }

            //            // separate out scenarios
            //            if (scopeToken.contains("@")) {
            //                String[] split = scopeToken.split("@");
            //                scopeToken = split[0];
            //                scenarioUrns = split[1].split(",");
            //            }

            // Separate out observer path if any
            if (scopeToken.contains("#")) {
                String[] split = scopeToken.split("#");
                scopeToken = split[0];
                observerId = split[1];
            }

            var path = scopeToken.split("\\.");
            type = path.length > 1 ? Scope.Type.CONTEXT : Scope.Type.SESSION;
            scopeId = path.length == 1 ? path[0] : (path[0] + "." + path[1]);
            if (path.length > 2) {
                observationPath = Arrays.copyOfRange(path, 2, path.length);
            }
        }

        return new ScopeData(type, scopeId, observationPath, observerId/*, scenarioUrns, traitIncarnations,
                resolutionNamespace*/);
    }
}