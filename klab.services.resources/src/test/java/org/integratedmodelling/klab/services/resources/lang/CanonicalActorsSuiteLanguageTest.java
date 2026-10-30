package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.KActorsStandaloneSetup;
import org.integratedmodelling.languages.BehaviorSyntaxImpl;
import org.integratedmodelling.languages.kActors.Behavior;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.kactors.compiler.BehaviorAnalyzer;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.junit.jupiter.api.Test;

/** Grammar and lexical-state regression for the complete canonical staging suite snapshot. */
class CanonicalActorsSuiteLanguageTest {
  @Test void completeCanonicalSuiteParsesAndRegistersSharedDefinitionsBeforeActions() throws Exception {
    ServiceConfiguration.injectInstantiators();
    String source;
    try (var input = getClass().getResourceAsStream("/kactors/staging-canonical-testsuite.kactors")) {
      assertNotNull(input); source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
    var parser = new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source));
    var syntaxErrors = new ArrayList<String>();
    for (var node : parsed.getSyntaxErrors())
      syntaxErrors.add("line " + node.getStartLine() + ": " + node.getSyntaxErrorMessage().getMessage());
    assertFalse(parsed.hasSyntaxErrors(), syntaxErrors.toString());
    var root = (Behavior) parsed.getRootASTElement();
    new ResourceImpl(URI.createURI("memory:/staging-canonical-testsuite.kactors")).getContents().add(root);
    var validation = mock(LanguageValidationScope.class);
    when(validation.contextualize(any())).thenReturn(validation);
    // Descriptor stubs permit adaptation of literals; this test covers grammar/state binding,
    // while real-worldview semantic and verb validation is exercised by full-stack acceptance.
    when(validation.getConceptDescriptor(anyString())).thenAnswer(call -> {
      String urn = call.getArgument(0); var parts = urn.split(":", 2);
      var type = switch (parts[1]) {
        case "Elevation" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.LENGTH;
        case "Slope" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.ANGLE;
        case "Erosion" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.PROCESS;
        case "StreamConnection" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.RELATIONSHIP;
        case "HumanIndividual" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.AGENT;
        case "Terrestrial", "Aquatic", "Estuarine", "PhysicalEnvironment" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.REALM;
        case "Appropriate", "Appropriateness" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.ATTRIBUTE;
        case "African", "Continental" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.IDENTITY;
        case "Volitional", "Patient", "Participant", "Reference" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.ROLE;
        default -> org.integratedmodelling.languages.api.SemanticSyntax.Type.SUBJECT;
      };
      return new LanguageValidationScope.ConceptDescriptor(parts[0], parts[1], type, urn, "fixture",
          Set.of("PhysicalEnvironment", "Appropriateness", "Continental", "Participant").contains(parts[1]), false);
    });
    var syntax = new BehaviorSyntaxImpl(root, validation) {
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {
        throw new AssertionError(message);
      }
    };
    var notifications = new ArrayList<Notification>();
    var behavior = LanguageAdapter.INSTANCE.adaptBehavior(syntax, "klab.staging.vxii", notifications, 1L);
    assertTrue(notifications.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), notifications.toString());
    // Real traversal/lexical checks, without external semantic resolution or verb discovery.
    var analyzer = new BehaviorAnalyzer(behavior, new KActorsVisitor.LenientValidator());
    assertTrue(analyzer.analyze(), analyzer.getNotifications().toString());
    assertTrue(analyzer.getFields().stream().anyMatch(field -> field.name().equals("observer_definition")));
    assertEquals(29, behavior.getStatements().size()); // init, nine existing and nineteen selection cases
    assertTrue(behavior.getStatements().stream().anyMatch(action -> action.getUrn().equals("select_two_role_families")));
  }

  @Test void canonicalSelectionModelsParseIncludingTypedFailureParameter() throws Exception {
    var parser = new org.integratedmodelling.languages.KimStandaloneSetup()
        .createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    for (String name : List.of("selection", "selection_empty", "selection_missing",
        "selection_rollback", "selection_consumer_failure", "selection_native_dependency", "selection_deterministic")) {
      String source;
      try (var input = getClass().getResourceAsStream("/kim/selection/" + name + ".kim")) {
        assertNotNull(input, name); source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      }
      var parsed = parser.parse(new StringReader(source));
      var errors = new ArrayList<String>();
      for (var node : parsed.getSyntaxErrors())
        errors.add("line " + node.getStartLine() + ": " + node.getSyntaxErrorMessage().getMessage());
      assertFalse(parsed.hasSyntaxErrors(), name + ": " + errors);
      assertNotNull(parsed.getRootASTElement(), name);
    }
  }

  @Test void canonicalModelObservableUrnsRoundTripIncludingCollectiveCountOperands() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var kimParser = new org.integratedmodelling.languages.KimStandaloneSetup()
        .createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var observableInjector = new org.integratedmodelling.languages.ObservableStandaloneSetup()
        .createInjectorAndDoEMFRegistration();
    var parser = observableInjector.getInstance(IParser.class);
    var grammar = observableInjector.getInstance(org.integratedmodelling.languages.services.ObservableGrammarAccess.class);
    var scope = new org.integratedmodelling.klab.runtime.language.WorldviewValidationScope() {
      @Override public ConceptDescriptor getConceptDescriptor(String name) {
        var parts = name.split(":", 2);
        var type = switch (parts[1]) {
          case "PhysicalEnvironment", "Terrestrial", "Aquatic" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.REALM;
          case "Appropriateness", "Appropriate" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.ATTRIBUTE;
          case "Continental", "African" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.IDENTITY;
          case "Participant", "Patient", "Reference" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.ROLE;
          default -> org.integratedmodelling.languages.api.SemanticSyntax.Type.SUBJECT;
        };
        return new ConceptDescriptor(parts[0], parts[1], type, name, "fixture",
            Set.of("PhysicalEnvironment", "Appropriateness", "Continental", "Participant").contains(parts[1]), false);
      }
    };
    var expressions = new ArrayList<String>();
    for (var name : List.of("selection", "selection_empty", "selection_missing",
        "selection_rollback", "selection_consumer_failure", "selection_native_dependency", "selection_deterministic")) {
      String source;
      try (var input = getClass().getResourceAsStream("/kim/selection/" + name + ".kim")) {
        assertNotNull(input);source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      }
      var parsed = kimParser.parse(new StringReader(source));assertFalse(parsed.hasSyntaxErrors());
      var root = (org.integratedmodelling.languages.kim.Model) parsed.getRootASTElement();
      new ResourceImpl(URI.createURI("memory:/" + name + ".kim")).getContents().add(root);
      var syntax = new org.integratedmodelling.languages.NamespaceSyntaxImpl(root, scope) {
        @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject t,
            org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) {}
        @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject t,
            org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) { fail(m); }
      };
      var namespace = LanguageAdapter.INSTANCE.adaptNamespace(syntax, "klab.staging.vxii", List.of(), 1L);
      for (var statement : namespace.getStatements()) if (statement instanceof org.integratedmodelling.klab.api.lang.kim.KimModel model) {
        model.getObservables().forEach(observable -> expressions.add(observable.getUrn()));
        model.getDependencies().forEach(observable -> expressions.add(observable.getUrn()));
      }
    }
    expressions.addAll(List.of("earth:Region named selected optional", "count of earth:Region", "count of (each earth:Region)",
        "count of (each earth:Terrestrial earth:Region)", "count of (each agency:Patient earth:Stream)",
        "count of (each earth:Terrestrial earth:Region) named selected optional",
        "count of (each earth:Region of earth:Region) named selected optional",
        "count of (each earth:Region) > 0"));
    for (var expression : expressions) {
      String canonical = null;
      boolean optional = expression.endsWith(" optional");
      for (int pass = 0; pass < 4; pass++) {
        var parsed = parser.parse(grammar.getObservableSemanticsRule(), new StringReader(expression));
        var errors = new ArrayList<String>();for (var node : parsed.getSyntaxErrors()) errors.add(node.getSyntaxErrorMessage().getMessage());
        assertFalse(parsed.hasSyntaxErrors(), expression + ": " + errors);
        var syntax = new org.integratedmodelling.languages.ObservableSyntaxImpl(
            (org.integratedmodelling.languages.observable.ObservableSemantics) parsed.getRootASTElement(), scope) {
          @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject t,
              org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) {}
          @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject t,
              org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String m) { fail(m); }
        };
        var observable = LanguageAdapter.INSTANCE.adaptObservable(syntax, null, null, null);
        var encoded = observable.getUrn();
        var semantics = observable.getSemantics();
        if (expression.startsWith("count of")) {
          assertFalse(semantics.isCollective(), "Counting a collection produces a singular quality: " + expression);
          assertTrue(semantics.is(org.integratedmodelling.klab.api.knowledge.SemanticType.NUMEROSITY));
          assertTrue(semantics.is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY));
          if (expression.contains("(each ")) assertTrue(semantics.getObservable().isCollective(), expression);
          if (expression.contains("earth:Region of earth:Region"))
            assertTrue(encoded.contains("earth:Region of earth:Region"), "Keep the operand's inherent qualifier");
          if (expression.contains("agency:Patient")) assertTrue(encoded.contains("agency:Patient"), "Keep the role predicate");
          if (expression.contains("> 0")) assertTrue(encoded.contains("> 0"), "Keep the value requirement");
          semantics.getObservable().getUrn(); // exercise child-first vs root-first finalized reads
          ((org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl) semantics).resetDefinition();
          assertFalse(semantics.isCollective());
          assertEquals(encoded.substring(0, encoded.contains(" named ") ? encoded.indexOf(" named ") : encoded.length()),
              semantics.getUrn(), "Refreshing semantics must preserve the production suffix separately");
        }
        assertEquals(optional, observable.isOptional(), expression);
        assertFalse(encoded.contains("each each"), encoded);
        if (canonical != null) assertEquals(canonical, encoded, "Repeated adaptation must not add parentheses");
        if (expression.contains("named selected")) {
          assertEquals("selected", observable.getFormalName());
        }
        canonical = encoded;expression = encoded;
      }
    }
  }
}
