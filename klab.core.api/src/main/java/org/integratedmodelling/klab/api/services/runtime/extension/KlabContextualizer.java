package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * This annotation can be used along with a Contextualizer class to declare the
 * contextualizer without needing to provide a k.DL prototype for it. It is also
 * allowed to specify the annotation over a method for those calls that can be
 * used functionally, such as the runtime generators for space and time, or any
 * function to be called as arguments in a k.IM or k.Actors call.
 * 
 * @author Ferd
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface KlabContextualizer {

	/**
	 * Arguments are used in the declaration to describe inputs, outputs and
	 * parameters.
	 * 
	 * @author Ferd
	 *
	 */
	public @interface Argument {

		String name();

		Artifact.Type type();

		String description();

		String dataflowLabel() default "";

		boolean optional() default false;

		boolean isFinal() default false;
	}

	/**
	 * Single, lowercase name. Will be compoundend with the enclosing library's
	 * namespace to build a path that must be unique.
	 * 
	 * @return
	 */
	String name();

	/**
	 * Version is mandatory and should be coded to reflect the version of any
	 * components that the contextualizer is part of. Must be a semantic version
	 * parseable by {@link Version}.
	 * 
	 * @return
	 */
	String version();

	String description();

	/**
	 * If this is true, one instance of the associated contextualizer class or
	 * method is created and reused. Otherwise a new one is obtained at each
	 * reference in the dataflow.
	 * 
	 * @return
	 */
	boolean reentrant() default false;

	String dataflowLabel() default "";

	Argument[] imports() default {};

	Argument[] exports() default {};

	Argument[] parameters() default {};

	Artifact.Type type();

}
