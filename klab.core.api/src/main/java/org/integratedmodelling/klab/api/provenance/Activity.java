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
package org.integratedmodelling.klab.api.provenance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.services.KlabService;

/**
 * Activity (process). Primary processes produce artifacts. Secondary processes (after creation) may
 * modify them. Activities in k.LAB should always be linked to (1) the root provenance node for a
 * context scope through the main provenance node or a parent activity; (2) the agent that
 * engendered them; and (3) the plan represented by a dataflow or actuator. The latter can be
 * optional if the activity is explicit and the actor is a user, unless we want to capture the
 * user's intention.
 *
 * <p>Activities have URNs containing a full hierarchical path so that a client that obtains them
 * with contextualization events can reconstruct the call sequence.
 *
 * <p>To optimize the provenance graph, actions that are repeated by the scheduler will not generate
 * a new activity but add a timestamp to {@link #getSchedulerTime()}; TODO obsolete
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface Activity extends Provenance.Node {

  default RuntimeAsset.Type classify() {
    return RuntimeAsset.Type.ACTIVITY;
  }

  String getServiceId();

  String getServiceName();

  KlabService.Type getServiceType();

  String getDataflow();

  /**
   * The URN is a dot-separated hierarchically organized identifier, starting with the context scope
   * ID, and enables the reconstruction of the activity hierarchy at the client side.
   *
   * @return
   */
  String getUrn();

  enum Type {
    INITIALIZATION,
    RESOLUTION,
    CONTEXTUALIZATION,
    INSTANTIATION,
    EXECUTION
  }

  enum Outcome {
    FAILURE,
    SUCCESS,
    INTERNAL_FAILURE
  }

  Type getType();

  /**
   * System time of start.
   *
   * @return a long.
   */
  long getStart();

  /**
   * System time of end.
   *
   * @return a long.
   */
  long getEnd();

  /**
   * A quantification of the¶ "size" of the activity in any metric that can be compared across the
   * provenance graph.
   *
   * @return
   */
  long getSize();

  /**
   * The number of k.LAB credits expended during the activity.
   *
   * @return
   */
  long getCredits();

  /**
   * Logs each time that the action was executed (in lieu of having an action per each execution).
   * Empty for any action that wasn't called by the scheduler. If not empty the first time could be
   * initialization or after, based on the occurrent character of the linked observation.
   *
   * @return
   */
  List<Long> getSchedulerTime();

  /**
   * Optional description for human consumption and report generation.
   *
   * @return
   */
  String getDescription();

  /**
   * Non-null for all activities that manipulate an observation, including those that failed.
   *
   * @return
   */
  String getObservationUrn();

  Outcome getOutcome();

  String getStackTrace();
}
