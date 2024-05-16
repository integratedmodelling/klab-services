package org.integratedmodelling.common.lang.kactors;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KActorsStatementImpl extends KActorsCodeStatementImpl implements KActorsStatement {

    private static final long serialVersionUID = -2182769468866983874L;

    private Type type;
    private String urn;

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    @Override
    public void visit(KlabStatement.KlabStatementVisitor visitor) {

    }

    public static class CallImpl extends KActorsStatementImpl implements KActorsStatement.Call {

        private static final long serialVersionUID = -8705959693429812179L;

        private Type type = Type.ACTION_CALL;
        private String callId;
        private ConcurrentGroup group;
        private String recipient;
        private String message;
        private Parameters<String> arguments;
        private List<Triple<KActorsValue, KActorsStatement, String>> actions = new ArrayList<>();
        private List<Call> chainedCalls = new ArrayList<>();

        public void setType(Type type) {
            this.type = type;
        }

        public void setCallId(String callId) {
            this.callId = callId;
        }

        public void setGroup(ConcurrentGroup group) {
            this.group = group;
        }

        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setArguments(Parameters<String> arguments) {
            this.arguments = arguments;
        }

        public void setActions(List<Triple<KActorsValue, KActorsStatement, String>> actions) {
            this.actions = actions;
        }

        public void setChainedCalls(List<Call> chainedCalls) {
            this.chainedCalls = chainedCalls;
        }

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public String getCallId() {
            return this.callId;
        }

        @Override
        public ConcurrentGroup getGroup() {
            return this.group;
        }

        @Override
        public String getRecipient() {
            return this.recipient;
        }

        @Override
        public String getMessage() {
            return this.message;
        }

        @Override
        public Parameters<String> getArguments() {
            return this.arguments;
        }

        @Override
        public List<Triple<KActorsValue, KActorsStatement, String>> getActions() {
            return this.actions;
        }

        @Override
        public List<Call> getChainedCalls() {
            return this.chainedCalls;
        }

    }

    // case ASSERTION:
    public static class AssertImpl extends KActorsStatementImpl implements KActorsStatement.Assert {

        public static class AssertionImpl extends KActorsStatementImpl implements Assertion {

            private static final long serialVersionUID = 323694264259675055L;

            private Type type = Type.ASSERTION;

            private List<Call> calls;
            private KActorsValue expression;
            private KActorsValue value;

            @Override
            public Type getType() {
                return this.type;
            }

            @Override
            public List<Call> getCalls() {
                return this.calls;
            }

            @Override
            public KActorsValue getExpression() {
                return this.expression;
            }

            @Override
            public KActorsValue getValue() {
                return this.value;
            }

            public void setType(Type type) {
                this.type = type;
            }

            public void setCalls(List<Call> calls) {
                this.calls = calls;
            }

            public void setExpression(KActorsValue expression) {
                this.expression = expression;
            }

            public void setValue(KActorsValue value) {
                this.value = value;
            }

        }

        private static final long serialVersionUID = 3223282784534460612L;

        private Type type = Type.ASSERT_STATEMENT;
        private Parameters<String> arguments = Parameters.create();
        private List<Assertion> assertions = new ArrayList<>();

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public Parameters<String> getArguments() {
            return this.arguments;
        }

        @Override
        public List<Assertion> getAssertions() {
            return this.assertions;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setArguments(Parameters<String> arguments) {
            this.arguments = arguments;
        }

        public void setAssertions(List<Assertion> assertions) {
            this.assertions = assertions;
        }

    }

    // case ASSIGNMENT:
    public static class AssignmentImpl extends KActorsStatementImpl implements KActorsStatement.Assignment {

        private static final long serialVersionUID = -7539637852015470864L;

        private Type type = Type.ASSIGNMENT;
        private String recipient;
        private String variable;
        private KActorsValue value;

        private Assignment.Scope scope;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public String getRecipient() {
            return this.recipient;
        }

        @Override
        public String getVariable() {
            return this.variable;
        }

        @Override
        public KActorsValue getValue() {
            return this.value;
        }

        @Override
        public Assignment.Scope getAssignmentScope() {
            return this.scope;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setRecipient(String recipient) {
            this.recipient = recipient;
        }

        public void setVariable(String variable) {
            this.variable = variable;
        }

        public void setValue(KActorsValue value) {
            this.value = value;
        }

        public void setScope(Assignment.Scope scope) {
            this.scope = scope;
        }

    }
    // case BREAK_STATEMENT:
    public static class BreakImpl extends KActorsStatementImpl implements KActorsStatement.Break {

        private static final long serialVersionUID = 3236346034825914080L;

        private Type type = Type.BREAK_STATEMENT;

        @Override
        public Type getType() {
            return this.type;
        }

        public void setType(Type type) {
            this.type = type;
        }
    }
    // case CONCURRENT_GROUP:
    public static class ConcurrentGroupImpl extends KActorsStatementImpl implements KActorsStatement.ConcurrentGroup {

        private static final long serialVersionUID = 6294586114679129470L;

        private Type type = Type.CONCURRENT_GROUP;
        private List<KActorsStatement> statements = new ArrayList<>();
        private Map<String, KActorsValue> groupMetadata = new LinkedHashMap<>();
        private List<Pair<KActorsValue, KActorsStatement>> groupActions = new ArrayList<>();

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public List<KActorsStatement> getStatements() {
            return this.statements;
        }

        @Override
        public Map<String, KActorsValue> getGroupMetadata() {
            return this.groupMetadata;
        }

        @Override
        public List<Pair<KActorsValue, KActorsStatement>> getGroupActions() {
            return this.groupActions;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setStatements(List<KActorsStatement> statements) {
            this.statements = statements;
        }

        public void setGroupMetadata(Map<String, KActorsValue> groupMetadata) {
            this.groupMetadata = groupMetadata;
        }

        public void setGroupActions(List<Pair<KActorsValue, KActorsStatement>> groupActions) {
            this.groupActions = groupActions;
        }

    }
    // case DO_STATEMENT:
    public static class DoImpl extends KActorsStatementImpl implements KActorsStatement.Do {

        private static final long serialVersionUID = 7461317479122184162L;

        private Type type = Type.DO_STATEMENT;
        private KActorsValue condition;
        private KActorsStatement body;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public KActorsValue getCondition() {
            return this.condition;
        }

        @Override
        public KActorsStatement getBody() {
            return this.body;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setCondition(KActorsValue condition) {
            this.condition = condition;
        }

        public void setBody(KActorsStatement body) {
            this.body = body;
        }

    }
    // case FAIL_STATEMENT:
    public static class FailImpl extends KActorsStatementImpl implements KActorsStatement.Fail {

        private static final long serialVersionUID = -4224842263629289954L;

        private Type type = Type.FAIL_STATEMENT;
        private String message;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public String getMessage() {
            return this.message;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setMessage(String message) {
            this.message = message;
        }

    }
    // case FIRE_VALUE:
    public static class FireImpl extends KActorsStatementImpl implements KActorsStatement.FireValue {

        private static final long serialVersionUID = -5778811918801633787L;

        private Type type = Type.FIRE_VALUE;
        private KActorsValue value;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public KActorsValue getValue() {
            return this.value;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setValue(KActorsValue value) {
            this.value = value;
        }

    }
    // case FOR_STATEMENT:
    public static class ForImpl extends KActorsStatementImpl implements KActorsStatement.For {

        private static final long serialVersionUID = 8082208856388206845L;

        private Type type = Type.FOR_STATEMENT;

        private String variable;
        private KActorsValue iterable;
        private KActorsStatement body;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public String getVariable() {
            return this.variable;
        }

        @Override
        public KActorsValue getIterable() {
            return this.iterable;
        }

        @Override
        public KActorsStatement getBody() {
            return this.body;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setVariable(String variable) {
            this.variable = variable;
        }

        public void setIterable(KActorsValue iterable) {
            this.iterable = iterable;
        }

        public void setBody(KActorsStatement body) {
            this.body = body;
        }

    }
    // case IF_STATEMENT:
    public static class IfImpl extends KActorsStatementImpl implements KActorsStatement.If {

        private static final long serialVersionUID = 4140432604976940584L;

        private Type type = Type.IF_STATEMENT;

        private KActorsValue condition;
        private KActorsStatement thenBody;
        private List<Pair<KActorsValue, KActorsStatement>> elseIfs = new ArrayList<>();
        private KActorsStatement elseBody;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public KActorsValue getCondition() {
            return this.condition;
        }

        @Override
        public KActorsStatement getThenBody() {
            return this.thenBody;
        }

        @Override
        public List<Pair<KActorsValue, KActorsStatement>> getElseIfs() {
            return this.elseIfs;
        }

        @Override
        public KActorsStatement getElseBody() {
            return this.elseBody;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setCondition(KActorsValue condition) {
            this.condition = condition;
        }

        public void setThenBody(KActorsStatement thenBody) {
            this.thenBody = thenBody;
        }

        public void setElseIfs(List<Pair<KActorsValue, KActorsStatement>> elseIfs) {
            this.elseIfs = elseIfs;
        }

        public void setElseBody(KActorsStatement elseBody) {
            this.elseBody = elseBody;
        }

    }
    // case INSTANTIATION:
    public static class InstantiationImpl extends KActorsStatementImpl implements KActorsStatement.Instantiation {

        private static final long serialVersionUID = 5132585275613790159L;

        private Type type = Type.INSTANTIATION;
        private String behavior;
        private Parameters<String> arguments;
        private List<Triple<KActorsValue, KActorsStatement, String>> actions = new ArrayList<>();
        private String actorBaseName;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public String getBehavior() {
            return this.behavior;
        }

        @Override
        public Parameters<String> getArguments() {
            return this.arguments;
        }

        @Override
        public List<Triple<KActorsValue, KActorsStatement, String>> getActions() {
            return this.actions;
        }

        @Override
        public String getActorBaseName() {
            return this.actorBaseName;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setBehavior(String behavior) {
            this.behavior = behavior;
        }

        public void setArguments(Parameters<String> arguments) {
            this.arguments = arguments;
        }

        public void setActions(List<Triple<KActorsValue, KActorsStatement, String>> actions) {
            this.actions = actions;
        }

        public void setActorBaseName(String actorBaseName) {
            this.actorBaseName = actorBaseName;
        }

    }
    // case SEQUENCE:
    public static class SequenceImpl extends KActorsStatementImpl implements KActorsStatement.Sequence {

        private static final long serialVersionUID = -4623874747196805260L;

        private Type type = Type.SEQUENCE;
        private List<KActorsStatement> statements = new ArrayList<>();

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public List<KActorsStatement> getStatements() {
            return this.statements;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setStatements(List<KActorsStatement> statements) {
            this.statements = statements;
        }

    }
    // case TEXT_BLOCK:
    public static class TextBlockImpl extends KActorsStatementImpl implements KActorsStatement.TextBlock {

        private static final long serialVersionUID = 5688683773565546787L;

        private Type type = Type.TEXT_BLOCK;
        private String text;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public String getText() {
            return this.text;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setText(String text) {
            this.text = text;
        }

    }
    // case WHILE_STATEMENT:
    public static class WhileImpl extends KActorsStatementImpl implements KActorsStatement.While {

        private static final long serialVersionUID = -732138882065296927L;

        private Type type = Type.WHILE_STATEMENT;
        private KActorsValue condition;
        private KActorsStatement body;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public KActorsValue getCondition() {
            return this.condition;
        }

        @Override
        public KActorsStatement getBody() {
            return this.body;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setCondition(KActorsValue condition) {
            this.condition = condition;
        }

        public void setBody(KActorsStatement body) {
            this.body = body;
        }

    }

}
