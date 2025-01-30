package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Expression.CompilerOption;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.List;

/**
 * The language service provides validation and compilation for expressions and service calls used
 * in k.IM an k.Actors, supporting one or more external expression languages, with the Groovy
 * variant used in k.LAB as a default. The capabilities should describe the languages available and
 * how the returned code is executed (if a runtime is required, the service should also provide
 * engine plug-in extensions that implement it).
 *
 * <p>The service gets notified of all new prototypes and annotations gathered through annotations
 * and k.DL declarations, and can validate and execute them. It can also find a service call in a
 * remote component and load it transparently.
 *
 * @author Ferd
 */
public interface Language extends Service {

  public static final String DEFAULT_EXPRESSION_LANGUAGE = "K.LAB.GROOVY";

  // TODO turn these into values of an enum
  public static final String KWV = "k.IM worldview definition";

  /** k.IM is the k.LAB semantic modeling language */
  public static final String KIM = "k.IM modelling";

  /** k.Actors is the k.LAB actors language */
  public static final String KACTORS = "k.Actors";

  /** The k.LAB observation strategy and dataflow encoding language */
  public static final String KOBSERVATION = "k.Observation";

  default String getServiceName() {
    return "klab.language.service";
  }

  interface LanguageProcessor {

    /**
     * To compile an expression within a given scope, the scope should be passed for analysis and
     * the descriptor used to compile into an expression.
     *
     * @param expression an expression to analyze
     * @param scope a scope or null
     * @return the analyzed descriptor
     */
    Expression.Descriptor analyze(
        ExpressionCode expression, Scope scope, CompilerOption... options);

    /**
     * Turn a previously analyzed expression descriptor (without errors) into executable code.
     *
     * @param options
     * @return
     */
    Expression compile(Expression.Descriptor descriptor, CompilerOption... options);
  }

  /**
   * Return the language processor for the passed language. Must not return null for at least
   * DEFAULT_EXPRESSION_LANGUAGE.
   *
   * @param language
   * @return
   */
  LanguageProcessor getLanguageProcessor(String language);

  /**
   * To compile an expression within a given scope, the scope should be passed for analysis and the
   * descriptor used to compile into an expression.
   *
   * @param code
   * @param scope
   * @param options
   * @return
   */
  default Expression.Descriptor analyze(
      ExpressionCode code, Scope scope, CompilerOption... options) {
    var processor = getLanguageProcessor(code.getLanguage());
    return processor.analyze(code, scope, options);
  }

  /**
   * Short-cut, scope-less expression compiler for non-contextual execution of code in the default
   * language.
   *
   * @param expression
   * @param language
   * @param options
   * @return a compiled expression
   */
  default Expression compile(String expression, String language, CompilerOption... options) {
    var processor = getLanguageProcessor(DEFAULT_EXPRESSION_LANGUAGE);
    var descriptor =
        processor.analyze(
            ExpressionCode.of(expression, DEFAULT_EXPRESSION_LANGUAGE), null, options);
    return descriptor.compile();
  }

  /**
   * Validate a service call against its prototype. Unknown service calls should produce an error.
   *
   * @param call
   * @return
   */
  List<Notification> validate(ServiceCall call);

  /**
   * Validate an annotation versus its known prototype. Unknown annotations should produce a single
   * warning to that extent.
   *
   * @param annotation
   * @return
   */
  List<Notification> validate(Annotation annotation);

  /**
   * Validate a k.Actors message call against the known verbs. This should only be called if no
   * local actions override the action name.
   *
   * @param message
   * @return
   */
  List<Notification> validate(Call message);

  /**
   * Execute a service call and return its result. A mismatch in the result class should produce an
   * exception. No validation should be done at this stage.
   *
   * @param <T>
   * @param call
   * @param scope
   * @param resultClass
   * @return
   */
  <T> T execute(ServiceCall call, Scope scope, Class<T> resultClass);
}
