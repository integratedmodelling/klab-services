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
package org.integratedmodelling.klab.api.knowledge.observation;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.Scope;

/**
 * The interface Observation, which is the semantic equivalent of an Artifact and represents an
 * observable in the observation graph of a k.LAB context. Once created in a k.LAB session, it can
 * be made reactive by supplementing it with a behavior, which will create an agent accessible
 * through the context scope focused on the observation. Models may bind instantiated observations
 * to actor files that will provide behaviors for their instances (or a subset thereof). Once made
 * reactive, they can interact with each other and the system.
 *
 * <p>The ID of an observation is a positive long for efficiency. Paths such as 3.44.234 identify
 * observation hierarchies to reconstruct scopes. If the ID is negative, the observation is
 * unresolved and does not exist in the knowledge graph. So a client may <em>send</em> an unresolved
 * observation (normally created with {@link
 * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin#createObservation(Scope, Object...)} but
 * will never <em>receive</em> one, except in case of resolution error.
 *
 * <p>TODO we could just use Observation (abstract) + DirectObservation (rename to Substantial) and
 * State, then everything else is taken care of by the semantics (folder ==
 * getObservable().isCollective()), the DT and its graph.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Observation extends Knowledge, Artifact, Resolvable, RuntimeAsset {

  long UNASSIGNED_ID = -1;

  default RuntimeAsset.Type classify() {
    return RuntimeAsset.Type.OBSERVATION;
  }

  /**
   * A name should never be null, although only substantials have the name as a defining feature.
   * Names do not need to be unique or conform to any syntax rule.
   *
   * @return
   */
  String getName();

  /**
   * Return the observable.
   *
   * @return the observation's observable
   */
  Observable getObservable();

  /**
   * Return a view of this observation restricted to the passed locator, which is applied to the
   * scale to obtain a new scale, used as a filter to obtain the view. The result should be able to
   * handle both conformant scaling (e.g. fix one dimension) and non-conformant (i.e. one state maps
   * to multiple ones with irregular extent coverage) in both reading and writing.
   *
   * @param locator
   * @return a rescaled view of this observation
   * @throws IllegalArgumentException if the locator is unsuitable for the observation
   */
  Observation at(Locator locator);

  Observation EMPTY_OBSERVATION =
      new ObservationImpl() {

        @Override
        public boolean isEmpty() {
          return true;
        }
      };

  static Observation empty() {
    return EMPTY_OBSERVATION;
  }

  Object getValue();

  /**
   * After resolution, this will report the 0-1 coverage resolved. Before resolution this will be 0.
   *
   * @return
   */
  double getResolvedCoverage();
}
