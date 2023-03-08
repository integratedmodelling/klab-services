package org.integratedmodelling.klab.api.lang.kim;

/**
 * An active statement encodes an object that can have a runtime behavior specified
 * through contextualization actions.
 * 
 * @author ferdinando.villa
 *
 */
public interface KimActiveStatement extends KimStatement {

    /**
     * Return the behavior specified in k.IM, possibly empty.
     * 
     * @return a behavior, never null.
     */
    KimBehavior getBehavior();
    
}
