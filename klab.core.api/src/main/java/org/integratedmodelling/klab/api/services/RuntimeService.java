package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Mutable;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * The runtime service holds the actual digital twins referred to by context scopes. Client scopes
 * will register themselves at creation to obtain the scope header ({@link
 * org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} that enables communication. Scopes
 * should unregister themselves after use.
 *
 * <p>All other main functions of the runtime service are exposed through the GraphQL endpoint that
 * gives access to each context.
 *
 * @author Ferd
 */
public interface RuntimeService extends KlabService {

  /**
   * The core functors for k.LAB dataflow supporting the primary k.IM constructs such as inline
   * expressions, lookup tables and the like. The runtime must support all of these.
   *
   * <p>Calls to these functions are created directly by the resolver when {@link Contextualizable}s
   * of different k.IM types and/or {@link
   * org.integratedmodelling.klab.api.knowledge.ObservationStrategy}es from the reasoner are
   * translated into dataflow actuators. Implementations are free to choose whether to implement
   * actual service calls or implement a more efficient compilation strategy for these functors.
   *
   * @author Ferd
   */
  enum CoreFunctor {

    // TODO store parameters and arguments for validation. At the moment it's just convention and
    //  documentation.
    /**
     * Resolve one or more URNs. Comes with a 'urns' parameter carrying a list of URNs for multiple
     * resource sets.
     */
    URN_RESOLVER("klab.core.urn.resolver"),
    /**
     * Contextualize a scalar or vector expression. Comes with an 'expression' parameter carrying
     * the k.IM KimExpression syntactic object for compilation and analysis.
     */
    EXPRESSION_RESOLVER("klab.core.expression.resolver"),
    /**
     * Contextualize a lookup table (or classification) within a scalar wrapper. Comes with variable
     * parameters pointing to a classification, a lookup table, the URN of the lookup table or an
     * "according to" external resource to be resolved. TODO formalize parameters and check usages.
     */
    LUT_RESOLVER("klab.core.lut.resolver"),
    /**
     * Contextualize a constant value. Comes with a 'value' parameter in case of POD values or with
     * a 'urn' parameter for complex values to be resolved through resources.
     */
    CONSTANT_RESOLVER("klab.core.constant.resolver"),
    /**
     * Defer the resolution of the observations produced by this contextualization. Comes with a
     * 'strategy' parameter containing the contextualized ObservationStrategy to use.
     */
    DEFER_RESOLUTION("klab.core.resolution.defer");

    private final String serviceCallUrn;
    private Map<String, Artifact.Type> arguments;

    CoreFunctor(String serviceCall) {
      this.serviceCallUrn = serviceCall;
    }

    public String getServiceCallName() {
      return serviceCallUrn;
    }

    public static CoreFunctor classify(ServiceCall serviceCall) {
      if (serviceCall.getUrn().startsWith("klab.core.")) {
        var filtered =
            Arrays.stream(values())
                .filter(v -> serviceCall.getUrn().equals(v.serviceCallUrn))
                .toList();
        return filtered.isEmpty() ? null : filtered.getFirst();
      }
      return null;
    }
  }

  default String getServiceName() {
    return "klab.runtime.service";
  }

  /**
   * Submit an observation to the digital twin for inclusion in the knowledge graph in the passed
   * scope and start its resolution. The return value is a future for the resolved observation,
   * whose resolution may cause other observations to be made. If resolution fails, the future will
   * complete exceptionally and the observation ID will be {@link Observation#UNASSIGNED_ID}, which
   * signals that the digital twin has rejected the observation (for example because one was already
   * present). The observation exists in the DT in an unresolved state until resolution has
   * finished.
   *
   * <p>The submit operation is transactional, and a failed submission will leave the knowledge
   * graph unaltered. Note that observations of non-collective subjects and agents will complete
   * successfully even if they cannot be "explained" by the resolver, i.e. the ID will be valid and
   * the knowledge graph will contain the observation, whose {@link Observation#isResolved()} will
   * return false. All other observations will complete exceptionally if no dataflow can be built
   * for them.
   *
   * @param observation
   * @param scope
   * @return
   */
  CompletableFuture<Observation> submit(@Mutable Observation observation, ContextScope scope);

  //  /**
  //   * The main function of the runtime. It will be invoked externally only when the dataflow is
  //   * externally supplied and fully resolved, like from a {@link
  //   * org.integratedmodelling.klab.api.knowledge.Resource}.
  //   *
  //   * @param dataflow
  //   * @param contextScope
  //   * @return
  //   * FIXME/CHECK should this be behind the API?
  //   */
  //  Observation runDataflow(Dataflow dataflow, Geometry geometry, ContextScope contextScope);

  //  /**
  //   * Submit the ID of a valid observation to invoke the resolver, build a dataflow and run it to
  //   * obtain the resolved observation. Pass the ID of an accepted observation obtained through
  // {@link
  //   * #submit(Observation, ContextScope)}. The two operations are used in {@link
  //   * ContextScope#observe(Observation)} to provide the full functionality with notification to
  // the
  //   * scope.
  //   *
  //   * @param id
  //   * @param scope
  //   * @return the ID of the task running in the runtime, which must be identical to the
  // observation
  //   *     URN and will be sent to the scope with the resolution result message.
  //   */
  //  CompletableFuture<Observation> resolve(long id, ContextScope scope);
//
//  /**
//   * Retrieve any assets from the knowledge graph in the digital twin matching a given class and
//   * some query objects.
//   *
//   * @param contextScope the scope for the request, which will determine the point in the knowledge
//   *     graph to start searching from
//   * @param assetClass the type of asset requested
//   * @param queryParameters any objects that will identify one or more assets of the passed type in
//   *     the passed scope, such as an observable, a string for a name or a geometry. All passed
//   *     objects will restrict the search.
//   * @param <T>
//   * @return
//   * @deprecated use the query system on the KG
//   */
//  <T extends RuntimeAsset> List<T> retrieveAssets(
//      ContextScope contextScope, Class<T> assetClass, Object... queryParameters);

  /**
   * Use the resources service and the plug-in system to handle a model proposal from the resolver.
   * The incoming request will propose to use resources, functions and the like; the runtime may
   * provide some of those natively or use the resources services to locate them and load them. If
   * the empty resource set is returned, it should contain informative notifications and the
   * resolver will look for a different strategy.
   *
   * <p>FIXME this should be internal and use a non-existing, generic ingest(ResourceSet)
   *
   * @param contextualizables
   * @param scope
   * @return
   */
  ResourceSet resolveContextualizables(
      List<Contextualizable> contextualizables, ContextScope scope);

  /**
   * All services publish capabilities and have a call to obtain them. Must list all the available
   * contextualizers and verbs, with associated costs, so that they can be checked before sending a
   * dataflow.
   *
   * @author Ferd
   */
  interface Capabilities extends ServiceCapabilities {
    Storage.Type getDefaultStorageType();
  }

  /**
   * Scope CAN be null for generic public capabilities.
   *
   * @param scope
   * @return
   */
  Capabilities capabilities(Scope scope);

  /**
   * Retrieve information for all the active sessions accessible to the passed scope. The info is
   * enough to recreate the same scopes at client side.
   *
   * @param scope any scope, which will define visibility. User scopes with admin role will obtain
   *     everything.
   * @return the list of sessions with their contexts
   */
  List<SessionInfo> getSessionInfo(Scope scope);

  /**
   * Release the passed session, releasing any context scopes created in it.
   *
   * @param scope
   * @return
   */
  boolean releaseSession(SessionScope scope);

  /**
   * Release the passed scope, deleting all data. Should
   *
   * @param scope
   * @return
   */
  boolean releaseContext(ContextScope scope);

  /**
   * Send a query to the knowledge graph identified by the passed scope and return the result.
   *
   * @param knowledgeGraphQuery
   * @param scope
   * @return
   * @param <T>
   */
  <T extends RuntimeAsset> List<T> queryKnowledgeGraph(
      KnowledgeGraph.Query<T> knowledgeGraphQuery, Scope scope);

  interface Admin {

    /**
     * If runtime exceptions have caused the building of test cases, retrieve them as a map of case
     * class->source code, with the option of deleting them after responding.
     *
     * @param scope if service scope, send all; otherwise send those pertaining to the scope
     * @param deleteExisting delete after sending
     * @return
     */
    Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting);
  }
}
