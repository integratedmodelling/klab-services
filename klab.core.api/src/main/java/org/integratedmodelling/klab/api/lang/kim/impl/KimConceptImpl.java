package org.integratedmodelling.klab.api.lang.kim.impl;

import java.io.Serial;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.SemanticClause;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.utils.Utils;

public class KimConceptImpl extends KimStatementImpl implements KimConcept {

  @Serial private static final long serialVersionUID = 8531431719010407385L;

  private String name;
  private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
  // Nullable for legacy syntax; distinct from the aggregate expression status in type.
  private Boolean declaredSubjective;

  public Boolean getDeclaredSubjective() { return declaredSubjective; }
  public void setDeclaredSubjective(Boolean declaredSubjective) {
    this.declaredSubjective = declaredSubjective;
  }
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
  private Contextualization contextualization;

  public Set<SemanticType> getArgumentType() {
    return argumentType;
  }

  public void setArgumentType(Set<SemanticType> argumentType) {
    this.argumentType = argumentType;
  }

  public KimConceptImpl() {}

  private Set<SemanticType> argumentType = EnumSet.noneOf(SemanticType.class);

  private KimConceptImpl(KimConceptImpl other) {
    super(other);
    this.name = other.name;
    this.type.addAll(other.type);
    this.declaredSubjective = other.declaredSubjective;
    this.observable = other.observable;
    this.parent = other.parent;
    this.inherent = other.inherent;
    this.goal = other.goal;
    this.causant = other.causant;
    this.caused = other.caused;
    this.compresent = other.compresent;
    this.comparisonConcept = other.comparisonConcept;
    this.authorityTerm = other.authorityTerm;
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
    if (other.argumentType != null) this.argumentType.addAll(other.argumentType);
    this.pattern = other.pattern;
    this.patternVariables.addAll(other.patternVariables);
    this.valueOperators.addAll(other.valueOperators);
    this.contextualization = other.contextualization;
  }

  @Override
  public String getName() {
    return this.name;
  }

  /** Copy every syntax position, excluding the parent back-reference, then transform each copy.
   * The callback must not mutate the source. Cached definitions are invalidated on the copies.
   */
  public KimConceptImpl mapSyntax(java.util.function.Consumer<KimConceptImpl> transform) {
    return mapSyntax(List.of(), (path, node) -> transform.accept(node));
  }

  /** Paths identify syntax positions, including clause roles and list indices, never object identity. */
  public KimConceptImpl mapSyntax(List<String> path,
      java.util.function.BiConsumer<List<String>, KimConceptImpl> transform) {
    return mapSyntax(path, new SyntaxMapping(transform, true));
  }

  static final class SyntaxMapping {
    private final java.util.function.BiConsumer<List<String>, KimConceptImpl> action;
    private final boolean invalidateDefinitions;
    private final boolean mapKey;
    private final IdentityHashMap<Object, List<String>> active;

    SyntaxMapping(java.util.function.BiConsumer<List<String>, KimConceptImpl> action,
        boolean invalidateDefinitions) {
      this(action, invalidateDefinitions, false, new IdentityHashMap<>());
    }

    private SyntaxMapping(java.util.function.BiConsumer<List<String>, KimConceptImpl> action,
        boolean invalidateDefinitions, boolean mapKey, IdentityHashMap<Object, List<String>> active) {
      this.action = action;
      this.invalidateDefinitions = invalidateDefinitions;
      this.mapKey = mapKey;
      this.active = active;
    }

    boolean invalidateDefinitions() { return invalidateDefinitions; }

    void enter(Object value, List<String> path) {
      if (active.containsKey(value))
        throw new IllegalArgumentException("Cyclic syntax value at " + path + "; ancestor " + active.get(value));
      active.put(value, List.copyOf(path));
    }

    void leave(Object value) { active.remove(value); }

    SyntaxMapping keyCopy() {
      return new SyntaxMapping((path, node) -> {}, false, true, active);
    }
  }

  private KimConceptImpl mapSyntax(List<String> path, SyntaxMapping transform) {
    path = List.copyOf(path);
    transform.enter(this, path);
    try {
    var ret = new KimConceptImpl(this);
    ret.parent = null;
    ret.observable = map(observable, childPath(path, "observable"), transform);
    ret.inherent = map(inherent, childPath(path, "inherent"), transform);
    ret.goal = map(goal, childPath(path, "goal"), transform);
    ret.causant = map(causant, childPath(path, "causant"), transform);
    ret.caused = map(caused, childPath(path, "caused"), transform);
    ret.compresent = map(compresent, childPath(path, "compresent"), transform);
    ret.comparisonConcept = map(comparisonConcept, childPath(path, "comparisonConcept"), transform);
    ret.relationshipSource = map(relationshipSource, childPath(path, "relationshipSource"), transform);
    ret.relationshipTarget = map(relationshipTarget, childPath(path, "relationshipTarget"), transform);
    ret.cooccurrent = map(cooccurrent, childPath(path, "cooccurrent"), transform);
    ret.adjacent = map(adjacent, childPath(path, "adjacent"), transform);
    ret.traits = map(traits, childPath(path, "traits"), transform);
    ret.roles = map(roles, childPath(path, "roles"), transform);
    ret.operands = map(operands, childPath(path, "operands"), transform);
    ret.valueOperators = new ArrayList<>();
    for (int i = 0; i < valueOperators.size(); i++) {
      var operator = valueOperators.get(i);
      ret.valueOperators.add(Pair.of(operator.getFirst(), mapValue(operator.getSecond(),
          childPath(path, "valueOperators", Integer.toString(i), "operand"), transform)));
    }
    if (transform.invalidateDefinitions()) ret.urn = null;
    transform.action.accept(path, ret);
    return ret;
    } finally {
      transform.leave(this);
    }
  }

  /** Map a supported concept-valued slot; detach ordinary literal payloads without interpreting them. */
  public static Object mapValue(Object value,
      java.util.function.Consumer<KimConceptImpl> transform) {
    return mapValue(value, List.of(), (path, node) -> transform.accept(node));
  }

  public static Object mapValue(Object value, List<String> path,
      java.util.function.BiConsumer<List<String>, KimConceptImpl> transform) {
    return mapValue(value, path, new SyntaxMapping(transform, true));
  }

  /** Detached structural copy preserving definitions, excluding the parent back-reference. */
  public static Object copyValue(Object value) {
    return mapValue(value, List.of(), new SyntaxMapping((path, node) -> {}, false));
  }

  /**
   * Structural value traversal shared by copying and substitution. Collection elements and map
   * values follow LanguageAdapter.adaptValue. Keys are detached labels, not classification sites.
   * Map paths use encounter-order entry ordinals plus key/value sides, never key.toString(). The
   * adapter's LinkedHashMap order is retained, and collisions after copying fail rather than lose data.
   */
  static Object mapValue(Object value, List<String> path, SyntaxMapping transform) {
    if (value == null) return null;
    if (value instanceof KimConcept concept) return map(concept, path, transform);
    if (value instanceof KimObservableImpl observable)
      return new KimObservableImpl(observable, childPath(path, "observable"), transform);
    if (value instanceof org.integratedmodelling.klab.api.lang.kim.KimObservable)
      throw new IllegalArgumentException("Unsupported observable syntax implementation: " + value.getClass());
    if (value instanceof Map<?, ?> source) {
      transform.enter(value, path);
      try {
        var result = new LinkedHashMap<Object, Object>();
        int index = 0;
        for (var entry : source.entrySet()) {
          var entryPath = childPath(path, "entries", Integer.toString(index++));
          var key = mapValue(entry.getKey(), childPath(entryPath, "key"), transform.keyCopy());
          if (result.containsKey(key))
            throw new IllegalArgumentException("Copied map keys collide at " + entryPath);
          result.put(key, mapValue(entry.getValue(), childPath(entryPath, "value"), transform));
        }
        return result;
      } finally { transform.leave(value); }
    }
    if (value instanceof Collection<?> source) {
      transform.enter(value, path);
      try {
        // Adapter collections become lists. Preserve set equality when the collection is a key.
        Collection<Object> result = transform.mapKey && source instanceof Set<?>
            ? new LinkedHashSet<>() : new ArrayList<>();
        int index = 0;
        for (var item : source) {
          var itemPath = childPath(path, "items", Integer.toString(index++));
          if (!result.add(mapValue(item, itemPath, transform)))
            throw new IllegalArgumentException("Copied set key elements collide at " + itemPath);
        }
        return result;
      } finally { transform.leave(value); }
    }
    if (value.getClass().isArray()) {
      transform.enter(value, path);
      try {
        var component = value.getClass().getComponentType();
        int length = java.lang.reflect.Array.getLength(value);
        var result = java.lang.reflect.Array.newInstance(component, length);
        if (component.isPrimitive()) System.arraycopy(value, 0, result, 0, length);
        else for (int i = 0; i < length; i++) {
          var itemPath = childPath(path, "elements", Integer.toString(i));
          var item = mapValue(java.lang.reflect.Array.get(value, i), itemPath, transform);
          if (item != null && !component.isInstance(item))
            throw new IllegalArgumentException("Mapped array element has incompatible type at " + itemPath);
          java.lang.reflect.Array.set(result, i, item);
        }
        return result;
      } finally { transform.leave(value); }
    }
    return KimSyntaxCopy.copy(value);
  }

  static List<String> childPath(List<String> path, String... components) {
    var ret = new ArrayList<>(path);
    ret.addAll(List.of(components));
    return List.copyOf(ret);
  }

  private static KimConcept map(KimConcept concept, List<String> path, SyntaxMapping transform) {
    if (concept == null) return null;
    if (!(concept instanceof KimConceptImpl syntax))
      throw new IllegalArgumentException("Unsupported syntax implementation: " + concept.getClass());
    return syntax.mapSyntax(path, transform);
  }

  private static List<KimConcept> map(List<KimConcept> concepts, List<String> path, SyntaxMapping transform) {
    var result = new ArrayList<KimConcept>();
    for (int i = 0; i < concepts.size(); i++)
      result.add(map(concepts.get(i), childPath(path, Integer.toString(i)), transform));
    return result;
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

  @Override
  public Contextualization getDescriptionType() {
    return contextualization;
  }

  public void setDescriptionType(Contextualization contextualization) {
    this.contextualization = contextualization;
  }

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

  /**
   * Remove a component concept (must be a trait: use semantic roles for clauses and operators).
   *
   * @param concept
   * @return
   */
  public boolean remove(KimConcept concept) {
    if (this.traits.remove(concept)) {
      resetDefinition();
      return true;
    }
    return false;
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
        case VALUE_OPERATOR:
          ret.valueOperators.clear();
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
          ret.roles = copyWithout(ret.roles, (trait) -> declaration.equals(trait.getUrn()));
          break;
        case TRAIT:
          ret.traits = copyWithout(ret.traits, (trait) -> declaration.equals(trait.getUrn()));
          break;
        default:
          break;
      }
    }

    ret.resetDefinition();

    return ret;
  }

  private static List<KimConcept> copyWithout(
      List<KimConcept> concepts, Predicate<KimConcept> removeCheck) {
    List<KimConcept> ret = new ArrayList<>();
    for (KimConcept c : concepts) {
      if (!removeCheck.test(c)) {
        ret.add(c);
      }
    }
    return ret;
  }

  public void addTraits(
      List<KimConcept> traits, BiPredicate<KimConcept, KimConcept> checkForConflict) {
    if (!traits.isEmpty()) {
      addPredicates(traits, this.traits, checkForConflict);
    }
  }

  public void addRoles(
      List<KimConcept> roles, BiPredicate<KimConcept, KimConcept> checkForConflict) {
    if (!roles.isEmpty()) {
      addPredicates(roles, this.roles, checkForConflict);
    }
  }

  private void addPredicates(
      List<KimConcept> traitsToAdd,
      List<KimConcept> originalTraits,
      BiPredicate<KimConcept, KimConcept> checkForConflict) {
    if (checkForConflict == null) {
      originalTraits.addAll(traitsToAdd);
    } else {
      List<KimConcept> toRemove = new ArrayList<>();
      for (var original : originalTraits) {
        for (var newTrait : traitsToAdd) {
          if (checkForConflict.test(newTrait, original)) {
            toRemove.add(original);
          }
        }
      }
      originalTraits.removeAll(toRemove);
      originalTraits.addAll(traitsToAdd);
    }
    resetDefinition();
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
    if (declaredSubjective != null) {
      boolean subjective = org.integratedmodelling.klab.api.lang.kim.ExpressionStatus.isSubjective(this);
      type.remove(SemanticType.SUBJECTIVE);
      if (subjective) type.add(SemanticType.SUBJECTIVE);
    }
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
   *
   * <p>TODO generalize to a tokenizer where the append() can be delegated to a separate method that
   * also knows the type and role of each token, so that it can be connected to highlighting
   */
  private String selector() {
    if (is(SemanticType.ANY)) return "any";
    if (is(SemanticType.ALL)) return "all";
    if (is(SemanticType.NONE)) return "no";
    return null;
  }

  public String finalizeDefinition() {

    if (this.urn != null) {
      return this.urn;
    }

    if (!isCollective() && observable != null && observable.isCollective()) {
      collective = true;
    }

    String main = "";
    StringBuilder ret = new StringBuilder(isCollective() ? "each" : "");
    String selector = selector();
    if (selector != null) {
      ret.append(ret.isEmpty() ? "" : " ").append(selector);
    }

    if (semanticModifier != null) {
      ret.append(ret.isEmpty() ? "" : " ").append(semanticModifier.declaration[0]);
      ret.append((ret.isEmpty()) ? "" : " ")
          .append(name == null ? ((KimConceptImpl) observable).finalizeDefinition() : name);
      if (comparisonConcept != null) {
        ret.append(" ")
            .append(semanticModifier.declaration[1])
            .append(" ")
            .append(((KimConceptImpl) comparisonConcept).finalizeDefinition());
      }
      main = ret.toString();
      ret = new StringBuilder();
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

    if (semanticModifier == null) {
      ret.append((ret.isEmpty()) ? "" : " ")
          .append(name == null ? ((KimConceptImpl) observable).finalizeDefinition() : name);
    } else {
      ret.append((ret.isEmpty()) ? "" : " ").append(main);
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

    for (var operator : valueOperators) {
      ret.append(" ").append(operator.getFirst() == ValueOperator.IS ? "is" : operator.getFirst().declaration);
      if (operator.getSecond() != null) ret.append(" ").append(renderValue(operator.getSecond()));
    }

    for (KimConcept operand : operands) {
      ret.append(" ")
          .append(expressionType == Expression.INTERSECTION ? "and" : "or")
          .append(" ")
          .append(((KimConceptImpl) operand).computeUrnAndParenthesize());
    }

    this.urn = ret.toString();
    this.contextualization = Contextualization.forSemantics(this);

    return this.urn;
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

  private static String renderValue(Object value) {
    if (value instanceof KimConceptImpl concept) return concept.computeUrnAndParenthesize();
    if (value instanceof org.integratedmodelling.klab.api.knowledge.Semantics semantics) return semantics.getUrn();
    if (value instanceof String text) return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    if (value instanceof Iterable<?> items) {
      var rendered = new ArrayList<String>();
      for (var item : items) rendered.add(renderValue(item));
      return "(" + String.join(", ", rendered) + ")";
    }
    return String.valueOf(value);
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

  private <T> T formatParenthesized(CodeAppender<T> appender, boolean addParentheses) {

    // needed for side effects
    var pdef = computeUrnAndParenthesize();
    var needsParentheses = pdef.startsWith("(") && pdef.endsWith(")");

    if (needsParentheses && addParentheses) {
      appender.append("(", LexicalRole.OPEN_PARENTHESIS);
    }

    var collective = !isCollective() && observable != null && observable.isCollective();
    if (collective) {
      appender.append("each", LexicalRole.KEYWORD);
    }
    if (selector() != null) {
      appender.append(selector(), LexicalRole.KEYWORD);
    }
    if (negated) {
      appender.append("not", LexicalRole.KEYWORD);
    }

    for (KimConcept trait : traits) {
      ((KimConceptImpl) trait).formatParenthesized(appender, true);
    }

    for (KimConcept role : roles) {
      ((KimConceptImpl) role).formatParenthesized(appender, true);
    }

    if (semanticModifier != null) {
      appender.append(semanticModifier.declaration[0], LexicalRole.PREFIX_SEMANTIC_OPERATOR);
      if (name != null) {
        appender.append(name, LexicalRole.CONCEPT, this);
      } else {
        observable.format(appender);
      }
      if (comparisonConcept != null) {
        appender.append(semanticModifier.declaration[1], LexicalRole.PREFIX_SEMANTIC_OPERATOR);
        comparisonConcept.format(appender);
      }
    } else {
      if (name != null) {
        appender.append(name, LexicalRole.CONCEPT, this);
      } else if (observable != null) {
        observable.format(appender);
      }
    }

    if (inherent != null) {
      appender.append("of", LexicalRole.KEYWORD);
      ((KimConceptImpl) inherent).formatParenthesized(appender, true);
    }

    if (causant != null) {
      appender.append("caused by", LexicalRole.KEYWORD);
      ((KimConceptImpl) causant).formatParenthesized(appender, true);
    }

    if (caused != null) {
      appender.append("causing", LexicalRole.KEYWORD);
      ((KimConceptImpl) caused).formatParenthesized(appender, true);
    }

    if (compresent != null) {
      appender.append("with", LexicalRole.KEYWORD);
      ((KimConceptImpl) compresent).formatParenthesized(appender, true);
    }

    if (cooccurrent != null) {
      appender.append("during", LexicalRole.KEYWORD);
      ((KimConceptImpl) cooccurrent).formatParenthesized(appender, true);
    }

    if (adjacent != null) {
      appender.append("adjacent to", LexicalRole.KEYWORD);
      ((KimConceptImpl) adjacent).formatParenthesized(appender, true);
    }

    if (goal != null) {
      appender.append("for", LexicalRole.KEYWORD);
      ((KimConceptImpl) goal).formatParenthesized(appender, true);
    }

    if (relationshipSource != null) {
      appender.append("linking", LexicalRole.KEYWORD);
      ((KimConceptImpl) relationshipSource).formatParenthesized(appender, true);
      if (relationshipTarget != null) {
        appender.append("to", LexicalRole.KEYWORD);
        ((KimConceptImpl) relationshipTarget).formatParenthesized(appender, true);
      }
    }

    // TODO value operators

    for (KimConcept operand : operands) {
      appender.append(
          expressionType == Expression.INTERSECTION ? "and" : "or", LexicalRole.KEYWORD);
      ((KimConceptImpl) operand).formatParenthesized(appender, true);
    }

    if (needsParentheses && addParentheses) {
      appender.append(")", LexicalRole.CLOSED_PARENTHESIS);
    }

    return appender.output();
  }

  @Override
  public <T> T format(CodeAppender<T> appender) {
    return formatParenthesized(appender, false);
  }
}
