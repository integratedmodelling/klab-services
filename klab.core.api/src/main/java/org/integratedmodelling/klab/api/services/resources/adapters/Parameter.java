package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.knowledge.Artifact;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Parameter {

	/**
	 * The parameter name. Must be a simple lowercase name. Dot-separated paths are
	 * accepted and will be shown hierarchically in the resource editor; they won't
	 * be admissible as URN parameters.
	 * 
	 * @return
	 */
	String name();

	/**
	 * Type(s) accepted for the parameter.
	 * 
	 * @return
	 */
	Artifact.Type[] type();

	/**
	 * If optional, the adapter must be prepared to not have it.
	 * 
	 * @return
	 */
	boolean optional() default false;

	/**
	 * If true, this parameter can be passed in the URN.
	 * 
	 * @return
	 */
	boolean urnParameter() default false;

	/**
	 * If true, this parameter belongs in the resource configuration. If also
	 * accepted in the URN, this will serve as default, overriddable through the
	 * URN.
	 * 
	 * @return
	 */
	boolean configurationParameter() default true;
}
