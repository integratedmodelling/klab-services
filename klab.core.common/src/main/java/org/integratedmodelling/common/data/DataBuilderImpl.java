package org.integratedmodelling.common.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.common.data.Instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class DataBuilderImpl implements Data.Builder {

  Instance.Builder builder;
  Instance.Builder parentBuilder;
  Geometry geometry;
  Observable observable;

  public DataBuilderImpl(String name, Observable observable, Geometry geometry) {
    this.builder = Instance.newBuilder();
    this.builder.setName(name);
    this.builder.setGeometry(geometry.encode());
    this.builder.setObservable(observable.getUrn());
    this.builder.setNotifications(new ArrayList<>());
    this.builder.setMetadata(new LinkedHashMap<>());
    this.builder.setInstances(null);
    this.builder.setDoubleData(null);
    this.builder.setFloatData(null);
    this.builder.setIntData(null);
    this.builder.setLongData(null);
    this.geometry = geometry;
    this.observable = observable;
  }

  private DataBuilderImpl(
      String name, Observable observable, Geometry geometry, Instance.Builder parentBuilder) {
    this(name, observable, geometry);
    this.parentBuilder = parentBuilder;
  }

  @Override
  public Data.Builder notification(Notification notification) {
    var nBuilder = org.integratedmodelling.klab.common.data.Notification.newBuilder();
    //
    this.builder.getNotifications().add(nBuilder.build());
    return this;
  }

  @Override
  public Data.Builder metadata(String key, Object value) {
    builder.getMetadata().put(key, Utils.Data.asString(value));
    return this;
  }

  @Override
  public Data.Builder state(Observable observable) {
    return new DataBuilderImpl(
        observable.getStatedName() == null ? observable.getUrn() : observable.getStatedName(),
        observable,
        this.geometry,
        this.builder);
  }

  @Override
  public Data.Builder object(String name, Observable observable, Geometry geometry) {
    return new DataBuilderImpl(name, observable, geometry, this.builder);
  }

  @Override
  public <T extends Data.Filler> T filler(Class<T> fillerClass) {
    //    switch (observable.getDescriptionType()) {
    //        // VALIDATE and RETURN REQUESTED FILLER
    //    }
    return null;
  }

  @Override
  public Data build() {
    var instance = builder.build();
    if (parentBuilder != null) {
      if (parentBuilder.getInstances() == null) {
        parentBuilder.setInstances(new ArrayList<>());
      }
      parentBuilder.getInstances().add(instance);
    }
    return BaseDataImpl.create(instance);
  }
}
