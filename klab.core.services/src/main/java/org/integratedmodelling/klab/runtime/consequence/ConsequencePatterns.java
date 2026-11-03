package org.integratedmodelling.klab.runtime.consequence;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;

/** Shared structural checks for the accepted positive consequence-pattern contract. */
public final class ConsequencePatterns {
  private ConsequencePatterns() {}
  /** Preserve Boolean scope: a first operand is stored on the head, with other operands alongside. */
  public static List<KimConcept> positiveLeaves(KimConcept syntax) {
    syntax=unwrapped(syntax);
    if(syntax==null||syntax.isNegated()||syntax.is(SemanticType.NONE)||syntax.is(SemanticType.ANY)||syntax.isPattern())
      throw new IllegalArgumentException("Negative, absent or wildcard emergence requires an explicit contract");
    if(syntax.getParent()!=null)throw new IllegalArgumentException("Parent-qualified emergence requires an explicit evaluator");
    if(!syntax.getValueOperators().isEmpty())throw new IllegalArgumentException("Emergence value conditions require an explicit evaluator");
    if(syntax.getDescriptionType()!=null&&syntax.getDescriptionType()!=Contextualization.forSemantics(syntax))
      throw new IllegalArgumentException("Overridden emergence contextualization requires an explicit evaluator");
    if(syntax.getOperands().isEmpty())return List.of(syntax);
    if(syntax.getSemanticModifier()!=null||syntax.getExpressionType()==KimConcept.Expression.SINGLETON)
      throw new IllegalArgumentException("Unsupported transformed Boolean emergence");
    if(!(syntax instanceof KimConceptImpl implementation))throw new IllegalArgumentException("Unsupported Boolean syntax implementation");
    var first=(KimConceptImpl)implementation.removeComponents();first.setOperands(new ArrayList<>());first.setExpressionType(KimConcept.Expression.SINGLETON);
    first.getType().remove(SemanticType.UNION);first.getType().remove(SemanticType.INTERSECTION);first.resetDefinition();
    var leaves=new ArrayList<KimConcept>();leaves.addAll(positiveLeaves(first));
    for(var operand:syntax.getOperands())leaves.addAll(positiveLeaves(operand));return List.copyOf(leaves);
  }
  public static KimConcept unwrapped(KimConcept syntax) {
    while(syntax!=null&&syntax.getObservable()!=null&&syntax.getOperands().isEmpty()
        &&syntax.getSemanticModifier()==null&&!syntax.isNegated()&&!syntax.isCollective()
        &&!syntax.isPattern()&&syntax.getValueOperators().isEmpty()
        &&(syntax.getDescriptionType()==null||syntax.getDescriptionType()==Contextualization.forSemantics(syntax))
        &&syntax.getParent()==null&&syntax.getInherent()==null&&syntax.getGoal()==null&&syntax.getCausant()==null&&syntax.getCaused()==null
        &&syntax.getCompresent()==null&&syntax.getComparisonConcept()==null&&syntax.getCooccurrent()==null&&syntax.getAdjacent()==null
        &&syntax.getRelationshipSource()==null&&syntax.getRelationshipTarget()==null
        &&syntax.getAuthority()==null&&syntax.getAuthorityTerm()==null
        &&syntax.getTraits().isEmpty()&&syntax.getRoles().isEmpty()&&syntax.getModifiers().isEmpty()&&syntax.getAbout().isEmpty())
      syntax=syntax.getObservable();
    return syntax;
  }
}
