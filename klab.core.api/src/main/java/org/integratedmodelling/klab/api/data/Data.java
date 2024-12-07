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
package org.integratedmodelling.klab.api.data;

import java.util.Iterator;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Encoded k.LAB data object, resulting from decoding a resource URN in a specified geometry. The interface
 * supports both direct building within an existing artifact or setting of data into the Avro-based encoding
 * for remote operation.
 * <p>
 * A builder is passed to any adapter
 * {@link org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter.Encoder} that requests
 * one. by the runtime. The built a {@code KlabData} object built can send binary data to a client or directly
 * construct an artifact.
 * <p>
 * TODO explore stream-based options for the transfer.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Data {

    @FunctionalInterface
    interface IntFiller {
        void add(int value);
    }

    @FunctionalInterface
    interface FloatFiller {
        void add(float value);
    }

    @FunctionalInterface
    interface BooleanFiller {
        void add(boolean value);
    }

    @FunctionalInterface
    interface DoubleFiller {
        void add(double value);
    }

    @FunctionalInterface
    interface KeyedFiller {
        void add(Object value);
    }

    @FunctionalInterface
    interface ObjectFiller {
        ObjectBuilder add();
    }

    interface ObjectBuilder {

        /**
         * @return
         */

        ObjectBuilder name();

        ObjectBuilder geometry(Geometry geometry);

        /**
         * To create object states or sub-objects, a new builder in the context of this one may be requested.
         *
         * @return
         */
        Builder builder();
    }

    interface FillCurve extends PrimitiveIterator.OfLong {

    }

    /**
     * A builder is passed to a resource encoder and is used to define the result of a resource's
     * contextualization.
     * <p>
     * TODO maybe would be better to have type-specific builders (or ALSO have them) and adapt
     *  the resource type to the builder requested in the encoder parameters.
     */
    interface Builder {

        /**
         * The default fill curve for the state geometry under consideration. Normally the fastest possible. A
         * different one can be constructed and passed to the fillers as required.
         *
         * @return
         */
        FillCurve fillCurve();

        /**
         * Return the adder for a state whose values are boolean.
         *
         * @param fillCurve pass {@link #fillCurve()} for the default X/Y curve
         * @return the adder for state
         */
        BooleanFiller booleanState(FillCurve fillCurve);

        BooleanFiller booleanState(String stateIdentifier, FillCurve fillCurve);

        FloatFiller floatState(FillCurve fillCurve);

        FloatFiller floatState(String stateIdentifier, FillCurve fillCurve);

        IntFiller intState(FillCurve fillCurve);

        IntFiller intState(String stateIdentifier, FillCurve fillCurve);

        DoubleFiller doubleState(FillCurve fillCurve);

        DoubleFiller doubleState(String stateIdentifier, FillCurve fillCurve);

        KeyedFiller keyedState(FillCurve fillCurve);

        KeyedFiller keyedState(String stateIdentifier, FillCurve fillCurve);

        ObjectFiller objectCollection();

        ObjectFiller objectCollection(String observationIdentifier);

        /**
         * Add a notification to be added to the result. If an error-level notification is added, nothing is
         * sent except the notification and any execution metadata.
         *
         * @param notification
         */
        void notification(Notification notification);

        /**
         * Build the final data object.
         *
         * @return the finished data
         */
        Data build();
    }

    /**
     * The artifact type of the primary artifact.
     *
     * @return
     */
    Artifact.Type getArtifactType();

    /**
     * Return any notifications passed through a builder. Notifications are a global list that refers to all
     * artifacts.
     *
     * @return all notifications
     */
    List<Notification> getNotifications();

    /**
     * Return the number of objects at the level of this data response, 0 if !type.isCountable(), 0 or more if
     * object or event.
     *
     * @return
     */
    int getObjectCount();

    /**
     * The number of states in the primary artifact, normally 1 if type == quality or 0 if not.
     *
     * @return
     */
    int getStateCount();

    /**
     * @param i
     * @return
     */
    Scale getObjectScale(int i);

    /**
     * @param i
     * @return
     */
    String getObjectName(int i);

    /**
     * @param i
     * @return
     */
    Metadata getObjectMetadata(int i);

    /**
     * Normally null, unless the resource is a characterizer that classifies an object or a resolves an
     * abstract trait or role into one or more (in OR) concrete ones. The results are worldview-bound.
     *
     * @return
     * @deprecated should use the observable of an Artifact with collapsed scale for each subcontext of
     * interest
     */
    Concept getSemantics();

    /**
     * Get overall metadata for the resource extraction operation.
     *
     * @return
     */
    Metadata getMetadata();

}
