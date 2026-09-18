package org.integratedmodelling.klab.services.runtime;

import java.util.List;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Participant validation and graph representation shared by direct and collective submission. */
final class ConnectionSupport {
  private ConnectionSupport() {}

  static boolean isIndividual(Observation observation) {
    return observation.getObservable().is(SemanticType.RELATIONSHIP)
        && !observation.getObservable().getSemantics().isCollective();
  }

  static List<Observation> participants(Observation relationship, ContextScope scope) {
    if (!isIndividual(relationship))
      throw new IllegalArgumentException("A connector must emit individual relationship observations");
    var supplied = relationship.getParticipants();
    if (supplied.isEmpty() && scope.getSourceObservation() != null && scope.getTargetObservation() != null)
      supplied = List.of(scope.getSourceObservation(), scope.getTargetObservation());
    if (supplied.size() != 2)
      throw new IllegalArgumentException("A relationship or bond requires exactly two participants");
    var source = participant(supplied.get(0), scope);
    var target = participant(supplied.get(1), scope);
    if (source.getId() == target.getId())
      throw new IllegalArgumentException("A relationship requires two distinct participants");
    var reasoner = scope.getService(Reasoner.class);
    var sourceType = reasoner.relationshipSource(relationship.getObservable());
    var targetType = reasoner.relationshipTarget(relationship.getObservable());
    boolean forward = matches(source, sourceType, reasoner) && matches(target, targetType, reasoner);
    boolean reverse = relationship.getObservable().is(SemanticType.BIDIRECTIONAL)
        && matches(target, sourceType, reasoner) && matches(source, targetType, reasoner);
    if (!forward && !reverse)
      throw new IllegalArgumentException("Relationship participants do not match the declared endpoint types");
    var result = List.of(source, target);
    if (relationship instanceof ObservationImpl impl) impl.setParticipants(result);
    return result;
  }

  private static boolean matches(Observation participant,
      org.integratedmodelling.klab.api.knowledge.Concept type, Reasoner reasoner) {
    return type == null || reasoner.is(participant.getObservable(), type.singular());
  }

  private static Observation participant(Observation supplied, ContextScope scope) {
    var participant = supplied == null ? null : scope.getObservation(supplied.getId());
    if (participant == null || participant.isEmpty()
        || participant.getObservable().getSemantics().isCollective()
        || !SemanticType.isEnumerableSubstantial(participant.getObservable().getSemantics().getType()))
      throw new IllegalArgumentException("Relationship participants must be individual substantials in this context");
    return participant;
  }

  static void link(Observation relationship, ContextScope scope) {
    var endpoints = participants(relationship, scope);
    var transaction = scope.getCurrentTransaction();
    if (relationship.getObservable().is(SemanticType.BIDIRECTIONAL)) {
      for (var endpoint : endpoints)
        transaction.link(relationship, endpoint, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT);
    } else {
      transaction.link(relationship, endpoints.get(0), GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE);
      transaction.link(relationship, endpoints.get(1), GraphModel.Relationship.HAS_RELATIONSHIP_TARGET);
    }
  }

  static void checkIdentity(Observation existing, Observation requested, ContextScope scope) {
    if (!isIndividual(requested) || existing == requested) return;
    var expected = participants(requested, scope).stream().map(Observation::getId).toList();
    var actual = scope.getRelationshipParticipants(existing).stream().map(Observation::getId).toList();
    boolean same = requested.getObservable().is(SemanticType.BIDIRECTIONAL)
        ? actual.size() == 2 && new java.util.HashSet<>(actual).equals(new java.util.HashSet<>(expected))
        : actual.equals(expected);
    if (!same)
      throw new IllegalArgumentException("Relationship identity is already bound to different participants");
  }
}
