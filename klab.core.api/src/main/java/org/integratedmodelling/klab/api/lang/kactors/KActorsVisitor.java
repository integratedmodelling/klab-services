package org.integratedmodelling.klab.api.lang.kactors;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Statement;

public class KActorsVisitor implements Statement.Visitor {

  public class KActorsContext implements Statement.Visitor.Context {

    private Context parent;
    private KActorsBehavior behavior;
    private KActorsAction action;
    private List<KActorsStatement> upstream = new ArrayList<>();

    public KActorsContext(KActorsBehavior behavior) {
      this.behavior = behavior;
    }

    public KActorsContext(KActorsContext context) {
      this.parent = context;
      this.behavior = context.behavior;
      this.action = context.action;
    }

    public KActorsContext(KActorsContext context, KActorsAction action) {
      this.parent = context;
      this.behavior = context.behavior;
      this.action = action;
    }

    public KActorsContext(KActorsContext context, KActorsStatement statement) {
      this.parent = context;
      this.behavior = context.behavior;
      this.action = context.action;
      this.upstream.addAll(context.upstream);
      this.upstream.add(statement);
    }
  }

  public void visit(KActorsBehavior behavior) {
    var context = new KActorsContext(behavior);
    for (var action : behavior.getStatements()) visitAction(action, context);
  }

  private void visitAction(KActorsAction action, Context context) {
    var con = new KActorsContext((KActorsContext) context, action);
    for (var annotation : action.getAnnotations()) visitAnnotation(annotation, con);
    for (var statement : action.getCode()) visitStatement(statement, con);
  }

  @Override
  public void visitAnnotation(Annotation annotation, Context context) {}

  @Override
  public final void visitStatement(Statement statement, Context context) {
    var ctx = new KActorsContext((KActorsContext) context, (KActorsStatement) statement);
    switch (statement) {
      case KActorsStatement.Do doStatement -> visitDo(doStatement, ctx);
      case KActorsStatement.Assert assertStatement -> visitAssert(assertStatement, ctx);
      case KActorsStatement.Fail failStatement -> visitFail(failStatement, ctx);
      case KActorsStatement.Fire fireGroupStatement -> visitFire(fireGroupStatement, ctx);
      case KActorsStatement.If ifStatement -> visitIf(ifStatement, ctx);
      case KActorsStatement.While whileStatement -> visitWhile(whileStatement, ctx);
      case KActorsStatement.For forStatement -> visitFor(forStatement, ctx);
      case KActorsStatement.Break breakStatement -> visitBreak(breakStatement, ctx);
      case KActorsStatement.Text textStatement -> visitText(textStatement, ctx);
      case KActorsStatement.Assignment assignmentStatement ->
          visitAssignment(assignmentStatement, ctx);
      case KActorsStatement.Verb callStatement -> visitVerb(callStatement, ctx);
      case KActorsStatement.Group concurrentStatement -> visitConcurrent(concurrentStatement, ctx);
      case KActorsStatement.Sequence sequenceStatement -> visitSequence(sequenceStatement, ctx);

      default -> throw new IllegalArgumentException("Unsupported statement type: " + statement);
    }
  }

  private void visitMatch(
      KActorsStatement.Verb.MatchAction matchStatement, KActorsContext context) {}

  private void visitValue(KActorsValue value, KActorsContext context) {}

  private void visitDo(KActorsStatement.Do doStatement, KActorsContext context) {}

  private void visitAssert(KActorsStatement.Assert assertStatement, KActorsContext context) {}

  private void visitFail(KActorsStatement.Fail failStatement, KActorsContext context) {}

  private void visitFire(KActorsStatement.Fire fireValueStatement, KActorsContext context) {}

  private void visitIf(KActorsStatement.If ifStatement, KActorsContext context) {}

  private void visitWhile(KActorsStatement.While whileStatement, KActorsContext context) {}

  private void visitFor(KActorsStatement.For forStatement, KActorsContext context) {}

  private void visitBreak(KActorsStatement.Break breakStatement, KActorsContext context) {}

  private void visitText(KActorsStatement.Text textStatement, KActorsContext context) {}

  private void visitAssignment(
      KActorsStatement.Assignment assignmentStatement, KActorsContext context) {}

  private void visitVerb(KActorsStatement.Verb callStatement, KActorsContext context) {}

  private void visitConcurrent(
      KActorsStatement.Group concurrentStatement, KActorsContext context) {}

  private void visitSequence(KActorsStatement.Sequence sequenceStatement, KActorsContext context) {}
}
