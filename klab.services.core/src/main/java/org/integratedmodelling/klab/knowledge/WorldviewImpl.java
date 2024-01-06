package org.integratedmodelling.klab.knowledge;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WorldviewImpl implements Worldview {
    private String urn;
    private Version version;
    private Metadata metadata = Metadata.create();
    private List<Annotation> annotations = new ArrayList<>();
    private List<KimOntology> ontologies = new ArrayList<>();
    private List<KimObservationStrategy> observationStrategies = new ArrayList<>();
    private boolean empty;

    @Override
    public String getUrn() {
        return this.urn;
    }

    @Override
    public Version getVersion() {
        return this.version;
    }

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

    @Override
    public List<Annotation> getAnnotations() {
        return this.annotations;
    }

    @Override
    public List<KimOntology> getOntologies() {
        return this.ontologies;
    }

    @Override
    public Collection<KimObservationStrategy> getObservationStrategies() {
        return this.observationStrategies;
    }

    @Override
    public boolean isEmpty() {
        return this.empty;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public void setOntologies(List<KimOntology> ontologies) {
        this.ontologies = ontologies;
    }

    public void setObservationStrategies(List<KimObservationStrategy> observationStrategies) {
        this.observationStrategies = observationStrategies;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}
