package org.integratedmodelling.klab.api.scope;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.api.utils.Utils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * The scope for an observation context and any observations made within it. The observation scope
 * is the handle to the "digital twin" of the observations made in it and contains all the methods
 * that give access to the observation, dataflow and provenance graphs. These are available from the
 * GraphQL API served by the {@link org.integratedmodelling.klab.api.services.RuntimeService}.
 *
 * <p>The context scope always has an observer that can be switched to another when making
 * observations. The observer is an observation of an agent that also specifies an observed geometry
 * in addition to its own. The calling client should explicitly provide an observer; if not, the
 * digital twin should provide one by default, using information from the parent scopes (session
 * user identity, including any roles specified in the user groups subscribed to). Observations of
 * the same observable with different observers are different observations.
 *
 * <p>A context scope is used to add observations, which will trigger resolution, through {@link
 * #submit(Observation)} and carries information (parent observation, observer, namespace, project,
 * scenarios) that is relevant to the resolver. Scopes can be specialized to customize resolution
 * before {@link #submit(Observation)} is called, and their status is passed across network
 * boundaries, encoded in the scope header added to requests that trigger resolution. If resolution
 * in the runtime fails, the resulting observation will be in an unresolved state, unusable to make
 * any further resolution, and the context is inconsistent unless all the inconsistent observations
 * are substantials that can simply be stated. In the runtime service API, the GraphQL endpoint is
 * used to submit observations to a digital twin for resolution through a mutation.
 *
 * <p>The context scope carries the URL of the digital twin (a GraphQL endpoint) and can be
 * connected to another to form larger, multi-observer, distributed scopes.
 *
 * @author Ferd
 */
public interface ContextScope extends SessionScope {

  @Override
  default Type getType() {
    return Type.CONTEXT;
  }

  /**
   * Context scopes have a URL that enables communication with clients but also allows other
   * contexts to connect to them and become part of federated contexts to form distributed digital
   * twins. When connected, contexts share events and state through messaging, with visibility
   * defined by their authenticated agreement.
   *
   * @return
   */
  URL getUrl();

  Activity getCurrentActivity();

  /**
   * Return the observer for this context. This should normally not be null even if the context is
   * not focused on an observation except at context initialization; the system should provide a
   * default observer built from session data if non-observer observations are made without an
   * explicit observer. The scale of the observer implies the default context of observation.
   *
   * @return
   */
  Observation getObserver();

  /**
   * A context scope is inconsistent if resolution has failed for one or more <em>dependent</em>
   * observations, such as qualities and processes. These can be retrieved through {@link
   * #getInconsistencies(boolean)} passing true as parameter.
   *
   * @return
   */
  boolean isConsistent();

  /**
   * Return all the observations visible in this context, ordered by submission timestamp.
   *
   * @return
   */
  List<Observation> getObservations();

  /**
   * Retrieve the observation with the passed observable in this scope from the knowledge graph.
   * This will only retrieve one observation. Calling this one with a countable observable may
   * produce a singular observation or a collective whose children are the requested observation in
   * case there are multiple observations in this scope. To retrieve all the instances of the
   * collective, retrieve the collective and then call {@link #getChildrenOf(Observation)} on it.
   * The API user is responsible for checking the collective status of the result.
   *
   * @param observable can pass an observable, but the result will be insensitive to units, name or
   *     anything not related to semantics.
   * @return the observation (possibly a collective) or null. The resulting observation may be
   *     unresolved; the implementation decides what to do with it and is responsible for checking.
   */
  Observation getObservation(Semantics observable);

  /**
   * Return all observations in this scope for which resolution has failed. Optionally only return
   * dependent observations, whose presence makes the context and the associated digital twin
   * inconsistent.
   *
   * @param dependentOnly if true, only dependent observations are returned
   * @return the requested inconsistent observations in this scope
   */
  Collection<Observation> getInconsistencies(boolean dependentOnly);

  /**
   * Return all the known observation perspectives for the passed observable. These are the
   * different observations of the same observable made by different observers. The observer in the
   * scope and in the passed observable will be ignored when matching.
   *
   * @param observable
   * @param <T>
   * @return zero or more observations of the same observable. If > 1, the resulting observations
   *     are guaranteed to have different observers.
   */
  <T extends Observation> Collection<T> getPerspectives(Observable observable);

  /**
   * Return the observer that has made the observation passed. It should never be null. This is done
   * by inspecting the observation graph.
   *
   * @param observation
   * @return
   */
  Observation getObserverOf(Observation observation);

  /**
   * Produce a {@link Data} package that contains the data content of the passed observations. The
   * object should be lazy and only fill in its contents when the actual data are requested. It can
   * be sent to services to pass around data content for distributed computation workflows.
   *
   * @param observations
   * @return
   */
  Data getData(Observation... observations);

  /**
   * Return the consistent observations made in the root context using {@link #submit(Observation)}.
   * The resulting collection must iterate in order of observation, established by provenance. All
   * the root observations must be direct, so they cannot be inconsistent even if they are
   * unresolved.
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

  /**
   * Return a scope focused on a specific context observation. The focus determines the observations
   * found and made, and filters the dataflow and provenance returned.
   *
   * @param contextObservation
   * @return a scope focused on the passed observation.
   */
  ContextScope within(Observation contextObservation);

  /**
   * Return a new context with source and target set to create and resolve a relationship.
   *
   * @param source
   * @param target
   * @return
   */
  ContextScope between(Observation source, Observation target);

  /**
   * Each scope manages a digital twin. At client side or on slave servers this may be null or
   * limited in functionality.
   *
   * @return
   */
  DigitalTwin getDigitalTwin();

  /**
   * Pass a connected ContextScope (possibly the result of {@link #connect(URL)} or {@link
   * #connect(DigitalTwin.Configuration)}) and return a new ContextScope that merges this
   * ContextScope with the passed one. Permissions must allow the merge.
   *
   * @param remoteContext
   * @return
   */
  ContextScope connect(ContextScope remoteContext);

  /**
   * Submit an observation for inclusion into the knowledge graph at the point implied by the
   * current scope, starting its resolution and/or validation, returning a future for the resolved
   * observation or for an {@link Observation#isEmpty() empty} one in case of failure. This method
   * is the key operation to operate on a digital twin in k.LAB.
   *
   * <p>The scope is notified of any events related to the resolution. Messages will be sent for
   * each activity undertaken, including resolution and initialization of any secondary
   * observations. Any connected scope will receive all messages related to the same digital twin.
   *
   * <p>The observation may contain resolution metadata (still TBD) which can be used to resolve it
   * from an existing, possibly remote, storage. The resolution metadata must contain an adapter ID
   * and all the necessary information for it. If validation and ingestion at the appropriate
   * position in the knowledge graph succeeds, the resolved observation, now part of the knowledge
   * graph, is returned.
   *
   * @param observation an unresolved observation to be resolved by the runtime and added to the
   *     knowledge graph.
   * @return a {@link Future} producing the resolved observation when resolution is finished and the
   *     observation is part of the knowledge graph. If resolution has failed, the observation in
   *     the future will be {@link Observation#isEmpty() empty}.
   */
  CompletableFuture<Observation> submit(Observation observation);

  /**
   * Return all observations affected by the passed one in this scope, either through model
   * dependencies or behaviors. "Affected" is any kind of reaction, not necessarily implied by
   * semantics.
   *
   * @param observation
   * @return
   */
  Collection<Observation> affects(Observation observation);

  /**
   * Return all observations that the passed one affects in this scope, either through model
   * dependencies or behaviors. "Affected" is any kind of reaction, not necessarily implied by
   * semantics.
   *
   * @param observation
   * @return
   */
  Collection<Observation> affected(Observation observation);

  /**
   * Start the scheduling if the context occurs; do nothing if not, or if there are no new
   * transitions to calculate. This can be called multiple times, normally after each observation,
   * with intelligent "replay" of any transitions that need to be seen again.
   */
  void runTransitions();

  /**
   * Return the portion of the provenance graph that pertains to this scope. This may be empty in an
   * empty context, never null. Provenance will be relative to the context observation this scope
   * focuses on. The full provenance graph will be returned by calling this method on the result of
   * {@link #getRootContextScope()}.
   *
   * @return the provenance graph for this scope
   */
  Provenance getProvenance();

  /**
   * Get the portion of the provenance graph that pertains to the passed observation. Can be used by
   * the reporting system to document each individual observation.
   *
   * @param observation
   * @return
   */
  Provenance getProvenanceOf(Observation observation);

  /**
   * Return the compiled report that pertains to this scope. The result may be a subgraph of the
   * root report available from the root context scope. There is one report per root context.
   * Actuators will add sections to it as models are computed, based on the documentation templates
   * associated with models and their parts. The report can be compiled and rendered at any time.
   * Each observation in the same context will report the same report.
   *
   * <p>TODO pass reporting options
   *
   * @return
   */
  Report getReport();

  /**
   * Return the dataflow that pertains to this scope. The result may be a subgraph of the root
   * context scope's dataflow. There is one dataflow per context, and it's never null. It starts
   * empty and incorporates all the dataflows created by the resolver when new observations are
   * made. Each dataflow is inserted in the main one at the appropriate position, so that running
   * the dataflow again will recreate the exact same context. The dataflow returned pertains to the
   * observation that the scope is focused on.
   *
   * <p>Note that the dataflow will not recompute observations, so the partial dataflow may not be a
   * complete strategy for the observation as it may reuse information already available in upstream
   * scopes.
   *
   * @return
   */
  Dataflow getDataflow();

  /**
   * Return the root context scope with the overall observer and the full observation graph.
   *
   * @return
   */
  ContextScope getRootContextScope();

  //  /**
  //   * The main method to retrieve anything visible to this scope from the knowledge graph.
  //   *
  //   * @param resultClass
  //   * @param queryData
  //   * @param <T>
  //   * @return
  //   * @deprecated use query on KG
  //   */
  //  <T extends RuntimeAsset> List<T> query(Class<T> resultClass, Object... queryData);

  //  <T extends RuntimeAsset> List<T> queryKnowledgeGraph(KnowledgeGraph.Query<T>
  // knowledgeGraphQuery);

  /**
   * Return the parent observation of the passed observation. The runtime context maintains the
   * logical structure graph (ignores grouping of artifacts).
   *
   * @param observation
   * @return the parent, or null if root subject
   */
  Observation getParentOf(Observation observation);

  /**
   * Return all children of the passed observation, using the logical structure (i.e. skipping
   * observation groups). The runtime context maintains the structure graph.
   *
   * @param observation an observation. Quality observations have no children but no error should be
   *     raised.
   * @return the parent, or an empty collection if no children
   */
  Collection<Observation> getChildrenOf(Observation observation);

  /**
   * Inspect the network graph of the current context, returning all relationships that have the
   * passed subject as target.
   *
   * @param observation a {@link Observation} object.
   * @return a {@link java.util.Collection} object.
   */
  Collection<Observation> getOutgoingRelationshipsOf(Observation observation);

  /**
   * Inspect the network graph of the current context, returning all relationships that have the
   * passed subject as target.
   *
   * @param observation a {@link Observation} object.
   * @return a {@link java.util.Collection} object.
   */
  Collection<Observation> getIncomingRelationshipsOf(Observation observation);

  /**
   * Set resolution constraints here. Returns a new scope with all the constraints added to the ones
   * in this. Pass nothing (null array) to reset the constraints and return a new scope with no
   * constraints.
   */
  ContextScope withResolutionConstraints(ResolutionConstraint... resolutionConstraints);

  /**
   * Return all the raw resolution constraints. Used when calling REST endpoints, the <code>
   * getConstraint[s](...)</code> methods should be used when resolving.
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
   * A data structure incorporating the results of parsing a scope token string into all its
   * possible components. The scope token is added to requests that need a scope below UserScope
   * through the {@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} HTTP request
   * header. Empty means the passed token was null. Tokens must appear in the header content in the
   * order below. All fields may be null, including the arrays, if not passed in the token.
   *
   * @param type the scope type, based on the path length
   * @param scopeId the ID with which the scope should be registered
   * @param observationPath if there is a focal observation ID, the path to the observation
   * @param observerId if there is an observer field after #, the path to the observer
   */
  record ScopeData(Scope.Type type, String scopeId, long[] observationPath, long observerId
      /*, String[] scenarioUrns, Map<String, String> traitIncarnations,
      String resolutionNamespace*/ ) {
    public boolean empty() {
      return scopeId() == null;
    }
  }

  /**
   * Obtain the properly formatted scope token for the {@link
   * org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} to use in a request. The root
   * context scope must have been registered by the runtime service, which is done automatically by
   * client scopes.
   *
   * @param scope
   * @return
   */
  static String getScopeId(ContextScope scope) {

    StringBuffer ret = new StringBuffer(512);

    ret.append(scope.getId());

    /**
     * If the context observation is unresolved, it cannot be retrieved from the knowledge graph, so
     * do not add it; the calling function will need to reconstruct the scope in other ways
     */
    if (scope.getContextObservation() != null && scope.getContextObservation().getId() > 0) {

      var cobs = new ArrayList<Observation>();
      ContextScope rootContext = scope;
      cobs.add(scope.getContextObservation());
      while (rootContext.getParentScope() instanceof ContextScope parentContext) {
        if (parentContext.getContextObservation() == null) {
          break;
        } else if (cobs.isEmpty()
            || cobs.getLast().getId() != parentContext.getContextObservation().getId()) {
          cobs.add(parentContext.getContextObservation());
        }
        rootContext = parentContext;
      }

      for (var obs : cobs.reversed()) {
        ret.append("." + obs.getId());
      }
    }

    // observers are necessarily resolved
    if (scope.getObserver() != null) {
      ret.append("#").append(scope.getObserver().getId());
    }

    return ret.toString();
  }

  static Geometry getResolutionGeometry(ContextScope scope) {

    var resolutionGeometry =
        scope.getConstraint(ResolutionConstraint.Type.Geometry, Geometry.class);
    if (resolutionGeometry == null || resolutionGeometry.isEmpty()) {
      if (scope.getContextObservation() != null) {
        resolutionGeometry = scope.getContextObservation().getGeometry();
      }
      if ((resolutionGeometry == null || resolutionGeometry.isEmpty())
          && scope.getObserver() != null) {
        resolutionGeometry = scope.getObserver().getGeometry();
      }
    }
    return resolutionGeometry;
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
    long[] observationPath = null;
    long observerId = Observation.UNASSIGNED_ID;

    if (scopeToken != null) {
      // Separate out observer path if any
      if (scopeToken.contains("#")) {
        String[] split = scopeToken.split("#");
        scopeToken = split[0];
        observerId = Long.parseLong(split[1]);
      }

      var path = scopeToken.split("\\.");
      type = path.length > 1 ? Scope.Type.CONTEXT : Scope.Type.SESSION;
      scopeId = path.length == 1 ? path[0] : (path[0] + "." + path[1]);
      if (path.length > 2) {
        List<Long> longs = new ArrayList<>();
        for (int i = 2; i < path.length; i++) {
          longs.add(Long.parseLong(path[i]));
        }
        observationPath = Utils.Numbers.longArrayFromCollection(longs);
      }
    }

    return new ScopeData(
        type, scopeId, observationPath, observerId /*, scenarioUrns, traitIncarnations,
                resolutionNamespace*/);
  }
}
