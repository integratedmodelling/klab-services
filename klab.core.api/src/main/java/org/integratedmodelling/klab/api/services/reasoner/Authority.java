package org.integratedmodelling.klab.api.services.reasoner;

import java.lang.annotation.*;

/**
 * Authorities are built from components and are unique to the reasoner. The @Authority annotation
 * tags classes and methods so that an {@link org.integratedmodelling.klab.api.knowledge.Authority}
 * object can be built by the ComponentRegistry. When an authority is referenced, the Reasoner looks
 * up a service in the scope that provides it; failing that, it looks for a component that provides
 * it and installs it.
 *
 * Provides tags for methods that:
 *
 * - configure the authority providing the worldview anchor concept and parameters
 * - produce the identity for a code and potentially a sub-authority
 * - produce the parent chain for a code and potentially a sub-authority
 * - search the authority for a code based on a search string
 * - provide language-friendly labels that can be used in place of a code
 * - provide Markdown documentation for the authority, a subauthority, or a code
 * - provide an image that can be used to illustrate a code
 *
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Authority {

  /**
   * Mandatory name of the authority.
   *
   * @return
   */
  String name();

  /**
   * Optional names of sub-authorities that this authority provides.
   *
   * @return
   */
  String[] subAuthorities() default {};


}
