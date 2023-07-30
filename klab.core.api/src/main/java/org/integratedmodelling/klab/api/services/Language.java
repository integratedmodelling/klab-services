package org.integratedmodelling.klab.api.services;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Expression.CompilerOption;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * The language service provides validation and compilation for expressions and service calls used
 * in k.IM an k.Actors, supporting one or more external expression languages, with the Groovy
 * variant used in k.LAB as a default. The capabilities should describe the languages available and
 * how the returned code is executed (if a runtime is required, the service should also provide
 * engine plug-in extensions that implement it).
 * <p>
 * The service gets notified of all new prototypes and annotations gathered through annotations and
 * k.DL declarations, and can validate and execute them.
 * 
 * @author Ferd
 *
 */
public interface Language extends Service {

    public final static String DEFAULT_EXPRESSION_LANGUAGE = "K.LAB.GROOVY";

    default String getServiceName() {
        return "klab.language.service";
    }

    /**
     * To compile an expression within a given scope, the scope should be passed for analysis and
     * the descriptor used to compile into an expression.
     * 
     * @param expression
     * @param language
     * @param scope
     * @param options
     * @return
     */
    Expression.Descriptor describe(String expression, String language, Scope scope, CompilerOption... options);

    /**
     * Short-cut, scope-less expression compiler for non-contextual execution.
     * 
     * @param expression
     * @param language
     * @param options
     * @return
     */
    Expression compile(String expression, String language, CompilerOption... options);

    /**
     * Validate a service call against its prototype. Unknown service calls should produce an error.
     * 
     * @param call
     * @return
     */
    List<Notification> validate(ServiceCall call);

    /**
     * Validate an annotation versus its known prototype. Unknown annotations should produce a
     * single warning to that extent.
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
     * Execute a service call and return its result. A mismatch in the result class should produce
     * an exception. No validation should be done at this stage.
     * 
     * @param <T>
     * @param call
     * @param scope
     * @param resultClass
     * @return
     */
    <T> T execute(ServiceCall call, Scope scope, Class<T> resultClass);

}
