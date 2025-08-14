package org.integratedmodelling.common.data;

import java.util.*;
import java.util.stream.Collectors;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.common.data.Instance;

/**
 * The builder to use when the digital twin is not available locally and a data package is prepared
 * for a remote client to consume. Wraps an Avro-enabled {@link Instance}.
 */
public class SerializingDataBuilder implements Data.Builder {

  private final Instance.Builder builder;
  private Instance.Builder parentBuilder;
  private final Geometry geometry;
  private Data.FillCurve fillCurve;
  private Map<Object, Integer> objectKey;
  private int objectCounter = 1;
  private String adapter;

  public SerializingDataBuilder(String name, Observable observable, Geometry geometry) {
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
  }

  private SerializingDataBuilder(
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
  public Data.Builder adapter(String adapterId) {
    this.adapter = adapterId;
    return this;
  }

  @Override
  public Data.Builder metadata(String key, Object value) {
    builder.getMetadata().put(key, Utils.Data.asString(value));
    return this;
  }

  @Override
  public Data.Builder state(Observable observable) {
    return new SerializingDataBuilder(
        observable.getStatedName() == null ? observable.getUrn() : observable.getStatedName(),
        observable,
        this.geometry,
        this.builder);
  }

  @Override
  public Data.Builder object(String name, Observable observable, Geometry geometry) {
    return new SerializingDataBuilder(name, observable, geometry, this.builder);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T extends Storage.Shard> T buffer(
      Class<T> fillerClass, Data.FillCurve fillCurve) {
//    if (fillerClass == Storage.DoubleBuffer.class) {
//      return (T) new DoubleBufferFiller(fillCurve);
//    }
    throw new KlabUnimplementedException(
        "Buffer request for " + fillerClass.getSimpleName() + " illegal or unimplemented");
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

//  private class DoubleBufferFiller extends MockBuffer implements Storage.DoubleBuffer {
//
//    DoubleBufferFiller(Data.FillCurve fillCurve) {
//      builder.setFillingCurve(fillCurve.name());
//      builder.setDoubleData(
//          new ArrayList<>(
//              /* TODO more proper pre-allocation, or use temp memory-mapped space with a List interface */ ));
//    }
//
//    @Override
//    public DoubleScanner scan() {
//      throw new KlabIllegalStateException(
//          "This buffer is intended for use in a builder and only supports input methods");
//    }
//
//    @Override
//    public double get(long offset) {
//      return builder.getDoubleData().get((int) offset);
////      throw new KlabIllegalStateException(
////          "This buffer is intended for use in a builder and only supports input methods");
//    }
//
//    @Override
//    public void set(double value, long offset) {
//      builder.getDoubleData().add(value);
//    }
//
//    @Override
//    public void fill(double value) {
//      // TODO set a singleDoubleValue
//      //      builder.getDoubleData().add(value);
//    }
//  }

  /* private class DoubleInstanceFiller implements Data.DoubleFiller {

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
  }*/

//  private abstract static class MockBuffer implements Storage.Buffer {
//
//    @Override
//    public long offset(Data.Cursor other, long... dimensionOffsets) {
//      throw new KlabIllegalStateException(
//          "This buffer is intended for use in a builder and only supports input methods");
//    }
//
//    @Override
//    public long size() {
//      return 0;
//    }
//
//    @Override
//    public long offset() {
//      throw new KlabIllegalStateException(
//          "This buffer is intended for use in a builder and only supports input methods");
//    }
//
//    @Override
//    public String getUrn() {
//      return "";
//    }
//
//    @Override
//    public long getTimestamp() {
//      throw new KlabIllegalStateException(
//          "This buffer is intended for use in a builder and only supports input methods");
//    }
//
//    @Override
//    public long getId() {
//      throw new KlabIllegalStateException(
//          "This buffer is intended for use in a builder and only supports input methods");
//    }
//
//    @Override
//    public long getTransientId() {
//      throw new KlabIllegalStateException(
//          "This buffer is intended for use in a builder and only supports input methods");
//    }
//  }
}
