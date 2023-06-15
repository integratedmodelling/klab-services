package org.integratedmodelling.klab.api.lang.impl.kactors;

import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;

public class KActorsStatementImpl extends KActorsCodeStatementImpl implements KActorsStatement {

	private static final long serialVersionUID = -2182769468866983874L;

	private Type type;
	
	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public Type getType() {
		return type;
	}

}
