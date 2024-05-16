package org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Geometry.Encoding;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

import java.io.Serial;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class TimePeriodImpl implements TimePeriod {

	@Serial
	private static final long serialVersionUID = -3603666163320131572L;

	@Override
	public Time collapsed() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Time getExtent(long stateIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TimeInstant getStart() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TimeInstant getEnd() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TimeDuration getStep() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Resolution getResolution() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Resolution getCoverageResolution() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getCoverageLocatorStart() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getCoverageLocatorEnd() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean is(Type type) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Type getTimeType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean intersects(Dimension dimension) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double getLength(Unit temporalUnit) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Time getNext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Time earliest() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Time latest() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Time initialization() {
		return null;
	}

	@Override
	public Time termination() {
		return null;
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
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <T extends TopologicallyComparable<T>> Extent<T> merge(Extent<T> other, LogicalConnector how) {
		return null;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		return 0;
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isRegular() {
		// TODO Auto-generated method stub
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Parameters<String> getParameters() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ExtentDimension extentDimension() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String encode(Encoding... options) {
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

//
//	protected ServiceCall encodeCall() {
//		ServiceCallImpl ret = new ServiceCallImpl();
//		// TODO
//		return ret;
//	}

	@Override
	public String encode(String language) {
		return /*encodeCall().encode(language)*/ "TIME-UNIMPLEMENTED";
	}
}
