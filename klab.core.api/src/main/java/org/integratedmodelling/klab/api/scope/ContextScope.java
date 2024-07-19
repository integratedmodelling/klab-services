package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.api.utils.Utils;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;

/**
 * The scope for a context and any observations made within it. The context scope is the door to the "digital
 * twin" of the observations made in it. The scope always has an observer that can be switched to another. A
 * scope is also passed around during resolution and carries scope information (namespace, project, scenarios)
 * that is relevant to the resolver. Scopes can be specialized to customize resolution before
 * {@link #observe(Object...)} is called, and their status is passed across network boundaries, encoded in the
 * scope header added to requests that trigger resolution.
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
     * Return the observer for this context. This will never be null. The scale of the observer implies the
     * default context of observation.
     *
     * @return
     */
    Subject getObserver();

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
    ContextScope withObserver(Subject observer);

    /**
     * Return a new observation scope that sets the passed scenarios for any future observation.
     *
     * @param scenarios
     * @return
     */
    ContextScope withScenarios(String... scenarios);

    /**
     * Return a new context scope with the passed namespace of resolution. Used by the resolver or to
     * fine-tune resolution.
     *
     * @param namespace
     * @return
     */
    ContextScope withResolutionNamespace(String namespace);


    /**
     * Return a scope focused on a specific context observation. The focus determines the observations found
     * and made, and filters the dataflow and provenance returned.
     *
     * @param contextObservation
     * @return a scope focused on the passed observation.
     */
    ContextScope within(DirectObservation contextObservation);

    /**
     * Contextualize to a specific subclass of a trait, which is observed within the current context
     * observation, and defaults to the passed value overall if the resolution fails. The trait becomes a
     * constraint in the iteration of the scale, making the iterators skip any state where the trait may be
     * different from the one set through this method.
     * <p>
     * Using this method will trigger resolution and computation for each new base trait subsuming
     * abstractTrait. Implementations may make its resolution lazy or not.
     *
     * @param abstractTrait subsuming value, may be concrete but will be observed if absent, so if concrete
     *                      the base trait will be observed instead.
     * @param concreteTrait the fill value, may be abstract as long as it's subsumed by abstractTrait
     * @return a new context scope that only considers the passed incarnation of the trait.
     */
    ContextScope withContextualizedPredicate(Concept abstractTrait, Concept concreteTrait);

    /**
     * Add another context to this one to build a higher-level one. Authentication details will define what is
     * seen and done.
     *
     * @param remoteContext
     * @return
     */
    ContextScope connect(URL remoteContext);

    /**
     * Make an observation. Must be called on a context scope, possibly focused on a given root observation
     * using {@link #within(DirectObservation)}}. If no root observation is present in the scope, the
     * arguments must fully specify a subject, either through a direct definition from a passed object or a
     * URN specifying a definition or a subject observation. If the observer is focused on a scale,  the
     * context can decide to use it as a scale for the root observation.
     * <p>
     * Observables will be routinely specified through URNs, which will be validated as any observable object
     * - concepts/observables, resource URNs, model/acknowledgement URNs, or full URLs specifying a
     * context/observation in an externally hosted runtime to link to the current context. Passing descriptors
     * for concepts, observables, acknowledgements or models should not cause errors.
     * <p>
     * In case the observable specifies a relationship, k.LAB will attempt to instantiate it, observing its
     * source/target endpoints as well, unless two subject observations are passed, in which case a specified
     * relationship will be instantiated between them using them as source and target respectively. In the
     * latter case, each relationship will be resolved but configuration detection will only happen upon
     * exiting the scope where observe() is called.
     * <p>
     * If the observation is at root level, or connecting two root-level subject through a relationship, the
     * overall geometry of the context will be automatically adjusted.
     *
     * @param observables URN(s) specifying resolvables, or direct objects that can be resolved and observed.
     * @return a future for the observation being contextualized.
     */
    Future<Observation> observe(Object... observables);

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
     * Start the scheduling if the context occurs; do nothing if not. This can be called again at each
     * observation, with intelligent "replay" of any transitions that need to be seen again.
     */
    void runTransitions();

    /**
     * The provenance graph. Empty in an empty context, never null. Provenance will be relative to the context
     * observation this scope focuses on.
     *
     * @return the provenance graph for this scope
     */
    Provenance getProvenance();

    /**
     * There is one report per root context. Actuators will add sections to it as models are computed, based
     * on the documentation templates associated with models and their parts. The report can be compiled and
     * rendered at any time. Each observation in the same context will report the same report.
     *
     * @return
     */
    Report getReport();

    /**
     * There is one dataflow per context, and it's never null. It starts empty and incorporates all the
     * dataflows created by the resolver when new observations are made. Each dataflow is inserted in the main
     * one at the appropriate position, so that running the dataflow again will recreate the exact same
     * context.
     *
     * @return
     */
    Dataflow<Observation> getDataflow();

    /**
     * Return the parent observation of the passed observation. The runtime context maintains the logical
     * structure graph (ignores grouping of artifacts).
     *
     * @param observation
     * @return the parent, or null if root subject
     */
    DirectObservation getParentOf(Observation observation);

    /**
     * Return all children of the passed observation, using the logical structure (i.e. skipping observation
     * groups). The runtime context maintains the structure graph.
     *
     * @param observation an observation. {@link State States} have no children but no error should be
     *                    raised.
     * @return the parent, or an empty collection if no children
     */
    Collection<Observation> getChildrenOf(Observation observation);

    /**
     * Return mappings for any predicates contextualized with
     * {@link #withContextualizedPredicate(Concept, Concept)} at context creation.
     *
     * @return the contextualized predicates mapping
     */
    Map<Concept, Concept> getContextualizedPredicates();

    /**
     * Inspect the network graph of the current context, returning all relationships that have the passed
     * subject as target.
     *
     * @param observation a {@link DirectObservation} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Relationship> getOutgoingRelationships(DirectObservation observation);

    /**
     * Inspect the network graph of the current context, returning all relationships that have the passed
     * subject as target.
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
     * When resolving, the resolution namespace that provides the resolution scope must be provided. In other
     * situations this will be null.
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
     * A context is born "empty" and since k.LAB 0.12 does not have a root observation, but when used in
     * resolution may acquire a root observation which serves as context for the resolution.
     *
     * @return
     */
    DirectObservation getResolutionObservation();

    /**
     * Create a new scope if necessary where the catalog uses the passed local names and the scale, if not
     * null, is the passed one. Called before each actuator's functors are executed and passed to the functor
     * executor.
     *
     * @param scale      may be null, meaning that the original scale is unchanged
     * @param localNames if empty, the catalog remains the same
     * @return a localized context or this one if nothing needs to change
     */
    ContextScope withContextualizationData(DirectObservation contextObservation, Scale scale, Map<String,
            String> localNames);


    /**
     * A data structure incorporating the results of parsing a scope token string into all its possible
     * components. The scope token is added to requests that need a scope below UserScope through the
     * {@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} HTTP request header. Empty means the
     * passed token was null. Tokens must appear in the header content in the order below. All fields may be
     * null, including the arrays, if not passed in the token.
     *
     * @param type                the scope type, based on the path length
     * @param scopeId             the ID with which the scope should be registered
     * @param observationPath     if there is a focal observation ID, the path to the observation
     * @param observerId          if there is an observer field after #, the path to the observer
     * @param scenarioUrns        any scenario URNS, passed as a comma-separated list after the @ marker, if
     *                            present
     * @param traitIncarnations   traits incarnated, passed after a & marker as =-separated strings
     * @param resolutionNamespace passed after a $ sign
     */
    public record ScopeData(Scope.Type type, String scopeId, String[] observationPath, String observerId,
                            String[] scenarioUrns, Map<String, String> traitIncarnations,
                            String resolutionNamespace) {
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

        if (!scope.getResolutionScenarios().isEmpty()) {
            ret.append("@").append(Utils.Strings.join(scope.getResolutionScenarios(), ","));
        }

        if (!scope.getContextualizedPredicates().isEmpty()) {
            ret.append("&");
            StringBuffer buf = new StringBuffer();
            for (Concept key : scope.getContextualizedPredicates().keySet()) {
                buf.append((buf.isEmpty() ? "" : ",") + key.getUrn() + "=" + scope.getContextualizedPredicates().get(key).getUrn());
            }
            ret.append(buf);
        }

        if (scope.getResolutionNamespace() != null) {
            ret.append("$").append(scope.getResolutionNamespace());
        }

        return ret.toString();
    }

    /**
     * Parse a scope token into the corresponding data structure
     *
     * @param scopeToken
     * @return
     */
    public static ScopeData parseScopeId(String scopeToken) {

        Scope.Type type = Scope.Type.USER;
        String scopeId = null;
        String[] observationPath = null;
        String observerId = null;
        String[] scenarioUrns = null;
        String resolutionNamespace = null;
        Map<String, String> traitIncarnations = null;

        if (scopeToken != null) {

            if (scopeToken.contains("$")) {
                String[] split = scopeToken.split("\\@");
                scopeToken = split[0];
                resolutionNamespace = split[1];
            }

            if (scopeToken.contains("&")) {
                String[] split = scopeToken.split("\\@");
                scopeToken = split[0];
                traitIncarnations = new LinkedHashMap<>();
                for (var pair : split[1].split(",")) {
                    String[] pp = pair.split("=");
                    traitIncarnations.put(pp[0], pp[1]);
                }
            }

            // separate out scenarios
            if (scopeToken.contains("@")) {
                String[] split = scopeToken.split("@");
                scopeToken = split[0];
                scenarioUrns = split[1].split(",");
            }

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

        return new ScopeData(type, scopeId, observationPath, observerId, scenarioUrns, traitIncarnations,
                resolutionNamespace);
    }
}