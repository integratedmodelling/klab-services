package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.*;

/**
 * Use on a Java class to make it the implementation of an object defined through a <code>define</code>
 * instruction in k.IM. If the class is a {@link org.integratedmodelling.klab.api.lang.kim.KlabStatement} it
 * will be created passing the correspondent code object and will be traceable in the modeler's navigator. The
 * mandatory "value" name is the name or dot-separated path of the class used in the code after
 * <code>define</code>, and it must be unique, which mandates the use of namespaces in classes that are
 * defined in extensions. The reference implementation will define some first-class object classes such as
 * <code>observation</code> and <code>table</code>, plus some forthcoming classes for provenance and bridging
 * to RDF.
 * <p>
 * Implementations must be accessible to the resources service in order to be functional. Any service that
 * uses semantics or more high-level objects will need to handle the defined class as a syntactic object and
 * translate it, if needed, to what they need.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Instance {

    /**
     * The class name, mandatory and unique. In extensions, this must be a path with at least two
     * dot-separated components, using reverse domain conventions.
     *
     * @return
     */
    String value();

    String label() default "";

    /**
     * URL of a 16px icon to use for navigation. Optional.
     */
    String iconUrl() default "";

    String description();
}
