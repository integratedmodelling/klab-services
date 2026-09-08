package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class ObservationPatternMatcherTest {
  private final Reasoner reasoner = mock(Reasoner.class);
  private final ObservationPatternMatcher matcher = new ObservationPatternMatcher(reasoner, ignored -> null);

  private static CapturePattern capture(String name) {
    var p = new CapturePatternImpl(); p.setName(name); p.setOperand(new AnyPatternImpl()); return p;
  }
  private static BooleanPattern group(PatternBooleanMode mode, PatternExpression... operands) {
    var p = new BooleanPatternImpl(); p.setMode(mode); p.setOperands(List.of(operands)); return p;
  }
  private static ConceptImpl concept(String name, SemanticType type) {
    var c = new ConceptImpl(); c.setUrn(name); c.setType(EnumSet.of(type)); return c;
  }

  @Test void failedBooleanBranchesAndNegationDoNotLeakCaptures() {
    var captures = new LinkedHashMap<String, Object>();
    var failing = group(PatternBooleanMode.ALL, capture("lost"), new AbsentPatternImpl());
    assertFalse(matcher.match(failing, "value", captures));
    assertTrue(captures.isEmpty());
    assertTrue(matcher.match(group(PatternBooleanMode.EITHER, failing, capture("kept")), "value", captures));
    assertEquals(Map.of("kept", "value"), captures);
    var not = new NotPatternImpl(); not.setOperand(failing);
    assertTrue(matcher.match(not, "value", captures));
    assertEquals(Map.of("kept", "value"), captures);
  }

  @Test void absentFalseAndReservedNamesAreDistinct() {
    var captures = new LinkedHashMap<String, Object>();
    var scalar = new StrategyScalarImpl(); scalar.setBooleanValue(false);
    var p = new ScalarPatternImpl(); p.setValue(scalar);
    assertTrue(matcher.match(p, false, captures));
    assertFalse(matcher.match(p, null, captures));
    assertFalse(matcher.match(new AnyPatternImpl(), null, captures));
    assertTrue(matcher.match(new AbsentPatternImpl(), List.of(), captures));
    assertThrows(IllegalArgumentException.class, () -> matcher.match(capture("this"), "value", captures));
  }

  @Test void sameRequiresAnExistingCapture() {
    var same = new SamePatternImpl(); same.setName("item");
    var captures = new LinkedHashMap<String, Object>();
    assertThrows(IllegalArgumentException.class, () -> matcher.match(same, "a", captures));
    assertTrue(matcher.match(capture("item"), "a", captures));
    assertTrue(matcher.match(same, "a", captures));
    assertFalse(matcher.match(same, "b", captures));
  }

  @Test void logicalOperandsUseCanonicalOrderAndARealRemainder() {
    var root = concept("root", SemanticType.UNION);
    var a = concept("a", SemanticType.SUBJECT);
    var b = concept("b", SemanticType.SUBJECT);
    when(reasoner.operands(root)).thenReturn(List.of(b, a));
    var p = new LogicalPatternImpl(); p.setConnector(LogicalPatternConnector.OR);
    p.setOrdering(OperandOrdering.CANONICAL); p.setOperands(List.of(capture("first")));
    var rest = new RemainderCaptureImpl(); rest.setName("remaining"); p.setRemainder(rest);
    var captures = new LinkedHashMap<String, Object>();
    assertTrue(matcher.match(p, root, captures));
    assertSame(a, captures.get("first")); assertSame(b, captures.get("remaining"));
  }

  @Test void unorderedCollectionsBacktrackWithoutLeakingBindings() {
    var p = new ExactCollectionPatternImpl();
    var scalar = new StrategyScalarImpl(); scalar.setText("a");
    var a = new ScalarPatternImpl(); a.setValue(scalar);
    p.setElements(List.of(capture("other"), a));
    var captures = new LinkedHashMap<String, Object>();
    assertTrue(matcher.match(p, List.of("a", "b"), captures));
    assertEquals(Map.of("other", "b"), captures);
  }

  @Test void unsupportedPatternFieldsFailExplicitly() {
    var field = new ChildPatternFieldImpl(); field.setChild(PatternChild.VALUE_OPERATORS);
    field.setValue(new AnyPatternImpl());
    var p = new NodePatternImpl(); p.setFields(List.of(field));
    assertThrows(UnsupportedOperationException.class,
        () -> matcher.match(p, concept("subject", SemanticType.SUBJECT), new HashMap<>()));
  }
}
