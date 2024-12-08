package org.integratedmodelling.klab.tests.services.resources;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.data.Instance;

/**
 * One of these is created per resource contextualization request. Drives the functions in the adapter to
 * create the contextualized resource payload, which is an Instance object  from the Avro schema.
 */
public class ResourceContextualizer {

    private final Adapter adapter;
    private final Resource resource;
    private final Geometry geometry;

    /**
     * Pass a previously contextualized resource
     * @param adapter
     * @param resource
     * @param geometry
     */
    public ResourceContextualizer(Adapter adapter, Resource resource, Geometry geometry) {
        this.adapter = adapter;
        this.resource = resource;
        this.geometry = geometry;
    }

    /**
     * Contextualize the resource to the specified geometry if the adapter provides this function.
     *
     * @return
     */
    public Resource getContextualizedResource() {
        // TODO use the adapter if it does contextualize resources
        return this.resource;
    }

    /**
     * Produce the contextualized data from the resource in the passed geometry. Any errors will end up
     * in the Instance notifications.
     *
     * @return
     */
    public Instance getData() {

        var builder = Instance.newBuilder();

        return builder.build();
    }

}
