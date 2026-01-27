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

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.net.URL;
import java.util.List;

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

  /**
   * In resolved observations, reports adapter and its parameters, if any, plus the service ID where
   * the observation is hosted. Unresolved observations may provide contextualization data if they
   * have been computed already outside the digital twin, so that the resolver can validate the
   * source and compile it.
   */
  interface ContextualizationData {
    /**
     * This is non-null if there is inline content. Must fit the observation's geometry.
     *
     * @return
     */
    Data getData();

    /**
     * The URL of the service where the observation is hosted. Null in non-resolved observations at
     * client side.
     *
     * @return
     */
    URL getServiceUrl();

    /**
     * The service ID where the observation is hosted. Null in non-resolved observations.
     *
     * @return
     */
    String getServiceId();

    /**
     * The ID of the adapter that contextualized the observation, if any.
     *
     * @return
     */
    String getAdapterId();

    /**
     * The parameters for the adapter that contextualized the observation, if any. Parameters
     * prefixed with `resource.` specify more persistent operations to create or update existing
     * resources in specified services.
     *
     * @return
     */
    Parameters<String> getParameters();

    /**
     * The native sharding strategy, if a quality. Calculated from the contextualization strategy on
     * the first contextualization. We need this when reconstructing an existing observation, so we
     * save it in the graph.
     *
     * @return
     */
    Data.ShardingStrategy getNativeShardingStrategy();

    /**
     * If true, we are asking the resolver to persist this contextualization data as a resource and
     * an annotated model that uses it. These will be part of a scenario unique to the running
     * scope, and may be later published to a chosen resources service upon further validation and
     * curation. They will appear in the modeler when tuned to the scope.
     *
     * @return
     */
    boolean isPersistent();
  }

  /**
   * The role played by an observation in a dependency hierarchy. This depends solely on the
   * observable's semantics so it's redundant, but being able to classify it streamlines and
   * clarifies the code and any API use.
   */
  enum Role {
    COLLECTIVE_SUBSTANTIAL,
    INDIVIDUAL_SUBSTANTIAL,
    RELATIONAL,
    DEPENDENT
    // TODO classifications and categorizations
  }

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
   * In situations where messaging is unavailable or the scope aren't receiving messages, the
   * observation returning from a {@link
   * org.integratedmodelling.klab.api.services.RuntimeService#submit(Observation, ContextScope)}
   * will contain notifications to inform of whatever exception or warning happened during its
   * resolution or contextualization. If the returning observation is empty, there should be an
   * error notification explaining why.
   *
   * @return
   */
  List<Notification> getNotifications();

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

  static Observation empty(Notification notification) {
    var ret = new ObservationImpl();
    ret.setEmpty(true);
    ret.getNotifications().add(notification);
    return ret;
  }

  Object getValue();

  /**
   * The observation records the timestamps of last update due to any event that required its
   * contextualization. Substantials and their qualities have an initial 0 value to represent the
   * "past" - that's because substantials exists besides simulated time, so that their first state
   * (computed when the INITIALIZATION event is received) is represented by the period 0-(beginning
   * re: time in geometry of context observation). If this is empty the observation hasn't been
   * resolved yet.
   *
   * @return
   */
  List<Long> getEventTimestamps();

  /**
   * In resolved observations, reports adapter and its parameters, if any, plus the service ID where
   * the observation is hosted. Unresolved observations may provide contextualization data if they
   * have been computed already outside the digital twin. If an observation with contextualization
   * data is submitted, the resolver is obliged to validate the source and compile it.
   *
   * @return contextualization data or null. If the observation comes from a service, this is never
   *     null.
   */
  ContextualizationData getContextualizationData();

  /**
   * After resolution, this will report the 0-1 coverage resolved. Before resolution this will be 0.
   *
   * @return
   */
  double getResolvedCoverage();

  static Role classifyRole(Observation observation) {

    // TODO check classifications and categorizations
    if (observation.getObservable().is(SemanticType.QUALITY)
        || observation.getObservable().is(SemanticType.PROCESS)) {
      return Role.DEPENDENT;
    } else if (observation.getObservable().is(SemanticType.RELATIONSHIP)) {
      return Role.RELATIONAL;
    } else if (observation.getObservable().getSemantics().isCollective()) {
      return Role.COLLECTIVE_SUBSTANTIAL;
    }
    return Role.INDIVIDUAL_SUBSTANTIAL;
  }
}
