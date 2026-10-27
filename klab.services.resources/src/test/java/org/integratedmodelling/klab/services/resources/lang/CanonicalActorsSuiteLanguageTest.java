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
        case "Terrestrial", "PhysicalEnvironment" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.REALM;
        case "Volitional" -> org.integratedmodelling.languages.api.SemanticSyntax.Type.ATTRIBUTE;
        default -> org.integratedmodelling.languages.api.SemanticSyntax.Type.SUBJECT;
      };
      return new LanguageValidationScope.ConceptDescriptor(parts[0], parts[1], type, urn, "fixture", false, false);
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
    assertEquals(10, behavior.getStatements().size()); // init and nine canonical cases
  }
}
