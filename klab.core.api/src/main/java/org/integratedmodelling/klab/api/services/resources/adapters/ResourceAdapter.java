package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * In k.LAB 12.0+, resource adapters can be implemented in a single class using
 * annotations only for the functionalities supported.
 * 
 * @author Ferd
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ResourceAdapter {

	/**
	 * Name of the resource adapter. Lowercase and [a-z] characters only, can be a
	 * dot-separated path. It will be validated, must be unique in the k.LAB
	 * ecosystem, and dot-separated paths must resolve to implementations under the
	 * same namespace. Two resource adapters may report the same name if and only if
	 * one is universal and the other is not.
	 * 
	 * @return
	 */
	String name();

	/**
	 * Adapter parameters. All must be declared to be accepted.
	 * 
	 * @return
	 */
	Parameter[] parameters() default {};

	/**
	 * A threadsafe adapter will be instantiated once and reused to serve all
	 * requests, including concurrent ones. A non-threadsafe adapter will be
	 * instantiated at each request.
	 * 
	 * @return
	 */
	boolean threadsafe() default true;

	/**
	 * If true, this serves universal URNs with the
	 * <code>klab:adapter:....:....</code> pattern. It is legal to have two adapter
	 * implementations to support both universal and non-universal use of the same
	 * adapter.
	 * 
	 * @return
	 */
	boolean universal() default false;

	/**
	 * Type of the resources using this adapter. Leaving the VOID default here will
	 * require a method annotated with @Type to be present which will return the
	 * type of each resource.
	 * 
	 * @return
	 */
	Artifact.Type type() default Artifact.Type.VOID;
}
