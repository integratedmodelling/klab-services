package org.integratedmodelling.klab.api.knowledge;

import java.util.Objects;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Request-local proof for a whole-support count/presence reduction, not a discovery certificate.
 * Completion and members must be read again at execution. No spatial/time broadcasting is permitted.
 */
public record CollectiveReductionBinding(Mode mode, SingularContextBinding context,
    String operand, String support, Long observerId, String observerUrn, String observerClass)
    implements java.io.Serializable {
  public static final String DATA_KEY = "collectiveReduction";
  public enum Mode { COUNT, PRESENCE }

  public static boolean scalarSupport(Geometry geometry) {
    return geometry != null && !geometry.isEmpty() && !geometry.isUniversal()
        && geometry.size() == 1 && !geometry.getDimensions().isEmpty() && geometry.getDimensions().stream()
            .noneMatch(d -> d.distributed() || d.isGeneric());
  }

  /** Portable proof of originally non-distributed support; Scale encoders may discard that flag. */
  public static Geometry wholeSupport(Geometry geometry) {
    if (!scalarSupport(geometry)) throw new IllegalArgumentException("Expected whole non-distributed support");
    var portable = org.integratedmodelling.klab.api.geometry.impl.GeometryImpl.makeGeometry(geometry.encode(), 0);
    for (var dimension : portable.getDimensions())
      ((org.integratedmodelling.klab.api.geometry.impl.GeometryImpl.DimensionImpl) dimension).setRegular(false);
    return Geometry.create(portable.encode());
  }

  public static CollectiveReductionBinding capture(Observation request, ContextScope scope,
      Reasoner reasoner) {
    if (request == null || scope == null || reasoner == null || request.getObservable() == null || !scalarSupport(request.getGeometry())
        || !SingularContextBinding.eligible(scope.getContextObservation())) return null;
    var target = request.getObservable();
    // Explicit perspective and unit mediation need their own derivation contract.
    if (target.isAbstract() || target.getObserverSemantics() != null || target.getUnit() != null
        || target.getCurrency() != null || target.getRange() != null) return null;
    var split = reasoner.splitOperators(target);
    if (split == null || split.getSecond().size() != 1 || split.getFirst() == null) return null;
    Mode mode = split.getSecond().getFirst() == SemanticType.NUMEROSITY ? Mode.COUNT
        : split.getSecond().getFirst() == SemanticType.PRESENCE ? Mode.PRESENCE : null;
    var base = split.getFirst();
    if (mode == null || target.getContextualization() != (mode == Mode.COUNT
        ? Contextualization.QUANTIFICATION : Contextualization.VERIFICATION) || base.isAbstract()
        || !(base.is(SemanticType.SUBJECT) || base.is(SemanticType.AGENT))) return null;
    var collective = reasoner.resolveObservable(base.collective().getUrn());
    if (collective == null || collective.isAbstract() || !collective.getSemantics().isCollective()) return null;
    var cap = reasoner.capabilities(scope);
    if (!(cap instanceof Reasoner.Capabilities semantic) || semantic.getKnowledgeRevision() < 0
        || semantic.getServiceId() == null) return null;
    var current = scope.getContextObservation();
    var observer = scope.getObserver();
    if (observer != null && (observer.getId() <= 0 || observer.getUrn() == null
        || observer.getObservable() == null || !observer.getObservable().is(SemanticType.AGENT))) return null;
    var binding = new CollectiveReductionBinding(mode, new SingularContextBinding(current.getId(),
        current.getUrn(), current.getObservable().getUrn(), Geometry.forTransport(current.getGeometry()).encode(),
        target.getUrn(), semantic.getServiceId(), semantic.getKnowledgeRevision()), collective.getUrn(),
        wholeSupport(request.getGeometry()).encode(), observer == null ? null : observer.getId(),
        observer == null ? null : observer.getUrn(), observer == null ? null : observer.getObservable().getUrn());
    binding.require(scope, target, request.getGeometry());
    return binding;
  }

  private boolean matchesSupport(Geometry geometry, ContextScope scope) {
    if (!scalarSupport(geometry)) return false;
    if (Objects.equals(support, Geometry.forTransport(geometry).encode())) return true;
    // Promotion encodes a PHYSICAL whole interval as T0 although its actual extent is not distributed.
    // Only a local Scale may use this equivalence; an authored portable T0 request stays ineligible.
    if (geometry instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Scale scale) {
      var authored = Geometry.create(support);
      return scalarSupport(authored) && Objects.equals(
          org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.create(authored, scope).encode(), scale.encode());
    }
    return false;
  }

  public void require(ContextScope scope, Observable target, Geometry geometry) {
    if (mode == null || context == null || operand == null || !matchesSupport(geometry, scope))
      throw new IllegalStateException("Unsupported or changed collective reduction support");
    context.requireContext(scope);
    if (target == null || target.isAbstract() || !target.is(SemanticType.QUALITY)
        || !Objects.equals(context.childObservable(), target.getUrn())
        || target.getContextualization() != (mode == Mode.COUNT ? Contextualization.QUANTIFICATION : Contextualization.VERIFICATION))
      throw new IllegalStateException("Collective reduction result semantics changed");
    var observer = scope.getObserver();
    if (!Objects.equals(observerId, observer == null ? null : observer.getId())
        || !Objects.equals(observerUrn, observer == null ? null : observer.getUrn())
        || !Objects.equals(observerClass, observer == null || observer.getObservable() == null ? null : observer.getObservable().getUrn())
        || target.getObserverSemantics() != null || target.getUnit() != null
        || target.getCurrency() != null || target.getRange() != null)
      throw new IllegalStateException("Collective reduction observer or unit contract changed");
    var reasoner = scope.getService(Reasoner.class);
    var split = reasoner.splitOperators(target);
    var expected = mode == Mode.COUNT ? SemanticType.NUMEROSITY : SemanticType.PRESENCE;
    if (split == null || split.getSecond().size() != 1 || split.getSecond().getFirst() != expected
        || split.getFirst() == null || split.getFirst().isAbstract()
        || !(split.getFirst().is(SemanticType.SUBJECT) || split.getFirst().is(SemanticType.AGENT))
        || !Objects.equals(operand, reasoner.resolveObservable(split.getFirst().collective().getUrn()).getUrn()))
      throw new IllegalStateException("Collective reduction operand proof changed");
  }
}
