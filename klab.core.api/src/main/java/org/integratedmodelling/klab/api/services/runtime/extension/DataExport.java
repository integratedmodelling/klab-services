package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.knowledge.Artifact;

import java.lang.annotation.*;

/**
 * Use over a method within a @{@link Library}-annotated class or on a class with a single public method to
 * provide an export method to a given media type for a given geometry, data type and/or semantics.
 * <p>
 * TODO document the argument matching for the methods.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DataExport {

    /**
     * Mandatory media type to be matched with
     *
     * @return
     */
    String mediaType();

    /**
     * Geometry of the observation we apply to. Should only state the main dimensions but may also provide
     * scale constraints.
     *
     * @return
     */
    String geometry();

    /**
     * Data type to filter the applicable observations. Alternative to {@link #semantics()}, default means
     * anything is accepted.
     *
     * @return
     */
    Artifact.Type dataType() default Artifact.Type.VOID;


    /**
     * Optional semantics to filter the applicable observation. Must be a quality in the current worldview.
     * Should be only needed in very specific situations.
     *
     * @return
     */
    String semantics() default "";
}
