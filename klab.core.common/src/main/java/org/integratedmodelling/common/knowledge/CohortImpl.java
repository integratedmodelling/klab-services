package org.integratedmodelling.common.knowledge;

import org.ehcache.spi.service.ServiceConfiguration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class CohortImpl implements Cohort {

  private Metadata metadata = Metadata.create();
  private Observable observable;
  private long id = Observation.UNASSIGNED_ID;
  private long transientId = Klab.getNextId();
  private long parentTransientId = 0;
  private int childrenCount;
  private long parentId = -1;

  @Override
  public Observable getObservable() {
    return observable;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public void setObservable(Observable observable) {
    this.observable = observable;
  }

  @Override
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  @Override
  public Type classify() {
    return Type.COHORT;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }

  @Override
  public int getChildrenCount() {
    return childrenCount;
  }

  public void setChildrenCount(int childrenCount) {
    this.childrenCount = childrenCount;
  }

  @Override
  public long getParentId() {
    return parentId;
  }

  public void setParentId(long parentId) {
    this.parentId = parentId;
  }


}
