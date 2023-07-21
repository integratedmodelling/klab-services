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
import java.util.Map;

import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * A subject can be inside a root observation or (unique among observation)
 * <i>be</i> a root observation itself. As identities, all Subjects in a session
 * can also be observers for other observations, even in different contexts. As
 * observations, their lineage is accessible through their {@link ContextScope},
 * which is also where observations are made. Only subjects can be the context
 * of all the other observations: events, processes, relationships,
 * configurations and other subjects.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Subject extends DirectObservation, Identity {

	/**
	 * <p>
	 * getEvents.
	 * </p>
	 *
	 * @return a {@link java.util.Collection} object.
	 */
	Collection<Event> getEvents();

	/**
	 * <p>
	 * getProcesses.
	 * </p>
	 *
	 * @return a {@link java.util.Collection} object.
	 */
	Collection<Process> getProcesses();

	/**
	 * <p>
	 * getSubjects.
	 * </p>
	 *
	 * @return a {@link java.util.Collection} object.
	 */
	Collection<Subject> getSubjects();

	/**
	 * <p>
	 * getRelationships.
	 * </p>
	 *
	 * @return a {@link java.util.Collection} object.
	 */
	Collection<Relationship> getRelationships();

	/**
	 * <p>
	 * getConfigurations.
	 * </p>
	 *
	 * @return a {@link java.util.Map} object.
	 */
	Map<Concept, Configuration> getConfigurations();

}
