/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found 
 * see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.knowledge.observation.scale.time;

import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl.TimeInstantImpl;
import org.integratedmodelling.klab.api.lang.kim.KimDate;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * 
 * @author Ferd
 *
 */
public interface TimeInstant extends Serializable, Comparable<TimeInstant>, Locator {

	/**
	 * <p>
	 * getMillis.
	 * </p>
	 *
	 * @return a long.
	 */
	long getMilliseconds();

	/**
	 * 
	 * @param end
	 * @return
	 */
	boolean isAfter(TimeInstant end);

	/**
	 * 
	 * @param start
	 * @return
	 */
	boolean isBefore(TimeInstant start);

	/**
	 * Return a re-parseable Kim specification.
	 * 
	 * @return
	 */
	String getSpecification();

	/**
	 * Day in the year starting a 0.
	 * 
	 * @return
	 */
	int getDayOfYear();

	/**
	 * 
	 * @return
	 */
	int getYear();

	long getPeriods(TimeInstant other, Resolution resolution);

	TimeInstant minus(int periods, Resolution resolution);

	TimeInstant plus(int periods, Resolution resolution);

	/**
	 * True if this time starts at a point correspondent with the beginning of the
	 * passed resolution - e.g. if the resolution is Months, return true if the time
	 * is the beginning of a month.
	 * 
	 * @param res
	 * @return
	 */
	boolean isAlignedWith(Resolution res);

	int getDay();

	int getMonth();

	int getHour();

	int getMinute();

	/**
	 * Minimum between two instants, returning null without error if any of the
	 * operands is null.
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	static TimeInstant min(TimeInstant a, TimeInstant b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.getMilliseconds() > b.getMilliseconds() ? b : a;
	}

	/**
	 * Maximum between two instants, returning null without error if any of the
	 * operands is null.
	 * 
	 * @param a
	 * @param b
	 * @return
	 */
	static TimeInstant max(TimeInstant a, TimeInstant b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a.getMilliseconds() < b.getMilliseconds() ? b : a;
	}

	/**
	 * Adjust to beginning of nearest period
	 * 
	 * @param temporalAggregation
	 * @return
	 */
	TimeInstant beginOf(Resolution.Type temporalAggregation);

	/**
	 * Adjust to beginning of next nearest period
	 * 
	 * @param temporalAggregation
	 * @return
	 */
	TimeInstant endOf(Resolution.Type temporalAggregation);

	String toRFC3339String();

	public static TimeInstant create(int year, int month, int day) {
		return new TimeInstantImpl(year, month, day);
	}

	public static TimeInstant create(int year, int month, int day, int hour, int minutes, int seconds,
			int milliseconds) {
		return new TimeInstantImpl(year, month, day, hour, minutes, seconds, milliseconds);
	}

	public static TimeInstant create(int year) {
		return new TimeInstantImpl(year);
	}

	public static TimeInstant create(ZonedDateTime time) {
		return new TimeInstantImpl(time);
	}

	public static TimeInstant create(long milliseconds) {
		return new TimeInstantImpl(milliseconds);
	}

	public static TimeInstant create() {
		return new TimeInstantImpl();
	}

	static TimeInstant create(KimDate kimDate) {
		return create(kimDate.getYear(), kimDate.getMonth(), kimDate.getDay(), kimDate.getHour(), kimDate.getMin(),
				kimDate.getSec(), kimDate.getMs());
	}
}
