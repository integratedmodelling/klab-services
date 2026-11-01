package org.integratedmodelling.klab.runtime.language;

import java.io.StringReader;
import java.util.ArrayList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.languages.ObservableStandaloneSetup;
import org.integratedmodelling.languages.ObservableSyntaxImpl;
import org.integratedmodelling.languages.SemanticSyntaxImpl;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.observable.ConceptExpression;
import org.integratedmodelling.languages.observable.ObservableSemantics;
import org.integratedmodelling.languages.services.ObservableGrammarAccess;

/** Owns its parser, adapter and validation scope; resolves against captured source beans only. */
public final class CapturedSemanticExpressionParser {
  private static boolean registered;
  private final IParser parser;
  private final ObservableGrammarAccess grammar;
  private final WorldviewValidationScope scope;
  private final LanguageAdapter adapter = new LanguageAdapter();

  private static synchronized void registerLanguage() {
    if (!registered) {
      // Language definitions are process-wide. Runtime parser and semantic state below are owned.
      new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration();
      registered = true;
    }
  }

  public CapturedSemanticExpressionParser(Worldview snapshot) {
    registerLanguage();
    var injector = new ObservableStandaloneSetup().createInjector();
    parser = injector.getInstance(IParser.class);
    grammar = injector.getInstance(ObservableGrammarAccess.class);
    scope = new WorldviewValidationScope(snapshot);
  }

  public synchronized KimConcept concept(String definition) {
    var result = parser.parse(grammar.getConceptExpressionRule(), new StringReader(definition));
    if (result.hasSyntaxErrors() || !(result.getRootASTElement() instanceof ConceptExpression expression))
      throw new IllegalArgumentException("Invalid concept expression: " + definition);
    var errors = new ArrayList<String>();
    var syntax = new SemanticSyntaxImpl(expression, false, null, scope) {
      @Override protected void logError(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) { errors.add(message); }
      @Override protected void logWarning(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) {}
    };
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
    return adapter.adaptSemantics(syntax, null, null, null);
  }

  public synchronized KimObservable observable(String definition) {
    var result = parser.parse(grammar.getObservableSemanticsRule(), new StringReader(definition));
    if (result.hasSyntaxErrors() || !(result.getRootASTElement() instanceof ObservableSemantics expression))
      throw new IllegalArgumentException("Invalid observable expression: " + definition);
    var errors = new ArrayList<String>();
    var syntax = new ObservableSyntaxImpl(expression, scope) {
      @Override protected void logError(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) { errors.add(message); }
      @Override protected void logWarning(ParsedObject target, EObject object,
          EStructuralFeature feature, String message) {}
    };
    if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
    return adapter.adaptObservable(syntax, null, null, null);
  }
}
