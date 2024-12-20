package org.integratedmodelling.klab.api.services.resources.adapters;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;

import java.lang.annotation.*;

/**
 * Annotates methods that implement (and possibly define) an export schema specified through
 * {@link org.integratedmodelling.klab.api.services.resources.ResourceTransport} beans. The methods can be in
 * a class annotated with {@link org.integratedmodelling.klab.api.services.runtime.extension.Library} or
 * {@link ResourceAdapter}.
 * <p>
 * Recognized method parameters should include an {@link org.integratedmodelling.klab.api.knowledge.Urn} or
 * directly a Resource for resources with parameters, a scope, and possibly a String for a media type that
 * should be part of the annotation and established through content negotiation. The method must return an
 * {@link org.integratedmodelling.klab.api.knowledge.Urn} or a {@link String} that encodes one.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Exporter {

    /**
     * The unique ID of the export schema this annotates. This is a single lowercase identifier that is used
     * with the library's namespace to build the complete ID as a dot-separated path. The leading path
     * ({@link org.integratedmodelling.klab.api.services.runtime.extension.Library}'s namespace is the "class"
     * of the import and this schema ID specifies the import source or method (e.g.
     * <code>component.jar</code>).
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
     * Mandatory media type that will be sent along with the exported byte stream.
     *
     * @return
     */
    String mediaType();

    String[] fileExtensions() default {};

    String description();
}
