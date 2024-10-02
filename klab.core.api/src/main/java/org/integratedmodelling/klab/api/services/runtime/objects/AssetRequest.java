package org.integratedmodelling.klab.api.services.runtime.objects;

import org.integratedmodelling.klab.api.collections.impl.MetadataImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

public class AssetRequest {

    private Observation contextObservation;
    private Observable observable;
    private String name;
    private Geometry geometry;
    private RuntimeAsset.Type knowledgeClass;
    private Metadata metadata = new MetadataImpl();
    private long id = Observation.UNASSIGNED_ID;

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public RuntimeAsset.Type getKnowledgeClass() {
        return knowledgeClass;
    }

    public void setKnowledgeClass(RuntimeAsset.Type knowledgeClass) {
        this.knowledgeClass = knowledgeClass;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Observation getContextObservation() {
        return contextObservation;
    }

    public void setContextObservation(Observation contextObservation) {
        this.contextObservation = contextObservation;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Observable getObservable() {
        return observable;
    }

    public void setObservable(Observable observable) {
        this.observable = observable;
    }
}
