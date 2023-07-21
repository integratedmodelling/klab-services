package org.integratedmodelling.klab.api.services.resources.adapters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.ContextScope;

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
