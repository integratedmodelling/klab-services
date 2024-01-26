package org.integratedmodelling.klab.api.lang.impl.kactors;

import org.integratedmodelling.klab.api.lang.impl.kim.KimStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsCodeStatement;
import org.integratedmodelling.klab.api.utils.Utils;

public abstract class KActorsCodeStatementImpl extends KimStatementImpl implements KActorsCodeStatement 	{

	private static final long serialVersionUID = -3317041109502570786L;

    private String tag;

    @Override
    public String toString() {
        if (sourceCode() != null) {
            return sourceCode();
        }
        return Utils.Paths.getLast(this.getClass().getCanonicalName(), '.') + " (no source available)";
    }

    @Override
	public String getTag() {
		return tag;
	}

	public void setTag(String tag) {
		this.tag = tag;
	}

//	@Override
//	public void visit(Visitor visitor) {
//		// TODO Auto-generated method stub
//
//	}
}
