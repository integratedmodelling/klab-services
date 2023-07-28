package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * This annotation can be used along with a class to declare the result class or
 * with a method to declare the equivalent call for a function available in the
 * k.IM language. Annotations are automatically extracted from the classpath
 * when the class bearing them is in a recognized package (in this
 * implementation <code>org.integratedmodelling.klab.runtime</code> and any
 * package declared in a plug-in manifest). Other packages can be scanned
 * explicitly according to implementation. To create namespaces, declare methods
 * or classes within another class annotated with {@link Library}. The
 * equivalent annotation for k.Actors actions is {@link Verb}.
 * 
 * @author Ferd
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface KlabPrototype {

	/**
	 * Arguments are used in the declaration to describe inputs, outputs and
	 * parameters.
	 * 
	 * @author Ferd
	 *
	 */
	public @interface Argument {

		String name();

		Artifact.Type[] type();

		String description();

		String dataflowLabel() default "";

		boolean optional() default false;

		boolean isFinal() default false;

		boolean constant() default false;

		boolean artifact() default false;
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

	String geometry() default "";

	/**
	 * If this is true, one instance of the associated contextualizer class or
	 * method is created and reused. Otherwise a new one is obtained at each
	 * reference in the dataflow.
	 * 
	 * @return
	 */
	boolean reentrant() default false;

	boolean filter() default false;

	String dataflowLabel() default "";

	Argument[] imports() default {};

	Argument[] exports() default {};

	Argument[] parameters() default {};

	Artifact.Type[] type();

}
