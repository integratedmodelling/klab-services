/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.provenance;

import java.util.Collection;
import java.util.Optional;

import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;

/**
 * Activity (process). Primary processes produce artifacts. Secondary processes
 * (after creation) may modify them. Activities in k.LAB represent each
 * execution of an actuator, which represents a IPlan (part of the overall plan
 * that is the IActuator).
 * 
 * @author Ferd
 * @version $Id: $Id
 */
public interface Activity extends Provenance.Node {

	/**
	 * System time of start.
	 *
	 * @return a long.
	 */
	long getStart();

	/**
	 * System time of end.
	 *
	 * @return a long.
	 */
	long getEnd();

	/**
	 * Scheduler time of action. Null if agent is not the k.LAB scheduler.
	 * 
	 * @return
	 */
	Time getSchedulerTime();

	/**
	 * If the action was caused by another action, return the action that caused it.
	 *
	 * @return a {@link java.util.Optional} object.
	 */
	Optional<Activity> getCause();

	/**
	 * Actions are made by agents.
	 *
	 * @return a {@link org.integratedmodelling.klab.api.provenance.Agent} object.
	 */
	Agent getAgent();

}
