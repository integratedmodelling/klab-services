package org.integratedmodelling.klab.api.services.runtime.impl;

import org.integratedmodelling.klab.api.lang.ExpressionCode;

import java.io.Serial;

/**
 * Just a wrapper for some code and an optional language identifier. Used
 * explicitly only where code must be distinguished from other string values,
 * such as in classifiers.
 * 
 * @author Ferd
 *
 */
public class ExpressionCodeImpl implements ExpressionCode {

	@Serial
	private static final long serialVersionUID = -8760044805799296995L;

	private String code;
	private String language;
	private boolean forcedScalar;
	private String sourceCode;

	@Override
	public String getCode() {
		return this.code;
	}

	@Override
	public String getLanguage() {
		return this.language;
	}

	@Override
	public boolean isForcedScalar() {
		return this.forcedScalar;
	}

	@Override
	public String getSourceCode() {
		return this.sourceCode;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public void setForcedScalar(boolean forcedScalar) {
		this.forcedScalar = forcedScalar;
	}

	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}

	@Override
	public String encode(String language) {
		return (forcedScalar ? "#" : "") + "[" + code + "]";
	}

}
