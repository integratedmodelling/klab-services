package org.integratedmodelling.klab.runtime.kactors.compiler;

import groovy.lang.GroovyObjectSupport;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

/**
 * Base class for the Java/Groovy compiled actor that translates a k.Actors behavior, substituting
 * the interpreted k.Actors VM
 */
public class ActorBase extends GroovyObjectSupport {

    private final KActorsBehavior behavior;

    public ActorBase(KActorsBehavior behavior) {
        this.behavior = behavior;
    }

    protected Object resolveIdentifier(String identifier) {
        return null;
    }

}
