package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.geometry.Geometry;

public class ResourceContextualizationRequest {

    String resourceUrn;
    Geometry geometry;

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public String getResourceUrn() {
        return resourceUrn;
    }

    public void setResourceUrn(String resourceUrn) {
        this.resourceUrn = resourceUrn;
    }
}
