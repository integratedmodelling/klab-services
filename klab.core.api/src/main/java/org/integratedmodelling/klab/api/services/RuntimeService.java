package org.integratedmodelling.klab.api.services;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;

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
   * <p>They all share the common parameters <code>_target</code> and <code>_targetId</code> for the
   * target of the computation (main if not present). TODO parameterize the enums with their stated
   * parameters.
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
     * Contextualize directly from {@link
     * org.integratedmodelling.klab.api.knowledge.observation.Observation.ContextualizationData}
     * containing the valid ID and parameters of an embeddable adapter, creating a temporary
     * resource on the fly in the runtime. Reserved for submissions from federated clients that have
     * computed storage independently.
     */
    ADAPTER_RESOLVER("klab.core.adapter.resolver"),
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

  /**
   * Submit an unresolved observation to the digital twin for inclusion in the knowledge graph in
   * the given scope and start its resolution. The return value is a future for the resolved
   * observation, whose contextualization may cause other observations to be made. If the resolution
   * fails, the future completes with an empty observation. If resolution succeeds, the finished
   * observation will have its {@link Observation#getId()} set to a valid ID (>0) and a valid URN.
   *
   * <p>If the observation submitted is resolved (its ID is valid when submitted), the submission is
   * ignored and the completed future for the submitted observation is returned. An observation of a
   * substantial (endurant subject or agent) must have a valid URN specifying its unique identity,
   * including a namespace and an identifier separated by a colon, which will be prefixed with the
   * scope ID and a predefined catalog name to form the final URN when resolved. An observation of a
   * dependent is normally not given a URN upon submission; it will contain one after it's resolved.
   *
   * <p>The submit operation is transactional, i.e., a failed submission will leave the knowledge
   * graph unaltered. Note that observations of individual substantials, i.e. non-collective
   * subjects and agents, will complete successfully even if they cannot be "explained" by the
   * resolver, i.e., the ID/URN will be valid and the knowledge graph will contain the observation.
   * All other observations will complete exceptionally if no dataflow can be built for them, and
   * the knowledge graph will not contain the observation submitted after completion.
   *
   * <p>During submission, all activities generated will be sent to the scope and can be intercepted
   * for monitoring. Upon successful completion, all activities, plans and observations will also be
   * committed to the knowledge graph.
   *
   * @param observation the observation to submit. If a resolved observation (or one that resolves
   *     to one) is submitted, the result will be a completed future containing it.
   * @param scope the context scope in which to submit the observation. Must be compatible with the
   *     observation's semantics.
   * @return a future that completes with the resolved observation when resolution is complete. The
   *     observation returned in the future may be an empty one if resolution has failed. If
   *     successful, it will be the same object submitted, completed with its URN and ID, along with
   *     possible metadata.
   */
  CompletableFuture<Observation> submit(Observation observation, ContextScope scope);

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

    Set<CRUDOperation> getPermissions();

//    /**
//     * True if this runtime provides an AMQP broker service on {@link
//     * org.integratedmodelling.klab.api.services.runtime.Channel#LOCAL_BROKER_URL} for local
//     * messaging. This should only return true in a local service authenticated with a user
//     * certificate.
//     *
//     * @return
//     */
//    boolean isBroker();
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
  List<ContextInfo> getContextInfo(Scope scope);

  /**
   * Establishes a connection to a specific context within the designated session scope. The call
   * passes a configuration and gets back a context, which may have been reconstructed at the
   * service side from existing storage. The scope will later be registered with the services.
   *
   * @return the context scope that represents the connected context, or null
   */
  ContextScope connectContext(DigitalTwin.Configuration configuration, UserScope userScope);

  /**
   * Retrieve the configuration of the DT identified by scopeId and hosted within a specified
   * session scope.
   *
   * @param scopeId
   * @param scope
   * @return
   */
  DigitalTwin.Configuration getConfiguration(String scopeId, UserScope scope);

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

  /**
   * TODO this could simply be a KG query now that a Commit is a RuntimeAsset
   *
   * @param commitId
   * @param scope
   * @return
   */
  KnowledgeGraph.Commit getCommit(long commitId, ContextScope scope);

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
