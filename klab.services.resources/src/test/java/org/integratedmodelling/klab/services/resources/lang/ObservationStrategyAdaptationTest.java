package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservationPlanImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.languages.ObservationStandaloneSetup;
import org.integratedmodelling.languages.ObservationSyntaxAdapter;
import org.integratedmodelling.languages.ParsedObjectImpl;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.junit.jupiter.api.Test;

class ObservationStrategyAdaptationTest {
  @Test void languageGuideUnaryPatternsAndExplicitYieldAdapt() {
    var document = adapt("""
        strategies guide version 2.0;
        strategy 1 named presence.direct
          for presence of earth:Region
          observe $this to direct;
        strategy 1 named count.direct
          for count of earth:Region
          observe $this to direct
          yield direct;
        strategy 1 named presence.projected
          for pattern {
            node {
              semantic_operator = operator("presence",
                operand = capture entity as semantic(is, {{ earth:Region }}));
            }
          }
          observe $this to direct;
        """);
    assertEquals(3, document.getStatements().size());
    assertNotNull(document.getStatements().get(0).getSelection().getAlternatives().getFirst().getObservable());
    assertNotNull(document.getStatements().get(1).getSelection().getAlternatives().getFirst().getObservable());
    assertInstanceOf(KimObservationPlan.PlanYield.class, document.getStatements().get(1).getPlan().getSteps().getLast());
    var node = (KimObservationPlan.NodePattern) document.getStatements().get(2)
        .getSelection().getAlternatives().getFirst().getPattern().getExpression();
    var field = (KimObservationPlan.ChildPatternField) node.getFields().getFirst();
    assertEquals(KimObservationPlan.PatternChild.SEMANTIC_OPERATOR, field.getChild());
    var operator = (KimObservationPlan.ValueOperatorPattern) field.getValue();
    // Operator projection is unevaluated: the current datatype retains quoted spelling.
    assertEquals("\"presence\"", operator.getOperator());
    assertInstanceOf(KimObservationPlan.CapturePattern.class, operator.getSlots().getFirst().getPattern());
  }

  private final IParser parser = new ObservationStandaloneSetup()
      .createInjectorAndDoEMFRegistration().getInstance(IParser.class);
  private final LanguageValidationScope scope = new LanguageValidationScope() {
    @Override public ConceptDescriptor getConceptDescriptor(String urn) {
      var parts = urn.split(":", 2);
      return new ConceptDescriptor(parts[0], parts[1],
          parts[1].contains("Temperature") ? SemanticSyntax.Type.TEMPERATURE : SemanticSyntax.Type.SUBJECT,
          urn, "test concept", false, false);
    }
    @Override public void notifyCoreConcept(String urn, SemanticSyntax.Type type) {}
    @Override public LanguageValidationScope contextualize(EObject context) { return this; }
  };

  private ObservationSyntax.StrategyDocument syntax(String text) {
    var result = parser.parse(new StringReader(text));
    var errors = new ArrayList<String>();
    result.getSyntaxErrors().forEach(node -> errors.add(node.getSyntaxErrorMessage().getMessage()));
    assertTrue(errors.isEmpty(), errors.toString());
    var root = (org.integratedmodelling.languages.observation.ObservationDocument) result.getRootASTElement();
    new ResourceImpl(URI.createURI("memory:/strategy.obs")).getContents().add(root);
    return (ObservationSyntax.StrategyDocument) new ObservationSyntaxAdapter() {
      @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) {}
      @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) {
        fail(message);
      }
    }.adapt(root, scope);
  }

  private KimObservationStrategyDocument adapt(String text) {
    return LanguageAdapter.INSTANCE.adaptStrategies(syntax(text), "example.project", List.of(), 123L);
  }

  private String fixture(String name) throws Exception {
    try (var input = getClass().getResourceAsStream("/observation/" + name)) {
      assertNotNull(input, name);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test void corpusAdaptsAndRoundTripsThroughRegisteredInterfaces() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    Set<Class<?>> classes = new HashSet<>();
    for (String file : List.of("observations-proposed.obs", "contexts-proposed.obs", "patterns.obs", "tuples.obs")) {
      var text = fixture(file);
      KimObservationStrategyDocument document = adapt(text);
      assertEquals(text, document.getSourceCode());
      assertEquals(2, document.getModelVersion());
      assertEquals("example.project", document.getProjectName());
      assertEquals(123L, document.getLastUpdateTimestamp());
      assertNotNull(document.getVersion());
      if (file.equals("observations-proposed.obs")) assertEquals(8, document.getStatements().size());
      String json = mapper.writerFor(KimObservationStrategyDocument.class).writeValueAsString(document);
      assertFalse(json.contains("org.integratedmodelling.languages."), "No syntax/EMF classes on the wire");
      assertFalse(json.contains("org.eclipse.emf"));
      var restored = mapper.readValue(json, KimObservationStrategyDocument.class);
      assertEquals(normalizeSets(mapper.readTree(json)), normalizeSets(mapper.readTree(mapper.writeValueAsString(restored))));
      assertEquals(document.importedNamespaces(false), restored.importedNamespaces(false));
      for (var strategy : restored.getStatements()) {
        assertEquals("example.project", strategy.getProjectName());
        assertEquals(document.getUrn(), strategy.getNamespace());
        assertTrue(strategy.getSource().getCode().contains(
            text.substring(strategy.getOffsetInDocument(), strategy.getOffsetInDocument() + strategy.getLength())),
            "The retained Xtext fragment may also contain leading comments/whitespace");
        // Each statement must also travel by its interface outside its parent document.
        var strategyJson = mapper.writerFor(KimObservationStrategy.class).writeValueAsString(strategy);
        assertEquals(normalizeSets(mapper.readTree(strategyJson)), normalizeSets(mapper.readTree(mapper.writeValueAsString(
            mapper.readValue(strategyJson, KimObservationStrategy.class)))));
        var nodes = new ArrayList<KimObservationPlan.Node>();
        collect(strategy.getSelection(), nodes);
        strategy.getSetup().forEach(node -> collect(node, nodes));
        collect(strategy.getPlan(), nodes);
        for (var node : nodes) {
          classes.add(node.getClass());
          assertNotNull(node.getSource());
          assertNotNull(node.getSource().getUri());
          // Deserialize through both the common node root and each node's specific public interface.
          var nodeJson = mapper.writerFor(KimObservationPlan.Node.class).writeValueAsString(node);
          assertEquals(node.getClass(), mapper.readValue(nodeJson, KimObservationPlan.Node.class).getClass());
          for (Class<?> type : node.getClass().getInterfaces()) {
            if (KimObservationPlan.Node.class.isAssignableFrom(type)) {
              var typedJson = mapper.writerFor(type).writeValueAsString(node);
              assertEquals(node.getClass(), mapper.readValue(typedJson, type).getClass());
            }
          }
        }
      }
    }
    var missing = new TreeSet<String>();
    for (var type : KimObservationPlanImpl.class.getDeclaredClasses()) {
      if (KimObservationPlan.Node.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())
          && !classes.contains(type)) missing.add(type.getSimpleName());
    }
    assertTrue(missing.isEmpty(), "Add adapted, populated round-trip fixtures for " + missing);
  }

  @Test void preservesTupleBindingsFalseValuesPortsAndTerminalMerge() throws Exception {
    var document = adapt(fixture("tuples.obs"));
    var strategy = document.getStatements().getFirst();
    var bindings = ((KimObservationPlan.LetSetup) strategy.getSetup().get(1)).getBindings();
    assertEquals(List.of("first", "remaining"), bindings.getFirst().getNames());
    assertEquals("", ((KimObservationPlan.StrategyScalar) bindings.get(1).getValue()).getText());
    assertEquals(false, ((KimObservationPlan.StrategyScalar) bindings.get(2).getValue()).getBooleanValue());
    assertEquals(12L, ((KimObservationPlan.StrategyScalar) bindings.get(3).getValue()).getNumber().longValue());
    var merge = (KimObservationPlan.GraphMerge) strategy.getPlan().getSteps().get(2);
    assertNull(merge.getName());
    assertEquals("missing_graph", merge.getRight().getName(), "Undefined names survive for the later validation pass");
    var source = merge.getRight().getSource();
    assertEquals("missing_graph", document.getSourceCode().substring(source.getOffset(), source.getOffset() + source.getLength()));
    var pattern = (KimObservationPlan.BooleanPattern) strategy.getSelection().getAlternatives().getFirst().getPattern().getExpression();
    assertEquals("remaining", ((KimObservationPlan.LogicalPattern) pattern.getOperands().get(1)).getRemainder().getName());
  }

  @Test void unnamedProducersPreserveNullNamesAcrossTransport() throws Exception {
    var document = adapt("""
        strategies anonymous version 2.0;
        strategy 0 named direct for imod:Quality observe $this;
        strategy 0 named recursive for imod:Quality resolve $this;
        """);
    var mapper = JacksonConfiguration.newObjectMapper();
    document = mapper.readValue(mapper.writerFor(KimObservationStrategyDocument.class)
        .writeValueAsString(document), KimObservationStrategyDocument.class);
    for (var strategy : document.getStatements()) {
      assertNull(((KimObservationPlan.GraphProducer) strategy.getPlan().getSteps().getFirst()).getName());
    }
  }

  @Test void retainsHeaderLiteralsAndSemanticDependencies() throws Exception {
    var document = adapt("""
        strategies headers version 3.1 using example.defaults, example.special
        metadata { label: "Example" } coverage { enabled: false nested: (1, 2) };
        strategy 0 named direct for earth:Region observe {{ climate:AirTemperature in degC }} to result;
        """);
    assertEquals(List.of("example.defaults", "example.special"), document.getImports());
    assertEquals(Set.of("example.defaults", "example.special"), document.importedNamespaces(true));
    assertTrue(document.importedNamespaces(false).containsAll(Set.of("earth", "climate")));
    assertEquals("Example", document.getMetadata().get("label"));
    assertEquals(false, document.getCoverage().get("enabled"));
    var mapper = JacksonConfiguration.newObjectMapper();
    String encoded = mapper.writerFor(KimObservationStrategyDocument.class).writeValueAsString(document);
    document = mapper.readValue(encoded, KimObservationStrategyDocument.class);
    assertEquals(normalizeSets(mapper.readTree(encoded)), normalizeSets(mapper.valueToTree(document)));
    var producer = (KimObservationPlan.GraphProducer) document.getStatements().getFirst().getPlan().getSteps().getFirst();
    var observable = ((KimObservationPlan.ClosedObservable) producer.getTarget().getExpression()).getObservable();
    assertEquals("degC", observable.getUnit());
  }

  @Test void diagnosticsBecomePortableAndReachTheDocument() throws Exception {
    var syntax = syntax("strategies diagnostics version 2.0; strategy 0 named direct for matcher example.match() observe $this to result;");
    var call = syntax.getStrategies().getFirst().getSelection().getAlternatives().getFirst().getMatcher().getCall();
    call.getNotifications().add(new ParsedObjectImpl.Notification(null,
        new LanguageValidationScope.ValidationMessage("test error", 42, LanguageValidationScope.Level.ERROR)));
    var document = LanguageAdapter.INSTANCE.adaptStrategies(syntax, "project", List.of(), 1L);
    assertEquals(1, document.getNotifications().size());
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(document), KimObservationStrategyDocument.class);
    var diagnostic = restored.getNotifications().iterator().next();
    assertEquals(Notification.Level.Error, diagnostic.getLevel());
    assertEquals("diagnostics", diagnostic.getLexicalContext().getDocumentUrn());
    assertEquals(call.getCodeOffset(), diagnostic.getLexicalContext().getOffsetInDocument());
  }

  /** The existing transport reconstructs Sets as HashSets. Only declared Set fields are unordered. */
  private static com.fasterxml.jackson.databind.JsonNode normalizeSets(com.fasterxml.jackson.databind.JsonNode node)
      throws Exception {
    if (node.isObject() && node.has("@CLASS")) {
      Class<?> type = Class.forName(node.get("@CLASS").asText());
      for (var current = type; current != null; current = current.getSuperclass()) {
        for (var field : current.getDeclaredFields()) {
          var value = node.get(field.getName());
          if (Set.class.isAssignableFrom(field.getType()) && value != null && value.isArray()) {
            var items = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            value.forEach(items::add);
            items.sort(Comparator.comparing(Object::toString));
            var array = (com.fasterxml.jackson.databind.node.ArrayNode) value;
            array.removeAll();
            items.forEach(array::add);
          }
        }
      }
    }
    if (node.isContainerNode()) for (var child : node) normalizeSets(child);
    return node;
  }

  private static void collect(KimObservationPlan.Node node, List<KimObservationPlan.Node> nodes) {
    if (node != null) {
      nodes.add(node);
      node.children().forEach(child -> collect(child, nodes));
    }
  }
}
