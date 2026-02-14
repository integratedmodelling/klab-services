package org.integratedmodelling.klab.runtime.data.stac.model;

public class Feature {
    protected ItemType type;
    protected STACGeometry<?> geometry;
    protected Object properties;

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public STACGeometry<?> getGeometry() {
        return geometry;
    }

    public void setGeometry(STACGeometry<?> geometry) {
        this.geometry = geometry;
    }

    public Object getProperties() {
        return properties;
    }

    public void setProperties(Object properties) {
        this.properties = properties;
    }
}
