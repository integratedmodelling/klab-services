package org.integratedmodelling.klab.api.services.resources.adapters;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * The descriptor for a resource adapter, built from the annotation in a class annotated with
 * {@link ResourceAdapter} and part of a resource service's capabilities.
 *
 * @author Ferd
 */
public interface Adapter {

    String getName();

    /**
     * If this is void, the resource type must be asked case-by-case to the adapter, through an adapter
     * handler that knows which method to call.
     *
     * @return
     */
    Artifact.Type getResourceType();

    /**
     * Version. Cannot be null. Multiple versions of the same adapter may coexist in a service.
     *
     * @return
     */
    Version getVersion();

}
