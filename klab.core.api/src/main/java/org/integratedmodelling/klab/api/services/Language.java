package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.lang.Expression;
import org.integratedmodelling.klab.api.lang.Expression.CompilerOption;

/**
 * The language service provides validation and compilation for expressions used
 * in k.IM an k.Actors, supporting one or more external languages, with the
 * Groovy variant used in k.LAB as a default. The capabilities should describe
 * the languages available and how the returned code is executed (if a runtime
 * is required, the service should also provide engine plug-in extensions that
 * implement it).
 * 
 * @author Ferd
 *
 */
public interface Language extends KlabService {

	public final static String DEFAULT_EXPRESSION_LANGUAGE = "K.LAB.GROOVY";

	default String getServiceName() {
		return "klab.language.service";
	}

	/**
	 * To compile an expression within a given scope, the scope should be passed for
	 * analysis and the descriptor used to compile into an expression.
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

}
