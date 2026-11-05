package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.knowledge.InstantiationResolution;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.junit.jupiter.api.Test;

class InstantiationResolutionSourceTest {
  @Test void unquotedPolicyModesSurviveActualKimParserAndSemanticBeanTransport() {
    ServiceConfiguration.injectInstantiators();
    var parser = new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    for (var mode : List.of("none", "collective", "individual")) {
      var parsed = parser.parse(new StringReader("private namespace test.policy;\n@resolution("+mode+")\nmodel each test:Road;"));
      assertFalse(parsed.hasSyntaxErrors(), mode);
      var root = (org.integratedmodelling.languages.kim.Model) parsed.getRootASTElement();
      new org.eclipse.emf.ecore.resource.impl.ResourceImpl(org.eclipse.emf.common.util.URI.createURI("memory:/policy.kim"))
          .getContents().add(root);
      var types = new BasicObservableValidationScope() {
        @Override public ConceptDescriptor getConceptDescriptor(String name) {
          if (name.equals("test:Road")) return new ConceptDescriptor("test", "Road", SemanticSyntax.Type.SUBJECT, "Road", "", false, false);
          return super.getConceptDescriptor(name);
        }
      };
      var syntax = new NamespaceSyntaxImpl(root, types) {
        @Override protected void logWarning(ParsedObject p, org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String message) {}
        @Override protected void logError(ParsedObject p, org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String message) { fail(message); }
      };
      var document = LanguageAdapter.INSTANCE.adaptNamespace(syntax, "project", List.of(), 1L);
      var model = (KimModel) document.getStatements().getFirst();
      var restored = org.integratedmodelling.common.utils.Utils.Json.parseObject(
          org.integratedmodelling.common.utils.Utils.Json.asString(model), KimModel.class);
      assertEquals(InstantiationResolution.Mode.valueOf(mode.toUpperCase()),
          InstantiationResolution.mode(InstantiationResolution.annotation(restored.getAnnotations())));
    }
  }
}
