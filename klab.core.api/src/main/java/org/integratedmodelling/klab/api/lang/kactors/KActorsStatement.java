package org.integratedmodelling.klab.api.lang.kactors;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;

/**
 * Statements have a type according to which each can be casted to the corresponding sub-interface.
 *
 * @author Ferd
 */
public interface KActorsStatement extends KActorsCodeStatement {

  enum Type {
    VERB_STATEMENT,
    IF_STATEMENT,
    FOR_STATEMENT,
    DO_STATEMENT,
    WHILE_STATEMENT,
    TEXT_BLOCK,
    FIRE_VALUE,
    ASSIGNMENT,
    CONCURRENT_GROUP,
    SEQUENCE,
    INSTANTIATION,
    ASSERT_STATEMENT,
    ASSERTION,
    FAIL_STATEMENT,
    BREAK_STATEMENT
  }

  interface If extends KActorsStatement {

    KActorsValue getCondition();

    KActorsStatement getThenBody();

    List<Pair<KActorsValue, KActorsStatement>> getElseIfs();

    KActorsStatement getElseBody();
  }

  /**
   * Argument lists can be extended with metadata
   *
   * @author mario
   */
  interface Arguments extends Parameters<String> {

    List<String> getMetadataKeys();
  }

  interface Group extends KActorsStatement {

    public List<KActorsStatement> getStatements();

    Map<String, KActorsValue> getGroupMetadata();

    /**
     * Actions with the corresponding pattern to match to fired values. If the value is null, any
     * fired values matches.
     *
     * @return
     */
    List<Pair<KActorsValue, KActorsStatement>> getGroupActions();
  }

  interface Sequence extends KActorsStatement {

    public List<KActorsStatement> getStatements();
  }

  interface While extends KActorsStatement {

    KActorsValue getCondition();

    KActorsStatement getBody();
  }

  interface Return extends KActorsStatement {
    KActorsValue getValue();
  }

  interface Do extends KActorsStatement {

    KActorsValue getCondition();

    KActorsStatement getBody();
  }

  interface Fail extends KActorsStatement {
    String getMessage();
  }

  interface Break extends KActorsStatement {}

  /**
   * Assertions have either a (chain of) method calls or one expression to be evaluated in context.
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

      /**
       * Call chain whose final result will be compared with the value.
       *
       * @return
       */
      List<Verb> getCalls();

      /**
       * Expression to use as left side of assertion
       *
       * @return
       */
      KActorsValue getExpression();

      /**
       * Value to compare with (null == 'empty' is a legitimate value producing a non-null
       * IKActorsValue). If null, we are just asserting the absence of errors and that something
       * non-null and non-false was returned in case there is a return value.
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

  /**
   * for variable in iterable (body)
   *
   * @author Ferd
   */
  interface For extends KActorsStatement {

    String getVariable();

    KActorsValue getIterable();

    KActorsStatement getBody();
  }

  interface Assignment extends KActorsStatement {

    enum Scope {
      ACTOR,
      ACTION,
      FRAME
    }

    /**
     * Recipient is the part before the dot if set x.y value is issued. It may be null (local
     * variable in the internal actor's symbols), refer to the state of another actor, or be 'self'
     * which means the value is published to the state of the k.LAB identity connected to the actor,
     * not to the internal actor's state. It's not possible to touch the state of an identity
     * connected to another actor.
     *
     * @return
     */
    String getRecipient();

    /**
     * Variable, which may or may not be prefixed with a recipient.
     *
     * @return
     */
    String getVariable();

    /**
     * The value to set the variable to, which will be evaluated in the scope of the recipient
     * executing the set statement.
     *
     * @return
     */
    KActorsValue getValue();

    /**
     * Assignment from a verb requires a function or a supplier and triggers blocking behavior if
     * the verb is a supplier.
     *
     * @return
     */
    Verb getFunction();

    /**
     * Get the scope. Actor, action or block
     *
     * @return
     */
    Scope getAssignmentScope();
  }

  interface Fire extends KActorsStatement {

    KActorsValue getValue();
  }

  interface Text extends KActorsStatement {

    String getText();
  }

  interface Verb extends KActorsStatement {

    interface MatchAction extends KActorsStatement {

      /**
       * Value to match against the fired output. If null, anything matches except empty, unknown or
       * false.
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
       * Local names of variables to match to fired output. Matches progressively on lists, filling
       * with nulls if more variables than outputs.
       *
       * @return
       */
      List<String> getVariables();

      /**
       * Name of variable to capture the matched value with literal matches that have no variable
       * name.
       *
       * @return
       */
      String getCaptureAs();
    }

    /**
     * Parsed after checking with the loaded behavior manifest unless there is an explicit
     * recipient. If the message is unrecognized this will be null and the engine will have to match
     * it.
     *
     * @return
     */
    String getRecipient();

    /**
     * The message ID. Must contain the recipient when understood through behavior manifest.
     *
     * @return
     */
    String getMessage();

    /**
     * Arguments, possibly empty.
     *
     * @return
     */
    Parameters<String> getArguments();

    /**
     * Actions with the corresponding pattern to match to fired values. If the value is null, any
     * fired values matches.
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
   * the previous supplier or group thereof to supply their value before being executed. Using this
   * out of context should result in a warning.
   *
   * @return
   */
  boolean isSequential();

  /**
   * All statements can receive metadata from the code using the metadata tags. Metadata may be PODs
   * or {@link KActorsValue}s.
   *
   * @return
   */
  Metadata getMetadata();
}
