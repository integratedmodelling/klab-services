package org.integratedmodelling.klab.knowledge;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WorldviewImpl implements Worldview {
    private String urn;
    private Metadata metadata = Metadata.create();
    private List<KimOntology> ontologies = new ArrayList<>();
    private List<KimObservationStrategyDocument> observationStrategies = new ArrayList<>();
    private boolean empty;

    @Override
    public String getUrn() {
        return this.urn;
    }


    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

    @Override
    public List<KimOntology> getOntologies() {
        return this.ontologies;
    }

    @Override
    public Collection<KimObservationStrategyDocument> getObservationStrategies() {
        return this.observationStrategies;
    }

    @Override
    public boolean isEmpty() {
        return this.empty;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setOntologies(List<KimOntology> ontologies) {
        this.ontologies = ontologies;
    }

    public void setObservationStrategies(List<KimObservationStrategyDocument> observationStrategies) {
        this.observationStrategies = observationStrategies;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
