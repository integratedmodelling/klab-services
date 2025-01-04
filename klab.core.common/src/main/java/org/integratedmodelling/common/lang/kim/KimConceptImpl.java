package org.integratedmodelling.common.lang.kim;

import java.io.Serial;
import java.util.*;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.SemanticClause;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

public class KimConceptImpl extends KimStatementImpl implements KimConcept {

  @Serial private static final long serialVersionUID = 8531431719010407385L;

  //  private SemanticRole semanticRole;
  private String name;
  private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
  private KimConcept observable;
  private KimConcept parent;
  private KimConcept inherent;
  private KimConcept goal;
  private KimConcept causant;
  private KimConcept caused;
  private KimConcept compresent;
  private KimConcept comparisonConcept;
  private String authorityTerm;
  private String authority;
  private UnarySemanticOperator semanticModifier;
  private KimConcept relationshipSource;
  private KimConcept relationshipTarget;
  private List<KimConcept> traits = new ArrayList<>();
  private List<KimConcept> roles = new ArrayList<>();
  private boolean negated;
  private String urn;
  private List<KimConcept> operands = new ArrayList<>();
  private Expression expressionType;
  private SemanticType fundamentalType;
  private KimConcept cooccurrent;
  private KimConcept adjacent;
  private boolean collective;
  private boolean pattern;
  private Set<String> patternVariables = new HashSet<>();
  private List<Pair<ValueOperator, Object>> valueOperators = new ArrayList<>();

  public Set<SemanticType> getArgumentType() {
    return argumentType;
  }

  public void setArgumentType(Set<SemanticType> argumentType) {
    this.argumentType = argumentType;
  }

  public KimConceptImpl() {}

  private transient Set<SemanticType> argumentType = EnumSet.noneOf(SemanticType.class);

  private KimConceptImpl(KimConceptImpl other) {
    super(other);
    this.name = other.name;
    this.type = EnumSet.copyOf(other.type);
    this.observable = other.observable;
    this.parent = other.parent;
    this.inherent = other.inherent;
    this.goal = other.goal;
    this.causant = other.causant;
    this.caused = other.caused;
    this.compresent = other.compresent;
    this.comparisonConcept = other.comparisonConcept;
    this.authorityTerm = other.authority;
    this.authority = other.authority;
    this.semanticModifier = other.semanticModifier;
    this.collective = other.collective;
    this.relationshipSource = other.relationshipSource;
    this.relationshipTarget = other.relationshipTarget;
    this.traits.addAll(other.traits);
    this.roles.addAll(other.roles);
    this.negated = other.negated;
    this.urn = other.urn;
    this.operands.addAll(other.operands);
    this.expressionType = other.expressionType;
    this.fundamentalType = other.fundamentalType;
    this.cooccurrent = other.cooccurrent;
    this.adjacent = other.adjacent;
    this.argumentType = EnumSet.copyOf(other.argumentType);
    this.pattern = other.pattern;
    this.patternVariables.addAll(other.patternVariables);
    this.valueOperators.addAll(other.valueOperators);
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Set<SemanticType> getType() {
    return this.type;
  }

  @Override
  public KimConcept getObservable() {
    return this.observable;
  }

  @Override
  public KimConcept getInherent() {
    return this.inherent;
  }

  @Override
  public KimConcept getGoal() {
    return this.goal;
  }

  @Override
  public KimConcept getCausant() {
    return this.causant;
  }

  @Override
  public KimConcept getCaused() {
    return this.caused;
  }

  @Override
  public KimConcept getCompresent() {
    return this.compresent;
  }

  @Override
  public KimConcept getComparisonConcept() {
    return this.comparisonConcept;
  }

  @Override
  public String getAuthorityTerm() {
    return this.authorityTerm;
  }

  @Override
  public String getAuthority() {
    return this.authority;
  }

  @Override
  public UnarySemanticOperator getSemanticModifier() {
    return this.semanticModifier;
  }

  @Override
  public KimConcept getRelationshipSource() {
    return this.relationshipSource;
  }

  @Override
  public KimConcept getRelationshipTarget() {
    return this.relationshipTarget;
  }

  @Override
  public List<KimConcept> getTraits() {
    return this.traits;
  }

  @Override
  public List<KimConcept> getRoles() {
    return this.roles;
  }

  @Override
  public boolean isNegated() {
    return this.negated;
  }

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public boolean is(SemanticType type) {
    return this.type.contains(type);
  }

  @Override
  public List<KimConcept> getOperands() {
    return this.operands;
  }

  @Override
  public Expression getExpressionType() {
    return this.expressionType;
  }

  @Override
  public SemanticType getFundamentalType() {
    return this.fundamentalType;
  }

  @Override
  public KimConcept getCooccurrent() {
    return this.cooccurrent;
  }

  @Override
  public KimConcept getAdjacent() {
    return this.adjacent;
  }

  //  @Override
  //  public String getCodeName() {
  //    return this.codeName;
  //  }

  //  @Override
  //  public SemanticRole getSemanticRole() {
  //    return this.semanticRole;
  //  }
  //
  //  public void setSemanticRole(SemanticRole semanticRole) {
  //    this.semanticRole = semanticRole;
  //  }

  public void setName(String name) {
    this.name = name;
  }

  public void setType(Set<SemanticType> type) {
    this.type = type;
  }

  public void setObservable(KimConcept observable) {
    this.observable = observable;
  }

  public void setInherent(KimConcept inherent) {
    this.inherent = inherent;
  }

  public void setGoal(KimConcept motivation) {
    this.goal = motivation;
  }

  public void setCausant(KimConcept causant) {
    this.causant = causant;
  }

  public void setCaused(KimConcept caused) {
    this.caused = caused;
  }

  public void setCompresent(KimConcept compresent) {
    this.compresent = compresent;
  }

  public void setComparisonConcept(KimConcept comparisonConcept) {
    this.comparisonConcept = comparisonConcept;
  }

  public void setAuthorityTerm(String authorityTerm) {
    this.authorityTerm = authorityTerm;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  public void setSemanticModifier(UnarySemanticOperator semanticModifier) {
    this.semanticModifier = semanticModifier;
  }

  public void setRelationshipSource(KimConcept relationshipSource) {
    this.relationshipSource = relationshipSource;
  }

  public void setRelationshipTarget(KimConcept relationshipTarget) {
    this.relationshipTarget = relationshipTarget;
  }

  public void setTraits(List<KimConcept> traits) {
    this.traits = traits;
  }

  public void setRoles(List<KimConcept> roles) {
    this.roles = roles;
  }

  public void setNegated(boolean negated) {
    this.negated = negated;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setOperands(List<KimConcept> operands) {
    this.operands = operands;
  }

  public void setExpressionType(Expression expressionType) {
    this.expressionType = expressionType;
  }

  public void setFundamentalType(SemanticType fundamentalType) {
    this.fundamentalType = fundamentalType;
  }

  public void setCooccurrent(KimConcept cooccurrent) {
    this.cooccurrent = cooccurrent;
  }

  public void setAdjacent(KimConcept adjacent) {
    this.adjacent = adjacent;
  }

  //  public void setCodeName(String codeName) {
  //    this.codeName = codeName;
  //  }

  @Override
  public KimConcept getParent() {
    return parent;
  }

  public void setParent(KimConcept parent) {
    this.parent = parent;
  }

  @Override
  public String toString() {
    return this.urn;
  }

  /*
   * modification methods
   */

  public KimConcept removeOperator() {
    KimConceptImpl ret = new KimConceptImpl(this);
    if (this.semanticModifier != null) {
      ret.semanticModifier = null;
      ret.comparisonConcept = null;
      ret.type = this.argumentType;
    }
    ret.resetDefinition();
    return ret;
  }

  @Override
  public KimConcept addOperator(
      UnarySemanticOperator operator, KimConcept operand, KimConcept comparisonConcept) {
    KimConceptImpl ret = new KimConceptImpl(this);
    ret.semanticModifier = operator;
    ret.type = operator.getApplicableType(operand.getType()); // TODO check
    ret.observable = operand;
    ret.comparisonConcept = comparisonConcept;
    ret.resetDefinition();
    return ret;
  }

  public KimConcept removeComponents(SemanticRole... roles) {

    KimConceptImpl ret = new KimConceptImpl(this);

    for (SemanticRole role : roles) {

      switch (role) {
        case ADJACENT:
          ret.adjacent = null;
          break;
        case CAUSANT:
          ret.causant = null;
          break;
        case CAUSED:
          ret.caused = null;
          break;
        case COMPRESENT:
          ret.compresent = null;
          break;
        case COOCCURRENT:
          ret.cooccurrent = null;
          break;
        case GOAL:
          ret.goal = null;
          break;
        case INHERENT:
          ret.inherent = null;
          break;
        case ROLE:
          ret.roles.clear();
          break;
        case TRAIT:
          ret.traits.clear();
          break;
        case UNARY_OPERATOR:
          ((KimConceptImpl) ret.observable).semanticModifier = null;
          break;
        default:
          break;
      }
    }

    ret.resetDefinition();

    return ret;
  }

  public KimConcept removeComponents(List<String> declarations, List<SemanticRole> roles) {

    KimConceptImpl ret = new KimConceptImpl(this);

    for (int i = 0; i < declarations.size(); i++) {

      String declaration = declarations.get(i);
      SemanticRole role = roles.get(i);

      switch (role) {
        case ADJACENT:
          ret.adjacent = null;
          break;
        case CAUSANT:
          ret.causant = null;
          break;
        case CAUSED:
          ret.caused = null;
          break;
        case COMPRESENT:
          ret.compresent = null;
          break;
        case COOCCURRENT:
          ret.cooccurrent = null;
          break;
        case GOAL:
          ret.goal = null;
          break;
        case INHERENT:
          ret.inherent = null;
          break;
        case ROLE:
          ret.roles = copyWithout(ret.roles, declaration);
          break;
        case TRAIT:
          ret.traits = copyWithout(ret.traits, declaration);
          break;
        default:
          break;
      }
    }

    ret.resetDefinition();

    return ret;
  }

  private static List<KimConcept> copyWithout(List<KimConcept> concepts, String declaration) {
    List<KimConcept> ret = new ArrayList<>();
    for (KimConcept c : concepts) {
      if (!c.toString().equals(declaration)) {
        ret.add(c);
      }
    }
    return ret;
  }

  @Override
  public List<Pair<SemanticRole, KimConcept>> getModifiers() {
    List<Pair<SemanticRole, KimConcept>> ret = new ArrayList<>();
    for (var role : SemanticRole.values()) {
      switch (role) {
        case INHERENT -> {
          if (inherent != null) ret.add(Pair.of(role, inherent));
        }
        case ADJACENT -> {
          if (adjacent != null) ret.add(Pair.of(role, adjacent));
        }
        case CAUSED -> {
          if (caused != null) ret.add(Pair.of(role, caused));
        }
        case CAUSANT -> {
          if (causant != null) ret.add(Pair.of(role, causant));
        }
        case COMPRESENT -> {
          if (compresent != null) ret.add(Pair.of(role, compresent));
        }
        case GOAL -> {
          if (goal != null) ret.add(Pair.of(role, goal));
        }
        case COOCCURRENT -> {
          if (cooccurrent != null) ret.add(Pair.of(role, cooccurrent));
        }
        case RELATIONSHIP_SOURCE -> {
          if (relationshipSource != null) ret.add(Pair.of(role, relationshipSource));
        }
        case RELATIONSHIP_TARGET -> {
          if (relationshipTarget != null) ret.add(Pair.of(role, relationshipTarget));
        }
        default -> {}
      }
    }
    return ret;
  }

  /** Call after a setting made after the concept had been finalized */
  public void resetDefinition() {
    this.urn = null;
    finalizeDefinition();
  }

  /**
   * Called after definition is complete. Will create the URN as a normalized text declaration that
   * can be parsed back into a concept.
   *
   * <p>TODO must also establish abstract nature and handle generics *
   *
   * <p>TODO must also compute the code name and reference name, which shouldn't come from the
   * semantics.
   */
  public String finalizeDefinition() {

    if (this.urn != null) {
      return this.urn;
    }

    if (!isCollective() && observable != null && observable.isCollective()) {
      collective = true;
    }

    StringBuilder ret = new StringBuilder(isCollective() ? "each" : "");

    if (semanticModifier != null) {
      ret.append(ret.isEmpty() ? "" : " ").append(semanticModifier.declaration[0]);
    }

    if (negated) {
      ret.append(ret.isEmpty() ? "" : " ").append("not");
    }

    traits.sort(
        (o1, o2) ->
            ((KimConceptImpl) o1)
                .finalizeDefinition()
                .compareTo(((KimConceptImpl) o2).finalizeDefinition()));

    for (KimConcept trait : traits) {
      ret.append((ret.isEmpty()) ? "" : " ")
          .append(((KimConceptImpl) trait).computeUrnAndParenthesize());
    }

    roles.sort(
        (o1, o2) ->
            ((KimConceptImpl) o1)
                .finalizeDefinition()
                .compareTo(((KimConceptImpl) o2).finalizeDefinition()));
    for (KimConcept role : roles) {
      ret.append((ret.isEmpty()) ? "" : " ")
          .append(((KimConceptImpl) role).computeUrnAndParenthesize());
    }

    ret.append((ret.isEmpty()) ? "" : " ")
        .append(name == null ? ((KimConceptImpl) observable).finalizeDefinition() : name);

    if (comparisonConcept != null) {
      ret.append(" ")
          .append(semanticModifier.declaration[1])
          .append(" ")
          .append(((KimConceptImpl) comparisonConcept).finalizeDefinition());
    }

    if (inherent != null) {
      ret.append(" of ").append(((KimConceptImpl) inherent).computeUrnAndParenthesize());
    }

    if (causant != null) {
      ret.append(" caused by ").append(((KimConceptImpl) causant).computeUrnAndParenthesize());
    }

    if (caused != null) {
      ret.append(" causing ").append(((KimConceptImpl) caused).computeUrnAndParenthesize());
    }

    if (compresent != null) {
      ret.append(" with ").append(((KimConceptImpl) compresent).computeUrnAndParenthesize());
    }

    if (cooccurrent != null) {
      ret.append(" during ").append(((KimConceptImpl) cooccurrent).computeUrnAndParenthesize());
    }

    if (adjacent != null) {
      ret.append(" adjacent to ").append(((KimConceptImpl) adjacent).computeUrnAndParenthesize());
    }

    if (goal != null) {
      ret.append(" for ").append(((KimConceptImpl) goal).computeUrnAndParenthesize());
    }

    if (relationshipSource != null) {
      ret.append(" linking ")
          .append(((KimConceptImpl) relationshipSource).computeUrnAndParenthesize());
      if (relationshipTarget != null) {
        ret.append(" to ")
            .append(((KimConceptImpl) relationshipTarget).computeUrnAndParenthesize());
      }
    }

    // TODO value operators

    for (KimConcept operand : operands) {
      ret.append(" ")
          .append(expressionType == Expression.INTERSECTION ? "and" : "or")
          .append(" ")
          .append(((KimConceptImpl) operand).computeUrnAndParenthesize());
    }

    return this.urn = ret.toString();
  }

  /**
   * Compute the URN and add parentheses around it unless it is already enclosed in parentheses or
   * it is a simple expression. The latter is zero+ predicates + one observable without semantic
   * modifiers or value operators.
   *
   * <p>TODO check if we need to parenthesize unary operators, <code>not</code> and <code>each
   * </code>.
   *
   * @return the computed URN with parentheses where necessary.
   */
  private String computeUrnAndParenthesize() {

    String urn = finalizeDefinition();
    boolean trivial = getModifiers().isEmpty() && valueOperators.isEmpty();

    if (trivial) {
      var allTraits = type.contains(SemanticType.PREDICATE);
      var countElements = traits.size() + roles.size();
      if (observable == null && name != null) {
        // may have the name in the traits, in which case the main element doesn't count
        boolean present = false;
        for (var predicate : Utils.Collections.join(traits, roles)) {
          if (name.equals(predicate.getUrn())) {
            present = true;
            break;
          }
        }
        if (!present) {
          countElements++;
        }
      } else if (observable != null) {
        countElements++;
      }
      trivial = !allTraits || countElements == 1;
    }
    return trivial ? urn : ("(" + urn + ")");
  }

  private String stringify(String term) {

    if (term.startsWith("\"")) {
      return term;
    }

    boolean ws = false;

    // stringify anything that's not a lowercase ID
    for (int i = 0; i < term.length(); i++) {
      if (Character.isWhitespace(term.charAt(i))
          || !(Character.isLetter(term.charAt(i))
              || Character.isDigit(term.charAt(i))
              || term.charAt(i) == '_')) {
        ws = true;
        break;
      }
    }

    // TODO should escape any internal double quotes, unlikely
    return ws ? ("\"" + term + "\"") : term;
  }

  @Override
  public boolean isCollective() {
    return collective;
  }

  public void setCollective(boolean collective) {
    this.collective = collective;
  }

  @Override
  public int hashCode() {
    return Objects.hash(urn);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    KimConceptImpl other = (KimConceptImpl) obj;
    return Objects.equals(finalizeDefinition(), other.finalizeDefinition());
  }

  @Override
  public List<Pair<ValueOperator, Object>> getValueOperators() {
    return valueOperators;
  }

  public void setValueOperators(List<Pair<ValueOperator, Object>> valueOperators) {
    this.valueOperators = valueOperators;
  }

  public static KimConcept nothing() {
    var ret = new KimConceptImpl();
    ret.setName("Nothing");
    ret.setNamespace("owl");
    ret.setType(EnumSet.of(SemanticType.NOTHING));
    ret.setUrn("owl:Nothing");
    return ret;
  }

  @Override
  public boolean isPattern() {
    return pattern;
  }

  public void setPattern(boolean pattern) {
    this.pattern = pattern;
  }

  @Override
  public Set<String> getPatternVariables() {
    return patternVariables;
  }

  public void setPatternVariables(Set<String> patternVariables) {
    this.patternVariables = patternVariables;
  }

  @Override
  public void visit(Visitor visitor) {

    if (observable != null) {
      observable.visit(visitor);
    }

    if (authority != null) {
      //            visitor.visitAuthority(authority, authorityTerm);
    }

    for (KimConcept trait : traits) {
      trait.visit(visitor);
    }

    for (KimConcept role : roles) {
      role.visit(visitor);
    }

    if (inherent != null) {
      inherent.visit(visitor);
    }

    if (causant != null) {
      causant.visit(visitor);
    }

    if (caused != null) {
      caused.visit(visitor);
    }

    if (compresent != null) {
      compresent.visit(visitor);
    }

    if (cooccurrent != null) {
      cooccurrent.visit(visitor);
    }

    if (adjacent != null) {
      adjacent.visit(visitor);
    }

    //    if (temporalInherent != null) {
    //      temporalInherent.visit(visitor);
    //    }

    if (goal != null) {
      goal.visit(visitor);
    }

    if (relationshipSource != null) {
      relationshipSource.visit(visitor);
    }

    if (relationshipTarget != null) {
      relationshipTarget.visit(visitor);
    }

    if (comparisonConcept != null) {
      comparisonConcept.visit(visitor);
    }
  }

  @Override
  public Triple<UnarySemanticOperator, KimConcept, KimConcept> semanticOperation() {
    if (semanticModifier != null) {
      return Triple.of(semanticModifier, observable, comparisonConcept);
    }
    return null;
  }

  @Override
  public KimConcept semanticClause(SemanticClause semanticClause) {
    return switch (semanticClause) {
      case OF -> inherent;
      case FOR -> goal;
      case WITH -> compresent;
      case CAUSED_BY -> causant;
      case ADJACENT_TO -> adjacent;
      case CAUSING -> caused;
      case DURING -> cooccurrent;
      case LINKING -> relationshipSource;
      case TO -> relationshipTarget;
    };
  }
}
