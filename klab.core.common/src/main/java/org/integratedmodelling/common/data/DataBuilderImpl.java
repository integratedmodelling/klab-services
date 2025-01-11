package org.integratedmodelling.common.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.common.data.Instance;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Builder that wraps an Instance directly */
public class DataBuilderImpl implements Data.Builder {

  private Instance.Builder instanceBuilder;
  private ObjectFiller.ObjectBuilder objectBuilder; // not null if the instance is for an object

  class ObjectFiller implements Data.ObjectFiller {

    class ObjectBuilder implements Data.ObjectBuilder {

      private final Instance.Builder objectBuilder = Instance.newBuilder();

      public ObjectBuilder() {}

      Geometry geometry;
      String name;
      DataBuilderImpl parentBuilder;
      DataBuilderImpl dataBuilder;

      @Override
      public Data.ObjectBuilder name(String string) {
        this.objectBuilder.setName(string);
        return this;
      }

      @Override
      public Data.ObjectBuilder withMetadata(String key, Object value) {
        this.objectBuilder.getMetadata().put(key, Utils.Data.asString(value));
        return this;
      }

      @Override
      public Data.ObjectBuilder geometry(Geometry geometry) {
        this.objectBuilder.setGeometry(geometry.as(Geometry.class).encode());
        return this;
      }

      @Override
      public Data.Builder builder() {
        this.dataBuilder = new DataBuilderImpl(this);
        return this.dataBuilder;
      }

      @Override
      public void add() {
        if (dataBuilder != null) {
          objectBuilder.getInstances().add(dataBuilder.instanceBuilder.build());
        }
        parentBuilder.instanceBuilder.getInstances().add(objectBuilder.build());
      }
    }

    @Override
    public Data.ObjectBuilder newObject() {
      var ret = new ObjectBuilder();
      ret.objectBuilder.setNotifications(new ArrayList<>());
      ret.objectBuilder.setAttributes(new LinkedHashMap<>());
      ret.objectBuilder.setMetadata(new LinkedHashMap<>());
      ret.objectBuilder.setInstances(new ArrayList<>());
      ret.objectBuilder.setStates(new ArrayList<>());
      ret.parentBuilder = DataBuilderImpl.this;
      return ret;
    }
  }

  public DataBuilderImpl() {
    initInstance();
  }

  public DataBuilderImpl(String name, Geometry geometry) {
    initInstance();
    instanceBuilder.setName(name);
    instanceBuilder.setGeometry(geometry.encode());
  }

  private void initInstance() {
    this.instanceBuilder = Instance.newBuilder();
    this.instanceBuilder.setName("");
    this.instanceBuilder.setGeometry(Geometry.EMPTY.encode());
    this.instanceBuilder.setStates(new ArrayList<>());
    this.instanceBuilder.setMetadata(new LinkedHashMap<>());
    this.instanceBuilder.setAttributes(new LinkedHashMap<>());
    this.instanceBuilder.setNotifications(new ArrayList<>());
    this.instanceBuilder.setInstances(new ArrayList<>());
  }

  private DataBuilderImpl(ObjectFiller.ObjectBuilder objectBuilder) {
    initInstance();
    this.objectBuilder = objectBuilder;
  }

  @Override
  public Data.FillCurve fillCurve() {
    return Data.FillCurve.S2_YX;
  }

  @Override
  public Data.BooleanFiller booleanState(Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.BooleanFiller booleanState(String stateIdentifier, Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.FloatFiller floatState(Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.FloatFiller floatState(String stateIdentifier, Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.IntFiller intState(Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.IntFiller intState(String stateIdentifier, Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.DoubleFiller doubleState(Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.DoubleFiller doubleState(String stateIdentifier, Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.KeyedFiller keyedState(Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.KeyedFiller keyedState(String stateIdentifier, Data.FillCurve fillCurve) {
    return null;
  }

  @Override
  public Data.ObjectFiller objectCollection() {
    return new ObjectFiller();
  }

  @Override
  public Data.ObjectFiller objectCollection(String observationIdentifier) {
    return null;
  }

  @Override
  public void notification(Notification notification) {}

  @Override
  public Data build() {
    return new DataImpl(instanceBuilder.build());
  }
}
