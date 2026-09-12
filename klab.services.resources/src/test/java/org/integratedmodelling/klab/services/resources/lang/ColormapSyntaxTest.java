package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.StringReader;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.ObservableStandaloneSetup;
import org.integratedmodelling.languages.FunctionCallSyntaxImpl;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.api.FunctionCallSyntax;
import org.integratedmodelling.languages.services.ObservableGrammarAccess;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.view.modeler.visualization.ColorRamp;
import org.junit.jupiter.api.Test;

class ColormapSyntaxTest {
  @Test void functionCallsAlsoRetainNamedParametersFollowingThePrimaryValue() {
    var injector = new org.integratedmodelling.languages.KimStandaloneSetup().createInjectorAndDoEMFRegistration();
    String source = "example(\"terrain\", center=0, reverse=true)";
    var parsed = injector.getInstance(IParser.class).parse(
        injector.getInstance(org.integratedmodelling.languages.services.KimGrammarAccess.class).getFunctionCallRule(), new StringReader(source));
    assertFalse(parsed.hasSyntaxErrors());
    var syntax = new FunctionCallSyntaxImpl(
        (org.integratedmodelling.languages.observable.FunctionCall) parsed.getRootASTElement(),
        mock(LanguageValidationScope.class)) {
      @Override public String encode() { return source; }
      @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) { fail(message); }
      @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) { fail(message); }
    };
    assertEquals(3, syntax.getArguments().size());
    assertTrue(syntax.getArguments().containsKey("center"));
    assertTrue(syntax.getArguments().containsKey("reverse"));
  }
  @Test void primaryPaletteAndFollowingNamedParametersSurviveSourceAdaptation() throws Exception {
    var injector = new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration();
    String source = "@colormap(\"terrain\", center=0, min=-8000, max=4000, nodata=\"#f008\")";
    var parsed = injector.getInstance(IParser.class).parse(
        injector.getInstance(ObservableGrammarAccess.class).getAnnotationRule(), new StringReader(source));
    assertFalse(parsed.hasSyntaxErrors());
    var syntax = new FunctionCallSyntaxImpl(
        (org.integratedmodelling.languages.observable.Annotation) parsed.getRootASTElement(),
        mock(LanguageValidationScope.class)) {
      @Override public String encode() { return source; }
      @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) { fail(message); }
      @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) { fail(message); }
    };
    assertEquals(5, syntax.getArguments().size());
    var adapter = LanguageAdapter.class.getDeclaredMethod("adaptAnnotation", FunctionCallSyntax.class,
        String.class, String.class, KlabAsset.KnowledgeClass.class);
    adapter.setAccessible(true);
    var annotation = (Annotation) adapter.invoke(LanguageAdapter.INSTANCE, syntax, "test", "test", KlabAsset.KnowledgeClass.NAMESPACE);
    assertEquals(0, ((Number) annotation.get("center")).intValue());
    var ramp = ColorRamp.fromAnnotation(annotation);
    assertEquals(0xffe8e6b5, ramp.argb(0, -1, 1));
    assertEquals(0xff081d58, ramp.argb(-8000, -1, 1));
    assertEquals(0xfff5f5f5, ramp.argb(4000, -1, 1));
    assertEquals(0x88ff0000, ramp.argb(Double.NaN, -1, 1));
  }
}
