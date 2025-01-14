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
package org.integratedmodelling.klab.api.knowledge.observation.scale;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

import java.util.Collection;

/**
 * An {@code Extent} is a semantically aware {@link Dimension geometry dimension} that represents an
 * observation of the topology it describes. {@code Extent}s make up the dimensions of the
 * semantically aware {@link Geometry} represented by {@link Scale}.
 *
 * <p>In a {@code Extent}, {{@link #size()} will never return {@link Geometry#UNDEFINED} and the
 * shape returned by {@link #getShape()} will never contain undefined values.
 *
 * <p>{@code Extent}s are {@link Locator locators}, addressing the value space of observations.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Extent<T extends TopologicallyComparable<T>>
    extends Locator, Topology<T>, Geometry.Dimension {

  /**
   * TODO fill in and specialize in each extent implementation.
   *
   * @author Ferd
   */
  interface Constraint {}

  /**
   * Locate the extent and return another with the original located extent and offsets set in. Can
   * be passed another extent (e.g. a point to locate a cell in a grid space), one or more integer
   * locators, a time period, or anything that can be understood by the extent.
   *
   * @param locator
   * @return the extent, or null if location is impossible.
   */
  Extent<T> at(Locator locator);

  /**
   * Each extent must be able to return a worldview-dependent integer scale rank, usable to
   * constrain model retrieval to specific scales. In spatial extents this corresponds to something
   * like a "zoom level". The worldview establishes the scale for the ranking; otherwise, no
   * assumptions are made on the value except that higher values correspond to smaller extents.
   *
   * <p>The worldview defines this using numeric restrictions on the data property used to annotate
   * scale constraints and establishes the range and granularity for the ranking.
   *
   * @return an integer summarizing the extent's size within the range covered by the worldview
   */
  int getRank();

  /**
   * Collapse the multiplicity and return the extent that represents the full extent of our topology
   * with one single state. This extent may not be of the same class. Coverage must be accurate and
   * the "boundaries" should be the same.
   *
   * @return a new extent with size() == 1.
   */
  Extent<T> collapsed();

  /**
   * Return the dimensional coverage in the unit returned by {@link #getDimensionUnit()}.
   *
   * @return
   */
  double getDimensionSize();

  /**
   * The unit of the dimensional coverage. May be null if the dimension is not physical.
   *
   * @return
   */
  Unit getDimensionUnit();

  /**
   * Return the standardized (SI) dimension of the extent at the passed locator in the unit returned
   * by {@link #getDimensionUnit()}.
   *
   * @return
   */
  double getStandardizedDimension(Locator locator);

  /**
   * Produce the most suitable merged extent from a merge with the passed other.
   *
   * @param other
   * @param how
   * @return
   */
  <T extends TopologicallyComparable<T>> Extent<T> merge(Extent<T> other, LogicalConnector how);

  /**
   * Return the n-th state of the ordered topology as a new extent.
   *
   * @param stateIndex must be between 0 and {@link #size()}, exclusive.
   * @return a new extent, normally with {@link #size()} == 1, or this if it is 1-sized and 0 is
   *     passed.
   */
  T getExtent(long stateIndex);

  /**
   * An empty extent covers nothing, has {@link #size()} == 0 and implies an empty observation.
   *
   * @return
   */
  boolean isEmpty();

  /**
   * Check conformance with the passed extent constraints. Used in resolution and hard to cache, so
   * best implemented efficiently.
   *
   * @param constraints
   * @return
   */
  boolean matches(Collection<Constraint> constraints);

  /**
   * Make a deep copy of another extent, copying anything that is not immutable.
   *
   * @param extent
   * @return
   */
  public static Extent<?> copyOf(Extent<?> extent) {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException("k.LAB environment not configured");
    }
    return configuration.createExtentCopy(extent);
  }
}
