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

import java.util.List;
import java.util.PrimitiveIterator;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Encoded k.LAB data object, resulting from decoding a resource URN in a specified geometry. The
 * interface supports both direct building within an existing artifact or setting of data into the
 * Avro-based encoding for remote operation.
 *
 * <p>A <code>Data.Builder</code> is passed to any adapter {@link
 * org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter.Encoder} that
 * requests one. by the runtime. The runtime calls its #build method to create a {@code Data} object
 * that can send binary data to a requesting runtime or directly construct an artifact when the
 * adapter is available locally.
 *
 * <p>A <code>Data</code> object can also be passed to {@link
 * org.integratedmodelling.klab.api.services.ResourcesService#contextualize(Resource, Observation,
 * Data, Scope)} if the contextualization requires inputs. TODO the ContextScope should be able to
 * produce a lazy Data object from the list of requirements.
 *
 * <p>TODO explore stream-based options for the transfer.
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
    ObjectBuilder newObject();
  }

  interface ObjectBuilder {

    ObjectBuilder name(String string);

    ObjectBuilder withMetadata(String key, Object value);

    ObjectBuilder geometry(Geometry geometry);

    /**
     * To create object states or sub-objects, a new builder in the context of this one may be
     * requested.
     *
     * @return
     */
    Builder builder();

    /** Call this to add the new object to the object collection that created it. */
    void add();
  }

  /**
   * Any of the space-filling curves can be used in the data encoding. Each state with multiple
   * values must define the curve it uses. Normally these are used for 2D space but there may be 3D
   * and others in the future, so extend as needed.
   */
  enum FillCurve {
    S2_XY,
    S2_YX,
    S2_SIERPINSKI_3,
    S2_HILBERT
    // ... TODO more as needed. Sierpinsky can have different orders; the arrowhead can be extended
    // to 3D
  }

  /**
   * This returns an index iterator for the data geometry using the fill curve specified.
   *
   * @param curve
   * @return
   */
  PrimitiveIterator.OfLong getFillCurve(FillCurve curve);

  /**
   * A builder is passed to a resource encoder and is used to define the result of a resource's
   * contextualization.
   *
   * <p>TODO maybe would be better to have type-specific builders (or ALSO have them) and adapt the
   * resource type to the builder requested in the encoder parameters.
   */
  interface Builder {

    /**
     * The default fill curve for the state geometry under consideration. Normally the fastest
     * possible. A different one can be constructed and passed to the fillers as required.
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
     * Add a notification to be added to the result. If an error-level notification is added,
     * nothing is sent except the notification and any execution metadata.
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
   * If empty, nothing besides notifications should be accessed.
   *
   * @return
   */
  boolean isEmpty();

  /**
   * The artifact type of the primary artifact.
   *
   * @return
   */
  Artifact.Type getArtifactType();

  /**
   * Return any notifications passed through a builder. Notifications are a global list that refers
   * to all artifacts.
   *
   * @return all notifications
   */
  List<Notification> getNotifications();

  /**
   * Return the number of objects at the level of this data response, 0 if !type.isCountable(), 0 or
   * more if object or event.
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
   * Normally null, unless the resource is a characterizer that classifies an object or a resolves
   * an abstract trait or role into one or more (in OR) concrete ones. The results are
   * worldview-bound.
   *
   * @return
   * @deprecated should use the observable of an Artifact with collapsed scale for each subcontext
   *     of interest
   */
  Concept getSemantics();

  /**
   * Get overall metadata for the resource extraction operation.
   *
   * @return
   */
  Metadata getMetadata();

  static Builder builder() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
          "k.LAB environment not configured to create a data builder");
    }
    return configuration.getDataBuilder();
  }

  static Builder builder(String name, Geometry geometry) {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
          "k.LAB environment not configured to create a data builder");
    }
    return configuration.getDataBuilder(name, geometry);
  }

  static Data empty(String reason) {
    return new Data() {

      @Override
      public PrimitiveIterator.OfLong getFillCurve(FillCurve curve) {
        return null;
      }

      @Override
      public boolean isEmpty() {
        return true;
      }

      @Override
      public Artifact.Type getArtifactType() {
        return null;
      }

      @Override
      public List<Notification> getNotifications() {
        return List.of(Notification.error(reason));
      }

      @Override
      public int getObjectCount() {
        return 0;
      }

      @Override
      public int getStateCount() {
        return 0;
      }

      @Override
      public Scale getObjectScale(int i) {
        return null;
      }

      @Override
      public String getObjectName(int i) {
        return "";
      }

      @Override
      public Metadata getObjectMetadata(int i) {
        return null;
      }

      @Override
      public Concept getSemantics() {
        return null;
      }

      @Override
      public Metadata getMetadata() {
        return null;
      }
    };
  }
}
