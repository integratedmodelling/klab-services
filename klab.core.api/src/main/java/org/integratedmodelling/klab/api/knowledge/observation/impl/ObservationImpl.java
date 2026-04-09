package org.integratedmodelling.klab.api.knowledge.observation.impl;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serial;
import java.net.URL;
import java.util.*;
import java.util.HashMap;

/**
 * A "naked" observation only has an observable + metadata and provenance info. Additional metadata
 * are inserted to distinguish submitted observations from those that have been generated during
 * resolution.
 */
public class ObservationImpl implements Observation {

  @Serial private static final long serialVersionUID = 8993700853991252827L;

  /** The catalog name for the URN of a resolved substantial observation. */
  public static final String INDIVIDUALS_CATALOG_NAME = "individuals";

  private Observable observable;
  private Geometry geometry;
  private Metadata metadata = Metadata.create();
  private long id = UNASSIGNED_ID;
  private long transientId = Klab.getNextId();
  private long parentTransientId = 0;
  private int childrenCount;
  private long parentId = -1;
  private String urn;
  private Object value;
  private String name;
  private Artifact.Type type = Artifact.Type.OBSERVATION;
  private double resolvedCoverage;
  private List<Long> eventTimestamps = new ArrayList<>();
  private boolean substantialQuality;
  private List<Notification> notifications = new ArrayList<>();
  private ContextualizationData contextualizationData;
  private boolean empty;

  public static class ContextualizationDataImpl implements ContextualizationData {

    private Data data;
    private String serviceId;
    private String adapterId;
    private Parameters<String> parameters = Parameters.create();
    private URL serviceUrl;
    private Data.ShardingStrategy nativeShardingStrategy;
    private boolean persistent;

    @Override
    public Data getData() {
      return this.data;
    }

    @Override
    public String getServiceId() {
      return this.serviceId;
    }

    @Override
    public String getAdapterId() {
      return this.adapterId;
    }

    @Override
    public Parameters<String> getParameters() {
      return this.parameters;
    }

    public void setData(Data data) {
      this.data = data;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
    }

    public void setAdapterId(String adapterId) {
      this.adapterId = adapterId;
    }

    public void setParameters(Parameters<String> parameters) {
      this.parameters = parameters;
    }

    @Override
    public URL getServiceUrl() {
      return serviceUrl;
    }

    public void setServiceUrl(URL serviceUrl) {
      this.serviceUrl = serviceUrl;
    }

    @Override
    public Data.ShardingStrategy getNativeShardingStrategy() {
      return nativeShardingStrategy;
    }

    public void setNativeShardingStrategy(Data.ShardingStrategy nativeShardingStrategy) {
      this.nativeShardingStrategy = nativeShardingStrategy;
    }

    @Override
    public boolean isPersistent() {
      return persistent;
    }

    public void setPersistent(boolean persistent) {
      this.persistent = persistent;
    }

    @Override
    public String toString() {
      return "ContextualizationDataImpl{"
          + "serviceId='"
          + serviceId
          + '\''
          + ", adapterId='"
          + adapterId
          + '\''
          + '}';
    }
  }

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
    return type;
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
  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
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
    return empty;
  }

  public void setEmpty(boolean empty) {
    this.empty = empty;
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

  @Override
  public ContextualizationData getContextualizationData() {
    return contextualizationData;
  }

  public void setContextualizationData(ContextualizationData contextualizationData) {
    this.contextualizationData = contextualizationData;
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
        + "]"
        + (contextualizationData == null ? "X" : "*");
  }

  @Override
  public List<Notification> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Notification> notifications) {
    this.notifications = notifications;
  }

  public void setType(Artifact.Type type) {
    this.type = type;
  }

  /**
   * Non-API: we record the fact that an observation is a quality of a substantial, so that we know
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

  @Override
  public int getChildrenCount() {
    return childrenCount;
  }

  public void setChildrenCount(int childrenCount) {
    this.childrenCount = childrenCount;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }
}
