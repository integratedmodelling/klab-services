package org.integratedmodelling.klab.runtime.language;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;

/** Axis construction shared by source registration and direct semantic declaration. */
public final class RelationshipDeclarationSupport {
  private RelationshipDeclarationSupport() {}
  public static RelationshipAxes authored(KimConceptStatement statement) {
    return new RelationshipAxes(statement.getDeclaredRelationshipNature(),statement.getDeclaredRelationshipOrientation());
  }
  public static RelationshipAxes inherited(KimConcept expression,Function<KimConcept,Set<SemanticType>> named) {
    if(expression==null)return RelationshipAxes.UNSPECIFIED;
    var terms=new ArrayList<RelationshipAxes>();
    if(expression.getName()!=null)terms.add(RelationshipAxes.of(named.apply(expression)));
    else if(expression.getObservable()!=null)terms.add(inherited(expression.getObservable(),named));
    for(var operand:expression.getOperands())terms.add(inherited(operand,named));
    if(terms.isEmpty())return RelationshipAxes.UNSPECIFIED;
    var result=terms.getFirst();
    for(int i=1;i<terms.size();i++) result=expression.getExpressionType()==KimConcept.Expression.UNION
        ? result.common(terms.get(i)):result.merge(terms.get(i));
    return result;
  }
  public static RelationshipAxes resolve(KimConceptStatement statement,RelationshipAxes parents,boolean complete) {
    var own=authored(statement);
    if(statement.isAlias() && (own.nature()!=RelationshipAxes.Nature.UNSPECIFIED && own.nature()!=parents.nature()
        || own.orientation()!=RelationshipAxes.Orientation.UNSPECIFIED && own.orientation()!=parents.orientation()))
      throw new IllegalArgumentException("RELATIONSHIP_ALIAS_AXIS_CONFLICT: alias cannot refine its canonical target");
    var result=parents.merge(own);
    if(complete && !statement.isAbstract() && !statement.isAlias())result.requireComplete(statement.getUrn());
    return result;
  }
}
