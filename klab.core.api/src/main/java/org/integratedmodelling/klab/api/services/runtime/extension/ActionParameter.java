package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.*;

/**
 * Tags the action parameters if needed. Otherwise they are paired by name and type.
 * <p>
 * scoping to one or more script types;
 * re-entrant, static or other execution models
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ActionParameter {

    /**
     * ID of the component. Must be unique, please use unambiguous paths like package or project
     * names. Provides a namespace for its internal classes.
     *
     * @return component id
     */
    String name() default "";

    /**
     * Type if there is ambiguity.
     *
     * @return
     */
    Class<?> type() default Object.class;
}
