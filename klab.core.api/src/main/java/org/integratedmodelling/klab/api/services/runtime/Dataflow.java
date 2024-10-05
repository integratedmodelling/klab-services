/*
 * This file is part of k.LAB.
 *
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of
 * the Affero
 * GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory
 *  of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author
 * tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.services.runtime;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;

/**
 * Dataflows in k.LAB represent "raw" computations, created by the {@link Resolver} as a
 * response to observation queries and executed ("contextualized") by a
 * {@link RuntimeService} in a compatible {@link Scale} of choice to create, compute and
 * link {@link Artifact}s in response to a query for the observation of some
 * {@link Knowledge} in a {@link ContextScope}. The artifacts become part of the "digital
 * twin" represented by the context scope, and any behavior bound to them is initialized
 * and started. The computation no longer depends on reasoning over semantics, although it
 * may include semantics in its metadata for documentation, validation and possibly
 * further linking to other dataflows. Therefore a dataflow can be run by a
 * semantically-unaware workflow system, as long as the T type is a non-semantic
 * {@link Artifact}. In that case, the {@link Observable} in each {@link Actuator} it
 * contains can be used as "semantic metadata" and artifacts can be labeled using its
 * {@link Observable#getUrn()} method, or its {@link Observable#getName()} stated name.
 * <p>
 * A dataflow exposes a list of top-level {@link Actuator actuator}s, each of which
 * contains a chain of "child" actuators. The actuators in the child hierarchy belonging
 * to each "root" actuator must all be computed before those of the next, as the latter
 * may depend on the context created by running the previous chain. For example, the
 * actuators that initialize the observations in the context are created in a separate
 * hierarchy from those that create the processes that describe their change over time.
 * The order of computation and the possible parallelization between the children of each
 * root actuator are not encoded in the dataflow and must be computed by the runtime prior
 * to contextualization.
 * <p>
 * With the appropriate adapters and runtime library, dataflows can be translated to
 * <a href="https://www.commonwl.org/">CWL</a> or other scientific workflow
 * representations for
 * execution on the runtime of choice. The k.LAB resolver and runtime preserve semantics,
 * therefore the dataflows are mandatorily implemented in k.LAB as
 * <code>Dataflow&lt;Observation&gt;</code>.
 * <p>
 * A dataflow is the result of zero or more successive resolutions, and there is only one
 * top-level dataflow per context, created empty with the {@link ContextScope}. The
 * actuators from new dataflows resulting from query resolutions are added to it either at
 * the root level or extending actuators already in the actuator tree. For example,
 * instantiated objects are first instantiated by an instantiating actuator, after which
 * each instance is resolved, producing new dataflows whose actuators are added as
 * children of the instantiating one (identical dataflows are merged into one by unioning
 * their coverage). The same happens for abstract observables, whose actuators first
 * resolves all the corresponding concrete observables in the context, which are in turn
 * resolved to build the corresponding observations (and possibly merged to build an
 * observation corresponding to the abstract observable). It is normal during an
 * observation session to go back and forth between the resolver and the runtime; the
 * final dataflow, however, must recreate the entire context it describes when run in the
 * same scale.
 * <p>
 * In dataflows, actuators with {@link Actuator#getType()} describing "dependent"
 * observables (qualities and processes), or defined as having type
 * {@link Artifact.Type.RESOLVE} for independent observables, create their target
 * observations when outside an instantiator. So an {@link Instance} is compiled to a
 * <code>resolve</code> top-level actuator, possibly but not necessarily containing a
 * non-trivial resolved dataflow, and the runtime will create the observation in the
 * context scale when encountering it. Instantiated objects are built by an
 * {@link Artifact.Type.RESOLVE} actuator, which may contain more
 * {@link Artifact.Type.RESOLVE} actuators to resolve the individual instances in their
 * coverages.
 * <p>
 * Dataflows can be encoded and rebuilt from k.DL specifications (encoding using the
 * {@link Resolver} API, decoding through the {@link ResourcesService} API). Their
 * reported scale coverage represents the <em>total</em> coverage they can be computed in,
 * not the original coverage of the resolution that created them. The latter should be
 * added as associated {@link Provenance} information. Serialized Dataflows written by
 * users or created by k.LAB can be curated as URN-specified {@link Resource}s, which can
 * be referenced in k.LAB models. The resource editor should give edit access at least to
 * the geometry.
 *
 * @param <T> the most specific type of artifact this dataflow will build when run.
 * @author ferdinando.villa
 * @since 0.10.0
 */
public interface Dataflow<T extends Artifact> extends Serializable, RuntimeAsset {

    default RuntimeAsset.Type classify() {
        return Type.DATAFLOW;
    }

    /**
     * An empty dataflow is a valid dataflow that produces an
     * {@link Artifact#isEmpty() empty artifact} when run in its scale.
     *
     * @return true if the dataflow is empty
     */
    boolean isEmpty();

    /**
     * The geometry is the total coverage of this dataflow, resulting from compounding the
     * coverage of all the actuators it contains. When the dataflow results from
     * resolution, the only situation in which it contains a specific extent is when the
     * dataflow builds an instance (but the root dataflow may union >1 of those). The
     * geometry of curated dataflows may consist of representational constraints added or
     * derived from the models compiled in it, such as geometry (e.g. grid) or occurrent
     * time.
     *
     * @return
     */
    Geometry getCoverage();

    /**
     * The root-level actuators in the dataflow. They correspond to successive
     * resolutions, with sequential dependency on one another. For example, change is
     * computed as an independent resolution to be executed after the first resolution has
     * computed the initial conditions.
     *
     * @return
     */
    List<Actuator> getComputation();

    /**
     * Any named resources used in the dataflow as contextualizer call parameters. These
     * are serialized statements for classifications, lookup table definitions etc. The
     * runtime must be prepared to match variable names in service calls to the contents
     * of this map before executing the call.
     *
     * @return
     */
    Parameters<String> getResources();


    /**
     * Return a new empty dataflow.
     *
     * @param <T>
     * @param resultClass
     * @return
     */
    public static <T extends Artifact> Dataflow<T> empty(Class<T> resultClass) {
        return new Dataflow<T>() {

            @Override
            public long getId() {
                return 0;
            }

            @Serial
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
        };
    }

}
