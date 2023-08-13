package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * The annotation enables specialized validations for all phases of the resource
 * lifecycle. A non-universal adapter must provide at least a validator for
 * LocalImport.
 * 
 * @author Ferd
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Validator {

	enum LifecyclePhase {
		/**
		 * The resource is being imported for the first time. A local name, resource
		 * builder, resource parameters and any resource contents (as a file or
		 * directory) will be matched to the parameters of the validator method, which
		 * must return the configured builder.
		 */
		LocalImport,
		/**
		 * The local resource has been requested for staging and must comply with any
		 * metadata conventions indicated.
		 */
		LocalStaging,
		/**
		 * The locally staged resource is being published and must pass this validation
		 * for that to be successful. The method also takes the URN proposed and should
		 * return a collection of {@link Notification} objects which will be included
		 * with the resource package sent to the host; any error notifications will
		 * prevent publication.
		 */
		PrePublication,
		/**
		 * This validator will be called at the host side when publication is called.
		 */
		PostPublication,
		/**
		 * Called before the resource is sent for review, to ensure all the necessary
		 * fields that have passed previous validations are consistent with the process.
		 */
		PreReview,
		/**
		 * If a review process has modified the resource, this validator will be called
		 * to ensure consistency before the review status (which is passed to the method
		 * as an integer) is changed.
		 */
		PostReview
	}

	/**
	 * Resource lifecycle phase to which this validation applies.
	 * 
	 * @return
	 */
	LifecyclePhase[] phase();

	/**
	 * Metadata conventions to validate against, if any.
	 * 
	 * @return
	 */
	String metadataConventions() default "";

}
