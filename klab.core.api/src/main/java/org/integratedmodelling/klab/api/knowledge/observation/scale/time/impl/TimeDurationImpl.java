package org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution.Type;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;

public class TimeDurationImpl implements TimeDuration {

	private static final long serialVersionUID = 7766515470442027169L;

	@Override
	public int compareTo(TimeDuration o) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public TimeDuration anchor(TimeInstant instant) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAnchored() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public TimeInstant getStart() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Type getResolution() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isRegular() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long getMilliseconds() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getMaxMilliseconds() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getCommonDivisorMilliseconds() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Pair<TimeInstant, TimeInstant> localize() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getSpecification() {
		// TODO Auto-generated method stub
		return null;
	}

}
