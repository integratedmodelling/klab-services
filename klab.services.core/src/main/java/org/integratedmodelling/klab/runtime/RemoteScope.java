package org.integratedmodelling.klab.runtime;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * A remote scope is the remote peer of a local scope and is created using the authentication data
 * in a REST controller. Its {@link Channel} methods must be set up to communicate with the local
 * peer via a RPC channel.
 * 
 * @author Ferd
 * TODO!
 */
public abstract class RemoteScope implements Scope {

}
