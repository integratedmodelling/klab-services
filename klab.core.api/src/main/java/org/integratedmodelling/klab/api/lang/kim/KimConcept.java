package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.SemanticClause;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * A KimConcept is the declaration of a concept, i.e. a semantic expression built out of known
 * concepts and conforming to k.IM semantic constraints. Concept expressions compile to this
 * structure, which retains the final concept names only as fully qualified names. External
 * infrastructure can create the actual concepts that a reasoner can operate on.
 *
 * @author ferdinando.villa
 */
public interface KimConcept extends KlabStatement {

  enum Expression {
    SINGLETON,
    UNION,
    INTERSECTION
  }

  /**
   * A simple data structure containing the essential info about a basic concept that can be output
   * by any resource service that distributes or serves the worldview.
   *
   * @param namespace
   * @param conceptName
   * @param mainDeclaredType
   * @param label
   * @param description
   * @param isAbstract
   */
  public record Descriptor(
      String namespace,
      String conceptName,
      SemanticType mainDeclaredType,
      String label,
      String description,
      boolean isAbstract) {
    @Override
    public String toString() {
      return (isAbstract ? "abstract " : "")
          + mainDeclaredType.name().toLowerCase()
          + " "
          + namespace
          + ":"
          + conceptName;
    }
  }

  /**
   * A leaf declaration contains a name (e.g. 'elevation:Geography'); all others do not. When the
   * name is not null, there still may be a negation or a semantic operator.
   *
   * @return the concept name or null.
   */
  String getName();

  /**
   * The main observable, which must be unique. This is null in a leaf declaration, where {@link
   * #getName()} returns a non-null value.
   *
   * @return the main observable
   */
  KimConcept getObservable();

  /**
   * The type contains all declared attributes for the concept. An empty type denotes an
   * inconsistent concept. The k.IM validator ensures that any non-empty types are internally
   * consistent.
   *
   * @return the set of types
   */
  Set<SemanticType> getType();

  KimConcept getInherent();

  KimConcept getGoal();

  KimConcept getCausant();

  KimConcept getCaused();

  KimConcept getCompresent();

  KimConcept getComparisonConcept();

  String getAuthorityTerm();

  String getAuthority();

  UnarySemanticOperator getSemanticModifier();

  KimConcept getRelationshipSource();

  KimConcept getRelationshipTarget();

  List<KimConcept> getTraits();

  List<KimConcept> getRoles();

  boolean isNegated();

  boolean is(SemanticType type);

  /**
   * Add or set the unary operator, returning a new concept
   *
   * @param operator
   * @param operand
   * @param comparisonConcept
   * @return
   */
  KimConcept addOperator(
      UnarySemanticOperator operator, KimConcept operand, KimConcept comparisonConcept);

  List<Pair<SemanticRole, KimConcept>> getModifiers();

  /**
   * Collective resolution/perspective linked to the <code>each</code> keyword in the expression.
   * This determines the perspective in resolution: if a countable, resolution is instantiation; if
   * an inherency for a quality or predicate, the resolution happens "all at once".
   *
   * @return
   */
  boolean isCollective();

  /**
   * @param visitor
   */
  /**
   * If {@link #getExpressionType()} returns anything other than {@link Expression#SINGLETON}, the
   * operands are other declarations this is part of a union or intersection with.
   *
   * @return the operands
   */
  List<KimConcept> getOperands();

  /**
   * Type of expression. If anything other than {@link Expression#SINGLETON}, {@link #getOperands()}
   * will return a non-empty list.
   *
   * @return the expression type
   */
  Expression getExpressionType();

  /**
   * Get the fundamental type of this concept - one of the concrete trait or observable types,
   * including configuration and extent.
   *
   * @return
   */
  SemanticType getFundamentalType();

  /**
   * Get the 'co-occurrent' (during) event type if any.
   *
   * @return
   */
  KimConcept getCooccurrent();

  /**
   * Get the concept that this is stated to be adjacent to if any.
   *
   * @return
   */
  KimConcept getAdjacent();

//  /**
//   * Return a string suitable for naming a k.IM object after this concept.
//   *
//   * @return
//   */
//  String getCodeName();

  //  SemanticRole getSemanticRole();

  /**
   * Declared parent concept, if any.
   *
   * @return
   */
  KimConcept getParent();

  List<Pair<ValueOperator, Object>> getValueOperators();

  boolean isPattern();

  Collection<String> getPatternVariables();

  /**
   * If the concept is the result of a unary operation applied to one or two arguments, return the
   * operator along with its argument. Otherwise return null;
   *
   * @return
   */
  Triple<UnarySemanticOperator, KimConcept, KimConcept> semanticOperation();

  /**
   * If the concept contains the passed modifier, return its argument, otherwise return null.
   *
   * @param semanticClause
   * @return
   */
  KimConcept semanticClause(SemanticClause semanticClause);
}
