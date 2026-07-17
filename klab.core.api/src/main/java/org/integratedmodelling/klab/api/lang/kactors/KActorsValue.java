package org.integratedmodelling.klab.api.lang.kactors;

import java.io.Serializable;
import java.util.List;
import java.util.regex.Pattern;

import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Arguments;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

/**
 * Values can be a lot of different things in k.Actors and double as matches for fired values, so we
 * categorize them on parsing to allow quick matching. The categorization includes the distinction
 * between syntactic roles and is not just about type (e.g. a naked identifier is not a string). A
 * derived class should use this as delegate to define as() to match types to useful objects not
 * part of the interface.
 *
 * <p>Values can also be used to encode expressions, potentially building an evaluation tree,
 * although parenthesized expressions are for now made impossible by the syntax (round brackets used
 * for too many other purposes) and the only expression supported is a ternary operator. Operators
 * can be easily added but I'm not sure it would be a good idea without the possibility to
 * parenthesize (and the likely fact that an apparently legitimate parenthesized expression would
 * parse correctly and mean something else entirely).
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
   * A value prefixed with ` is deferred and its evaluation should be postponed for as long as
   * possible when passing as argument in actor calls or construction.
   *
   * @return
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
