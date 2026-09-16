package org.integratedmodelling.klab.indexing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class SemanticSearchSessionTest {
  private final Reasoner reasoner = mock(Reasoner.class);
  private final List<SemanticMatch> candidates = new ArrayList<>();
  private final Map<String, Observable> valid = new HashMap<>();
  private int sequence;
  private SemanticSearchResponse last;
  private final SemanticSearchSession session;

  SemanticSearchSessionTest() {
    when(reasoner.resolveObservable(anyString())).thenAnswer(call -> valid.get(call.getArgument(0)));
    when(reasoner.satisfiable(any())).thenReturn(true);
    session = new SemanticSearchSession(reasoner, (text, scope, limit) -> candidates,
        new SemanticSearchRequest());
  }

  @Test void queryingDoesNotEditAndSelectionRequiresCurrentProposalList() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    var first = call(SemanticSearchRequest.Mode.TOKEN);
    assertEquals("", first.getDeclaration()); assertNull(first.getObservable());
    call(SemanticSearchRequest.Mode.TOKEN);
    var stale = request(SemanticSearchRequest.Mode.SELECT);
    stale.setSelectedMatchId("test:Height"); stale.setMatchesRequestId(first.getRequestId());
    var rejected = session.handle(stale, 42);
    assertFalse(rejected.getErrors().isEmpty()); assertEquals("", rejected.getDeclaration());
    last = rejected;
    var selected = select("test:Height");
    assertEquals("test:Height", selected.getDeclaration()); assertNotNull(selected.getObservable());
  }

  @Test void parenthesesMustCloseAndUndoRestoresTheirState() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    valid.put("( test:Height )", valid.get("test:Height"));
    call(SemanticSearchRequest.Mode.TOKEN); call(SemanticSearchRequest.Mode.OPEN_SCOPE);
    var inner = select("test:Height");
    assertNull(inner.getObservable()); assertEquals(1, inner.getParenthesisDepth());
    assertTrue(inner.isCanCloseScope());
    assertNotNull(call(SemanticSearchRequest.Mode.CLOSE_SCOPE).getObservable());
    var undone = call(SemanticSearchRequest.Mode.UNDO);
    assertNull(undone.getObservable()); assertTrue(undone.isCanCloseScope());
    call(SemanticSearchRequest.Mode.UNDO);
    assertFalse(call(SemanticSearchRequest.Mode.CLOSE_SCOPE).getErrors().isEmpty());
    assertEquals("(", last.getDeclaration());
  }

  @Test void clausesConstrainOperandsAndNeverAcceptInvalidCompletion() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    concept("test:Tree", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    candidates.add(new SemanticMatch(SemanticLexicalElement.OF));
    valid.put("test:Height of test:Tree", valid.get("test:Height"));
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Height");
    var clause = select("of"); assertNull(clause.getObservable());
    assertTrue(clause.getMatches().stream().noneMatch(m -> m.getId().equals("test:Height")));
    assertNotNull(select("test:Tree").getObservable());
  }

  @Test void unaryOperandCategoriesUseLanguageMetadata() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    concept("test:Tree", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    candidates.add(new SemanticMatch(UnarySemanticOperator.COUNT));
    valid.put("count of test:Tree", valid.get("test:Height"));
    call(SemanticSearchRequest.Mode.TOKEN);
    var operand = select("count of");
    assertTrue(operand.getMatches().stream().noneMatch(m -> m.getId().equals("test:Height")));
    assertNotNull(select("test:Tree").getObservable());
  }

  @Test void valueOperatorsRequireAValueAndRejectInjectedExpressions() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    candidates.add(new SemanticMatch(ValueOperator.GREATER));
    valid.put("test:Height > 5", valid.get("test:Height"));
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Height");
    var operator = select(">"); assertTrue(operator.isAcceptsValue()); assertNull(operator.getObservable());
    var bad = request(SemanticSearchRequest.Mode.VALUE); bad.setQueryString("5 of test:Tree");
    assertFalse(session.handle(bad, 42).getErrors().isEmpty());
    var good = request(SemanticSearchRequest.Mode.VALUE); good.setQueryString("5");
    assertNotNull(session.handle(good, 42).getObservable());
    assertTrue(call(SemanticSearchRequest.Mode.UNDO).isAcceptsValue());
  }

  @Test void binaryOperatorsUseLanguageSpellingAndMatchingCategories() {
    concept("test:Tree", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    candidates.add(new SemanticMatch(BinarySemanticOperator.UNION));
    valid.put("test:Tree or test:Tree", valid.get("test:Tree"));
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Tree"); select("or");
    assertNotNull(select("test:Tree").getObservable());
    assertEquals("or", StyledKimToken.create(BinarySemanticOperator.UNION).getValue());
  }

  @Test void rejectedUnsatisfiableCandidatesAndOutOfOrderRequestsDoNotMutate() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Height");
    var stale = new SemanticSearchRequest(); stale.setRequestId(1); stale.setSearchMode(SemanticSearchRequest.Mode.UNDO);
    var result = session.handle(stale, 42);
    assertEquals("test:Height", result.getDeclaration()); assertFalse(result.getErrors().isEmpty());
    call(SemanticSearchRequest.Mode.UNDO);
    when(reasoner.satisfiable(any())).thenReturn(false);
    assertTrue(call(SemanticSearchRequest.Mode.TOKEN).getMatches().stream()
        .noneMatch(match -> match.getMatchType() == SemanticMatch.Type.CONCEPT));
  }

  @Test void constraintsHandleNestedNegationAndUnknownArgumentsSafely() {
    var quality = concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    assertTrue(SemanticScope.Constraint.not(SemanticType.ABSTRACT).matches(quality));
    assertFalse(SemanticScope.Constraint.not(SemanticType.QUALITY).matches(quality));
    assertTrue(SemanticScope.Constraint.of(SemanticType.QUALITY,
        SemanticScope.Constraint.not(SemanticType.ABSTRACT)).matches(quality));
    assertFalse(SemanticScope.Constraint.of("unknown constraint").matches(quality));
  }

  @Test void duringUsesTheSharedValidatorRuleRatherThanTheBroaderLexicalEnum() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    concept("test:Growth", SemanticType.OBSERVABLE, SemanticType.PROCESS);
    concept("test:Storm", SemanticType.OBSERVABLE, SemanticType.EVENT);
    candidates.add(new SemanticMatch(SemanticLexicalElement.DURING));
    valid.put("test:Height during test:Storm", valid.get("test:Height"));
    valid.put("test:Height during test:Growth", valid.get("test:Height"));
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Height");
    var response = select("during");
    assertTrue(response.getMatches().stream().anyMatch(m -> m.getId().equals("test:Storm")));
    assertTrue(response.getMatches().stream().noneMatch(m -> m.getId().equals("test:Growth")));
  }

  @Test void numericExponentsAreBoundedBeforeExpansion() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    candidates.add(new SemanticMatch(ValueOperator.GREATER));
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Height"); select(">");
    var request = request(SemanticSearchRequest.Mode.VALUE); request.setQueryString("1e999999999");
    var response = session.handle(request, 42);
    assertFalse(response.getErrors().isEmpty()); assertEquals("test:Height >", response.getDeclaration());
  }

  @Test void eachBuildsAnExplicitCollectiveAndUndoRestoresThePendingQualifier() {
    var tree = concept("test:Tree", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    valid.put("each test:Tree", collective("each test:Tree", tree));
    call(SemanticSearchRequest.Mode.TOKEN);
    var qualifier = select("each");
    assertEquals("each", qualifier.getDeclaration()); assertNull(qualifier.getObservable());
    assertTrue(qualifier.getMatches().stream().noneMatch(m -> m.getId().equals("each") || m.getId().equals("test:Height")));
    var completed = select("test:Tree");
    assertTrue(completed.getErrors().isEmpty());
    assertTrue(completed.getObservable().getSemantics().isCollective());
    assertEquals("each", call(SemanticSearchRequest.Mode.UNDO).getDeclaration());
    assertEquals("", call(SemanticSearchRequest.Mode.UNDO).getDeclaration());
  }

  @Test void eachAppliesToTheInherentWithoutMakingTheQualityCollective() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    concept("test:Tree", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    candidates.add(new SemanticMatch(SemanticLexicalElement.OF));
    valid.put("test:Height of each test:Tree", valid.get("test:Height"));
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Height"); select("of");
    assertTrue(last.getMatches().stream().anyMatch(m -> m.getId().equals("each")));
    select("each");
    var completed = select("test:Tree");
    assertEquals("test:Height of each test:Tree", completed.getDeclaration());
    assertNotNull(completed.getObservable());
    assertFalse(completed.getObservable().getSemantics().isCollective());
  }

  @Test void eachWorksInsideAnInherentGroupAndCannotBeRepeated() {
    concept("test:Height", SemanticType.OBSERVABLE, SemanticType.QUALITY);
    var tree = concept("test:Tree", SemanticType.OBSERVABLE, SemanticType.SUBJECT, SemanticType.COUNTABLE);
    candidates.add(new SemanticMatch(SemanticLexicalElement.OF));
    valid.put("each test:Tree", collective("each test:Tree", tree));
    valid.put("test:Height of ( each test:Tree )", valid.get("test:Height"));
    call(SemanticSearchRequest.Mode.TOKEN); select("test:Height"); select("of");
    call(SemanticSearchRequest.Mode.OPEN_SCOPE); select("each");
    var duplicate = select("each");
    assertFalse(duplicate.getErrors().isEmpty());
    assertEquals("test:Height of ( each", duplicate.getDeclaration());
    select("test:Tree");
    assertNotNull(call(SemanticSearchRequest.Mode.CLOSE_SCOPE).getObservable());
  }

  @Test void consecutiveOpeningParenthesesAreNotProposedOrAccepted() {
    call(SemanticSearchRequest.Mode.TOKEN);
    assertTrue(call(SemanticSearchRequest.Mode.OPEN_SCOPE).getErrors().isEmpty());
    assertFalse(last.isCanOpenScope());
    var repeated = call(SemanticSearchRequest.Mode.OPEN_SCOPE);
    assertFalse(repeated.getErrors().isEmpty()); assertEquals("(", repeated.getDeclaration());
  }

  private Observable collective(String urn, Concept base) {
    var concept = new org.integratedmodelling.common.knowledge.ConceptImpl();
    concept.setUrn(urn); concept.setType(base.getType()); concept.setCollective(true);
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setUrn(urn); observable.setSemantics(concept);
    return observable;
  }

  private Concept concept(String urn, SemanticType... types) {
    Set<SemanticType> semantics = EnumSet.copyOf(List.of(types));
    var concept = mock(Concept.class);
    when(concept.getUrn()).thenReturn(urn); when(concept.getType()).thenReturn(semantics);
    when(concept.getMetadata()).thenReturn(org.integratedmodelling.klab.api.data.Metadata.create());
    when(concept.is(any(SemanticType.class))).thenAnswer(call -> semantics.contains(call.getArgument(0)));
    when(reasoner.resolveConcept(urn)).thenReturn(concept);
    var observable = mock(Observable.class);
    when(observable.getSemantics()).thenReturn(concept); when(observable.getUrn()).thenReturn(urn);
    when(observable.is(any(SemanticType.class))).thenAnswer(call -> semantics.contains(call.getArgument(0)));
    valid.put(urn, observable);
    var match = new SemanticMatch(SemanticMatch.Type.CONCEPT, semantics);
    match.setId(urn); match.setName(urn); candidates.add(match);
    return concept;
  }
  private SemanticSearchRequest request(SemanticSearchRequest.Mode mode) {
    var request = new SemanticSearchRequest(); request.setRequestId(++sequence); request.setSearchMode(mode);
    return request;
  }
  private SemanticSearchResponse call(SemanticSearchRequest.Mode mode) {
    return last = session.handle(request(mode), 42);
  }
  private SemanticSearchResponse select(String id) {
    var request = request(SemanticSearchRequest.Mode.SELECT);
    request.setSelectedMatchId(id); request.setMatchesRequestId(last.getRequestId());
    return last = session.handle(request, 42);
  }
}
