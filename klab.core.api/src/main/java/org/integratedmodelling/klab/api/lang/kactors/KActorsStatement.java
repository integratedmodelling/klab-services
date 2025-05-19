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
        ACTION_CALL,
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

    interface ConcurrentGroup extends KActorsStatement {

        public List<KActorsStatement> getStatements();

        Map<String, KActorsValue> getGroupMetadata();

        /**
         * Actions with the corresponding pattern to match to fired values. If the value is null, any fired
         * values matches.
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

    interface Do extends KActorsStatement {

        KActorsValue getCondition();

        KActorsStatement getBody();

    }

    interface Fail extends KActorsStatement {
        String getMessage();
    }

    interface Break extends KActorsStatement {

    }

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
            List<Call> getCalls();

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

        public enum Scope {
            ACTOR,
            ACTION,
            FRAME
        }

        /**
         * Recipient is the part before the dot if set x.y value is issued. It may be null (local variable in
         * the internal actor's symbols), refer to the state of another actor, or be 'self' which means the
         * value is published to the state of the k.LAB identity connected to the actor, not to the internal
         * actor's state. It's not possible to touch the state of an identity connected to another actor.
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
         * The value to set the variable to, which will be evaluated in the scope of the recipient executing
         * the set statement.
         *
         * @return
         */
        KActorsValue getValue();

        /**
         * Get the scope. Actor, action or block
         *
         * @return
         */
        Scope getAssignmentScope();
    }

    interface FireValue extends KActorsStatement {

        KActorsValue getValue();

    }

    interface TextBlock extends KActorsStatement {

        String getText();

    }

    interface Instantiation extends KActorsStatement {

        /**
         * The behavior for the new actor
         *
         * @return
         */
        String getBehavior();

        /**
         * Arguments, possibly empty, for the main action. Should include a tag if the actor must be
         * referenced.
         *
         * @return
         */
        Parameters<String> getArguments();

        /**
         * Actions with the corresponding pattern to match values fired by the child actor. The third element
         * is the match ID to associate with the result, which may be null (and should be set to "$" if so).
         *
         * @return
         */
        List<Triple<KActorsValue, KActorsStatement, String>> getActions();

        /**
         * Each instantiation action needs a name to reference the actor, so that the parent actors can
         * dispatch messages to children appropriately when the actors create external controllers such as
         * view components. If the instantiation is called more than once, the path will have a 1-based index
         * appended after an underscore, so that any actors created in a loop can be differentiated. This gets
         * renamed to the tag if the parameters contain one.
         *
         * @return the base name - either the tag assigned in the parameters or an automatically generated
         * one.
         */
        String getActorBaseName();

    }

    interface Call extends KActorsStatement {

        /**
         * @return
         * @Override Each call statement has a unique ID, so that we can cache its execution strategy for
         * repeated executions.
         */
        String getCallId();

        /**
         * If group != null, the call message will be null and the actions will react to the firing of any of
         * the messages in the group.
         *
         * @return
         */
        ConcurrentGroup getGroup();

        /**
         * Parsed after checking with the loaded behavior manifest unless there is an explicit recipient. If
         * the message is unrecognized this will be null and the engine will have to match it.
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
         * Actions with the corresponding pattern to match to fired values. If the value is null, any fired
         * values matches.
         *
         * @return
         */
        List<Triple<KActorsValue, KActorsStatement, String>> getActions();

        /**
         * The top-level action call in a statement may be preceded by any number of chained functional calls
         * that modify the scope for execution.
         *
         * @return
         */
        List<Call> getChainedCalls();

    }

    /**
     * According to type, this statement can be cast to one of the above interfaces.
     *
     * @return
     */
    Type getType();

    /**
     * All statements can receive metadata from the code using the metadata tags
     *
     * @return
     */
    Metadata getMetadata();

}
