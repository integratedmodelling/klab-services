package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.knowledge.Resource;

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
@Target(ElementType.TYPE)
public @interface Inspector {

}
