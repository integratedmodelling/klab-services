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
package org.integratedmodelling.klab.api.identities;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * Identities own scopes and services, representing their legitimate owner. An identity is always
 * present in a scope and in each message sent through it. Identities must be small and well-behaved as
 * Javabeans.
 *
 * <p>TODO revise the chain as Institution(Hub) -> User -> Session -> DigitalTwin -> Observation,
 *  with the appropriate data.
 *
 * @author Ferd
 * @version $Id: $Id
 */
public interface Identity {

  enum Type {
    /** Identified by an institutional certificate. Each server has a top-level partner. */
    IM_PARTNER,

    /** Identified by a lever token, authenticated by a server. */
    LEVER,

    /** Identified by a node token, owned by a partner. */
    NODE,

    /** k.LAB service. May supersede the node. */
    SERVICE,

    /** Identified by a user token authenticated by a server. */
    IM_USER,

    /** Identified by a network session token owned by a server user. */
    NETWORK_SESSION,

    /** Identified by an engine token using a user certificate */
    ENGINE,

    /**
     * Identified by an engine user token released by an engine after authentication. Default engine
     * user is the server user who owns the engine.
     */
    ENGINE_USER,

    /** Identified by a session token owned by an engine user. */
    MODEL_SESSION,

    /** Identified by an observation token owned by a session. */
    OBSERVATION,

    /** Identifed by a task token owned by a context observation. */
    TASK,

    /**
     * A script identity identifies a script (namespace with run/test/observe annotations or
     * imperative code) running as a task within a session.
     */
    SCRIPT,

    /** The identity of the AI in k.LAB. Used in provenance recording. */
    KLAB
  }

  /**
   * Use to discriminate the identity.
   *
   * @return
   */
  Type getIdentityType();

  /**
   * Unique ID. When appropriate it corresponds to the authorization token retrieved upon
   * authentication. Assumed to expire at some sensible point in time, if stored it should be
   * validated before use and refreshed if necessary.
   *
   * @return a token to use as authentication when dealing with the engine.
   */
  String getId();

  /**
   * True if the identity is of the passed type.
   *
   * @param type a {@link
   *     org.integratedmodelling.klab.api.services.resources.adapters.ResolutionType.IIdentity.Type}
   *     object.
   * @return a boolean.
   */
  boolean is(Type type);

  /**
   * False for anonymous user identities or their children. Changes some of the results in
   * capabilities and other responses.
   *
   * @return
   */
  boolean isAuthenticated();

  /**
   * All identities can have arbitrary data associated.
   *
   * @return
   */
  Parameters<String> getData();

}
