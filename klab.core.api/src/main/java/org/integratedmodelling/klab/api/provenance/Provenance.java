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
package org.integratedmodelling.klab.api.provenance;

import java.util.*;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Resource;

/**
 * Provenance in k.LAB aligns with the Open Provenance Model (OPM), described in detail at
 * <a href="https://openprovenance.org">https://openprovenance.org</a> and the
 * correspondent W3C documents. The internal representation is more packed than the standard OPM graph, for
 * ease of implementation and use, and can be expanded into it when exporting the graph.
 * <p>
 * A provenance graph exists in each {@link org.integratedmodelling.klab.api.scope.ContextScope} and can be
 * iterated to produce the exact sequence of activities that incarnated it. Ideally all resources should also
 * come with a provenance graph, and the provenance of each resource used in a
 * {@link org.integratedmodelling.klab.api.services.runtime.Dataflow} should be part of the graph.
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface Provenance extends RuntimeAsset, Iterable<Activity> {

    default RuntimeAsset.Type classify() {
        return Type.PROVENANCE;
    }


    /**
     * The generic provenance node.
     *
     * @author Ferd
     */
    interface Node extends RuntimeAsset {

        /**
         * The ID of the node is the same as the ID of the relationship or actuator it relates to.
         *
         * @return
         */
        long getId();
        /**
         * Name is not unique and is just for human consumption. The internal identification of each node is
         * the provenance graph's problem.
         *
         * @return
         */
        String getName();

        /**
         * All nodes can carry POD metadata
         * @return
         */
        Metadata getMetadata();

        /**
         * Dataflows that end in disappointment produce empty nodes.
         *
         * @return true if empty
         */
        boolean isEmpty();
    }

    /**
     * True if there's nothing to see.
     *
     * @return true if empty
     */
    boolean isEmpty();

    /**
     * Return all the primary actions in chronological order.
     *
     * @return a {@link java.util.List} object.
     */
    List<Activity> getPrimaryActions();

    /**
     * Return all artifacts.
     *
     * @return a {@link java.util.Collection} object.
     */
    Collection<Artifact> getArtifacts();

    /**
     * Get the activity that caused the instantiation of the passed node, or null (should be null only for one
     * "init" activity, or it could be self).
     *
     * @param node
     * @return
     */
    Activity getCause(Node node);

    /**
     * Find the agent that is responsible for the passed node.
     *
     * @param node
     * @return
     */
    Agent getAgent(Node node);

    /**
     * Collect all objects of a given class encountered in the provenance graph.
     *
     * @param <T>
     * @param cls
     * @return
     */
    <T> Collection<T> collect(Class<? extends T> cls);

    static Provenance empty() {
        return new Provenance() {

            @Override
            public Iterator<Activity> iterator() {
                return new ArrayList<Activity>().iterator();
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public List<Activity> getPrimaryActions() {
                return Collections.emptyList();
            }

            @Override
            public Collection<Artifact> getArtifacts() {
                return Collections.emptyList();
            }

            @Override
            public Activity getCause(Node node) {
                return null;
            }

            @Override
            public Agent getAgent(Node node) {
                return null;
            }

            @Override
            public <T> Collection<T> collect(Class<? extends T> cls) {
                return Collections.emptyList();
            }

        };
    }

}
