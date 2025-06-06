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

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

/**
 * TODO rewrite the description
 *
 * @author ferdinando.villa
 * @since 0.10.0
 */
public interface Dataflow extends Serializable, RuntimeAsset {

  default RuntimeAsset.Type classify() {
    return Type.DATAFLOW;
  }

  /**
   * An empty dataflow is a valid dataflow that produces an {@link Artifact#isEmpty() empty
   * artifact} when run in its scale. It is <em>not</em> a trivial dataflow that leaves the
   * observation as it is - that is a non-empty dataflow with an empty computation.
   *
   * @return true if the dataflow is empty
   */
  boolean isEmpty();

  /**
   * The merged resource set describing any external resource (including worldview, namespaces,
   * projects, resources, components and service calls) this dataflow needs in order to run and
   * where to get it. The dependencies in here will have been coordinated with the runtime so
   * they're guaranteed available after resolution unless the runtime changes.
   *
   * @return
   */
  ResourceSet getRequirements();

  /**
   * The geometry is the total coverage of this dataflow, resulting from compounding the coverage of
   * all the actuators it contains. When the dataflow results from resolution, the only situation in
   * which it contains a specific extent is when the dataflow builds an instance (but the root
   * dataflow may union >1 of those). The geometry of curated dataflows may consist of
   * representational constraints added or derived from the models compiled in it, such as geometry
   * (e.g. grid) or occurrent time.
   *
   * @return
   */
  Geometry getCoverage();

  /**
   * The root-level actuators in the dataflow. They correspond to successive resolutions, with
   * sequential dependency on one another. For example, change is computed as an independent
   * resolution to be executed after the first resolution has computed the initial conditions.
   *
   * @return
   */
  List<Actuator> getComputation();

  /**
   * Return a new empty dataflow, signaling failure of a mandatory resolution.
   *
   * @return
   */
  static Dataflow empty() {
    return new Dataflow() {

      @Override
      public long getId() {
        return 0;
      }

      @Override
      public long getTransientId() {
        return 0;
      }

      @Serial private static final long serialVersionUID = -1115441423700817816L;

      @Override
      public boolean isEmpty() {
        return true;
      }

      @Override
      public ResourceSet getRequirements() {
        return ResourceSet.empty();
      }

      @Override
      public Coverage getCoverage() {
        return Coverage.empty();
      }

      @Override
      public List<Actuator> getComputation() {
        return Collections.emptyList();
      }
    };
  }

  /**
   * A trivial dataflow with no computations but whose {@link #isEmpty()} returns false. Used as the
   * resolution outcome of unresolved substantials that can exist unexplained, i.e. whose mere
   * existence is a valid observation.
   *
   * @return
   */
  static Dataflow trivial() {
    return new Dataflow() {

      @Override
      public long getId() {
        return 0;
      }

      @Override
      public long getTransientId() {
        return 0;
      }

      @Serial private static final long serialVersionUID = -1115441423700817816L;

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public ResourceSet getRequirements() {
        return ResourceSet.empty();
      }

      @Override
      public Coverage getCoverage() {
        return Coverage.empty();
      }

      @Override
      public List<Actuator> getComputation() {
        return Collections.emptyList();
      }
    };
  }
}
