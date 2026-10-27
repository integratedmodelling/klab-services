package org.integratedmodelling.klab.api.services.resolver;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionConstraintImpl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One or more resolution constraints may be added to a {@link
 * org.integratedmodelling.klab.api.scope.ContextScope}, through the API, k.Actors or any other
 * observer that require customizing the resolution environment, such as modifying the accessibility
 * of specific models or resources. The purpose of a resolution constraint is to (de)prioritize a
 * candidate model when resolving an observation, based on several possible conditions, expressed as
 * the constraint's {@link Type}. The associated data are visible only to enable serialization but
 * are handled internally.
 *
 * <p>Resolution constraints include:
 *
 * <ol>
 *   <li>black/whitelisting models, projects, namespaces, resources or resource services, either
 *       relative to a specific observable or set thereof, or globally;
 *   <li>setting specified namespaces and/or projects as prioritary in resolution to alter the scope
 *       of resolution based on the lexical scope of a model being resolved
 *   <li>defining priority scenarios to use first as resolution sources;
 *   <li>forcing the use of a specified model for a specified observable;
 *   <li>communicating the current provenance scope including activity, agent and plan;
 *   <li>communicating externally set concrete predicates to expand an abstract predicate in an
 *       observable instead of observing the abstract predicate first.
 *   <li>communicating interactively defined parameters to substitute defaults for the
 *       contextualizers in the dataflow.
 * </ol>
 *
 * <p>Any resolution constraints are found in a {@link
 * org.integratedmodelling.klab.api.scope.ContextScope}, returned by its {@link
 * ContextScope#getResolutionConstraints()} method and accessed through the family of <code>
 * getConstraint[s]</code> methods.
 *
 * <p>Note: constraints with any null-valued payload will be ignored. TODO check if these should be
 * a signal to remove existing constraints of the same class.
 *
 * @author Ferd
 */
public interface ResolutionConstraint extends Serializable {

  /**
   * Defines type, behavior and intended payload class for the constraint. Each class MUST be
   * serializable to JSON without incident.
   */
  enum Type {
    Scenarios(String.class, false /* debatable */),
    Geometry(Geometry.class, false),
    /** Persistent ID of the active observer in this twin. */
    Observer(Long.class, false),
    /** Public submission selection by an existing Agent observation's canonical graph URN.
     * Resolved to Observer's scoped persistent ID without changing authored observable semantics.
     */
    ObserverUrn(String.class, false),
    /** Suppress automatic default observer preparation for a root submission; explicit selections remain valid. */
    DoNotCreateDefaultObserver(Boolean.class, false),
    /** Effective lexical namespace, replacing the inherited namespace. It also selects the
     * namespace ranking overrides for all candidates. Instantiated members inherit their
     * producing model's namespace when Runtime submits them for individual resolution.
     */
    ResolutionNamespace(String.class, false),
    ResolutionProject(String.class, false),
    /**
     * this is used when communicating a scope whose context observation is not in the remote
     * knowledge graph yet
     */
    UnresolvedContextObservation(Observation.class, false),
    /** Paired request-local endpoint snapshots when a between scope is not yet in the remote graph. */
    UnresolvedSourceObservation(Observation.class, false),
    UnresolvedTargetObservation(Observation.class, false),
    UsingModel(String.class, false),
    ConcretePredicates(Concept.class, true),
    /** The dataflow needs the passed additional observables produced by an adapter. */
    AdditionalObservables(Observable.class, false),
    /** Observed qualities sharing the current bearer, used to prefer joint process models when
     * resolving change. Does not override strategy tiers, admission, observer or coverage checks. */
    ChangeQualities(Observable.class, false),
    Whitelist(String.class, false),
    Blacklist(String.class, false),
    Parameters(Parameters.class, true),
    Provenance(org.integratedmodelling.klab.api.provenance.Provenance.Node.class, false),
    UseAsObserver(Void.class, false),
    /**
     * If this is present, the instance comes from a definition whose URN is reported. No such
     * instance can appear in the same scope more than once.
     */
    InstanceUrn(String.class, false);

    /** Class of intended data types, used for runtime validation */
    public final Class<?> dataClass;

    /**
     * If true, the constraint data will be added to any other pre-existing in the scope. If false,
     * any new data will override the previous.
     */
    public final boolean incremental;

    private Type(Class<?> dataClass, boolean incremental) {
      this.dataClass = dataClass;
      this.incremental = incremental;
    }
  }

  /**
   * Number of items of the intended type in the internal data. Shorthand for <code>get(..).size()
   * </code>, provided for fluency.
   *
   * @return
   */
  int size();

  /**
   * True if there is no payload or the payload contains null data. Checked before use, so that
   * adding nulls in algorithms can be handled properly.
   *
   * @return
   */
  boolean empty();

  Type getType();

  /**
   * The
   *
   * @param dataClass
   * @param <T>
   * @return
   */
  <T> List<T> payload(Class<T> dataClass);

  /**
   * Used internally to merge a constraint with a previous one and return a new constraint.
   *
   * @param constraint
   * @return
   */
  ResolutionConstraint merge(ResolutionConstraint constraint);

  static ResolutionConstraint of(Type type, Object... data) {
    return new ResolutionConstraintImpl(type, data);
  }

  static boolean isRelationshipSnapshot(Type type) {
    return type == Type.UnresolvedSourceObservation || type == Type.UnresolvedTargetObservation;
  }

  record RelationshipScope(Observation source, Observation target) {}

  /** Restore a paired planning scope, never registering observations or changing native assets. */
  static RelationshipScope unresolvedRelationshipScope(ContextScope scope, ResolutionConstraint... constraints) {
    Observation source = null, target = null;
    boolean sourceSeen = false, targetSeen = false;
    if (constraints == null) return null;
    for (var constraint : constraints) {
      if (constraint == null || !isRelationshipSnapshot(constraint.getType())) continue;
      if (constraint.empty() || constraint.size() != 1)
        throw new IllegalArgumentException("A relationship scope snapshot requires one endpoint per side");
      if (constraint.getType() == Type.UnresolvedSourceObservation) {
        if (sourceSeen) throw new IllegalArgumentException("Duplicate relationship source snapshot");
        sourceSeen = true; source = constraint.payload(Observation.class).getFirst();
      } else {
        if (targetSeen) throw new IllegalArgumentException("Duplicate relationship target snapshot");
        targetSeen = true; target = constraint.payload(Observation.class).getFirst();
      }
    }
    if (!sourceSeen && !targetSeen) return null;
    if (!sourceSeen || !targetSeen || source == null || target == null || source.getId() == target.getId())
      throw new IllegalArgumentException("A relationship scope requires paired, distinct endpoints");
    return new RelationshipScope(relationshipEndpoint(scope, source, scope.getSourceObservation()),
        relationshipEndpoint(scope, target, scope.getTargetObservation()));
  }

  private static Observation relationshipEndpoint(ContextScope scope, Observation snapshot, Observation selected) {
    long id = snapshot.getId();
    if (id == Observation.UNASSIGNED_ID || id == Observation.QUERY_ID)
      throw new IllegalArgumentException("Relationship scope endpoints need assigned instance IDs");
    if (selected != null && selected.getId() != id)
      throw new IllegalArgumentException("Relationship scope snapshot contradicts the selected endpoint");
    Observation endpoint = null;
    // An assigned positive ID can still be owned by an uncommitted parent transaction. Prefer
    // native transaction state, then the committed graph; request snapshots never override either.
    for (var transaction = scope.getCurrentTransaction(); transaction != null && endpoint == null;
        transaction = transaction.getParent()) {
      for (var asset : transaction.assets()) {
        if (asset instanceof Observation observation && observation.getId() == id) {
          endpoint = transaction.readObservation(observation); break;
        }
      }
    }
    if (endpoint == null && id > 0) {
      if (scope.getDigitalTwin() == null)
        throw new IllegalArgumentException("Positive endpoint IDs require a graph lookup");
      endpoint = scope.getDigitalTwin().getKnowledgeGraph().getAsset(id, scope, Observation.class);
    }
    if (endpoint == null) endpoint = snapshot;
    if (endpoint.getId() != id || endpoint.isEmpty() || endpoint.getObservable() == null
        || endpoint.getObservable().getSemantics() == null
        || endpoint.getObservable().getSemantics().isCollective()
        || endpoint.getObservable().getSemantics().is(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT)
        || endpoint.getObservable().isGeneric()
        || !org.integratedmodelling.klab.api.knowledge.SemanticType.isEnumerableSubstantial(
            endpoint.getObservable().getSemantics().getType())
        || endpoint.getGeometry() == null || endpoint.getGeometry().isEmpty() || endpoint.getGeometry().size() <= 0)
      throw new IllegalArgumentException("Relationship scope endpoints must be individual substantial snapshots with geometry");
    return endpoint;
  }

  static boolean has(ContextScope scope, ResolutionConstraint.Type constraintType) {
    for (var constraint : scope.getResolutionConstraints()) {
      if (constraint.getType() == constraintType) {
        return true;
      }
    }
    return false;
  }

  static <T> List<T> getPayload(
      ContextScope scope, ResolutionConstraint.Type constraintType, Class<T> payloadClass) {
    for (var constraint : scope.getResolutionConstraints()) {
      if (constraint.getType() == constraintType) {
        return constraint.payload(payloadClass);
      }
    }
    return List.of();
  }
}
