package org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resources.adapters.Parameter;

import java.io.Serial;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class TimePeriodImpl implements TimePeriod {

  @Serial private static final long serialVersionUID = -3603666163320131572L;

  TimeInstant start;
  TimeInstant end;
  Resolution resolution;
  Time.Type type = Type.PHYSICAL;

  public TimePeriodImpl() {}

  public TimePeriodImpl(long start, long end) {
    this.start = TimeInstant.create(start);
    this.end = TimeInstant.create(end);
    this.resolution = Resolution.of(this.start, this.end);
  }

  public TimePeriodImpl(long start, long end, Time.Type type) {
    this.start = TimeInstant.create(start);
    this.end = TimeInstant.create(end);
    this.resolution = Resolution.of(this.start, this.end);
    this.type = type;
  }

  @Override
  public Time collapsed() {
    return this;
  }

  @Override
  public Time getExtent(long stateIndex) {
    if (stateIndex != 0) {
      throw new KlabIllegalArgumentException(
          "Illegal extent extracted from time period: " + stateIndex);
    }
    return this;
  }

  @Override
  public TimeInstant getStart() {
    return start;
  }

  @Override
  public TimeInstant getEnd() {
    return end;
  }

  @Override
  public TimeDuration getStep() {
    return TimeDuration.of(end.getMilliseconds() - start.getMilliseconds(), resolution.getType());
  }

  @Override
  public Resolution getResolution() {
    return resolution;
  }

  @Override
  public Resolution getCoverageResolution() {
    return resolution;
  }

  @Override
  public long getCoverageLocatorStart() {
    return 0;
  }

  @Override
  public long getCoverageLocatorEnd() {
    return 0;
  }

  @Override
  public boolean is(Type type) {
    return false;
  }

  @Override
  public Type getTimeType() {
    return type;
  }

  @Override
  public boolean intersects(Dimension dimension) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public double getLength(Unit temporalUnit) {
    // TODO Auto-generated method stub
    return end.getMilliseconds() - start.getMilliseconds();
  }

  @Override
  public Time getNext() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Time earliest() {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public Time latest() {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public Time initialization() {
    return type == Type.INITIALIZATION
        ? this
        : new TimePeriodImpl(start.getMilliseconds(), end.getMilliseconds(), Type.INITIALIZATION);
  }

  @Override
  public Time termination() {
    return type == Type.TERMINATION
        ? this
        : new TimePeriodImpl(start.getMilliseconds(), end.getMilliseconds(), Type.TERMINATION);
  }

  @Override
  public TimeInstant getFocus() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Time at(Locator locator) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int getRank() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public double getDimensionSize() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public Unit getDimensionUnit() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public double getStandardizedDimension(Locator locator) {
    return getDimensionSize();
  }

  @Override
  public <T extends TopologicallyComparable<T>> Extent<T> merge(
      Extent<T> other, LogicalConnector how) {
    return null;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean matches(Collection<Constraint> constraints) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public <T extends Locator> T as(Class<T> cls) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public long size() {
    return 1;
  }

  @Override
  public boolean contains(Time o) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean intersects(Time o) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Iterator<Time> iterator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public long[] locate(Dimension dimension) {
    // TODO
    return new long[0];
  }

  @Override
  public boolean isGeneric() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type getType() {
    return Dimension.Type.TIME;
  }

  @Override
  public boolean isRegular() {
    return false;
  }

  @Override
  public int getDimensionality() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public long offset(long... offsets) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public List<Long> getShape() {
    return List.of(start.getMilliseconds(), end.getMilliseconds());
  }

  @Override
  public Parameters<String> getParameters() {
    return Parameters.create();
  }

  @Override
  public ExtentDimension extentDimension() {
    return ExtentDimension.LINEAL;
  }

  @Override
  public String encode() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean distributed() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean contains(TimeInstant time) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean contains(long millisInstant) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean endsBefore(TimeInstant instant) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean endsBefore(Time other) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean overlaps(Time other) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public long getMillis() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String encode(String language) {
    return /*encodeCall().encode(language)*/ "TIME-UNIMPLEMENTED";
  }
}
