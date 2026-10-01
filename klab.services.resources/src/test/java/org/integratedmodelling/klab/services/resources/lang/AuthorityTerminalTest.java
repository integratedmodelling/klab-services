package org.integratedmodelling.klab.services.resources.lang;
import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.ObservableStandaloneSetup;
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax;
import org.junit.jupiter.api.Test;
class AuthorityTerminalTest {
  @Test void grammarAcceptsEverySerializedCodeAndDescriptorRetainsColons() {
    var parser = new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var scope = new WorldviewValidationScope();
    for (String code : List.of("123", "a:b", "a b", "MixedCase", "x] of test:Injected", "é", "x" + (char)92 + "y")) {
      String token = AuthorityIdentitySyntax.encode("TAXA", code);
      var parsed = parser.parse(new StringReader(token + ";"));
      assertFalse(parsed.hasSyntaxErrors(), token);
      var root = (org.integratedmodelling.languages.observable.ObservableSequence) parsed.getRootASTElement();
      var syntax = new org.integratedmodelling.languages.ObservableSyntaxImpl(
          root.getObservables().getFirst().getSemantics(), scope) {
        @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
            org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
        @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
            org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {
          fail(message);
        }
      };
      var adapted = LanguageAdapter.INSTANCE.adaptObservable(syntax, "test", "test",
          org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.ONTOLOGY);
      assertEquals(token, adapted.getSemantics().getUrn());
      var descriptor = scope.getConceptDescriptor(token);
      assertEquals(token.substring(5), descriptor.conceptName());
    }
  }
}
