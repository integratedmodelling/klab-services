package org.integratedmodelling.klab.api.lang.kactors;

import java.util.List;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;

/**
 * Statements have a type according to which each can be casted to the corresponding sub-interface.
 *
 * @author Ferd
 */
public interface KActorsStatement extends KActorsCodeStatement {

  /**
   * Type of statement for easier categorization by the parser and compiler. Each concrete statement
   * carries its own type.
   */
  enum Type {
    VERB_STATEMENT,
    IF_STATEMENT,
    FOR_STATEMENT,
    DO_STATEMENT,
    WHILE_STATEMENT,
    TEXT_BLOCK,
    FIRE_VALUE,
    RETURN_STATEMENT,
    ASSIGNMENT,
    GROUP,
    ASSERT_STATEMENT,
    ASSERTION,
    FAIL_STATEMENT,
    BREAK_STATEMENT
  }

  /** The syntactic counterpart of a k.Actors <code>if</code> statement. */
  interface If extends KActorsStatement {

    /**
     * A value that evaluates to true or false. Only one between getCondition() and getFunction()
     * may be non-null.
     *
     * @return
     */
    KActorsValue getCondition();

    /**
     * The function or supplier that evaluates to true or false when the condition is supplied
     * through a functional verb; requires a function or a supplier and triggers blocking behavior
     * if the verb is a supplier. Only one between getCondition() and getFunction() may be non-null.
     *
     * @return
     */
    Verb getFunction();

    /**
     * The body of the if statement.
     *
     * @return
     */
    KActorsStatement getThenBody();

    /**
     * The conditions and bodies of any <code>else if</code> statement. The condition may be a verb
     * or a value; only one of them may be non-null.
     *
     * @return
     */
    List<Pair<Pair<KActorsValue, Verb>, KActorsStatement>> getElseIfs();

    /**
     * The body of the <code>else</code> statement if present.
     *
     * @return
     */
    KActorsStatement getElseBody();
  }

  /**
   * Argument lists are normal {@link Parameters} (they can have both named and unnamed keys) and
   * can be extended with metadata.
   */
  interface Arguments extends Parameters<String> {

    /**
     * Any metadata supplied after the parameters using the k.Actors metadata syntax.
     *
     * @return
     */
    List<String> getMetadataKeys();
  }

  /**
   * A group of statements is merely a list of statements, included in parentheses in k.Actors,
   * which can optionally have trailing metadata and a tag.
   */
  interface Group extends KActorsStatement {

    /**
     * The sequence of statements in a group
     *
     * @return
     */
    List<KActorsStatement> getStatements();
  }

  /** The syntactic counterpart of a k.Actors <code>if</code> statement. */
  interface While extends KActorsStatement {

    /**
     * The termination condition, which must evaluate to a boolean. Only one between *
     * getCondition() and getFunction() may be non-null.
     *
     * @return
     */
    KActorsValue getCondition();

    /**
     * Defined when the condition is supplied through a functional verb; requires a function or a
     * supplier and triggers blocking behavior if the verb is a supplier. Only one between
     * getCondition() and getFunction() may be non-null.
     *
     * @return
     */
    Verb getFunction();

    /**
     * The body of the loop
     *
     * @return
     */
    KActorsStatement getBody();
  }

  /** The syntactic counterpart of a k.Actors <code>return</code> statement. */
  interface Return extends KActorsStatement {

    /**
     * The returned value. Exactly one between getValue() and getFunction() must be non-null. In an
     * emitter's reactive return the value is an exit code; executing the return stops scheduled
     * emissions and removes listeners without changing the action's emitter type.
     *
     * @return
     */
    KActorsValue getValue();

    /**
     * Defined when the return value is supplied through a functional verb; requires a function or
     * a supplier and triggers blocking behavior if the verb is a supplier. Only one between
     * getValue() and getFunction() must be non-null. See {@link #getValue()} for the emitter exit
     * code case.
     *
     * @return
     */
    Verb getFunction();
  }

  /** The syntactic counterpart of a k.Actors <code>do</code> statement. */
  interface Do extends KActorsStatement {

    /**
     * The condition to be evaluated after the body.
     *
     * @return
     */
    KActorsValue getCondition();

    /**
     * Defined when the condition is supplied through a functional verb; requires a function or a
     * supplier and triggers blocking behavior if the verb is a supplier. Only one between
     * getIterable() and getFunction() may be non-null.
     *
     * @return
     */
    Verb getFunction();

    /**
     * The body of the loop.
     *
     * @return
     */
    KActorsStatement getBody();
  }

  /**
   * The syntactic counterpart of a k.Actors <code>fail</code> statement, used in assertions and
   * test cases, or to throw exceptions during agent execution.
   */
  interface Fail extends KActorsStatement {

    /**
     * Message to communicate upon failure. More information and how to display it (for example,
     * interactivity in UI applications) may be specified in metadata.
     *
     * @return
     */
    String getMessage();
  }

  /**
   * The syntactic counterpart of a k.Actors <code>break</code> statement, used to exit loops. Has
   * no specs but may carry metadata for special uses.
   */
  interface Break extends KActorsStatement {}

  /**
   * Assertions have either a (chain of) method calls or one expression to be evaluated in context.
   * They should not appear in production behaviors and may not be retained when any agent other
   * than test cases are compiled for production.
   *
   * @author Ferd
   */
  interface Assert extends KActorsStatement {

    /**
     * Assertions are a chain of calls or an expression with an optional comparison value.
     *
     * @author Ferd
     */
    interface Assertion extends KActorsStatement {

      /** Expression whose result is being asserted, mutually exclusive with {@link #getCalls()}. */
      KActorsValue getExpression();

      /**
       * Call chain whose final result will be compared with the value.
       *
       * @return
       */
      List<Verb> getCalls();

      /**
       * Expected value to compare with. If null, the expression or call result is expected to be
       * successful and truthy; <code>is ok</code> is represented by a boolean true value.
       *
       * @return
       */
      KActorsValue getValue();
    }

    /**
     * Arguments, possibly empty. Used for targeting, filtering and the like.
     *
     * @return
     */
    Parameters<String> getArguments();

    /**
     * All the assertions subsumed by the statement. Arguments apply to all of them.
     *
     * @return
     */
    List<Assertion> getAssertions();
  }

  /** The syntactic counterpart of a k.Actors <code>for</code> statement */
  interface For extends KActorsStatement {

    /**
     * The variable to associate to any iteration.
     *
     * @return
     */
    String getVariable();

    /**
     * The expression to iterate over, which must evaluate to an {@link Iterable}.Only one between
     * getIterable() and getFunction() may be non-null.
     *
     * @return
     */
    KActorsValue getIterable();

    /**
     * Defined when the iterable is supplied through a functional verb; requires a function or a
     * supplier and triggers blocking behavior if the verb is a supplier. Only one between
     * getIterable() and getFunction() may be non-null.
     *
     * @return
     */
    Verb getFunction();

    /**
     * The body of the loop, which will receive the #getVariable as a local variable.
     *
     * @return
     */
    KActorsStatement getBody();
  }

  /**
   * Assignments use different syntax in different scopes. To initialize the state of an agent, the
   * <code>def</code> assignment must be used, which is only legal in an <code>init</code> action.
   * If an action in the same behavior wants to modify the value of a state variable, it must use
   * <code>set</code> assignments. Frame-scoped variables are assigned using the <code>
   * variable <- value</code> syntax, and the resulting variables are only visible within the scope
   * of the assignment (current action or group and any lower-level groups). It is illegal to use a
   * known state variable name on the left side of a scoped assignment.
   */
  interface Assignment extends KActorsStatement {

    enum Scope {
      /** Defined using <code>def</code> and changed through <code>set</code> */
      ACTOR,
      /**
       * Stack frame variable. Defined using <code>x <- value</code> and changeable with the same
       * syntax in its scope of visibility.
       */
      FRAME
    }

    /**
     * Variable name, always a simple lowercase identifier.
     *
     * @return
     */
    String getVariable();

    /**
     * The value to set the variable to, which will be evaluated in the scope of the recipient
     * executing the set statement. Expressions are represented as values of expression type.
     * Exactly one between getValue() and getFunction() must be non-null.
     *
     * @return
     */
    KActorsValue getValue();

    /**
     * Assignment from a verb requires a function or a supplier and triggers blocking behavior if
     * the verb is a supplier. Exactly one between getValue() and getFunction() must be non-null.
     *
     * @return
     */
    Verb getFunction();

    /** Get the scope (actor state or frame) */
    Scope getAssignmentScope();
  }

  /** The syntactic counterpart of a <code>fire</code> statement */
  interface Fire extends KActorsStatement {

    /**
     * The value to be fired through the {@link
     * org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope} as an event for the agent's event
     * bus. Only one between getValue() and getFunction() may be non-null.
     *
     * @return
     */
    KActorsValue getValue();

    /**
     * Defined when the value is supplied through a functional verb; requires a function or a
     * supplier and triggers blocking behavior if the verb is a supplier. Only one between
     * getIterable() and getFunction() may be non-null.
     *
     * @return
     */
    Verb getFunction();
  }

  /**
   * The syntactic counterpart of a text statement, corresponding to an extended text literal with
   * template fields and extended Markdown syntax, whose usage depends on the execution context
   * (chiefly used in user-facing applications).
   */
  interface Text extends KActorsStatement {

    /**
     * The unprocessed text of the statement. May be analyzed and returned as a smarter object in
     * the future.
     *
     * @return
     */
    String getText();
  }

  /**
   * The syntactic counterpart of a verb statement, corresponding to a verb invocation with possible
   * actions to match to values and execute if the verb is a supplier or an emitter. A verb
   * statement is a message to an imported agent (or to <code>self</code>), always contains a
   * recipient and a message selector, and optionally actions to match to returned or fired outputs.
   * It is an error to assign match actions to a verb statement that is not a supplier or emitter.
   */
  interface Verb extends KActorsStatement {

    interface MatchAction extends KActorsStatement {

      /**
       * Value to match against the emitted/returned output. If null, nothing matches except empty,
       * unknown or false.
       *
       * @return
       */
      KActorsValue getMatchCriterion();

      /**
       * Statement to execute in context after match succeeds.
       *
       * @return
       */
      KActorsStatement getActionOnMatch();

      /**
       * Local names of variables to match to fired output when those are needed in the subsequent
       * actions. Multiple variables are matched sequentially when the value emitted is a list or a
       * tuple, and filled with nulls if more variables than outputs are present.
       *
       * @return
       */
      List<String> getVariables();

      /**
       * Name of variable to capture the matched value with literal matches that have no variable
       * name: for example when matching a regular expression (<code>
       * verb: /regexp/ as x -> action(x)
       * </code>).
       *
       * @return
       */
      String getCaptureAs();
    }

    /**
     * Parsed after checking with the loaded behavior manifest unless there is an explicit
     * recipient. Never null (the code may not use a recipient, but that will be validated against
     * the agent's own actions and report <code>self</code>).
     *
     * @return
     */
    String getRecipient();

    /**
     * The message selector
     *
     * @return
     */
    String getMessage();

    /**
     * Arguments, possibly empty.
     *
     * @return
     */
    Arguments getArguments();

    /**
     * Actions with the corresponding pattern to match to fired values. If the value is null, any
     * fired values matches. A verb that resolves to a {@link
     * org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type#FUNCTION} cannot have
     * match actions.
     *
     * @return
     */
    List<MatchAction> getActions();
  }

  /**
   * If not null, this statement was tagged with a <code>#name</code> tag.
   *
   * @return
   */
  String getTag();

  /**
   * According to type, this statement can be cast to one of the above interfaces.
   *
   * @return
   */
  Type getType();

  /**
   * If true, this statement was preceded by <code>then</code> in the code, meaning it must wait for
   * the previous reactive call, or every reactive call in the previous group, to supply its first
   * value before being executed. Using this out of context (when the previous statement has no
   * match actions to wait for) should result in a warning.
   *
   * @return
   */
  boolean isSequential();

  /**
   * All statements can receive metadata from the code using the metadata tags. Metadata values may
   * be PODs or {@link KActorsValue}s.
   *
   * @return
   */
  Metadata getMetadata();
}
