package org.integratedmodelling.klab.components;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;

public class AdapterImpl implements Adapter {

    private String name;
    private Artifact.Type resourceType;
    private Version version;

    public AdapterImpl(Class<?> adapterClass, ResourceAdapter annotation) {
        this.name = annotation.name();
        this.version = Version.create(annotation.version());
        scanAdapterClass(adapterClass);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Artifact.Type getResourceType() {
        return this.resourceType;
    }

    @Override
    public Version getVersion() {
        return this.version;
    }

    @Override
    public Data contextualize(Resource resource, Geometry geometry) {

        // TODO use the reference implementation if adapter allows; otherwise create an implementation for
        //  this request

        // TODO contextualize the resource to the geometry if needed by the adapter implementation

        return Data.empty("Unimplemented, zio can");
    }

    private void scanAdapterClass(Class<?> adapterClass) {
    }

}
