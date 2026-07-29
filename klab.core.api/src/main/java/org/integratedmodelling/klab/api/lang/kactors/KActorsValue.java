package org.integratedmodelling.klab.api.lang.kactors;

import java.io.Serializable;
import org.integratedmodelling.klab.api.data.ValueType;

/**
 * A k.Actors value can represent different values in k.Actors, including non-literal values that
 * require evaluation, such as identifiers, code expressions, ternary expressuion and matches for
 * fired values. Their categorization depends on their syntactic roles and not just about type (e.g.
 * a naked identifier is not a string) and they may have no actual value counterpart (for example
 * when matching no-data, errors, "empty" objects or "any" value).
 *
 * <p>An error value (whose content may be null, an exception or a string message) will not match
 * anything except ANYTHING or ERROR.
 *
 * @author Ferd
 */
public interface KActorsValue extends KActorsCodeStatement, Serializable {

  /**
   * The value type
   *
   * @return
   */
  ValueType getType();

  /**
   * The value stated in the value statement. Will contain expression text, literals, or k.IM
   * syntactic objects. Will be null if unknown, any-value or anything.
   *
   * @return
   */
  <T> T getValue(Class<T> cls);

  /**
   * Return the value as the type passed. Meant to complement the enum in a fluent API and not to be
   * used for conversions.
   *
   * @param <T>
   * @param cls
   * @return
   */
  <T> T as(Class<? extends T> cls);

  /**
   * If true, the value specifies a constraint that excludes its own value when used in matching.
   *
   * @return
   */
  boolean isExclusive();

  /**
   * A value prefixed with {@code `} is deferred. Instead of evaluating the value at the call or
   * assignment site, the runtime preserves a reevaluatable computation. When a parameter or
   * variable aliases that computation, each use evaluates it again in the lexical scope and frame
   * captured at its declaration. This is meaningful primarily for computable values such as
   * expressions and ternary expressions; deferring a literal is legal but has no practical effect.
   *
   * @return true when evaluation must be deferred
   */
  boolean isDeferred();

  /**
   * If a cast was assigned using <code>as</code>, report the type to cast to after evaluation.
   * Otherwise null.
   *
   * @return
   */
  String getCast();
}
