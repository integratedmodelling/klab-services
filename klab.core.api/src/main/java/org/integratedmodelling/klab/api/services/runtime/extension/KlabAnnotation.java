package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabPrototype.Argument;

/**
 * This annotation can be used along with a dummy class or one that extends
 * Annotation to declare an annotation without needing to provide a k.DL
 * prototype for it. It's a stripped-down version of {@link KlabPrototype}
 * and uses the same {@link Argument} class to declare arguments.
 * 
 * @author Ferd
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface KlabAnnotation {

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

	Argument[] parameters() default {};

}
