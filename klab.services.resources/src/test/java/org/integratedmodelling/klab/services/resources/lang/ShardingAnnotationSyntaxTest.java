package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.StringReader;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.FunctionCallSyntaxImpl;
import org.integratedmodelling.languages.KimStandaloneSetup;
import org.integratedmodelling.languages.api.FunctionCallSyntax;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.services.KimGrammarAccess;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.data.ShardingAnnotations;
import org.integratedmodelling.klab.api.data.Data;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShardingAnnotationSyntaxTest {
  private Annotation parse(String source) throws Exception {
    var injector = new KimStandaloneSetup().createInjectorAndDoEMFRegistration();
    var parsed = injector.getInstance(IParser.class).parse(
        injector.getInstance(KimGrammarAccess.class).getAnnotationRule(), new StringReader(source));
    assertFalse(parsed.hasSyntaxErrors(), source);
    var syntax = new FunctionCallSyntaxImpl(
        (org.integratedmodelling.languages.observable.Annotation) parsed.getRootASTElement(),
        mock(LanguageValidationScope.class)) {
      @Override public String encode() { return source; }
      @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) { fail(message); }
      @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) { fail(message); }
    };
    var adapter = LanguageAdapter.class.getDeclaredMethod("adaptAnnotation", FunctionCallSyntax.class,
        String.class, String.class, KlabAsset.KnowledgeClass.class);
    adapter.setAccessible(true);
    return (Annotation) adapter.invoke(LanguageAdapter.INSTANCE, syntax, "test", "test",
        KlabAsset.KnowledgeClass.NAMESPACE);
  }

  @Test
  void namedValueIsAlsoNormalizedToAnUnnamedArgument() throws Exception {
    for (var declaration : new String[][] {
        {"split", "4", "4"}, {"type", "\"float\"", "float"}}) {
      var annotation = parse("@" + declaration[0] + "(value=" + declaration[1] + ")");
      assertEquals(declaration[0], annotation.getName());
      assertEquals(declaration[2], annotation.get("_p1").toString());
      assertNull(annotation.get(Annotation.VALUE_PARAMETER_KEY));
    }
  }

  @Test
  void positionalSyntaxIsLegalButDoesNotPopulateTheResolversValueKey() throws Exception {
    var annotation = parse("@split(4)");
    assertEquals(4, ((Number) annotation.get("_p1")).intValue());
    assertNull(annotation.get(Annotation.VALUE_PARAMETER_KEY));
    assertEquals(4, ShardingAnnotations.parse(List.of(annotation)).getSuggestedSplits());
  }

  @Test
  void lowercaseSourceAnnotationsReachTheSharedStrategyDecoder() throws Exception {
    for (boolean named : new boolean[] {false, true}) {
      String prefix = named ? "value=" : "";
      var strategy = ShardingAnnotations.parse(List.of(
          parse("@split(" + prefix + "3)"), parse("@maxsize(" + prefix + "1000)"),
          parse("@minsplitsize(" + prefix + "20)"), parse("@type(" + prefix + "\"float\")"),
          parse("@fillcurve(" + prefix + "\"D2_XInvY\")")));
      assertEquals(3, strategy.getSuggestedSplits());
      assertEquals(20, strategy.getMinSplitSize());
      assertEquals(1000, strategy.getMaxBufferSize());
      assertEquals(Data.FillCurve.D2_XInvY, strategy.getCurve());
      assertEquals(org.integratedmodelling.klab.api.data.Storage.Type.FLOAT, strategy.getDataType());
    }
  }

  @Test
  void camelCaseCompilerNamesAreRejectedByTheCurrentKimGrammar() {
    var injector = new KimStandaloneSetup().createInjectorAndDoEMFRegistration();
    for (String source : new String[] {"@fillCurve(\"D2_YX\")", "@minSplitSize(16)", "@maxSize(1024)"}) {
      var parsed = injector.getInstance(IParser.class).parse(
          injector.getInstance(KimGrammarAccess.class).getAnnotationRule(), new StringReader(source));
      assertTrue(parsed.hasSyntaxErrors(), "Known grammar/compiler naming mismatch: " + source);
    }
  }
}
