package org.integratedmodelling.klab.runtime.kactors.actors;

import groovy.lang.GroovyObjectSupport;

/**
 * Base class for the Java/Groovy compiled actor that translates a k.Actors behavior, substituting
 * the interpreted k.Actors VM
 */
public class ActorBase extends GroovyObjectSupport {

    protected Object resolveIdentifier(String identifier) {
        return null;
    }

}
