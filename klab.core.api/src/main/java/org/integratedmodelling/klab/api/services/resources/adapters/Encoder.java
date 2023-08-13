package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.ContextScope;

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
