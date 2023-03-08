package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.kim.KimActiveStatement;
import org.integratedmodelling.klab.api.lang.kim.KimBehavior;

/**
 * An active statement encodes an object that can have a runtime behavior specified through
 * contextualization actions.
 * 
 * @author ferdinando.villa
 *
 */
public class KimActiveStatementImpl extends KimStatementImpl implements KimActiveStatement {

    private static final long serialVersionUID = -8237389232551882921L;
    
    private KimBehavior behavior;

    @Override
    public KimBehavior getBehavior() {
        return behavior;
    }

    public void setBehavior(KimBehavior behavior) {
        this.behavior = behavior;
    }

}
