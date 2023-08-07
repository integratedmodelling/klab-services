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
package org.integratedmodelling.klab.api.services.runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;

/**
 * Dataflows in k.LAB represent "raw" computations, which can be run by a
 * {@link RuntimeService} to create, compute and link {@link Artifact}s in
 * response to a query for the observation of some {@link Knowledge} in a
 * {@link ContextScope}. The computation is stripped of all semantics (although
 * it may report semantics in metadata for documentation and linking). Therefore
 * it can be run by a semantically-unaware workflow system, as long as the T
 * type is a non-semantic artifact.
 * <p>
 * A dataflow is the result of successive resolution, and there is only one
 * dataflow per context, which is added to by successive resolutions. According
 * to the resolution context, these may add root-level actuators or extend those
 * in the actuator tree. For example, instantiated objects are then resolved;
 * the same happens for abstract observables, whose resolution explains all the
 * concretized observables in the context. It is normal in a context to go back
 * and forth between the resolver and the runtime; the final dataflow, however,
 * must recreate the entire context it describes when run.
 * <p>
 * In dataflow, actuators that are dependent are those that describe dependent
 * observables (qualities and processes) or are defined as <code>void</code> for
 * independent observables. Those create their target observations when outside
 * of an instantiator. So an {@link Instance} is compiled to a void top-level
 * actuator, possibly containing a non-trivial resolved dataflow. Instantiated
 * objects are built by an <code>object</code> actuator, which may contain void
 * ones to resolve the individual instances in their coverages.
 * <p>
 * Dataflows can be serialized and rebuilt from KDL specifications. They report
 * coverage which is not the original coverage of the resolution that created
 * them, but the <em>total</em> coverage they can be computed in.
 * <p>
 * Dataflows written by users or created by k.LAB can be stored as URN-specified
 * {@link Resource}s, which can be referenced in k.LAB models.
 * <p>
 * A dataflow lists a series of top-level {@link Actuator actuator}s, which must
 * be computed in the specified order.
 * <p>
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 * @param <T> the most specific type of artifact this dataflow will build when
 *            run.
 * @since 0.10.0
 */
public interface Dataflow<T extends Artifact> extends Serializable {

	/**
	 * An empty dataflow is a valid dataflow that produces an
	 * {@link Artifact#isEmpty() empty artifact} when run in its scale.
	 *
	 * @return true if the dataflow is empty
	 */
	boolean isEmpty();

	/**
	 * The geometry must compile to the total coverage of this dataflow, resulting
	 * from intersecting the coverage of all the actuators it contains. The only
	 * situation in which it contains a specific extent is when the dataflow builds
	 * an instance (but the root dataflow may union >1 of those). It may contain
	 * representational constraints derived from the models compiled in it, such as
	 * geometry (e.g. grid) or occurrent time.
	 * 
	 * @return
	 */
	Geometry getCoverage();

	/**
	 * The root-level actuators in the dataflow. They correspond to successive
	 * resolutions, with sequential dependency on one another. For example, change
	 * is computed as an independent resolution to be executed after the first
	 * resolution has computed the initial conditions.
	 * 
	 * @return
	 */
	List<Actuator> getComputation();

	/**
	 * Any named resources used in the dataflow as contextualizer call parameters.
	 * These are serialized statements for classifications, lookup table definitions
	 * etc. The runtime must be prepared to match variable names in service calls to
	 * the contents of this map before executing the call.
	 * 
	 * @return
	 */
	Parameters<String> getResources();

	/**
	 * Modifies this dataflow by merging in another dataflow computed to resolve
	 * some {@link Knowledge} in the passed scope. This will add to the actuator
	 * that corresponds to the context observation in the scope, or to the main
	 * dataflow computations if the scope points to the root of the context. The
	 * coverage of the dataflow may be modified accordingly.
	 * 
	 * @param dataflow
	 * @param scope
	 */
	void add(Dataflow<T> dataflow, ContextScope scope);

	/**
	 * Return a new empty dataflow.
	 * 
	 * @param <T>
	 * @param resultClass
	 * @return
	 */
	public static <T extends Artifact> Dataflow<T> empty(Class<T> resultClass) {
		return new Dataflow<T>() {

			private static final long serialVersionUID = -1115441423700817816L;

			@Override
			public boolean isEmpty() {
				return true;
			}

			@Override
			public Coverage getCoverage() {
				return Coverage.empty();
			}

			@Override
			public List<Actuator> getComputation() {
				return Collections.emptyList();
			}

			@Override
			public Parameters<String> getResources() {
				return Parameters.create();
			}

			@Override
			public void add(Dataflow<T> dataflow, ContextScope scope) {
			}

		};
	}

}
