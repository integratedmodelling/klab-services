package org.integratedmodelling.klab.runtime.scale.time;

import java.util.Iterator;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.observations.scale.ITopologicallyComparable.MergingOption;
import org.integratedmodelling.klab.runtime.scale.ExtentImpl;

public class TimeImpl extends ExtentImpl<Time> implements Time {

	private static final long serialVersionUID = 5936628543110335175L;

	@Override
	public Time at(Object... locators) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getRank() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Time getBoundingExtent() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getDimensionSize(Unit unit) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Pair<Double, Unit> getStandardizedDimension(Locator locator) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Time merge(Time other, LogicalConnector how) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends Locator> T as(Class<T> cls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean contains(Time o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean overlaps(Time o) {
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
	public Time collapse() {
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
	public boolean is(org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Type type) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Type getTimeType() {
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
	public TimeInstant getFocus() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean hasChangeDuring(Time time) {
		// TODO Auto-generated method stub
		return false;
	}

}
