package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * In k.LAB 12.0+, resource adapters can be implemented in a single class using
 * annotations only for the functionalities supported. The class does not need
 * to implement any specific interface. A class extending {@link Adapter} will
 * be created from the analysis of the code and become the adapter
 * implementation.
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
	Artifact.Type[] type() default Artifact.Type.VOID;

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

	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ ElementType.TYPE, ElementType.METHOD })
	public @interface Publisher {

	}

	/**
	 * Tags the encoding method(s), which must return {@link KlabData} when applied
	 * with a {@link Resource} and a {@link ContextScope}. The method also should
	 * take a {@link Parameters} map if the resource URN arguments are used.
	 * 
	 * @author Ferd
	 *
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface Encoder {

	}

	/**
	 * Tags a contextualizer function that will take a {@link Resource} and a
	 * {@link ContextScope} and return a contextualized resource ready for use in
	 * that context. If there is no need for contextualization, there should be no
	 * contextualizer method.
	 * 
	 * @author Ferd
	 *
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Contextualizer {

	}

	/**
	 * Tags a method that will inspect the resource at server side upon publication
	 * and make any modification required to better serve it, including potentially
	 * changing the adapter if needed. The method should take and return a
	 * {@link Resource} which will have been validated for publication already.
	 * Implementations should be prepared to switch adapters after calling the
	 * resource.
	 * 
	 * @author Ferd
	 *
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public @interface Inspector {

	}

	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Importer {

	}

	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Exporter {

	}

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

}
