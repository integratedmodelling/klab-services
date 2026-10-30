package org.integratedmodelling.klab.api.services.runtime;

import java.util.Objects;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityRequest;

/** Restricted existing MemberPlan syntax lowered to the separate typed result operation.
 * It cannot be smuggled into a one-Observation dataflow as a scalar result.
 */
public final class MemberQualityPlan {
  private MemberQualityPlan() {}
  public static MemberQualityRequest lower(KimObservationStrategy strategy,ContextScope scope) {
    if(strategy==null || strategy.getRank()<1 || strategy.getPlan()==null)
      throw new IllegalArgumentException("Member-quality execution requires an explicit Tier-1-or-later member plan");
    var steps=strategy.getPlan().getSteps();
    if(steps.size()!=3 || !(steps.get(0) instanceof GraphProducer collection)
        || !(steps.get(1) instanceof MemberPlan members) || !(steps.get(2) instanceof PlanYield result)
        || collection.getMode()!=ProducerMode.RESOLVE || collection.getName()==null
        || collection.getContext()!=null || collection.getFallback()!=null || !collection.getInputs().isEmpty()
        || members.getVariable()==null || members.getVariable().isBlank() || members.getCollection()==null
        || !Objects.equals(collection.getName(),members.getCollection().getName()) || members.getName()==null
        || result.getGraph()==null || !Objects.equals(members.getName(),result.getGraph().getName())
        || members.getBody()==null)
      throw new IllegalArgumentException("Expected one resolved collective, one member loop and its collection yield");
    var body=members.getBody().getSteps();
    if(body.size()!=2 || !(body.get(0) instanceof GraphProducer child) || !(body.get(1) instanceof PlanYield yield)
        || child.getMode()!=ProducerMode.RESOLVE || child.getName()==null || child.getContext()==null
        || !Objects.equals("$"+members.getVariable(),child.getContext().getSpelling())
        || child.getFallback()!=null || !child.getInputs().isEmpty() || yield.getGraph()==null
        || !Objects.equals(child.getName(),yield.getGraph().getName()))
      throw new IllegalArgumentException("Expected one native quality resolve within the member and its yield");
    var reasoner=scope==null?null:scope.getService(Reasoner.class);
    if(reasoner==null || !SingularContextBinding.eligible(scope.getContextObservation()))
      throw new IllegalStateException("Member plan requires an existing visible invocation context");
    var collective=closed(collection.getTarget(),reasoner);var quality=closed(child.getTarget(),reasoner);
    if(!collective.getSemantics().isCollective() || collective.isAbstract()
        || !(collective.is(SemanticType.SUBJECT)||collective.is(SemanticType.AGENT))
        || quality.isAbstract() || !quality.is(SemanticType.QUALITY))
      throw new IllegalArgumentException("Member plan targets must be a closed collective and native quality");
    var cap=reasoner.capabilities(scope);
    if(!(cap instanceof Reasoner.Capabilities semantic) || semantic.getServiceId()==null || semantic.getKnowledgeRevision()<0)
      throw new IllegalStateException("Member plan semantic revision is unavailable");
    var context=scope.getContextObservation();
    return new MemberQualityRequest(new SingularContextBinding(context.getId(),context.getUrn(),
        context.getObservable().getUrn(),Geometry.forTransport(context.getGeometry()).encode(),quality.getUrn(),
        semantic.getServiceId(),semantic.getKnowledgeRevision(),semantic.getSemanticContentFingerprint(),semantic.getSemanticSessionId()),collective.getUrn(),quality.getUrn());
  }
  private static Observable closed(StrategyTarget target,Reasoner reasoner) {
    KimObservable source=target==null?null:target.getObservable();
    if(source==null && target!=null && target.getExpression() instanceof ClosedObservable closed)source=closed.getObservable();
    if(source==null || source.getUrn()==null || source.isOptional() || source.getUnit()!=null
        || source.getCurrency()!=null || source.getRange()!=null || source.getObserverSemantics()!=null
        || source.getValue()!=null || source.getDefaultValue()!=null || source.getModelReference()!=null
        || source.getAttributeIdentifier()!=null || !source.getValueOperators().isEmpty())
      throw new IllegalArgumentException("Member plan requires closed unmediated observable targets");
    var value=reasoner.resolveObservable(source.getUrn());
    if(value==null)throw new IllegalArgumentException("Unknown member plan target "+source.getUrn());
    return value;
  }
}
