package org.integratedmodelling.klab.api.services.resources.adapters;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;

import java.lang.annotation.*;

/**
 * Annotates methods that implement (and possibly define) an import schema specified through
 * {@link org.integratedmodelling.klab.api.services.resources.ResourceTransport} beans. The methods can be in
 * a class annotated with {@link org.integratedmodelling.klab.api.services.runtime.extension.Library} or
 * {@link ResourceAdapter} (if the latter, the import will be expected to produce a Resource adopting that
 * same adapter).
 * <p>
 * Recognized method parameters can be {@link java.io.File}, {@link java.net.URL} or
 * {@link java.io.InputStream} for binary content,
 * {@link org.integratedmodelling.klab.api.collections.Parameters} for property-specified content, Resource
 * for resources with parameters, and scopes. An {@link org.integratedmodelling.klab.api.knowledge.Urn} may be
 * added which will be paired to a suggested URN if the caller has supplied one (the method may modify it).
 * The method must return an {@link org.integratedmodelling.klab.api.knowledge.Urn} or a {@link String} that
 * encodes one, which will be the final, unique URN.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Importer {

    /**
     * The unique ID of the import schema this implements. This is the complete ID as a dot-separated path,
     * whose leading path is the "class" of the import and the last element specifies the import source or
     * method (e.g. <code>component.jar</code>).
     *
     * @return
     */
    String schema();

    KlabAsset.KnowledgeClass knowledgeClass();

    /**
     * If specified, these define the schema's properties which will be passed as a
     * {@link org.integratedmodelling.klab.api.collections.Parameters} object. If not, the schema will expect
     * binary content as a File, URL or InputStream.
     *
     * @return
     */
    KlabFunction.Argument[] properties() default {};

    /**
     * Optional media type that will be matched to the input's in case multiple schemata are possible for
     * different media types.
     *
     * @return
     */
    String mediaType() default "";

    String[] fileExtensions() default {};

    String description();

}
