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

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

/**
 * Tags a class that contains extensions for one of the k.LAB components.
 * Extensions can be contextualizers, actor functions or other elements; the tag
 * they are annotated with determines the use that can be made of them and their
 * registration in a service. Libraries have a name that is prepended to the
 * name of any object it declares; only the core k.LAB implementation is allowed
 * to use the CORE_LIBRARY identifier, which makes them available with a single
 * identifier.
 * <p>
 * Some libraries can be imported with an "import" instruction from a language.
 * Others are pre-loaded in test cases and scripts. Libraries are used to
 * provide k.Actors actions and k.IM contextualizers through static inner
 * classes or methods, which should bear the annotation {@link Verb} or
 * {@link KlabPrototype}.
 * <p>
 * Contextualizers implementations from the core libraries can be overridden
 * from configuration using classes from loaded plug-ins, so that the runtime
 * can be customized to specific needs in terms of accuracy or logics.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Library {
	
	public static final String CORE_LIBRARY = "__CORE_LIBRARY__";

	/**
	 * ID of the component. Must be unique, please use unambiguous paths like
	 * package or project names. Provides a namespace for its internal classes.
	 * 
	 * @return component id
	 */
	String name();

	/**
	 * If this library should be loaded by default into a particular type of
	 * behavior (e.g. test case), set the type(s) here.
	 * 
	 * @return
	 */
	KActorsBehavior.Type[] defaultFor() default {};

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

}
