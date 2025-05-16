package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.*;

/**
 * Specialized library for actors and verbs. Should support:
 *
 * scoping to one or more script types;
 * re-entrant, static or other execution models
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Actor {

    /**
     * ID of the component. Must be unique, please use unambiguous paths like package or project
     * names. Provides a namespace for its internal classes.
     *
     * @return component id
     */
    String name();


}
