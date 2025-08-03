package org.integratedmodelling.klab.api.services.runtime.extension;

import java.lang.annotation.*;

/**
 * Specialized library for actors and verbs. Can create either a singleton or an actor class whose
 * agents must be instantiated. In both cases, a k.Actor behavior must specify "using" either the
 * singleton name or the class name. If the name is a pathname, mandatory in components, the 'using'
 * clause must declare a local alias: <code>using geospatial.raster as raster,
 * using geospatial.Coverage as RasterObject</code> before the actor can be used in code. Verbs
 * declared within the class will apply to it.
 *
 * <p>TODO a @Constructor annotation should define the constructor(s) for non-singleton actors.
 *
 * <p>scoping to one or more script types; re-entrant, static or other execution models
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Actor {

  /**
   * ID of the actor. Must be unique and include a path for any actor declared outside the core
   * distribution. The name must be all lowercase for singletons and camelcase for objects.
   */
  String name();

  String description();

  boolean singleton() default false;
}
