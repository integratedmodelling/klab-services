package org.integratedmodelling.klab.api.lang.impl.kactors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;

public class KActorsStatementImpl extends KActorsCodeStatementImpl implements KActorsStatement {

    private static final long serialVersionUID = -2182769468866983874L;

    private Type type;

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
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
    public static class AssertionImpl extends KActorsStatementImpl implements KActorsStatement.Assert {

        private static final long serialVersionUID = 3223282784534460612L;

        private Type type = Type.ASSERT_STATEMENT;

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public Parameters<String> getArguments() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public List<Assertion> getAssertions() {
            // TODO Auto-generated method stub
            return null;
        }

    }

    // case ASSIGNMENT:
    public static class AssignmentImpl extends KActorsStatementImpl implements KActorsStatement.Assignment {

        private static final long serialVersionUID = -7539637852015470864L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getRecipient() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getVariable() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsValue getValue() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Scope getScope() {
            // TODO Auto-generated method stub
            return null;
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

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsValue getCondition() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsStatement getBody() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case FAIL_STATEMENT:
    public static class FailImpl extends KActorsStatementImpl implements KActorsStatement.Fail {

        private static final long serialVersionUID = -4224842263629289954L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getMessage() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case FIRE_VALUE:
    public static class FireImpl extends KActorsStatementImpl implements KActorsStatement.FireValue {

        private static final long serialVersionUID = -5778811918801633787L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsValue getValue() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case FOR_STATEMENT:
    public static class ForImpl extends KActorsStatementImpl implements KActorsStatement.For {

        private static final long serialVersionUID = 8082208856388206845L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getVariable() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsValue getIterable() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsStatement getBody() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case IF_STATEMENT:
    public static class IfImpl extends KActorsStatementImpl implements KActorsStatement.If {

        private static final long serialVersionUID = 4140432604976940584L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsValue getCondition() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsStatement getThen() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public List<Pair<KActorsValue, KActorsStatement>> getElseIfs() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsStatement getElse() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case INSTANTIATION:
    public static class InstantiationImpl extends KActorsStatementImpl implements KActorsStatement.Instantiation {

        private static final long serialVersionUID = 5132585275613790159L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getBehavior() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Parameters<String> getArguments() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public List<Triple<KActorsValue, KActorsStatement, String>> getActions() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getActorBaseName() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case SEQUENCE:
    public static class SequenceImpl extends KActorsStatementImpl implements KActorsStatement.Sequence {

        private static final long serialVersionUID = -4623874747196805260L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public List<KActorsStatement> getStatements() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case TEXT_BLOCK:
    public static class TextBlockImpl extends KActorsStatementImpl implements KActorsStatement.TextBlock {

        private static final long serialVersionUID = 5688683773565546787L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getText() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    // case WHILE_STATEMENT:
    public static class WhileImpl extends KActorsStatementImpl implements KActorsStatement.While {

        private static final long serialVersionUID = -732138882065296927L;

        @Override
        public Type getType() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsValue getCondition() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public KActorsStatement getBody() {
            // TODO Auto-generated method stub
            return null;
        }

    }

}
