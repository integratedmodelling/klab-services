package org.integratedmodelling.klab.services.resources.lang;
import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.eclipse.xtext.parser.IParser;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityProposals;
import org.junit.jupiter.api.Test;
class AuthorityProposalLanguageTest {
  @Test void actualKimAnnotationPairsWithAuthorityIdentity() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    String source = "private namespace test.proposals version 1.0;\n"
        + "@proposal(authoritycode=\"taxonomy.species:FelisCatus\")\nmodel each TAXA:[3DXV3] life:Individual;";
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
    var extraction = AuthorityProposals.extract(document);
    assertTrue(extraction.notifications().isEmpty(), extraction.notifications().toString());
    assertEquals(1, extraction.candidates().size());
    assertEquals("3DXV3", extraction.candidates().getFirst().identity());
    assertEquals("taxonomy.species", extraction.candidates().getFirst().namespace());
  }
}
