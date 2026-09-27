package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.languages.KimStandaloneSetup;
import org.integratedmodelling.languages.NamespaceSyntaxImpl;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.kim.Model;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class LookupTableLanguageAdapterTest {

  @Test
  void adaptsAndTransportsAForwardReferenceToTheStagingTable() throws Exception {
    var syntax = syntax(source());
    var namespace = LanguageAdapter.INSTANCE.adaptNamespace(syntax, "test-project", List.of(), 1L);

    var model = assertInstanceOf(KimModel.class, namespace.getStatements().get(0));
    var lookup = model.getContextualization().getFirst().getLookupTable();
    assertTable(lookup);
    assertEquals(
        "staging.vxii.test.lut.basic.RECREATION_OPPORTUNITY_TABLE", lookup.getUrn());

    var definition = assertInstanceOf(KimSymbolDefinition.class, namespace.getStatements().get(1));
    assertTable(assertInstanceOf(KimLookupTable.class, definition.getValue()));

    var mapper = JacksonConfiguration.newObjectMapper();
    var transported =
        mapper.readValue(
            mapper.writerFor(KimNamespace.class).writeValueAsString(namespace), KimNamespace.class);
    var transportedModel = (KimModel) transported.getStatements().get(0);
    assertTable(transportedModel.getContextualization().getFirst().getLookupTable());
  }

  @Test
  void invalidContextualizationTargetBecomesDocumentNotification() {
    var namespace =
        LanguageAdapter.INSTANCE.adaptNamespace(
            syntax(
                """
                private namespace test.invalid.target version 1.0;

                model test:Score
                  observing test:Elevation
                  set elevation to [1];
                """),
            "test-project",
            List.of(),
            1L);

    var model = assertInstanceOf(KimModel.class, namespace.getStatements().getFirst());
    assertTrue(model.getContextualization().isEmpty());
    assertFalse(model.getNotifications().isEmpty());
    assertTrue(
        model.getNotifications().stream()
            .anyMatch(
                notification ->
                    notification.getLevel() == Notification.Level.Error
                        && notification
                            .getMessage()
                            .contains("Unknown contextualization target elevation")));
    assertTrue(
        namespace.getNotifications().stream()
            .anyMatch(
                notification ->
                    notification
                        .getMessage()
                        .contains("Unknown contextualization target elevation")));
  }

  private static void assertTable(KimLookupTable lookup) {
    assertEquals(9, lookup.getTable().getRowCount());
    assertEquals(4, lookup.getTable().getColumnCount());
    assertEquals(
        List.of("remoteness", "recreation_potential", "score", "description"),
        lookup.getTable().getHeaders());
    // Definitions do not choose a result until invoked; model references do.
    if (!lookup.getArguments().isEmpty()) {
      assertEquals(2, lookup.getLookupColumnIndex());
      assertEquals("*", lookup.getArguments().get(3).id);
    }
    assertEquals(
        "low provision, easily accessible", lookup.getTable().row(0)[3].getStringMatch());
    assertEquals(
        "high provision, not easily accessible", lookup.getTable().row(8)[3].getStringMatch());
  }

  private static NamespaceSyntaxImpl syntax(String text) {
    IParser parser =
        new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var result = parser.parse(new StringReader(text));
    var errors = new ArrayList<String>();
    result.getSyntaxErrors().forEach(node -> errors.add(node.getSyntaxErrorMessage().getMessage()));
    assertTrue(errors.isEmpty(), errors.toString());
    var root = (Model) result.getRootASTElement();
    new ResourceImpl(URI.createURI("memory:/staging-lut.kim")).getContents().add(root);
    return new NamespaceSyntaxImpl(root, scope()) {
      @Override
      protected void logWarning(
          ParsedObject target, EObject object, EStructuralFeature feature, String message) {}

      @Override
      protected void logError(
          ParsedObject target, EObject object, EStructuralFeature feature, String message) {
        throw new AssertionError(message);
      }
    };
  }

  private static LanguageValidationScope scope() {
    return new LanguageValidationScope() {
      @Override
      public ConceptDescriptor getConceptDescriptor(String urn) {
        var parts = urn.split(":", 2);
        return new ConceptDescriptor(
            parts[0], parts[1], SemanticSyntax.Type.QUANTITY, urn, urn, false, false);
      }

      @Override
      public void notifyCoreConcept(String urn, SemanticSyntax.Type type) {}

      @Override
      public LanguageValidationScope contextualize(EObject context) {
        return this;
      }
    };
  }

  private static String source() {
    return """
        private namespace staging.vxii.test.lut.basic version 1.0;

        model test:Score
          lookup (remoteness, recreation_potential, ?, *) into RECREATION_OPPORTUNITY_TABLE;

        define RECREATION_OPPORTUNITY_TABLE as
        ===
          remoteness | recreation_potential | score | description
          ---------------------------------------------------------
          <= 0.25     | < 0.5                | 1     | 'low provision, easily accessible',
          0.25 to 0.5 | < 0.5                | 2     | 'low provision, accessible',
          > 0.5       | < 0.5                | 3     | 'low provision, not easily accessible',
          <= 0.25     | 0.5 to 0.75          | 4     | 'medium provision, easily accessible',
          0.25 to 0.5 | 0.5 to 0.75          | 5     | 'medium provision, accessible',
          > 0.5       | 0.5 to 0.75          | 6     | 'medium provision, not easily accessible',
          <= 0.25     | > 0.75               | 7     | 'high provision, easily accessible',
          0.25 to 0.5 | > 0.75               | 8     | 'high provision, accessible',
          > 0.5       | > 0.75               | 9     | 'high provision, not easily accessible'
        ===;
        """;
  }
}
