package org.integratedmodelling.klab.api.services.reasoner;

import java.lang.annotation.*;

/**
 * Authorities are built from components and are unique to the reasoner. The @Authority annotation
 * tags classes and methods so that an {@link org.integratedmodelling.klab.api.services.Authority}
 * object can be built by the ComponentRegistry. When an authority is referenced, the Reasoner looks
 * up a service in the scope that provides it; failing that, it looks for a component that provides
 * it and installs it.
 *
 * <p>Provides tags for methods that:
 *
 * <p>- configure the authority providing the worldview anchor concept and parameters - produce the
 * identity for a code and potentially a sub-authority - produce the parent chain for a code and
 * potentially a sub-authority - search the authority for a code based on a search string - provide
 * language-friendly labels that can be used in place of a code - provide Markdown documentation for
 * the authority, a subauthority, or a code - provide an image that can be used to illustrate a code
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Authority {

  /**
   * Mandatory URN of the authority.
   *
   * @return
   */
  String urn();

  /**
   * Parseable version of the authority.
   *
   * @return
   */
  String version();

  /**
   * If this is true, the authority's component can be installed into a Reasoner on demand. If
   * false, the authority is only available on Reasoners where its component was explicitly
   * configured. Resources services still advertise and deliver both kinds of authority.
   *
   * @return
   */
  boolean embeddable() default false;

  /**
   * If this is true, the authority exposes search facilities through the authority API.
   *
   * @return
   */
  boolean searchable() default false;

  /**
   * Optional names of codelists that this authority provides, including any that it relies upon.
   *
   * @return
   */
  String[] codelists() default {};

  /**
   * Optional names of sub-authorities that this authority provides.
   *
   * @return
   */
  String[] subAuthorities() default {};
}
