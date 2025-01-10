package org.integratedmodelling.common.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.common.data.Instance;

import java.util.ArrayList;
import java.util.List;

public class DataBuilderImpl implements Data.Builder {

  private final Instance.Builder instanceBuilder = Instance.newBuilder();

  class ObjectFiller implements Data.ObjectFiller {

    class ObjectBuilder implements Data.ObjectBuilder {

      public ObjectBuilder() {

      }

      Geometry geometry;
      String name;
      DataBuilderImpl dataBuilder;

      @Override
      public Data.ObjectBuilder name(String string) {
        this.name = name;
        return this;
      }

      @Override
      public Data.ObjectBuilder geometry(Geometry geometry) {
        this.geometry = geometry;
        return this;
      }

      @Override
      public Data.Builder builder() {
        this.dataBuilder = new DataBuilderImpl();
        return this.dataBuilder;
      }
    }

    @Override
    public Data.ObjectBuilder add() {
      var ret = new ObjectBuilder();
      return ret;
    }
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
    return null;
  }
}
