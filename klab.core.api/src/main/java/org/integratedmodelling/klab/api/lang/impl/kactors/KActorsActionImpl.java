package org.integratedmodelling.klab.api.lang.impl.kactors;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;

public class KActorsActionImpl extends KActorsCodeStatementImpl implements KActorsAction {

	private static final long serialVersionUID = 5202922350235994909L;
	
	private String urn;
	private KActorsStatement code;
	private List<String> argumentNames = new ArrayList<>();
	private boolean function;

	@Override
	public String getUrn() {
		return this.urn;
	}

	@Override
	public KActorsStatement getCode() {
		return this.code;
	}

	@Override
	public List<String> getArgumentNames() {
		return this.argumentNames;
	}

	@Override
	public boolean isFunction() {
		return this.function;
	}

	public void setUrn(String urn) {
		this.urn = urn;
	}

	public void setCode(KActorsStatement code) {
		this.code = code;
	}

	public void setArgumentNames(List<String> argumentNames) {
		this.argumentNames = argumentNames;
	}

	public void setFunction(boolean function) {
		this.function = function;
	}

}
