package org.integratedmodelling.klab.indexing;

import java.math.BigDecimal;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.ObservableValidator;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;

/**
 * Server-owned assisted composition. Querying never edits the expression; selections refer to a
 * particular proposal response. Edits are replayed and validated before replacing accepted state,
 * so rejected edits and undo cannot leave partial graph mutations behind.
 *
 * <p>Operand categories come from the language enums. Completed expressions are resolved and checked
 * by the Reasoner; these lexical constraints are not a replacement for ontology validation.
 */
public final class SemanticSearchSession {
  @FunctionalInterface
  public interface Search {
    List<SemanticMatch> query(String text, SemanticScope scope, int limit);
  }

  private final Reasoner reasoner;
  private final Search search;
  private final SemanticClauseSupport clauseSupport;
  private final Set<SemanticType> resultTypes;
  private final Set<SemanticMatch.Type> matchTypes;
  private List<Object> tokens = new ArrayList<>();
  private State state;
  private List<SemanticMatch> proposals = List.of();
  private int proposalRequestId;
  private int lastRequestId = -1;
  private record Literal(String code) {}
  private enum Qualifier { EACH }

  private static final class Frame {
    final int start;
    SemanticScope scope = SemanticScope.root();
    final Set<SemanticRole> used = EnumSet.noneOf(SemanticRole.class);
    Concept concept;
    boolean complete;
    boolean value;
    boolean collectiveExpression;
    Concept clauseOwner;
    SemanticRole clauseRole;
    boolean awaitingRelationshipTarget;
    final List<Concept> predicates = new ArrayList<>();
    int operandStart;
    Concept enclosingOwner;
    SemanticRole enclosingRole;
    Frame(int start) { this.start = start; }
  }

  private record State(Deque<Frame> frames, Observable observable) {
    Frame current() { return frames.peek(); }
  }

  public SemanticSearchSession(Reasoner reasoner, Search search, SemanticSearchRequest initial) {
    this(reasoner, search, initial, new SemanticClauseSupport(reasoner));
  }

  public SemanticSearchSession(Reasoner reasoner, Search search, SemanticSearchRequest initial,
      SemanticClauseSupport clauseSupport) {
    this.clauseSupport = Objects.requireNonNull(clauseSupport);
    this.reasoner = Objects.requireNonNull(reasoner);
    this.search = Objects.requireNonNull(search);
    resultTypes = initial.getSemanticTypes() == null ? Set.of() : Set.copyOf(initial.getSemanticTypes());
    matchTypes = initial.getMatchTypes() == null ? Set.of() : Set.copyOf(initial.getMatchTypes());
    state = replay(tokens);
  }

  public synchronized SemanticSearchResponse handle(SemanticSearchRequest request, int searchId) {
    var response = new SemanticSearchResponse(searchId, request.getRequestId());
    if (request.getRequestId() <= lastRequestId) {
      response.getErrors().add("This request is out of order. Search again before choosing a match.");
      describe(response);
      return response;
    }
    lastRequestId = request.getRequestId();
    var mode = request.getSearchMode() == null ? SemanticSearchRequest.Mode.TOKEN : request.getSearchMode();
    try {
      var next = new ArrayList<>(tokens);
      switch (mode) {
        case TOKEN -> { }
        case UNDO -> { if (!next.isEmpty()) next.removeLast(); }
        case OPEN_SCOPE -> next.add("(");
        case CLOSE_SCOPE -> next.add(")");
        case VALUE -> next.add(literal(request.getQueryString()));
        case SELECT -> {
          if (request.getMatchesRequestId() != proposalRequestId)
            throw new IllegalArgumentException("The result list has changed. Choose from the current results.");
          var match = proposals.stream()
              .filter(m -> Objects.equals(m.getId(), request.getSelectedMatchId()))
              .findFirst().orElseThrow(() -> new IllegalArgumentException("The selected match is no longer available."));
          next.add(token(match));
        }
      }
      if (next.size() > 128) throw new IllegalArgumentException("The expression is too long.");
      State candidate = replay(next);
      tokens = next;
      state = candidate;
    } catch (IllegalArgumentException ex) {
      response.getErrors().add(ex.getMessage());
    }
    describe(response);
    String text = mode == SemanticSearchRequest.Mode.TOKEN && request.getQueryString() != null
        ? request.getQueryString().strip().toLowerCase(Locale.ROOT) : "";
    if (text.length() > 256) {
      response.getErrors().add("Search text is too long.");
      text = "";
    }
    int limit = Math.max(1, Math.min(100, request.getMaxResults()));
    var matches = new ArrayList<SemanticMatch>();
    var candidates = new ArrayList<SemanticMatch>();
    if ("each".startsWith(text)) {
      var each = new SemanticMatch();
      each.setMatchType(SemanticMatch.Type.MODIFIER);
      each.setId("each");
      each.setName("each");
      each.setDescription("Make the following countable concept collective, including a clause operand.");
      candidates.add(each);
    }
    candidates.addAll(search.query(text, state.current().scope, Math.max(100, limit)));
    for (var match : candidates) {
      if (!matchTypes.isEmpty() && !matchTypes.contains(match.getMatchType())) continue;
      try {
        var trial = new ArrayList<>(tokens);
        trial.add(token(match));
        replay(trial);
        matches.add(match);
        if (matches.size() == limit) break;
      } catch (IllegalArgumentException ignored) {
        // An indexed lexical match is a proposal only if it is admissible in this expression.
      }
    }
    proposals = List.copyOf(matches);
    proposalRequestId = request.getRequestId();
    response.setMatches(matches);
    return response;
  }

  private void describe(SemanticSearchResponse response) {
    response.setDeclaration(declaration(tokens));
    response.setCode(tokens.stream().map(this::styled).toList());
    response.setParenthesisDepth(state.frames().size() - 1);
    response.setCanUndo(!tokens.isEmpty());
    response.setCanOpenScope(state.current().scope.lexicalRealm.contains(SemanticRole.GROUP_OPEN)
        && (tokens.isEmpty() || !"(".equals(tokens.getLast())));
    response.setCanCloseScope(state.frames().size() > 1 && state.current().complete
        && !state.current().awaitingRelationshipTarget);
    response.setAcceptsValue(state.current().value);
    Concept current = state.frames().stream().map(f -> f.concept).filter(Objects::nonNull).findFirst().orElse(null);
    response.setCurrentConcept(current);
    if (current != null) response.setClauses(clauseSupport.clauses(current));
    Observable observable = state.observable();
    if (observable != null && (!observable.is(SemanticType.PREDICATE)
        || reasoner.inherent(observable.getSemantics()) != null) && (resultTypes.isEmpty()
        || resultTypes.stream().anyMatch(observable::is))) {
      response.setObservable(observable);
      response.setCurrentType(SemanticType.fundamentalType(observable.getSemantics().getType()));
      response.setDescription(observable.getUrn());
    }
  }

  private Object token(SemanticMatch match) {
    if (match.getMatchType() == SemanticMatch.Type.CONCEPT) {
      Concept concept = reasoner.resolveConcept(match.getId());
      if (concept == null || concept.is(SemanticType.NOTHING))
        throw new IllegalArgumentException("This concept is unavailable.");
      return concept;
    }
    if (match.getMatchType() == SemanticMatch.Type.MODIFIER && "each".equals(match.getId()))
      return Qualifier.EACH;
    if (match.getUnaryOperator() != null) return match.getUnaryOperator();
    if (match.getBinaryOperator() != null) return match.getBinaryOperator();
    if (match.getValueOperator() != null) return match.getValueOperator();
    if (match.getModifier() != null) return match.getModifier();
    if ("(".equals(match.getId()) || ")".equals(match.getId())) return match.getId();
    throw new IllegalArgumentException("This kind of component is not yet supported by assisted composition.");
  }

  private State replay(List<Object> input) {
    Deque<Frame> frames = new ArrayDeque<>();
    frames.push(new Frame(0));
    for (int i = 0; i < input.size(); i++) {
      Object token = input.get(i);
      Frame frame = frames.peek();
      if ("(".equals(token)) {
        require(frame.scope.lexicalRealm.contains(SemanticRole.GROUP_OPEN), "A group cannot start here.");
        require(i == 0 || !"(".equals(input.get(i - 1)), "Choose a component before opening another group.");
        var nested = new Frame(i + 1);
        nested.scope = operands(frame.scope.logicalRealm);
        nested.predicates.addAll(frame.predicates);
        // Carry a clause bound into a directly grouped operand so its choices are filtered too.
        // A group used inside a unary operator is checked when that operator completes instead.
        if (frame.clauseRole != null && (i == frame.operandStart
            || i == frame.operandStart + 1 && input.get(i - 1) == Qualifier.EACH)) {
          nested.enclosingOwner = frame.clauseOwner; nested.enclosingRole = frame.clauseRole;
        } else if (i == frame.start || i == frame.start + 1 && input.get(i - 1) == Qualifier.EACH) {
          nested.enclosingOwner = frame.enclosingOwner; nested.enclosingRole = frame.enclosingRole;
        }
        frames.push(nested);
      } else if (")".equals(token)) {
        require(frames.size() > 1 && frame.complete && !frame.awaitingRelationshipTarget,
            "Complete the grouped expression before closing it.");
        Concept grouped = frame.concept;
        frames.pop();
        frame = frames.peek();
        require(admits(frame.scope, grouped), "This grouped expression is not a valid operand here.");
        complete(frame, input, i);
      } else if (token == Qualifier.EACH) {
        // The grammar permits each at the beginning of a ConceptExpression or directly after
        // a semantic clause such as 'of'. Binary logical operands require a group around each.
        boolean atStart = i == frame.start;
        boolean afterClause = i > 0 && input.get(i - 1) instanceof SemanticLexicalElement modifier
            && modifier.role != SemanticRole.RELATIONSHIP_SOURCE
            && modifier.role != SemanticRole.RELATIONSHIP_TARGET;
        require(!frame.complete && !frame.value && (atStart || afterClause),
            "The each qualifier must start a concept or follow a semantic clause.");
        frame.collectiveExpression |= atStart;
        var required = ObservableValidator.conceptAttributeRules().stream()
            .filter(rule -> rule.attribute() == ObservableValidator.ConceptAttribute.EACH)
            .findFirst().orElseThrow().requiredConceptType();
        var narrowed = new SemanticScope();
        for (var constraint : frame.scope.logicalRealm) {
          for (var type : required) narrowed.logicalRealm.add(SemanticScope.Constraint.of(constraint, type));
        }
        narrowed.logicalRealm.add(SemanticScope.Constraint.of(SemanticType.PREDICATE));
        narrowed.lexicalRealm.add(SemanticRole.GROUP_OPEN);
        if (atStart) narrowed.lexicalRealm.add(SemanticRole.UNARY_OPERATOR);
        frame.scope = narrowed;
      } else if (token instanceof Concept concept) {
        boolean predicate = concept.is(SemanticType.PREDICATE) && !concept.is(SemanticType.OBSERVABLE);
        require(admits(frame.scope, concept) || (predicate && !frame.scope.logicalRealm.isEmpty()),
            "This concept is not a valid operand here.");
        if (predicate) {
          var combined = new ArrayList<>(frame.predicates);
          combined.add(concept);
          require(clauseSupport.predicatesCompatible(combined), "This predicate is disjoint with an entered predicate.");
          frame.predicates.add(concept);
          frame.complete = false;
          // A predicate may complete a unary expression (e.g. type of), or prefix a future head.
          try {
            complete(frame, input, i);
            // A bare predicate can also remain a prefix for a subsequent head concept.
            if (frame.concept.is(SemanticType.PREDICATE))
              frame.scope.logicalRealm.addAll(SemanticScope.root().logicalRealm);
          } catch (IllegalArgumentException incomplete) { }
        } else {
          complete(frame, input, i);
          frame.predicates.clear();
        }
      } else if (token instanceof UnarySemanticOperator operator) {
        require(frame.scope.lexicalRealm.contains(SemanticRole.UNARY_OPERATOR), "A unary operator cannot occur here.");
        require(operator.declaration.length == 1, "Multi-operand unary operators are not yet supported.");
        var resultTypes = operator.apply(Set.of());
        if (resultTypes == null) resultTypes = Set.of(operator.returnType);
        var producedTypes = resultTypes;
        require(frame.scope.logicalRealm.stream().anyMatch(constraint -> constraint.admitsResult(producedTypes)),
            "This operator produces a result of the wrong category for this operand.");
        frame.scope = operands(ObservableValidator.unaryOperatorRules().stream()
            .filter(rule -> rule.operator() == operator).findFirst()
            .map(ObservableValidator.UnaryOperatorRule::requiredOperandType)
            .orElse(operator.getAllowedOperandTypes()));
        frame.complete = false;
      } else if (token instanceof SemanticLexicalElement modifier) {
        require(frame.awaitingRelationshipTarget == (modifier == SemanticLexicalElement.TO),
            "A linking source must be followed by to and a target.");
        require(frame.complete && modifier.role != null && modifier.argument != null
            && modifier.applicable.stream().anyMatch(frame.concept::is)
            && !frame.used.contains(modifier.role), "This clause is not applicable here.");
        frame.clauseOwner = frame.concept;
        frame.predicates.clear();
        frame.clauseRole = modifier.role;
        frame.operandStart = i + 1;
        frame.used.add(modifier.role);
        frame.scope = operands(ObservableValidator.clauseRules().stream()
            .filter(rule -> rule.clause().role == modifier.role).findFirst()
            .map(ObservableValidator.ClauseRule::requiredArgumentType).orElse(modifier.argument));
        if (modifier == SemanticLexicalElement.OF && frame.concept.is(SemanticType.PREDICATE))
          frame.scope.logicalRealm.add(SemanticScope.Constraint.of(SemanticType.QUALITY));
        frame.complete = false;
      } else if (token instanceof BinarySemanticOperator operator) {
        require(!frame.awaitingRelationshipTarget, "Complete linking ... to ... first.");
        require(frame.complete, "A binary operator needs a complete left operand.");
        require(operator != BinarySemanticOperator.FOLLOWS || frame.concept.is(SemanticType.EVENT),
            "The follows operator requires events.");
        frame.scope = operands(Set.of(SemanticType.fundamentalType(frame.concept.getType())));
        frame.complete = false;
      } else if (token instanceof ValueOperator operator) {
        require(frame.complete && frame.concept.is(SemanticType.QUALITY), "Value operators require a quality.");
        require(operator != ValueOperator.WHERE, "The where clause is not yet supported.");
        frame.complete = false;
        frame.scope = new SemanticScope();
        if (operator.nArguments == 0) complete(frame, input, i);
        else if (operator == ValueOperator.BY || operator == ValueOperator.DOWN_TO) {
          frame.scope = operands(Set.of(SemanticType.CLASS, SemanticType.COUNTABLE));
        } else {
          frame.value = true;
          frame.scope.lexicalRealm.add(SemanticRole.INLINE_VALUE);
        }
      } else if (token instanceof Literal) {
        require(frame.value, "A literal value is not expected here.");
        complete(frame, input, i);
      } else throw new IllegalArgumentException("Unsupported component.");
    }
    Observable observable = null;
    if (frames.size() == 1 && frames.peek().complete && !frames.peek().awaitingRelationshipTarget) {
      observable = resolve(declaration(input));
      require(observable.is(SemanticType.PREDICATE) && reasoner.inherent(observable.getSemantics()) == null
          || resultTypes.isEmpty() || resultTypes.stream().anyMatch(observable::is),
          "This observable does not match the requested result category.");
    }
    return new State(frames, observable);
  }

  private void complete(Frame frame, List<Object> input, int end) {
    if (frame.clauseRole != null) {
      var operand = resolve(declaration(input.subList(frame.operandStart, end + 1))).getSemantics();
      // Predicates may prefix an operand, but cannot consume the clause's required head.
      // In particular, preserve the countable scope after 'of Red' so Tree stays available.
      require(!operand.is(SemanticType.PREDICATE) || operand.is(SemanticType.OBSERVABLE),
          "Qualify the required clause operand with this predicate.");
      require(admits(frame.scope, operand), "The clause still requires an operand of the expected type.");
      validatePredicates(frame, operand);
    }
    // A source is a complete operand, but 'R linking S' is deliberately not a complete
    // Observable expression. Keep the relationship owner until the mandatory target arrives.
    if (frame.clauseRole == SemanticRole.RELATIONSHIP_SOURCE) {
      var operand = resolve(declaration(input.subList(frame.operandStart, end + 1))).getSemantics();
      require(clauseSupport.accepts(frame.clauseOwner, frame.clauseRole, operand),
          "The linking source must specialize its inherited links filler.");
      frame.concept = frame.clauseOwner;
      frame.clauseRole = null;
      frame.clauseOwner = null;
      frame.complete = true;
      frame.awaitingRelationshipTarget = true;
      frame.scope = new SemanticScope();
      frame.scope.lexicalRealm.add(SemanticRole.RELATIONSHIP_TARGET);
      return;
    }
    Observable observable = resolve(declaration(input.subList(frame.start, end + 1)));
    if (frame.clauseRole == null) validatePredicates(frame, observable.getSemantics());
    if (frame.collectiveExpression) {
      var required = ObservableValidator.conceptAttributeRules().stream()
          .filter(rule -> rule.attribute() == ObservableValidator.ConceptAttribute.EACH)
          .findFirst().orElseThrow().requiredConceptType();
      require(required.stream().anyMatch(observable::is) && observable.getSemantics().isCollective(),
          "The each qualifier must produce a collective countable observable.");
    }
    if (frame.enclosingRole != null) {
      require(clauseSupport.accepts(frame.enclosingOwner, frame.enclosingRole, observable.getSemantics()),
          "The grouped operand must specialize its enclosing clause restriction.");
    }
    if (frame.clauseRole != null) {
      // Resolve the whole operand, including predicates, unary operators and closed groups.
      // Checking only the final lexical concept would reject valid compound specializations.
      if (clauseSupport.hasBounds(frame.clauseOwner, frame.clauseRole)) {
        var operand = resolve(declaration(input.subList(frame.operandStart, end + 1))).getSemantics();
        require(clauseSupport.accepts(frame.clauseOwner, frame.clauseRole, operand),
            "The clause operand must specialize its existing direct or inherited restriction.");
      }
      frame.clauseRole = null;
      frame.clauseOwner = null;
      frame.awaitingRelationshipTarget = false;
    }
    frame.concept = observable.getSemantics();
    frame.complete = true;
    frame.value = false;
    frame.scope = new SemanticScope();
    frame.scope.lexicalRealm.add(SemanticRole.BINARY_OPERATOR);
    if (frame.concept.is(SemanticType.QUALITY)) frame.scope.lexicalRealm.add(SemanticRole.VALUE_OPERATOR);
    for (var modifier : SemanticLexicalElement.values()) {
      if (modifier.role != null && modifier != SemanticLexicalElement.TO && !frame.used.contains(modifier.role)
          && modifier.applicable.stream().anyMatch(frame.concept::is))
        frame.scope.lexicalRealm.add(modifier.role);
    }
  }

  private void validatePredicates(Frame frame, Concept target) {
    if (target.is(SemanticType.PREDICATE) && !target.is(SemanticType.OBSERVABLE)) return;
    for (var predicate : frame.predicates)
      require(clauseSupport.applicableTo(predicate, target),
          "This predicate cannot qualify a concept outside its applies to domain.");
  }

  private Observable resolve(String declaration) {
    Observable observable;
    try {
      observable = reasoner.resolveObservable(declaration);
      require(observable != null && observable.getSemantics() != null
          && (observable.is(SemanticType.OBSERVABLE) || observable.is(SemanticType.PREDICATE))
          && !observable.is(SemanticType.NOTHING) && reasoner.satisfiable(observable.getSemantics()),
          "The expression does not resolve to a consistent observable.");
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("The Reasoner cannot validate this expression: " + declaration, ex);
    }
    return observable;
  }

  private static SemanticScope operands(Collection<?> types) {
    var scope = new SemanticScope();
    for (Object type : types) scope.logicalRealm.add(type instanceof SemanticScope.Constraint constraint
        ? constraint : SemanticScope.Constraint.of(type));
    scope.logicalRealm.add(SemanticScope.Constraint.of(SemanticType.PREDICATE));
    scope.lexicalRealm.add(SemanticRole.GROUP_OPEN);
    scope.lexicalRealm.add(SemanticRole.UNARY_OPERATOR);
    return scope;
  }

  private static boolean admits(SemanticScope scope, Concept concept) {
    return scope.logicalRealm.stream().anyMatch(c -> c.matches(concept));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  private static Literal literal(String text) {
    require(text != null && text.length() <= 256, "Enter a number, boolean, or quoted text value.");
    String value = text.strip();
    if (value.equals("true") || value.equals("false")) return new Literal(value);
    if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
      String content = value.substring(1, value.length() - 1);
      require(content.chars().noneMatch(c -> c == 34 || c == 92 || Character.isISOControl(c)),
          "Quoted values cannot contain quotes, escapes, or control characters.");
      return new Literal(value);
    }
    try {
      var number = new BigDecimal(value);
      require(Math.abs((long) number.scale()) <= 1000 && number.precision() <= 256,
          "The numeric value is too large.");
      return new Literal(number.toPlainString());
    }
    catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a number, boolean, or quoted text value."); }
  }

  private StyledKimToken styled(Object token) {
    if (token == Qualifier.EACH) {
      var styled = new StyledKimToken();
      styled.setValue("each");
      styled.setColor(org.integratedmodelling.klab.api.lang.kim.style.KimStyle.Color.KEYWORD);
      styled.setFont(org.integratedmodelling.klab.api.lang.kim.style.KimStyle.FontStyle.BOLD);
      styled.setNeedsWhitespaceBefore(true);
      styled.setNeedsWhitespaceAfter(true);
      return styled;
    }
    if (token instanceof Literal literal) {
      var styled = new StyledKimToken();
      styled.setValue(literal.code());
      styled.setNeedsWhitespaceBefore(true);
      styled.setNeedsWhitespaceAfter(true);
      return styled;
    }
    return StyledKimToken.create(token);
  }

  private String declaration(List<Object> input) {
    return String.join(" ", input.stream().map(t -> styled(t).getValue()).toList());
  }
}
