package org.integratedmodelling.klab.api.services.runtime.extension;

import org.integratedmodelling.klab.api.knowledge.Artifact;

import java.lang.annotation.*;

/**
 * Use on top of a method within a @{@link Library}-annotated class or on a type with a single public method
 * to provide an exporter of an image for a given observation.
 * <p>
 * TODO document the argument matching for the methods.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DataImage {

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
     * If this method is particularly costly to run, provide an estimation between 0 (lowest) and 10 (highest)
     * so that the exporter can choose, assuming higher cost means higher quality and the cost is compounded
     * with the requested size of the image.
     *
     * @return
     */
    int cost() default 0;

    /**
     * Optional semantics to filter the applicable observation. Must be a quality in the current worldview.
     * Should be only needed in very specific situations.
     *
     * @return
     */
    String semantics() default "";
}
