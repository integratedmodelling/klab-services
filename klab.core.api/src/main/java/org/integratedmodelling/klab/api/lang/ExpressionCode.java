package org.integratedmodelling.klab.api.lang;

import org.integratedmodelling.klab.api.services.runtime.impl.ExpressionCodeImpl;

import java.io.Serializable;

/**
 * Just a wrapper for some code and an optional language identifier. Used
 * explicitly only where code must be distinguished from other string values,
 * such as in classifiers.
 * 
 * @author Ferd
 *
 */
public interface ExpressionCode extends Serializable, Encodeable {

	String getCode();
	
	String getLanguage();

	/**
	 * If true, this has been parsed from an expression introduced by #, which forces the
	 * evaluation to scalar.
	 * 
	 * @return
	 */
	boolean isForcedScalar();

	/**
	 * Return the reconstructed k.IM expression for inclusion in source code.
	 * 
	 * @return
	 */
	String getSourceCode();


	static ExpressionCodeImpl of(String code, String language) {
		var ret = new ExpressionCodeImpl();
		ret.setCode(code);
		ret.setLanguage(language);
		return ret;
	}
}
