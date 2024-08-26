package org.integratedmodelling.klab.api.knowledge.observation.impl;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.provenance.Provenance;

/**
 * A "naked" observation only has an observable + metadata and provenance info. This is not abstract because
 * descriptions like contextualizing a generic concept produce pure semantics, which is expressed as a simple
 * Observation with an observable that is the OR of all the contextualized components corresponding to the
 * generic ones in the observed one.
 */
public class ObservationImpl implements Observation {

    @Serial
    private static final long serialVersionUID = 8993700853991252827L;

    private Observable observable;
    private Geometry geometry;
    private Metadata metadata = Metadata.create();
    private Geometry observerGeometry;
    private long id = UNASSIGNED_ID;
    private String urn;
    private boolean resolved;
    private Object value;
    private String name;

    public ObservationImpl() {
    }

    protected ObservationImpl(Observable observable) {
        this.observable = observable;
    }

    @Override
    public Geometry getGeometry() {
        return this.geometry;
    }

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

    @Override
    public String getUrn() {
        return this.urn;
    }

    @Override
    public Collection<Artifact> collect(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Artifact trace(Concept role, DirectObservation roleContext) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Artifact> getChildArtifacts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Artifact> collect(Concept role, DirectObservation roleContext) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int groupSize() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Provenance getProvenance() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Type getType() {
        return observable.getArtifactType();
    }

    @Override
    public void release() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean is(Class<?> cls) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public <T> T as(Class<?> cls) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isArchetype() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long getLastUpdate() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean hasChangedDuring(Time time) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    public long getTimestamp() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Iterator<Artifact> iterator() {
        return Collections.singleton((Artifact) this).iterator();
    }

    @Override
    public Observable getObservable() {
        return this.observable;
    }

    @Override
    public Identity getObserver() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observation at(Locator locator) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Annotation> getAnnotations() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setObservable(Observable observable) {
        this.observable = observable;
    }


    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    @Override
    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    @Override
    public Geometry getObserverGeometry() {
        return observerGeometry;
    }

    public void setObserverGeometry(Geometry observerGeometry) {
        this.observerGeometry = observerGeometry;
    }

    @Override
    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ObservationImpl{" +
                "observable=" + observable +
                ", geometry=" + geometry +
                ", metadata=" + metadata +
                ", observerGeometry=" + observerGeometry +
                ", id=" + id +
                ", urn='" + urn + '\'' +
                ", resolved=" + resolved +
                ", value=" + value +
                ", name='" + name + '\'' +
                '}';
    }
}
