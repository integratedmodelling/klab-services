package org.integratedmodelling.klab.runtime.computation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class GroovyExpressionTest {

  private final GroovyProcessor processor = new GroovyProcessor();

  @Test
  void evaluatesStandaloneGroovyWithPairsAndMaps() {
    Expression expression = descriptor("a + b * 2", List.of(), List.of()).compile();

    assertEquals(11, expression.eval(null, "a", 3, "b", 4));
    assertEquals(11, expression.eval(null, Map.of("a", 3, "b", 4)));
  }

  @Test
  void rejectsMalformedStandaloneParameters() {
    Expression expression = descriptor("a", List.of(), List.of()).compile();

    assertThrows(KlabIllegalArgumentException.class, () -> expression.eval(null, "a"));
    assertThrows(KlabIllegalArgumentException.class, () -> expression.eval(null, 1));
    assertThrows(
        KlabIllegalArgumentException.class, () -> expression.eval(null, Map.of(1, "value")));
  }

  @Test
  void recompilesAfterSerialization() throws Exception {
    Expression expression = descriptor("a + 1", List.of(), List.of()).compile();
    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) {
      output.writeObject(expression);
    }

    Expression restored;
    try (var input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Expression) input.readObject();
    }
    assertEquals(4, restored.eval(null, "a", 3));
  }

  @Test
  void preservesWhitespaceAndClassifiesScalarAndObjectUses() {
    Observation elevation = observation("elevation", true);
    Observation slope = observation("slope", true);
    var descriptor =
        descriptor("(elevation.max - elevation) / slope", List.of(), List.of(elevation, slope));

    assertEquals("(elevationObs.max - elevation) / slope", descriptor.getProcessedCode());
    var elevationId = descriptor.getIdentifiers().get("elevation");
    assertEquals(1, elevationId.scalarReferenceCount());
    assertEquals(1, elevationId.nonScalarReferenceCount());
    assertEquals(List.of("max"), elevationId.methodsCalled());
    assertEquals(1, descriptor.getIdentifiers().get("slope").scalarReferenceCount());
  }

  @Test
  void transformsEveryObjectUseWithoutLosingTokenAlignment() {
    Observation elevation = observation("elevation", true);
    Observation slope = observation("slope", true);
    var descriptor =
        descriptor("elevation.max + slope.min + elevation", List.of(), List.of(elevation, slope));

    assertEquals(
        "elevationObs.max + slopeObs.min + elevation", descriptor.getProcessedCode());
    assertEquals(List.of("max"), descriptor.getIdentifiers().get("elevation").methodsCalled());
    assertEquals(List.of("min"), descriptor.getIdentifiers().get("slope").methodsCalled());
  }

  @Test
  void recognizesGroovySafeNavigationAsObjectUse() {
    Observation elevation = observation("elevation", true);
    var descriptor = descriptor("elevation?.max", List.of(), List.of(elevation));

    assertEquals("elevationObs?.max", descriptor.getProcessedCode());
    assertEquals(0, descriptor.getIdentifiers().get("elevation").scalarReferenceCount());
    assertEquals(1, descriptor.getIdentifiers().get("elevation").nonScalarReferenceCount());
  }

  @Test
  void resolvesSemanticLiteralsAtEvaluationTime() {
    Concept concept = mock(Concept.class);
    Reasoner reasoner = mock(Reasoner.class);
    Scope scope = mock(Scope.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveConcept("geography:Stream")).thenReturn(concept);

    var descriptor = descriptor("geography:Stream", List.of(), List.of());
    assertTrue(descriptor.getProcessedCode().trim().startsWith("_concept_"));
    assertTrue(descriptor.getTemplateFields().getFirst().contains("'geography:Stream'"));
    assertSame(concept, descriptor.compile().eval(scope));
  }

  @Test
  void leavesKlabLikeTextInsideStringsAndCommentsUntouched() {
    String code = "'geography:Stream' + ' {{earth:Thing}}' // ecology:Comment";
    var descriptor = descriptor(code, List.of(), List.of());

    assertEquals(code, descriptor.getProcessedCode());
    assertTrue(descriptor.getTemplateFields().isEmpty());
  }

  @Test
  void preservesStringLiteralsWhileContextualIdentifiersAreProcessed() {
    Observation elevation = observation("elevation", true);
    var descriptor = descriptor("elevation + ':x'", List.of(), List.of(elevation));

    assertTrue(descriptor.getProcessedCode().contains("':x'"));
    assertEquals("2:x", descriptor.compile().eval(null, "elevation", 2));
  }

  @Test
  void honorsPreprocessingOptions() {
    Observation elevation = observation("elevation", true);
    String code = "elevation.max";

    var ignored =
        descriptor(code, List.of(), List.of(elevation), Expression.CompilerOption.IgnoreContext);
    assertEquals(code, ignored.getProcessedCode());
    assertFalse(ignored.getIdentifiers().containsKey("elevation"));

    var raw =
        descriptor(
            "geography:Stream",
            List.of(),
            List.of(),
            Expression.CompilerOption.DoNotPreprocess);
    assertEquals("geography:Stream", raw.getProcessedCode());
    assertTrue(raw.getTemplateFields().isEmpty());
  }

  @Test
  void scansAContextWhenRequested() {
    Observation elevation = observation("elevation", true);
    ContextScope scope = mock(ContextScope.class);
    when(scope.getObservations()).thenReturn(List.of(elevation));

    var descriptor =
        (GroovyProcessor.GroovyDescriptor)
            processor.analyze(
                ExpressionCode.of("elevation", Language.DEFAULT_EXPRESSION_LANGUAGE),
                scope,
                List.of(),
                List.of(),
                Expression.CompilerOption.ScanContext);

    assertSame(elevation, descriptor.getIdentifiers().get("elevation").observation());
  }

  @Test
  void reportsUnsupportedLocatorsInsteadOfCorruptingTokens() {
    Observation elevation = observation("elevation", true);
    var descriptor = descriptor("elevation@S(1)", List.of(), List.of(elevation));

    assertFalse(descriptor.getNotifications().isEmpty());
    assertThrows(IllegalArgumentException.class, descriptor::compile);
  }

  @Test
  void reportsInvalidGroovyDuringAnalysis() {
    var descriptor = descriptor("1 +", List.of(), List.of());

    assertFalse(descriptor.getNotifications().isEmpty());
    assertThrows(IllegalArgumentException.class, descriptor::compile);
  }

  private GroovyProcessor.GroovyDescriptor descriptor(
      String code,
      List<Observation> outputs,
      List<Observation> inputs,
      Expression.CompilerOption... options) {
    return (GroovyProcessor.GroovyDescriptor)
        processor.analyze(
            ExpressionCode.of(code, Language.DEFAULT_EXPRESSION_LANGUAGE),
            null,
            outputs,
            inputs,
            options);
  }

  private Observation observation(String name, boolean quality) {
    Concept semantics = mock(Concept.class);
    when(semantics.codeName()).thenReturn(name);
    when(semantics.is(SemanticType.QUALITY)).thenReturn(quality);
    Observable observable = mock(Observable.class);
    when(observable.getStatedName()).thenReturn(name);
    when(observable.getSemantics()).thenReturn(semantics);
    Observation observation = mock(Observation.class);
    when(observation.getObservable()).thenReturn(observable);
    return observation;
  }
}
