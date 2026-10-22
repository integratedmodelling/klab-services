package org.integratedmodelling.klab.api.knowledge;

import java.util.Objects;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Request-local native-context witness, never a discovery, coverage or reuse certificate. */
public record SingularContextBinding(long contextId, String contextUrn, String contextObservable,
    String contextSupport, String childObservable, String reasonerId, long knowledgeRevision) implements java.io.Serializable {

  public static boolean eligible(Observation context) {
    return context != null && context.getId() > 0 && !context.isEmpty()
        && context.getUrn() != null && !context.getUrn().isBlank() && context.getObservable() != null
        && context.getObservable().getSemantics() != null
        && !context.getObservable().getSemantics().isCollective()
        && (context.getObservable().is(SemanticType.SUBJECT)
            || context.getObservable().is(SemanticType.AGENT))
        && context.getGeometry() != null && !context.getGeometry().isEmpty()
        && context.getEventTimestamps() != null && !context.getEventTimestamps().isEmpty();
  }

  /** Fail closed before any lookup, link or execution against a different context/revision. */
  public Observation require(ContextScope scope, Observable target) {
    if (scope == null || scope.isInterrupted()) throw new IllegalStateException("Interrupted context binding");
    if (scope.getCurrentTransaction() != null && scope.getCurrentTransaction().isFailed())
      throw new IllegalStateException("Failed singular binding attempt");
    var current = scope.getContextObservation();
    if (!eligible(current) || current.getId() != contextId
        || !Objects.equals(contextUrn, current.getUrn())
        || !Objects.equals(contextObservable, current.getObservable().getUrn())
        || !Objects.equals(contextSupport, Geometry.forTransport(current.getGeometry()).encode())
        || target == null || !target.is(SemanticType.QUALITY)
        || target.getContextualization() != Contextualization.QUANTIFICATION
        || !Objects.equals(childObservable, target.getUrn()))
      throw new IllegalStateException("Stale or incompatible singular context binding");
    var actual = scope.getObservation(contextId);
    if (!eligible(actual) || actual.getId() != contextId
        || !Objects.equals(contextUrn, actual.getUrn())
        || !Objects.equals(contextObservable, actual.getObservable().getUrn())
        || !Objects.equals(contextSupport, Geometry.forTransport(actual.getGeometry()).encode()))
      throw new IllegalStateException("Inaccessible singular context binding");
    var reasoner = scope.getService(Reasoner.class);
    var capabilities = reasoner == null ? null : reasoner.capabilities(scope);
    if (!(capabilities instanceof Reasoner.Capabilities semantic)
        || reasonerId == null || !Objects.equals(reasonerId, semantic.getServiceId())
        || knowledgeRevision < 0 || knowledgeRevision != semantic.getKnowledgeRevision())
      throw new IllegalStateException("Singular binding semantic revision is unavailable or changed");
    return actual;
  }
}
