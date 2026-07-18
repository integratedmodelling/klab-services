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
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * Tags a method that can be used as a functional verb in k.Actors. Must be defined for public
 * methods of classes tagged with {@link Library}. In a call chain, the first argument is the
 * "receiver" from the previous call.
 *
 * <p>Needs a Type that specified if this is a functional call, admitting asynchronous or
 * synchronous execution, or an emitter
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Verb {

  /** The behavior that the object pointed to by this annotation will have in k.Actors. */
  enum Type {
    /**
     * A function is a verb that will produce a value synchronously when called. Executions that use
     * its value will always wait for it to return the value before continuing. They should be used
     * in assignment statements (which will wait) or in a thread by using the reactor pattern, which
     * will remove the reactor's scope once the function returns.
     */
    FUNCTION,
    /**
     * A reactor will produce zero or a single value once at some point in the future, then its
     * function will be over. It is expected to return a {@link
     * java.util.concurrent.CompletableFuture} for the result.
     */
    SUPPLIER,
    /**
     * An emitter will produce zero or more values at some point after the call by invoking {@link
     * Agent.Scope#doFire(Object)}. Its scope determines the lifetime of the emitter. This verb will
     * run in its thread and is expected to only exit when {@link Agent.Scope#isDone()} returns
     * true.
     */
    EMITTER;
  }

  /**
   * Tags the action parameters if needed. Otherwise they are automatically paired by name and type.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface Argument {

    /**
     * Name is optional because actions can be called without named parameters. In that case, the
     * matching will be done by type; if there are ambiguities, the order of declaration will count.
     *
     * @return
     */
    String name() default "";

    /**
     * Mandatory description for the documentation.
     *
     * @return
     */
    String description();

    /**
     * @return
     */
    boolean optional() default false;

    /**
     * If true, argument must be an observation
     *
     * @return
     */
    boolean observation() default false;

    /**
     * If true, argument must be an actor. Observations of agents with associated behaviors are
     * automatically promoted.
     *
     * @return
     */
    boolean actor() default false;

    /**
     * If true, must be a POD literal and nothing else is accepted.
     *
     * @return
     */
    boolean constant() default false;

    /**
     * Type if there is ambiguity.
     *
     * @return
     */
    Class<?> type() default Object.class;
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
   * Classes fired by this verb. Fire posts the fired value to listeners but keeps running if the
   * implementation creates a live thread. By default nothing.
   *
   * @return
   */
  Class<?> fires() default Void.class;

  /**
   * Return type, if any. Normally not used because the type is inferred from the return value, but
   * here if there is a need to resolve ambiguities. Returning removes any listeners so it is meant
   * for actions whose listeners are deregistered after return. If used on a supplier, it should
   * declare the type of result that the supplier returns a {@link
   * java.util.concurrent.CompletableFuture} for.
   *
   * @return
   */
  Class<?> returns() default Void.class;

  /**
   * The execution mode of this verb. This may not be passed if {@link #fires()} is set, which
   * implies EMITTER, or the method returns a value (which implies FUNCTION) or a {@link
   * java.util.concurrent.CompletableFuture} (which implies SUPPLIER).
   *
   * @return
   */
  Type executionType() default Type.FUNCTION;
}
