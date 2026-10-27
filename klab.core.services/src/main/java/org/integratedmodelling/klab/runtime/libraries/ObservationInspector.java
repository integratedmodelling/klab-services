package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.integratedmodelling.klab.utilities.Utils;

/** Committed, read-only identity/semantic assertions; snapshots do not retain mutable observations. */
public final class ObservationInspector {
  private ObservationInspector() {}

  public record MemberState(String urn, String observable, String semantics, String geometry, List<String> attributions) {
    public MemberState { attributions = List.copyOf(attributions); }
  }

  public record MemberSnapshot(String context, long producer, long cohort,
      Map<Long, MemberState> members, Set<Long> classificationActivities) {
    public MemberSnapshot {
      members = Map.copyOf(members); classificationActivities = Set.copyOf(classificationActivities);
    }
  }

  private static KnowledgeGraph graph(ContextScope scope) {
    return Objects.requireNonNull(scope.getDigitalTwin(), "Missing digital twin").getKnowledgeGraph();
  }

  /**
   * Checks recorded selection, not the scope's current default. A null expected observer requires
   * explicit known-null provenance. Positive selections also require successful submission and
   * resolution of that exact Agent; this makes no claim about computational initialization.
   */
  public static boolean observerCheck(ContextScope scope, Observation observation, Observation expected) {
    if (observation == null || observation.getId() <= 0)
      throw new IllegalArgumentException("Expected a persisted observation");
    var graph = graph(scope);
    var committed = graph.getAsset(observation.getId(), scope, Observation.class);
    if (committed == null || committed.getId() != observation.getId() || committed.isEmpty()
        || observation.getObservable() == null || observation.getObservable().getSemantics() == null
        || committed.getObservable() == null
        || !observation.getObservable().getSemantics().equals(committed.getObservable().getSemantics())) return false;
    var observedCreators = graph.query(Activity.class, scope).target(committed)
        .along(GraphModel.Relationship.CREATED).run(scope);
    if (observedCreators.isEmpty()) return false;
    var observedCreatorIds = new HashSet<Long>();
    for (var creator : observedCreators)
      if (creator.getId() <= 0 || !observedCreatorIds.add(creator.getId())
          || creator.getOutcome() != Activity.Outcome.SUCCESS) return false;
    var selection = org.integratedmodelling.common.runtime.ObserverProvenance.recover(scope, committed);
    if (!selection.known()) return false;
    if (expected == null) return selection.observer() == null;
    var observable = expected.getObservable();
    if (expected.getId() <= 0 || observable == null || observable.getSemantics() == null
        || !observable.is(SemanticType.AGENT) || observable.is(SemanticType.ABSTRACT)
        || observable.getSemantics().isCollective())
      throw new IllegalArgumentException("Expected a persisted concrete singular Agent");
    var actual = selection.observer();
    var canonical = graph.getAsset(expected.getId(), scope, Observation.class);
    if (actual == null || actual.getId() != expected.getId() || canonical == null || canonical.isEmpty()
        || canonical.getObservable() == null || !canonical.getObservable().is(SemanticType.AGENT)
        || canonical.getObservable().is(SemanticType.ABSTRACT)
        || canonical.getObservable().getSemantics() == null
        || canonical.getObservable().getSemantics().isCollective()
        || !observable.getSemantics().equals(canonical.getObservable().getSemantics())
        || actual.getObservable() == null
        || !observable.getSemantics().equals(actual.getObservable().getSemantics())) return false;
    boolean submitted = false, resolved = false;
    var ids = new HashSet<Long>();
    for (var creator : graph.query(Activity.class, scope).target(canonical)
        .along(GraphModel.Relationship.CREATED).run(scope)) {
      if (creator.getId() <= 0 || !ids.add(creator.getId())
          || creator.getOutcome() != Activity.Outcome.SUCCESS) return false;
      submitted |= creator.getType() == Activity.Type.SUBMISSION;
      resolved |= creator.getType() == Activity.Type.RESOLUTION;
    }
    return submitted && resolved;
  }

  private static boolean is(Concept concept, Concept expected, Reasoner reasoner) {
    return concept != null && expected != null && (concept.equals(expected) || reasoner.is(concept, expected));
  }

  private static Concept concept(ContextScope scope, String definition) {
    var result = scope.getService(Reasoner.class).resolveConcept(definition);
    if (result == null) throw new IllegalArgumentException("Unresolved inspector concept: " + definition);
    return result;
  }

  private static Cohort cohort(ContextScope scope, Observation producer) {
    if (producer == null || producer.getId() <= 0 || producer.isEmpty()
        || !producer.getObservable().getSemantics().isCollective())
      throw new IllegalArgumentException("Expected a committed collective producer");
    var cohorts = graph(scope).query(Cohort.class, scope).source(producer)
        .along(GraphModel.Relationship.CONTRIBUTED_TO).run(scope);
    if (cohorts.size() != 1 || cohorts.getFirst().getId() <= 0)
      throw new IllegalStateException("Collective producer must contribute to exactly one committed cohort");
    return cohorts.getFirst();
  }

  private static List<Observation> members(ContextScope scope, Cohort cohort) {
    return graph(scope).query(Observation.class, scope).source(cohort)
        .along(GraphModel.Relationship.HAS_MEMBER).run(scope);
  }

  private static List<String> attributions(ContextScope scope, Observation member) {
    var result = new ArrayList<String>();
    for (var link : graph(scope).getLinks(member, GraphModel.Relationship.Direction.INCOMING,
        scope, GraphModel.Relationship.CLASSIFIED)) {
      var properties = new TreeMap<String, Object>(); properties.putAll(link.properties());
      result.add(link.source().getId() + ":" + Utils.Json.asString(properties));
    }
    Collections.sort(result); return List.copyOf(result);
  }

  public static MemberSnapshot snapshot(ContextScope scope, Observation producer) {
    var cohort = cohort(scope, producer);
    var states = new TreeMap<Long, MemberState>();
    for (var member : members(scope, cohort)) {
      if (member.getId() <= 0 || member.isEmpty() || member.getGeometry() == null
          || member.getGeometry().isEmpty() || member.getObservable().getSemantics().isCollective()
          || states.containsKey(member.getId()))
        throw new IllegalStateException("Invalid or duplicate committed cohort member");
      states.put(member.getId(), new MemberState(member.getUrn(), member.getObservable().getUrn(), member.getObservable().getSemantics().getUrn(),
          member.getGeometry().encode(), attributions(scope, member)));
    }
    if (states.isEmpty()) throw new IllegalStateException("A member snapshot must not be empty");
    var activities = new HashSet<Long>();
    for (var activity : graph(scope).query(Activity.class, scope).run(scope))
      if (activity.getType() == Activity.Type.CLASSIFICATION && activity.getId() > 0) activities.add(activity.getId());
    return new MemberSnapshot(scope.getId(), producer.getId(), cohort.getId(), states, activities);
  }

  private static MemberSnapshot current(ContextScope scope, MemberSnapshot before) {
    if (!scope.getId().equals(before.context())) throw new IllegalArgumentException("Snapshot belongs to another context");
    var producer = graph(scope).getAsset(before.producer(), scope, Observation.class);
    var result = snapshot(scope, producer);
    if (result.cohort() != before.cohort()) throw new IllegalStateException("Producer cohort changed");
    return result;
  }

  private static List<Concept> predicates(Concept semantics, Concept family, Reasoner reasoner) {
    var result = new ArrayList<Concept>();
    var direct = new ArrayList<>(reasoner.directTraits(semantics)); direct.addAll(reasoner.directRoles(semantics));
    for (var predicate : direct)
      if (!predicate.is(SemanticType.ABSTRACT) && is(predicate, family, reasoner)) result.add(predicate);
    return result;
  }

  private static boolean sameIdentity(MemberSnapshot before, MemberSnapshot after) {
    if (!before.members().keySet().equals(after.members().keySet())) return false;
    for (var entry : before.members().entrySet()) {
      var next = after.members().get(entry.getKey());
      if (!Objects.equals(entry.getValue().urn(), next.urn())
          || !entry.getValue().geometry().equals(next.geometry())) return false;
    }
    return true;
  }

  private static boolean hasAttribution(ContextScope scope, MemberSnapshot before, long id,
      MemberState old, MemberState next, Concept family, Reasoner reasoner, long auditId) {
    var member = graph(scope).getAsset(id, scope, Observation.class);
    if (member == null) return false;
    for (var edge : graph(scope).getLinks(member, GraphModel.Relationship.Direction.INCOMING,
        scope, GraphModel.Relationship.CLASSIFIED)) {
      if (edge.source().getId() != auditId || before.classificationActivities().contains(auditId) || edge.target().getId() != id
          || !(edge.source() instanceof Activity activity) || activity.getType() != Activity.Type.CLASSIFICATION
          || activity.getOutcome() != Activity.Outcome.SUCCESS) continue;
      var properties = edge.properties();
      if (!Objects.equals(properties.get("before"), old.observable())
          || !Objects.equals(properties.get("after"), next.observable())
          || !(properties.get("abstractPredicate") instanceof String requested)
          || !concept(scope, requested).equals(family)
          || !(properties.get("predicate") instanceof String result)) continue;
      if (predicates(concept(scope, next.semantics()), family, reasoner).stream()
          .anyMatch(predicate -> predicate.equals(concept(scope, result)))) return true;
    }
    return false;
  }

  /** Require an actual committed classification of existing identities, including typed provenance. */
  public static boolean classificationCheck(ContextScope scope, MemberSnapshot before, String familyName) {
    return classification(scope, before, familyName, false);
  }

  /** Require successful EXISTING-only audit evidence, not merely absence of state changes. */
  public static boolean classificationNoop(ContextScope scope, MemberSnapshot before, String familyName) {
    return classification(scope, before, familyName, true);
  }

  private static boolean classification(ContextScope scope, MemberSnapshot before, String familyName, boolean noop) {
    var after = current(scope, before);
    if (!sameIdentity(before, after) || noop && !before.members().equals(after.members())) return false;
    var reasoner = scope.getService(Reasoner.class); var family = concept(scope, familyName);
    var changed = new HashSet<Long>();
    for (var entry : before.members().entrySet()) {
      var old = concept(scope, entry.getValue().semantics());
      var next = concept(scope, after.members().get(entry.getKey()).semantics());
      if (predicates(next, family, reasoner).isEmpty()) return false;
      if (predicates(old, family, reasoner).isEmpty()) {
        changed.add(entry.getKey());
        if (old.equals(next) || after.members().get(entry.getKey()).attributions().equals(entry.getValue().attributions()))
          return false;
      }
    }
    if (!noop && changed.isEmpty()) return false; // A wholly preclassified fixture cannot prove mutation.
    int successfulAudits = 0;
    for (var activity : graph(scope).query(Activity.class, scope).run(scope)) {
      if (activity.getId() <= 0 || before.classificationActivities().contains(activity.getId()) || activity.getType() != Activity.Type.CLASSIFICATION
          || activity.getOutcome() != Activity.Outcome.SUCCESS) continue;
      var raw = activity.getMetadata().get(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS);
      if (raw == null) continue;
      // Metadata can expose a Parameters wrapper implementing Map. A JSON round trip through
      // Map.class would expose the wrapper's delegate bean instead of its logical entries.
      var audit = raw instanceof Map<?, ?> map ? map : Utils.Json.parseObject(raw.toString(), Map.class);
      if (!before.context().equals(audit.get("context")) || !(audit.get("memberResults") instanceof List<?> rows)
          || rows.size() != before.members().size()) continue;
      var seen = new HashSet<Long>(); boolean valid = true;
      for (var row : rows) {
        if (!(row instanceof Map<?, ?> item) || !(item.get("memberIdAtInvocation") instanceof Number id)
            || !(id instanceof Byte || id instanceof Short || id instanceof Integer || id instanceof Long)
            || id.longValue() <= 0
            || !before.members().containsKey(id.longValue()) || !seen.add(id.longValue())) { valid = false; break; }
        var old = before.members().get(id.longValue());
        if (!Objects.equals(item.get("inputObservable"), old.observable())
            || !(item.get("requestedPredicate") instanceof String requested)
            || !concept(scope, requested).equals(family)) { valid = false; break; }
        boolean existing = !predicates(concept(scope, old.semantics()), family, reasoner).isEmpty();
        if (!Objects.equals(item.get("origin"), existing ? "EXISTING" : "CLASSIFIER")
            || noop && !existing || !(item.get("concretePredicates") instanceof List<?> values) || values.isEmpty()) {
          valid = false; break;
        }
        var actual = predicates(concept(scope, after.members().get(id.longValue()).semantics()), family, reasoner);
        for (var value : values)
          if (!(value instanceof String name) || actual.stream().noneMatch(p -> p.equals(concept(scope, name))))
            valid = false;
        if (!valid) break;
      }
      if (valid && seen.equals(before.members().keySet()) && changed.stream().allMatch(id ->
          hasAttribution(scope, before, id, before.members().get(id), after.members().get(id), family, reasoner, activity.getId())))
        successfulAudits++;
    }
    return successfulAudits == 1;
  }

  /** Check the actual directed/bond axes, endpoint meanings and durable participant edges. */
  private static boolean relationshipFailure(ContextScope scope, Observation producer, String invariant) {
    scope.warn("Relationship inspector rejected producer " + producer.getId() + ": " + invariant);
    return false;
  }

  public static boolean relationshipsCheck(ContextScope scope, Observation producer, String relationshipName) {
    var expected = concept(scope, relationshipName); var reasoner = scope.getService(Reasoner.class);
    var expectedAxes = RelationshipAxes.of(expected.getType()); expectedAxes.requireComplete(relationshipName);
    if (!is(producer.getObservable().getSemantics().singular(), expected, reasoner)) return relationshipFailure(scope, producer, "producer semantics");
    var sourceType = reasoner.relationshipSource(expected); var targetType = reasoner.relationshipTarget(expected);
    if (sourceType == null || targetType == null) throw new IllegalStateException("Relationship endpoint meaning unavailable");
    var relationships = members(scope, cohort(scope, producer));
    if (relationships.isEmpty()) return relationshipFailure(scope, producer, "empty relationship member cohort");
    var pairs = new HashSet<List<Long>>(); var identities = new HashSet<Long>();
    for (var relationship : relationships) {
      var semantics = relationship.getObservable().getSemantics();
      if (!identities.add(relationship.getId()) || relationship.getId() <= 0 || semantics.isCollective()
          || !is(semantics, expected, reasoner) || !RelationshipAxes.of(semantics.getType()).equals(expectedAxes)
          || relationship.getGeometry() == null || relationship.getGeometry().isEmpty()) return relationshipFailure(scope, producer, "member identity, semantics, axes or geometry");
      var source = new ArrayList<Observation>(); var target = new ArrayList<Observation>();
      var bond = new ArrayList<Observation>();
      for (var edge : graph(scope).getLinks(relationship, GraphModel.Relationship.Direction.OUTGOING, scope,
          GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,
          GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT)) {
        if (!(edge.target() instanceof Observation reference) || reference.getId() <= 0) return relationshipFailure(scope, producer, "invalid participant reference");
        // Participant edges may carry lightweight or stale references. Assert current committed
        // meaning/geometry by identity, rather than trusting fields embedded in a link response.
        var endpoint = graph(scope).getAsset(reference.getId(), scope, Observation.class);
        if (endpoint == null || endpoint.getId() != reference.getId()) return relationshipFailure(scope, producer, "missing committed endpoint");
        switch (edge.type()) {
          case HAS_RELATIONSHIP_SOURCE -> source.add(endpoint);
          case HAS_RELATIONSHIP_TARGET -> target.add(endpoint);
          case HAS_RELATIONSHIP_PARTICIPANT -> bond.add(endpoint);
          default -> { return relationshipFailure(scope, producer, "unexpected participant edge"); }
        }
      }
      if (expectedAxes.orientation() == RelationshipAxes.Orientation.DIRECTED) {
        if (source.size() != 1 || target.size() != 1 || !bond.isEmpty()) return relationshipFailure(scope, producer, "directed participant cardinality");
      } else {
        if (!source.isEmpty() || !target.isEmpty() || bond.size() != 2) return relationshipFailure(scope, producer, "bond participant cardinality");
        // A bond has no participant order. Assign endpoint roles by meaning, never numeric IDs.
        var first = bond.getFirst(); var second = bond.getLast();
        if (is(first.getObservable().getSemantics(), sourceType, reasoner)
            && is(second.getObservable().getSemantics(), targetType, reasoner)) {
          source.add(first); target.add(second);
        } else if (is(second.getObservable().getSemantics(), sourceType, reasoner)
            && is(first.getObservable().getSemantics(), targetType, reasoner)) {
          source.add(second); target.add(first);
        } else return relationshipFailure(scope, producer, "bond endpoint meanings");
      }
      var from = source.getFirst(); var to = target.getFirst();
      var pair = expectedAxes.orientation() == RelationshipAxes.Orientation.BOND
          ? List.of(Math.min(from.getId(), to.getId()), Math.max(from.getId(), to.getId()))
          : List.of(from.getId(), to.getId());
      if (from.getId() == to.getId() || !pairs.add(pair)
          || !is(from.getObservable().getSemantics(), sourceType, reasoner)
          || !is(to.getObservable().getSemantics(), targetType, reasoner)) return relationshipFailure(scope, producer, "self-link, duplicate pair or endpoint meaning");
      var line = Scale.create(relationship.getGeometry()).getSpace();
      var left = Scale.create(from.getGeometry()).getSpace(); var right = Scale.create(to.getGeometry()).getSpace();
      if (line == null || left == null || right == null || line.isEmpty() || left.isEmpty() || right.isEmpty()) return relationshipFailure(scope, producer, "missing spatial geometry");
      var geometry = ShapeImpl.promote(line.getGeometricShape());
      var leftShape = ShapeImpl.promote(left.getGeometricShape()).transform(geometry.getProjection()).getJTSGeometry();
      var rightShape = ShapeImpl.promote(right.getGeometricShape()).transform(geometry.getProjection()).getJTSGeometry();
      if (!(leftShape instanceof org.locationtech.jts.geom.Point leftPoint)
          || !(rightShape instanceof org.locationtech.jts.geom.Point rightPoint))
        throw new UnsupportedOperationException("Relationship geometry assertion currently requires single-point endpoints");
      if (!(geometry.getJTSGeometry() instanceof org.locationtech.jts.geom.LineString connection)
          || connection.getNumPoints() != 2) return relationshipFailure(scope, producer, "two-point LineString geometry");
      var first = connection.getCoordinateN(0); var last = connection.getCoordinateN(1);
      var a = leftPoint.getCoordinate(); var b = rightPoint.getCoordinate();
      if (!Double.isFinite(first.x) || !Double.isFinite(first.y) || !Double.isFinite(last.x) || !Double.isFinite(last.y)
          || !Double.isFinite(a.x) || !Double.isFinite(a.y) || !Double.isFinite(b.x) || !Double.isFinite(b.y)) return relationshipFailure(scope, producer, "nonfinite endpoint coordinates");
      // ShapeImpl.canonicalGeometryForEncoding() uses JTS norm(), which may reverse a line.
      // Spatial support is orientation-neutral; directed roles come from the typed graph links
      // validated above. Coordinates must still match exactly, without a widened tolerance.
      boolean forward = first.equals2D(a) && last.equals2D(b);
      boolean reverse = first.equals2D(b) && last.equals2D(a);
      if (!forward && !reverse) return relationshipFailure(scope, producer, "line endpoints differ from participant points");
    }
    return true;
  }
}
