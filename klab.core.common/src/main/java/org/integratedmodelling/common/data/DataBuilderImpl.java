package org.integratedmodelling.common.data;

import java.util.*;
import java.util.stream.Collectors;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.common.data.Instance;

public class DataBuilderImpl implements Data.Builder {

  private final Instance.Builder builder;
  private Instance.Builder parentBuilder;
  private final Geometry geometry;
//  private Observable observable;
  private Data.Filler filler;
  private Data.SpaceFillingCurve spaceFillingCurve;
  private Map<Object, Integer> objectKey;
  private int objectCounter = 1;

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
    this.builder.setDataKey(null);
    this.geometry = geometry;
//    this.observable = observable;
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
  public <T extends Data.Filler> T filler(Class<T> fillerClass, Data.SpaceFillingCurve curve) {

    if (filler != null) {
      throw new KlabIllegalStateException("A data instance can only have one filler");
    }

    if (fillerClass == Data.DoubleFiller.class) {
      return (T) new DoubleInstanceFiller(curve);
    } else if (fillerClass == Data.FloatFiller.class) {
      return (T) new FloatInstanceFiller(curve);
    } else if (fillerClass == Data.IntFiller.class) {
      return (T) new IntInstanceFiller(curve);
    } else if (fillerClass == Data.LongFiller.class) {
      return (T) new LongInstanceFiller(curve);
    } else if (fillerClass == Data.ObjectFiller.class) {
      return (T) new ObjectInstanceFiller(curve);
    }
    throw new KlabIllegalStateException(
        "Unexpected filler class " + fillerClass.getCanonicalName());
  }

  @Override
  public Data build() {
    if (objectKey != null) {
      builder.setDataKey(
          objectKey.entrySet().stream()
              .map(e -> Map.entry(e.getKey().toString(), e.getValue().toString()))
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
    var instance = builder.build();
    if (parentBuilder != null) {
      if (parentBuilder.getInstances() == null) {
        parentBuilder.setInstances(new ArrayList<>());
      }
      parentBuilder.getInstances().add(instance);
    }
    return BaseDataImpl.create(instance);
  }

  private class DoubleInstanceFiller implements Data.DoubleFiller {

    DoubleInstanceFiller(Data.SpaceFillingCurve spaceFillingCurve) {
      builder.setFillingCurve(spaceFillingCurve.name());
      builder.setDoubleData(new ArrayList<>());
    }

    @Override
    public void add(double value) {
      builder.getDoubleData().add(value);
    }
  }

  private class FloatInstanceFiller implements Data.FloatFiller {

    FloatInstanceFiller(Data.SpaceFillingCurve spaceFillingCurve) {
      builder.setFillingCurve(spaceFillingCurve.name());
      builder.setFloatData(new ArrayList<>());
    }

    @Override
    public void add(float value) {
      builder.getFloatData().add(value);
    }
  }

  private class LongInstanceFiller implements Data.LongFiller {

    LongInstanceFiller(Data.SpaceFillingCurve spaceFillingCurve) {
      builder.setFillingCurve(spaceFillingCurve.name());
      builder.setLongData(new ArrayList<>());
    }

    @Override
    public void add(long value) {
      builder.getLongData().add(value);
    }
  }

  private class IntInstanceFiller implements Data.IntFiller {

    IntInstanceFiller(Data.SpaceFillingCurve spaceFillingCurve) {
      builder.setFillingCurve(spaceFillingCurve.name());
      builder.setIntData(new ArrayList<>());
    }

    @Override
    public void add(int value) {
      builder.getIntData().add(value);
    }
  }

  private class ObjectInstanceFiller implements Data.ObjectFiller {

    ObjectInstanceFiller(Data.SpaceFillingCurve spaceFillingCurve) {
      builder.setFillingCurve(spaceFillingCurve.name());
      builder.setIntData(new ArrayList<>());
      objectKey = new HashMap<>();
    }

    @Override
    public void add(Object value) {
      builder.getIntData().add(objectKey.computeIfAbsent(value, v -> objectCounter++));
    }
  }
}
