package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * This annotation can be used along with a Contextualizer class to declare the
 * contextualizer without needing to provide a k.DL prototype for it.
 * 
 * @author Ferd
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
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
	 * The namespace should be provided unless the annotated class is within a
	 * {@link Library}-annotated class, whose name provides the namespace.
	 * 
	 * @return
	 */
	String namespace() default "";

	String name();

	String description();

	String dataflowLabel() default "";

	Argument[] imports() default {};

	Argument[] exports() default {};

	Argument[] parameters() default {};

	Artifact.Type type();

}
