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
package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.provenance.Provenance;

import java.util.Collection;
import java.util.List;

/**
 * An Artifact can be any of the first-class products of a k.LAB task: a non-semantic
 * {@link DataArtifact} or {@link ObjectArtifact}, an observed (semantic) {@link Observation} (as
 * produced by most activities in k.LAB) or a {@link KimModel k.IM model description} when the model
 * has been produced by an observation activity, such as a learning model.
 * <p>
 * By implementing {@link java.lang.Iterable}, we also allow Artifacts to represent groups of
 * artifacts (e.g. all subjects instantiated by resolving a subject
 * {@link Observable observable}). This enables simpler handling of provenance, as each observation
 * activity returns one artifact, possibly iterable as a group.
 * <p>
 * From an OPM perspective, IArtifact is the equivalent of Entity, including its specialization into
 * Bundle if size() > 1.
 * <p>
 * Each artifact exposes the provenance graph it's part of, allowing all k.LAB tasks to simply
 * return any {@code IArtifact} and provide full information on what happened.
 * <p>
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface Artifact extends Provenance.Node, Iterable<Artifact> {

    default RuntimeAsset.Type classify() {
        return RuntimeAsset.Type.ARTIFACT;
    }

    /**
     * All non-semantic artifact types produced, contextualized or validated. Used in various
     * locations, not all compatible with all values.
     *
     * @author ferdinando.villa
     */
    enum Type {
        /**
         * Contextualizes number states.
         */
        NUMBER,
        /**
         * Contextualizes presence/absence states
         */
        BOOLEAN,
        /**
         * Contextualizes category states
         */
        CONCEPT,
        /**
         * Contextualizes processes, an occurrent so the resolution only happens at transitions and
         * not at initialization. Contextualizers must have T geometry.
         */
        PROCESS,
        /**
         * The other occurrent, this time an instantiator. Instantiators must have T geometry and
         * only run at transitions.
         */
        EVENT,
        /**
         * Instantiates or contextualizes objects, according to arity.
         */
        OBJECT,
        /**
         * Produces text values, to be transformed by successive contextualizers. Illegal in
         * contracts.
         */
        TEXT,
        /**
         * Contextualizes any quality. Only legal in contracts.
         */
        VALUE,
        /**
         * Produces range values. Only legal in parameters
         */
        RANGE,
        /**
         * Produce one of a set of values. Only legal in parameters, values are specified
         * externally.
         */
        ENUM,

        /**
         *
         */
        DATETIME,

        /**
         *
         */
        QUANTITY,

        /**
         * A value codified through a temporal resolution according to {@link Time.Resolution}.
         */
        TEMPORAL_RESOLUTION,

        /**
         *
         */
        URN,

        /**
         *
         */
        URL,

        /**
         *
         */
        GEOMETRY,

        /**
         * Produce extents other than time or space
         */
        EXTENT,
        /**
         * Produce temporal extents
         */
        TEMPORALEXTENT,
        /**
         * Produce spatial extents
         */
        SPATIALEXTENT,
        /**
         * Specify annotation contracts
         */
        ANNOTATION,
        /**
         * A list value
         */
        LIST,
        /**
         * Contextualizes any observation. Only legal in contracts.
         */
        OBSERVATION,
        /**
         * Not an artifact type, but adopted by the (void) actuators that resolve acknowledged
         * objects. Must be distinct from void as the latter may make view observations. Translates
         * directly to the k.DL actuator type.
         */
        RESOLVE,

        /**
         * No value - the type of computations that resolve views and for options in command
         * prototypes
         */
        VOID,

        /**
         * One map value guarantees functional correspondences between key and value. Adapters that
         * produce map values should return tables, not maps, as a map is a special case of a value
         * table.
         */
        MAP, RELATIONSHIP, CONFIGURATION;

        /**
         * Classify a POD type producing the type that represents it.
         *
         * @param o
         * @return a type for o. If o == null, VALUE is returned.
         */
        public static Type classify(Object o) {
            if (o instanceof Number) {
                return NUMBER;
            } else if (o instanceof Boolean) {
                return BOOLEAN;
            } else if (o instanceof String) {
                return TEXT;
            } else if (o instanceof NumericRangeImpl) {
                return RANGE;
            } else if (o instanceof List) {
                return LIST;
            }
            return VALUE;
        }

        public boolean isState() {
            return this == NUMBER || this == BOOLEAN || this == TEXT || this == CONCEPT || this == VALUE;
        }

        public boolean isNumeric() {
            return this == NUMBER;
        }

        public static boolean isCompatible(Type required, Type supplied) {

            // unknown/universal
            if (supplied == Type.VALUE || required == Type.VALUE) {
                return true;
            }

            if (required == supplied) {
                return true;
            } else if (required == BOOLEAN && supplied == NUMBER) {
                // runtime cast can do this
                return true;
            }

            // TODO probably needs improvement
            return false;
        }

        public boolean isCountable() {
            return this == EVENT || this == OBJECT;
        }

        public boolean isOccurrent() {
            return this == EVENT || this == PROCESS;
        }

        /**
         * Dependent means that the runtime must create the correspondent observation.
         *
         * @return
         */
        public boolean isDependent() {
            return this == RESOLVE || this == PROCESS || this == CONCEPT || this == NUMBER || this == BOOLEAN || this == TEXT;
        }

        public static Type forSemantics(Collection<SemanticType> type) {

            if (type.contains(SemanticType.CLASS)) {
                return Type.CONCEPT;
            } else if (type.contains(SemanticType.PRESENCE)) {
                return Type.BOOLEAN;
            } else if (type.contains(SemanticType.QUALITY)) { // don't reorder these!
                return Type.NUMBER;
            } else if (type.contains(SemanticType.EVENT)) {
                return Type.EVENT;
            } else if (type.contains(SemanticType.COUNTABLE)) {
                return Type.OBJECT;
            } else if (type.contains(SemanticType.CONFIGURATION)) {
                return Type.OBJECT;
            } else if (type.contains(SemanticType.PROCESS)) {
                return Type.PROCESS;
            } else if (type.contains(SemanticType.TRAIT) || type.contains(SemanticType.ROLE)) {
                // FIXME seems odd and contradicting the comment below
                return Type.VALUE;
            }
            // trait and role observers specify filters, which produce void.
            return Type.VOID;
        }
    }

    /**
     * The artifact structure keeps tabs on both the logical and the physical relationships of the
     * artifacts within a context. The scope of each observation contains the structure and exposes
     * it.
     *
     * @author Ferd
     */
    interface Structure {

        /**
         * The root artifact always has the session user as the observer.
         *
         * @return
         */
        Artifact getRootArtifact();

        /**
         * @param child
         * @return
         */
        Artifact getArtifactParent(Artifact child);

        /**
         * @param child
         * @return
         */
        Artifact getLogicalParent(Artifact child);

        /**
         * @param parent
         * @return
         */
        Collection<Artifact> getArtifactChildren(Artifact parent);

        /**
         * @param parent
         * @return
         */
        Collection<Artifact> getLogicalChildren(Artifact parent);

        /**
         * @param artifact
         * @return
         */
        boolean contains(Artifact artifact);

        /**
         * If an artifact is called into the context by a process, return the process.
         *
         * @param artifact
         * @return
         */
        Process getOwningProcess(Artifact artifact);

        /**
         * Retrieve the observer of the passed artifact.
         *
         * @param artifact
         * @return
         */
        Identity getObserver(Artifact artifact);

    }

    /**
     * The geometry linked to the observation. Observed artifacts will specialize this as IScale.
     *
     * @return the geometry
     */
    Geometry getGeometry();

    /**
     * Metadata. Never null, possibly empty.
     *
     * @return the metadata
     */
    Metadata getMetadata();

    /**
     * <p>
     * getUrn.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    String getUrn();

    /**
     * All the annotations proceeding from the k.IM lineage of this artifact (from the model that
     * produced it, the concepts it incarnates, etc.). Never null, possibly empty.
     * <p>
     * When artifacts are persisted, these may or may not be preserved.
     *
     * @return k.IM annotations in the lineage of this artifact.
     */
    Collection<Annotation> getAnnotations();

    /**
     * Collect all artifacts of the passed concept (or with the passed role/trait) up the provenance
     * chain.
     *
     * @param concept a {@link org.integratedmodelling.klab.api.knowledge.Concept} object.
     * @return a {@link java.util.Collection} object.
     */
    Collection<Artifact> collect(Concept concept);

    /**
     * Trace the nearest artifact with the passed role within the passed observation up the
     * provenance chain.
     *
     * @param role
     * @param roleContext
     * @return
     */
    Artifact trace(Concept role, Observation roleContext);

    /**
     * Some artifact may be connected into a hierarchical structure, which may or may not be the
     * same as the natural structure of the specific type of artifact.
     *
     * @return the child artifacts or an empty list
     */
    Collection<Artifact> getChildArtifacts();

    /**
     * Collect all artifacts with the passed role within the passed observation up the provenance
     * chain.
     *
     * @param role
     * @param roleContext
     * @return a {@link java.util.Collection} object.
     */
    Collection<Artifact> collect(Concept role, Observation roleContext);

    /**
     * The size of the group that this artifact is part of. Any artifact is part of a group
     * including at least itself.
     *
     * @return 1 or more
     */
    int groupSize();

    /**
     * Any observation that exists has provenance. Call this on the root observation for the entire
     * graph.
     *
     * @return the provenance record leading to this
     */
    Provenance getProvenance();

    /**
     * The type of this artifact. Types are a small set meant to enable more efficient storage and
     * correct contextualization.
     *
     * @return the type
     */
    Type getType();

    /**
     * Call when the artifact can be disposed of. This should schedule the removal of any storage
     * and free any resources without terminating the object itself, according to the implementation
     * of the storage provider. Calling release() is optional and should be done only on temporary
     * artifacts with a well-defined life span.
     */
    void release();

    /**
     * We leave specific views of artifacts flexible without specializing the base class through a
     * simple adaptation mechanism, suitable for PODs or more complex objects. This method, paired
     * with {@link #as(Class)}, enables checking for adaptability to specific types.
     *
     * @param cls
     * @return true if the artifact can be adapted to the passed type.
     */
    boolean is(Class<?> cls);

    /**
     * Use after {@link #is(Class)} has returned true to adapt to the corresponding object of that
     * type.
     *
     * @param cls
     * @return the specific class requested
     */
    <T> T as(Class<?> cls);

    /**
     * True if the artifact is not a true artifact but an archetype for others. In that case, its
     * specifics will be used to infer general properties that will be used for other artifacts.
     * <p>
     * So far used in learning models that learn contextualized observables, where no actual state
     * is learned in the context of learning (the actuator is void) but the semantics of the learned
     * state in each instantiated object is passed through an archetype.
     * <p>
     *
     * @return true if archetype. In this case, no use should be made of the artifact other than
     * inspection of the relevant specifics.
     */
    boolean isArchetype();

    /**
     * Get the last update time in <em>context</em> time, or 0 if the context is not temporal.
     *
     * @return
     */
    long getLastUpdate();

    /**
     * Checks if the artifact has changed in any way during the passed temporal transition. Will
     *
     * @param time
     * @return
     */
    boolean hasChangedDuring(Time time);
}
