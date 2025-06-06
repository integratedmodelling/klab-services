package org.integratedmodelling.klab.api.knowledge.observation.impl;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.provenance.Provenance;

import java.io.Serial;
import java.util.*;

/**
 * A "naked" observation only has an observable + metadata and provenance info. This is not abstract
 * because descriptions like contextualizing a generic concept produce pure semantics, which is
 * expressed as a simple Observation with an observable that is the OR of all the contextualized
 * components corresponding to the generic ones in the observed one.
 */
public class ObservationImpl implements Observation {

  @Serial private static final long serialVersionUID = 8993700853991252827L;

  private Observable observable;
  private Geometry geometry;
  private Metadata metadata = Metadata.create();
  private long id = UNASSIGNED_ID;
  private String urn;
  private Object value;
  private String name;
  private double resolvedCoverage;
  private List<Long> eventTimestamps = new ArrayList<>();
  private boolean substantialQuality;
  private long transientId = Klab.getNextId();

  public ObservationImpl() {}

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
  public Artifact trace(Concept role, Observation roleContext) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<Artifact> getChildArtifacts() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<Artifact> collect(Concept role, Observation roleContext) {
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
  public Artifact.Type getType() {
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
  public boolean hasChangedDuring(Time time) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public long getId() {
    return this.id;
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

  public void setGeometry(Geometry geometry) {
    this.geometry = geometry;
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
  public double getResolvedCoverage() {
    return resolvedCoverage;
  }

  public void setResolvedCoverage(double resolvedCoverage) {
    this.resolvedCoverage = resolvedCoverage;
  }

  @Override
  public List<Long> getEventTimestamps() {
    return eventTimestamps;
  }

  public void setEventTimestamps(List<Long> eventTimestamps) {
    this.eventTimestamps = eventTimestamps;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  /** DO NOT CALL - reserved for serialization purposes */
  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  @Override
  public String toString() {
    return "(OBS) "
        + observable
        + " ["
        + urn
        + "#"
        + (geometry == null ? "0" : geometry.size())
        + "]";
  }

  /**
   * Non-API: we record the fact that an observation is a quality of a substantial so that we know
   * when to initialize or check for initialization without having to interrogate the knowledge
   * graph to extract the observation's parent.
   *
   * @return
   */
  public boolean isSubstantialQuality() {
    return this.substantialQuality;
  }

  public void setSubstantialQuality(boolean substantialQuality) {
    this.substantialQuality = substantialQuality;
  }
}
