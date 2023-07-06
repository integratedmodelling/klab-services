package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * Annotates a method returning {@link Artifact.Type} and taking a
 * {@link Resource} as argument. Only used if the type is not specified in the
 * {@link ResourceAdapter} arguments.
 * 
 * @author Ferd
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Type {
	// no parameters
}
