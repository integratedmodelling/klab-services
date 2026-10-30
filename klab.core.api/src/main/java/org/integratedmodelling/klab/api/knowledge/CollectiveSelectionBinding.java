package org.integratedmodelling.klab.api.knowledge;

import java.util.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Finite native discovery, family attribution and conjunctive selection. This is a request
 * admission, never proof that discovery or classification has completed. */
public record CollectiveSelectionBinding(String requested, String nativeMembers,
    List<String> predicates, List<String> families, List<String> directives,
    String reasonerId, long revision, String support, Long observerId, String observerUrn, String scopeId,
    Context context, String observerClass, String semanticFingerprint, String semanticSessionId)
    implements java.io.Serializable {
  public CollectiveSelectionBinding(String requested, String nativeMembers, List<String> predicates,
      List<String> families, List<String> directives, String reasonerId, long revision, String support,
      Long observerId, String observerUrn, String scopeId, Context context, String observerClass,
      String semanticFingerprint) {
    this(requested, nativeMembers, predicates, families, directives, reasonerId, revision, support,
        observerId, observerUrn, scopeId, context, observerClass, semanticFingerprint, null);
  }
  /** Compatibility construction retains legacy status; never infer a certificate from capabilities. */
  public CollectiveSelectionBinding(String requested, String nativeMembers, List<String> predicates,
      List<String> families, List<String> directives, String reasonerId, long revision, String support,
      Long observerId, String observerUrn, String scopeId, Context context, String observerClass) {
    this(requested, nativeMembers, predicates, families, directives, reasonerId, revision, support,
        observerId, observerUrn, scopeId, context, observerClass, null);
  }
  public static final String DATA_KEY = "collectiveSelectionBinding";
  /** Precommit focus witness. Committed IDs are stable; provisional identities also need the
   * transient ID that Observation.forTransport preserves across service boundaries. */
  public record Context(long id, long transientId, String urn, String semantics, String support)
      implements java.io.Serializable {}
  public CollectiveSelectionBinding {
    if (semanticFingerprint != null && !SemanticContentFingerprint.valid(semanticFingerprint))
      throw new IllegalArgumentException("Invalid collective selection semantic fingerprint");
    predicates = List.copyOf(predicates); families = List.copyOf(families);
    directives = List.copyOf(directives);
  }

  public static CollectiveSelectionBinding capture(Observation request, ContextScope scope, Reasoner reasoner) {
    if (request == null || request.getObservable() == null || request.getGeometry() == null
        || request.getGeometry().isEmpty() || scope == null || scope.getId() == null
        || scope.getId().isBlank() || reasoner == null) return null;
    var focus = scope.getContextObservation();
    if (focus != null && (focus.isEmpty() || focus.getId() == Observation.QUERY_ID || focus.getId() == Observation.UNASSIGNED_ID
        || focus.getUrn() == null || focus.getObservable() == null || focus.getObservable().getSemantics() == null
        || focus.getGeometry() == null || focus.getGeometry().isEmpty())) return null;
    var selected = selectionSemantics(request.getObservable(), scope, reasoner);
    if (selected == null) return null;
    var capabilities = reasoner.capabilities(scope);
    if (!(capabilities instanceof Reasoner.Capabilities semantic)
        || semantic.getServiceId() == null || semantic.getKnowledgeRevision() < 0) return null;
    var observer = scope.getObserver();
    if (observer != null && !validObserver(observer)) return null;
    return new CollectiveSelectionBinding(selected.requested(), selected.nativeMembers(),
        selected.predicates(), selected.families(), selected.directives(),
        semantic.getServiceId(), semantic.getKnowledgeRevision(), Geometry.forTransport(request.getGeometry()).encode(),
        observer == null ? null : observer.getId(), observer == null ? null : observer.getUrn(), scope.getId(), context(scope),
        observerClass(observer), semantic.getSemanticContentFingerprint(), semantic.getSemanticSessionId());
  }

  /**
   * Recognize the finite semantic composition independently of support and scope admission. This
   * is never an execution grant: callers must still use capture/require to validate geometry,
   * focus, observer and semantic generation. Submission policy uses it only to reject a scoped
   * selection whose invalid/missing context must not become an unrestricted root request.
   */
  public static boolean hasSelectionSemantics(Observable target, ContextScope scope, Reasoner reasoner) {
    return selectionSemantics(target, scope, reasoner) != null;
  }

  private record SemanticSelection(String requested, String nativeMembers, List<String> predicates,
      List<String> families, List<String> directives) {}

  private static SemanticSelection selectionSemantics(Observable target, ContextScope scope, Reasoner reasoner) {
    if (target == null || target.getSemantics() == null || scope == null || reasoner == null) return null;
    if (target.getContextualization() != Contextualization.INSTANTIATION
        || !target.getSemantics().isCollective() || target.isAbstract() || target.is(SemanticType.ABSTRACT)
        || target.is(SemanticType.NOTHING) || target.isGeneric()
        || !(target.is(SemanticType.SUBJECT) || target.is(SemanticType.AGENT))
        || target.is(SemanticType.UNION) || target.is(SemanticType.INTERSECTION)
        || target.is(SemanticType.OCCURRENCE) || target.is(SemanticType.ANY)
        || target.is(SemanticType.ALL) || target.is(SemanticType.NONE)
        || target.getUnit() != null || target.getCurrency() != null || target.getRange() != null) return null;
    // HTTP and OWL restriction enumeration do not promise iteration order. The same semantic
    // request must capture the same finite family plan regardless of that transport detail.
    var predicates = new TreeMap<String,Concept>();
    for (var p : reasoner.directTraits(target)) predicates.put(p.getUrn(), p);
    for (var p : reasoner.directRoles(target)) predicates.put(p.getUrn(), p);
    if (predicates.isEmpty()) return null;
    var families = new LinkedHashMap<String,Concept>();
    for (var predicate : predicates.values()) {
      if (!predicate.is(SemanticType.PREDICATE) || predicate.isAbstract() || predicate.is(SemanticType.ABSTRACT)
          || predicate.is(SemanticType.ANY) || predicate.is(SemanticType.NOTHING)) return null;
      var family = reasoner.lexicalRoot(predicate);
      if (family == null || !family.is(SemanticType.PREDICATE)) return null;
      // The semantic builder rejects competing predicates from one family. Do not choose one.
      if (families.putIfAbsent(family.getUrn(), family) != null) return null;
    }
    var nativeMembers = target.builder(scope).without(predicates.values().toArray(Concept[]::new)).buildObservable();
    if (nativeMembers == null || nativeMembers.isAbstract() || nativeMembers.is(SemanticType.ABSTRACT)
        || nativeMembers.is(SemanticType.NOTHING) || nativeMembers.isGeneric()
        || !nativeMembers.getSemantics().isCollective()
        || Objects.equals(nativeMembers.getSemantics(), target.getSemantics())) return null;
    var directives = new ArrayList<String>();
    for (var family : families.values()) {
      var directive = family.isAbstract() || family.is(SemanticType.ABSTRACT)
          ? new ObservableBuildStrategy(family, scope).of(nativeMembers.asConcept()).buildObservable()
          : reasoner.resolveObservable("any " + family.getUrn() + " of " + nativeMembers.getSemantics().getUrn());
      if (directive == null || directive.getContextualization() != Contextualization.CLASSIFICATION) return null;
      directives.add(directive.getUrn());
    }
    return new SemanticSelection(target.getUrn(), nativeMembers.getUrn(),
        List.copyOf(predicates.keySet()), List.copyOf(families.keySet()), List.copyOf(directives));
  }

  private static String observerClass(Observation observer) {
    return observer == null || observer.getObservable() == null || observer.getObservable().getSemantics() == null
        ? null : observer.getObservable().getSemantics().getUrn();
  }

  private static boolean validObserver(Observation observer) {
    return observer.getId() > 0 && !observer.isEmpty() && observer.getUrn() != null && !observer.getUrn().isBlank()
        && observer.getObservable() != null && observer.getObservable().getSemantics() != null
        && observer.getObservable().getSemantics().is(SemanticType.AGENT)
        && !observer.getObservable().getSemantics().is(SemanticType.NOTHING)
        && observerClass(observer) != null && !observerClass(observer).isBlank();
  }

  public static Context context(ContextScope scope) {
    var focus = scope.getContextObservation();
    return focus == null ? null : new Context(focus.getId(), focus.getId() > 0 ? 0 : focus.getTransientId(),
        org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.logicalUrn(focus.getUrn()),
        focus.getObservable().getSemantics().getUrn(), Geometry.forTransport(focus.getGeometry()).encode());
  }

  /** Semantic snapshots are immutable within a resolution/execution attempt. */
  public void require(ContextScope scope, Observable target, Geometry geometry) {
    if (scope == null || scopeId == null || !Objects.equals(scopeId, scope.getId())
        || target == null || !Objects.equals(requested, target.getUrn())
        || geometry == null || !Objects.equals(support, Geometry.forTransport(geometry).encode())
        || predicates.isEmpty() || predicates.size() != families.size() || families.size() != directives.size()
        || new HashSet<>(families).size() != families.size() || reasonerId == null || revision < 0)
      throw new IllegalStateException("Changed collective selection request or support");
    var observer = scope.getObserver();
    if (observer != null && !validObserver(observer)
        || !Objects.equals(observerId, observer == null ? null : observer.getId())
        || !Objects.equals(observerUrn, observer == null ? null : observer.getUrn())
        || !Objects.equals(observerClass, observerClass(observer)))
      throw new IllegalStateException("Changed collective selection observer");
    if (!Objects.equals(context, context(scope)))
      throw new IllegalStateException("Changed collective selection native context");
    var capabilities = scope.getService(Reasoner.class).capabilities(scope);
    if (!(capabilities instanceof Reasoner.Capabilities semantic)
        || !Objects.equals(reasonerId, semantic.getServiceId()) || revision != semantic.getKnowledgeRevision()
        || !Objects.equals(semanticFingerprint, semantic.getSemanticContentFingerprint())
        || !Objects.equals(semanticSessionId, semantic.getSemanticSessionId()))
      throw new IllegalStateException("Changed collective selection semantic generation");
  }
}
