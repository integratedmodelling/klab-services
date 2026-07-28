package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.*;

/**
 * Tags a method in an {@link Actor}-tagged class that adapts an object to the agent type
 * implemented by the actor. The method must take exactly one source object (parameter matching can
 * validate a more specialized class at runtime) and may optionally take the
 * {@link org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope} in which the agent is being
 * created. It must return the adapted object, either directly or through a {@link
 * java.util.concurrent.CompletableFuture}; asynchronous results are joined by the runtime before
 * execution continues.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentAdapter {}
