package org.integratedmodelling.klab.api.lang.kactors.impl;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;

public abstract class KActorsStatementImpl extends KActorsCodeStatementImpl
    implements KActorsStatement {

  private Type type;
  private String urn;
  private String tag;
  private boolean sequential;

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
  public void visit(Visitor visitor) {}

  @Override
  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  @Override
  public boolean isSequential() {
    return sequential;
  }

  public void setSequential(boolean sequential) {
    this.sequential = sequential;
  }

  public static class SwitchImpl extends KActorsStatementImpl implements Switch {

    private KActorsValue value;
    private Verb function;
    private List<Verb.MatchAction> cases = new ArrayList<>();
    private String adaptedBehaviorUrn;

    @Override
    public KActorsValue getValue() {
      return this.value;
    }

    @Override
    public Verb getFunction() {
      return this.function;
    }

    @Override
    public List<Verb.MatchAction> getCases() {
      return this.cases;
    }

    @Override
    public String getAdaptedBehaviorUrn() {
      return this.adaptedBehaviorUrn;
    }

    public void setValue(KActorsValue value) {
      this.value = value;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

    public void setCases(List<Verb.MatchAction> cases) {
      this.cases = cases;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  public static class VerbImpl extends KActorsStatementImpl implements Verb {

    @Serial private static final long serialVersionUID = -8705959693429812179L;

    public static class MatchActionImpl extends KActorsStatementImpl implements MatchAction {

      private KActorsValue matchCriterion;
      private KActorsStatement actionOnMatch;
      private List<String> variables = new ArrayList<>();
      private String captureAs;

      @Override
      public KActorsValue getMatchCriterion() {
        return matchCriterion;
      }

      @Override
      public KActorsStatement getActionOnMatch() {
        return actionOnMatch;
      }

      @Override
      public List<String> getVariables() {
        return variables;
      }

      @Override
      public String getCaptureAs() {
        return captureAs;
      }

      public void setMatchCriterion(KActorsValue matchCriterion) {
        this.matchCriterion = matchCriterion;
      }

      public void setActionOnMatch(KActorsStatement actionOnMatch) {
        this.actionOnMatch = actionOnMatch;
      }

      public void setVariables(List<String> variables) {
        this.variables = variables;
      }

      public void setCaptureAs(String captureAs) {
        this.captureAs = captureAs;
      }

      @Override
      public <T> T format(CodeAppender<T> appender) {
        return null;
      }
    }

    private Type type = Type.VERB_STATEMENT;
    private String recipient;
    private String message;
    private Arguments arguments = new KActorsArgumentsImpl();
    private List<MatchAction> actions = new ArrayList<>();

    public void setType(Type type) {
      this.type = type;
    }

    public void setRecipient(String recipient) {
      this.recipient = recipient;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public void setArguments(Arguments arguments) {
      this.arguments = arguments;
    }

    @Override
    public Type getType() {
      return this.type;
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
    public Arguments getArguments() {
      return this.arguments;
    }

    @Override
    public List<MatchAction> getActions() {
      return actions;
    }

    public void setActions(List<MatchAction> actions) {
      this.actions = actions;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  // case ASSERTION:
  public static class AssertImpl extends KActorsStatementImpl implements KActorsStatement.Assert {

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }

    public static class AssertionImpl extends KActorsStatementImpl implements Assertion {

      @Serial private static final long serialVersionUID = 323694264259675055L;

      private Type type = Type.ASSERTION;

      private List<Verb> verbs;
      private KActorsValue expression;
      private KActorsValue value;

      @Override
      public Type getType() {
        return this.type;
      }

      @Override
      public List<Verb> getCalls() {
        return this.verbs;
      }

      @Override
      public KActorsValue getExpression() {
        return expression;
      }

      @Override
      public KActorsValue getValue() {
        return this.value;
      }

      public void setType(Type type) {
        this.type = type;
      }

      public void setCalls(List<Verb> verbs) {
        this.verbs = verbs;
      }

      public void setExpression(KActorsValue expression) {
        this.expression = expression;
      }

      public void setValue(KActorsValue value) {
        this.value = value;
      }

      @Override
      public <T> T format(CodeAppender<T> appender) {
        return null;
      }
    }

    @Serial private static final long serialVersionUID = 3223282784534460612L;

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
  public static class AssignmentImpl extends KActorsStatementImpl
      implements KActorsStatement.Assignment {

    @Serial private static final long serialVersionUID = -7539637852015470864L;

    private Type type = Type.ASSIGNMENT;
    private String variable;
    private KActorsValue value;
    private Verb function;

    private Assignment.Scope assignmentScope;
    private String adaptedBehaviorUrn;

    @Override
    public Type getType() {
      return this.type;
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
      return this.assignmentScope;
    }

    @Override
    public String getAdaptedBehaviorUrn() {
      return this.adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    public void setType(Type type) {
      this.type = type;
    }

    public void setVariable(String variable) {
      this.variable = variable;
    }

    public void setValue(KActorsValue value) {
      this.value = value;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

    public void setAssignmentScope(Assignment.Scope assignmentScope) {
      this.assignmentScope = assignmentScope;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  // case BREAK_STATEMENT:
  public static class BreakImpl extends KActorsStatementImpl implements KActorsStatement.Break {

    @Serial private static final long serialVersionUID = 3236346034825914080L;

    private Type type = Type.BREAK_STATEMENT;

    @Override
    public Type getType() {
      return this.type;
    }

    public void setType(Type type) {
      this.type = type;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  // case CONCURRENT_GROUP:
  public static class GroupImpl extends KActorsStatementImpl implements Group {

    @Serial private static final long serialVersionUID = 6294586114679129470L;

    private Type type = Type.GROUP;
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

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  // case DO_STATEMENT:
  public static class DoImpl extends KActorsStatementImpl implements KActorsStatement.Do {

    @Serial private static final long serialVersionUID = 7461317479122184162L;

    private Type type = Type.DO_STATEMENT;
    private KActorsValue condition;
    private KActorsStatement body;
    private Verb function;
    private String adaptedBehaviorUrn;

    @Override
    public String getAdaptedBehaviorUrn() {
      return adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

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

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  public static class ReturnImpl extends KActorsStatementImpl implements KActorsStatement.Return {

    @Serial private static final long serialVersionUID = -4956873828205184896L;

    private Type type = Type.RETURN_STATEMENT;
    private Verb function;
    private KActorsValue value;
    private String adaptedBehaviorUrn;

    @Override
    public String getAdaptedBehaviorUrn() {
      return adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    @Override
    public Type getType() {
      return type;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }

    @Override
    public KActorsValue getValue() {
      return value;
    }

    public void setValue(KActorsValue value) {
      this.value = value;
    }
  }

  public static class FailImpl extends KActorsStatementImpl implements KActorsStatement.Fail {

    @Serial private static final long serialVersionUID = -4224842263629289954L;

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

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  public static class YieldImpl extends KActorsStatementImpl implements KActorsStatement.Yield {

    @Serial private static final long serialVersionUID = -4956873828205184896L;

    private Type type = Type.RETURN_STATEMENT;
    private Verb function;
    private KActorsValue value;
    private String adaptedBehaviorUrn;

    @Override
    public String getAdaptedBehaviorUrn() {
      return adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    @Override
    public Type getType() {
      return type;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }

    @Override
    public KActorsValue getValue() {
      return value;
    }

    public void setValue(KActorsValue value) {
      this.value = value;
    }
  }

  //
  //  public static class FailImpl extends KActorsStatementImpl implements KActorsStatement.Fail {
  //
  //    @Serial private static final long serialVersionUID = -4224842263629289954L;
  //
  //    private Type type = Type.FAIL_STATEMENT;
  //    private String message;
  //
  //    @Override
  //    public Type getType() {
  //      return this.type;
  //    }
  //
  //    @Override
  //    public String getMessage() {
  //      return this.message;
  //    }
  //
  //    public void setType(Type type) {
  //      this.type = type;
  //    }
  //
  //    public void setMessage(String message) {
  //      this.message = message;
  //    }
  //
  //    @Override
  //    public <T> T format(CodeAppender<T> appender) {
  //      return null;
  //    }
  //  }

  // case FIRE_VALUE:
  public static class FireImpl extends KActorsStatementImpl implements Fire {

    @Serial private static final long serialVersionUID = -5778811918801633787L;

    private Type type = Type.FIRE_VALUE;
    private KActorsValue value;
    private Verb function;
    private String adaptedBehaviorUrn;

    @Override
    public String getAdaptedBehaviorUrn() {
      return adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

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

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  public static class ForImpl extends KActorsStatementImpl implements KActorsStatement.For {

    @Serial private static final long serialVersionUID = 8082208856388206845L;

    private Type type = Type.FOR_STATEMENT;

    private String variable;
    private KActorsValue iterable;
    private Verb function;
    private KActorsStatement body;
    private String adaptedBehaviorUrn;

    @Override
    public String getAdaptedBehaviorUrn() {
      return adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

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

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  // case IF_STATEMENT:
  public static class IfImpl extends KActorsStatementImpl implements KActorsStatement.If {

    @Serial private static final long serialVersionUID = 4140432604976940584L;

    private Type type = Type.IF_STATEMENT;

    private KActorsValue condition;
    private KActorsStatement thenBody;
    private Verb function;
    private List<Pair<Triple<KActorsValue, Verb, String>, KActorsStatement>> elseIfs =
        new ArrayList<>();
    private KActorsStatement elseBody;
    private String adaptedBehaviorUrn;

    @Override
    public String getAdaptedBehaviorUrn() {
      return adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

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
    public List<Pair<Triple<KActorsValue, Verb, String>, KActorsStatement>> getElseIfs() {
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

    public void setElseIfs(
        List<Pair<Triple<KActorsValue, Verb, String>, KActorsStatement>> elseIfs) {
      this.elseIfs = elseIfs;
    }

    public void setElseBody(KActorsStatement elseBody) {
      this.elseBody = elseBody;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  // case TEXT_BLOCK:
  public static class TextImpl extends KActorsStatementImpl implements Text {

    @Serial private static final long serialVersionUID = 5688683773565546787L;

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

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }

  // case WHILE_STATEMENT:
  public static class WhileImpl extends KActorsStatementImpl implements KActorsStatement.While {

    @Serial private static final long serialVersionUID = -732138882065296927L;

    private Type type = Type.WHILE_STATEMENT;
    private KActorsValue condition;
    private KActorsStatement body;
    private Verb function;
    private String adaptedBehaviorUrn;

    @Override
    public String getAdaptedBehaviorUrn() {
      return adaptedBehaviorUrn;
    }

    public void setAdaptedBehaviorUrn(String adaptedBehaviorUrn) {
      this.adaptedBehaviorUrn = adaptedBehaviorUrn;
    }

    @Override
    public Verb getFunction() {
      return function;
    }

    public void setFunction(Verb function) {
      this.function = function;
    }

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

    @Override
    public <T> T format(CodeAppender<T> appender) {
      return null;
    }
  }
}
