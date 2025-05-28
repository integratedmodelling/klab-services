/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tags a method that can be used as a functional verb in k.Actors. Must be defined for public
 * methods of classes tagged with {@link Library}. In a call chain, the first argument is the
 * "receiver" from the previous call.
 *
 * Needs a Type that specified if this is a functional call, admitting asynchronous or synchronous
 * execution, or an emitter
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Verb {

    enum ExecutionType {
        SYNC, ASYNC, EMITTER;
    }

    /**
     * ID of the component. Must be unique, please use unambiguous paths like package or project
     * names.
     * 
     * @return component id
     */
    String name() default "";

    /**
     * List of other project or component IDs that this one depends on.
     * 
     * @return id of projects or components we need
     */
    String[] requires() default {};

    /**
     * Descriptions should be given as they percolate to the k.Actors editor
     * 
     * @return
     */
    String description() default "";

    /**
     * Return type, if any. By default returns any object.
     * 
     * @return
     */
    Class<?> returns() default Object.class;

    /**
     * The execution mode of this verb.
     *
     * @return
     */
    ExecutionType executionType() default ExecutionType.SYNC;
}
