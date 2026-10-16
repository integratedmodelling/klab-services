package org.integratedmodelling.klab.services.resources.storage;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.lang.WorldviewValidationScope;
import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.eclipse.xtext.parser.IParser;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityProposals;
import org.junit.jupiter.api.Test;
class WorkspaceDocumentSourceTest {
  @org.junit.jupiter.api.BeforeAll static void initialize() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
  }
  @Test void ontologySourceIncludesDocumentWhitespaceAndComments() {
    String source = " \r\n// before\r\nontology test in domain root version 1.0.0;\r\n// after\r\n ";
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source));
    assertFalse(parsed.hasSyntaxErrors());
    var root = (org.integratedmodelling.languages.worldview.Ontology) parsed.getRootASTElement();
    var syntax = new OntologySyntaxImpl(root, new WorldviewValidationScope()) {
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) { fail(message); }
    };
    var document = LanguageAdapter.INSTANCE.adaptOntology(syntax, "project", List.of(), 1L);
    WorkspaceManager.preserveDocumentSource(document, root);
    assertEquals(source, document.getSourceCode());
  }

  @Test void proposalSourceHashIncludesLeadingAndTrailingHiddenTokens() {
    for (var ending : List.of("\n", "\r\n", "\r\n// trailing comment\r\n  \r\n")) {
      checkProposalSource(ending);
    }
  }

  private void checkProposalSource(String ending) {
    String source = "  \r\n// leading comment\r\nprivate namespace test.proposals version 1.0;\n"
        + "@proposal(authoritycode=\"taxonomy.species:FelisCatus\")\nmodel each TAXA:[3DXV3] life:Individual;" + ending;
    var parser = new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source)); assertFalse(parsed.hasSyntaxErrors());
    var root = (org.integratedmodelling.languages.kim.Model) parsed.getRootASTElement();
    new ResourceImpl(URI.createURI("memory:/proposals.kim")).getContents().add(root);
    var scope = new WorldviewValidationScope() {
      @Override public ConceptDescriptor getConceptDescriptor(String name) {
        if ("life:Individual".equals(name)) return new ConceptDescriptor("life", "Individual",
            org.integratedmodelling.languages.api.SemanticSyntax.Type.SUBJECT, "Individual", "", false, false);
        return super.getConceptDescriptor(name);
      }
    };
    var syntax = new NamespaceSyntaxImpl(root, scope) {
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) { fail(message); }
    };
    var document = LanguageAdapter.INSTANCE.adaptNamespace(syntax, "project", List.of(), 1L);
    WorkspaceManager.preserveDocumentSource(document, root);
    assertEquals(source, document.getSourceCode());
    var extraction = AuthorityProposals.extract(document);
    assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(source), extraction.candidates().getFirst().source().sourceHash());
    assertTrue(extraction.notifications().isEmpty(), extraction.notifications().toString());
    assertEquals(1, extraction.candidates().size());
    assertEquals("3DXV3", extraction.candidates().getFirst().identity());
    assertEquals("taxonomy.species", extraction.candidates().getFirst().namespace());
  }
}

