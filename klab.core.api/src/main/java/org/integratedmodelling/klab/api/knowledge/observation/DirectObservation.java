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
package org.integratedmodelling.klab.api.knowledge.observation;

import java.util.Collection;

import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.ObjectArtifact;
import org.integratedmodelling.klab.api.knowledge.Observable;

/**
 * An observation that can be acknowledged on its own without referencing
 * another. Every observation except qualities, processes and configurations is
 * direct. All direct observations can own {@link State}s that contextualize
 * their observed qualities.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public abstract interface DirectObservation extends Observation, ObjectArtifact {

	/**
	 * <p>
	 * All direct observations have naming dignity.
	 * </p>
	 *
	 * @return a {@link java.lang.String} object.
	 */
	String getName();

	/**
	 * Direct observations may have children. This is a convenience method to find a
	 * particular child artifact.
	 * 
	 * @param observable
	 * @return
	 */
	Observation getChildObservation(Observable observable);

	/**
	 * <p>
	 * Direct observations may have states of their own.
	 * </p>
	 *
	 * @return a {@link java.util.Collection} object.
	 */
	Collection<State> getStates();

	@Override
	DirectObservation at(Locator locator);

	/**
	 * If the observation derives from an emerging pattern, return it here.
	 * 
	 * @return
	 */
	Pattern getOriginatingPattern();

	/**
	 * Direct observations may die in action.
	 * 
	 * @return true if alive, i.e. receiving events from scheduler and context.
	 */
	boolean isActive();

}
