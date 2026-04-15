package org.integratedmodelling.klab.api.scope;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * The <code>ContextScope</code> is the handle to a {@link DigitalTwin}. Creating a ContextScope
 * also creates a digital twin unless an existing digital twin URL is provided. In any case a
 * ContextScope cannot exist without an associated DT.
 *
 * <p>A context scope is used to add observations, which will trigger resolution, through the {@link
 * Observation.Builder#submit()} call on a builder obtained calling {@link
 * #observation(Observable)}. The scope represents the point of insertion of the future observation
 * in the knowledge graph. carrying information (context observation of dependents/source-target of
 * relationships, observer) that locates the point of insertion, plus optional resolution
 * constraints that affect how the observation will be resolved (namespace, project, scenarios).
 * Such info is passed across network boundaries, encoded in the scope header added to requests that
 * trigger resolution. If resolution in the runtime fails, the resulting observation will be {@link
 * Observation#isEmpty() empty} and will carry notification explaining why.
 *
 * <p>The context scope carries the URL of the digital twin and can be connected to another to form
 * larger, multi-observer, distributed scopes.
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

  /**
   * The scope may be executing a DT transaction, implementing a provenance {@link Activity}.
   *
   * @return the current transaction or null if none is active.
   */
  DigitalTwin.Transaction getCurrentTransaction();

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
   * Return all the observations visible in this context, ordered by submission timestamp.
   *
   * @return
   */
  List<Observation> getObservations();

  /**
   * Return the <em>known</em> observation from the scope that corresponds to the passed one at the
   * scope point (i.e., as child of the current scope context observation or the DT root), or null.
   * The observation may be an already known object (either an existing, cached observation or an
   * instance created from a definition) or just a submitted, unresolved one with the same
   * observable at the same scope point. In all cases, null should be returned if the observation is
   * not in the knowledge graph OR in the current transaction. If an observation is returned, it
   * must be the very object cached at the scope side and contain the current state of the
   * resolution. Unique among the query functions, this one must also return any matching unresolved
   * observation from the current transaction.
   *
   * <p>In the special case of core substantial types, this method must use the identification
   * strategy implied by the semantics of the substantial to assess the presence of the observation.
   * This may imply checking URNs (after completing them with the DT coordinates if the incoming
   * observation is unresolved) or adopting specific strategies from the worldview.
   *
   * <p>This method is critical to the working of the resolver and dataflow compiler. Observations
   * that are not found by this method will be created; those that are in the transaction will be
   * skipped as their resolution is ongoing; those that are resolved at the time of the call will
   * satisfy a query and returned with no further processing.
   *
   * @param observation
   * @return
   */
  Observation getObservation(Observation observation);

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
   * Return the observer for the passed observation, or null. The observer of a dependent
   * observation can be inherited from its context observation if not specified directly.
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
   * If this scope is focused on a specific subject, return it.
   *
   * @return the context observation or null
   */
  Observation getContextObservation();

  /**
   * If the scope is focused on the source and target of a relationship, return the source
   * observation.
   *
   * @return
   */
  Observation getSourceObservation();

  /**
   * If the scope is focused on the source and target of a relationship, return the target
   * observation.
   *
   * @return
   */
  Observation getTargetObservation();

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
   * Each scope manages a digital twin. At client side or on slave servers this may be null orkn
   * limited in functionality.
   *
   * @return
   */
  DigitalTwin getDigitalTwin();

  /**
   * Pass a connected ContextScope (possibly the result of {@link #connect(URL)} or {@link
   * #connect(DigitalTwin.Configuration)}) and return a new ContextScope that merges this
   * ContextScope with the passed one. Permissions must align with the request.
   *
   * @param remoteContext
   * @return
   */
  ContextScope connect(ContextScope remoteContext);

  /**
   * Define an observation for inclusion into the knowledge graph at the point implied by the
   * current scope. The resulting builder should normally be finished by calling {@link
   * Observation.Builder#submit()} which starts resolution and/or validation and returning a future
   * for the resolved observation or for an {@link Observation#isEmpty() empty} one with
   * notifications in case of failure.
   *
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException if the observable
   *     is incompatible with the scope on which this is called: dependents must have a context
   *     observation set, relationships must have both source and target set. For substantials, any
   *     context set in the scope is ignored.
   * @param observable
   * @return a builder for the observation to be submitted.
   */
  Observation.Builder observation(Observable observable);

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

  /**
   * Return the parent observation of the passed observation. Synonym for a longer * <code>
   * getChildrenOf(RuntimeAsset...)</code> call.
   *
   * @param asset
   * @return the parent, or null if root subject
   */
  RuntimeAsset getParentOf(RuntimeAsset asset);

  /**
   * Return all children of the passed observation, using the logical structure (i.e. skipping
   * observation groups). The runtime context maintains the structure graph. Synonym for a longer
   * <code>getChildrenOf(RuntimeAsset...)</code> call.
   *
   * @param asset an observation. Quality observations have no children but no error should be
   *     raised.
   * @return the parent, or an empty collection if no children
   */
  Collection<RuntimeAsset> getChildrenOf(RuntimeAsset asset);

  /**
   * Inspect the network graph of the current context, returning all <code>relationship</code>
   * observations that have the passed subject as source.
   *
   * @param asset a {@link Observation} object.
   * @return a {@link java.util.Collection} object.
   */
  Collection<RuntimeAsset> getOutgoingRelationshipsOf(RuntimeAsset asset);

  /**
   * Inspect the network graph of the current context, returning all <code>relationship</code>
   * observations that have the passed subject as target.
   *
   * @param asset a {@link Observation} object.
   * @return a {@link java.util.Collection} object.
   */
  Collection<RuntimeAsset> getIncomingRelationshipsOf(RuntimeAsset asset);

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
   * The scope can be created with an explicitly set sharding strategy, to override or limit
   * anything that was set by models or contextualizers, within the limits of the runtime's
   * configuration.
   *
   * @param observation passed to enable specific choices w.r.t. geometry or observable, not
   *     necessarily implemented.
   * @return the non-null default sharding strategy, with default values wherever the implementation
   *     is allowed to choose, or null if the scope permits any sharding strategy.
   */
  Data.ShardingStrategy getShardingStrategy(Observation observation);

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
   * The {@link DigitalTwin.Configuration} that this scope was created with. The configuration will
   * pre-exist to the {@link DigitalTwin} itself, so this is exposed at the scope level for
   * registration purposes.
   *
   * @return
   */
  DigitalTwin.Configuration getConfiguration();

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
   * If the scope reflects an ongoing transaction, return its ID. Otherwise, return null.
   *
   * @return
   */
  String getTransactionId();

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
