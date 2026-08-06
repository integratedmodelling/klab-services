package org.integratedmodelling.klab.runtime.kactors;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;

public abstract class TestCaseBase extends RuntimeAgentBase {

  /** Execution scope reserved for test-case agents. */
  public static class TestCaseScope extends AgentScope {

    private final SessionScope session;
    private final ContextScope context;
    private Set<ContextScope> contexts = ConcurrentHashMap.newKeySet();
    private DomainObject data;

    public TestCaseScope(RuntimeAgentBase actor, SessionScope session, ContextScope context) {
      super(actor);
      this.session = session;
      this.context = context;
    }

    protected TestCaseScope(TestCaseScope parent, long actionId) {
      super(parent, actionId);
      this.session = parent.session;
      this.context = parent.context;
      this.contexts = parent.contexts;
      this.data = parent.data;
    }

    @Override
    public void setup() {
      synchronized (getAgent().report) {
        getAgent().report.put("start", System.currentTimeMillis());
        getAgent().report.put("parallel", getAgent().runTestsInParallel());
        updateStatistics(getAgent().report);
      }
      getAgent()
          .publishAgentMessage(
              getAgent().getUrn(),
              Message.MessageType.CustomAgentMessage,
              new RuntimeAgent.CustomMessage(
                  TestMessageType.TESTCASE_STARTED.constant(), getAgent().report));

      super.setup();
    }

    public void registerContext(ContextScope context) {
      contexts.add(context);
    }

    @Override
    public TestCaseBase getAgent() {
      return (TestCaseBase) super.getAgent();
    }

    @Override
    public void beforeAction(String actionName, List<Annotation> annotations) {
      var testAnnotation =
          annotations.stream().filter(annotation -> "test".equals(annotation.getName())).findFirst();
      if (testAnnotation.isEmpty()) {
        super.beforeAction(actionName, annotations);
        return;
      }
      contexts = ConcurrentHashMap.newKeySet();
      data = DomainObject.create();
      synchronized (getAgent().report) {
        getAgent().report.getChildren().add(data);
        data.put(DomainObject.TYPE, "test");
        data.put(DomainObject.URN, actionName);
        data.put("start", System.currentTimeMillis());
        data.put(
            DomainObject.NAME,
            testAnnotation.get().get("name") == null
                ? actionName
                : testAnnotation.get().get("name", String.class));
        if (testAnnotation.get().get("description") != null) {
          data.put(
              DomainObject.DESCRIPTION, testAnnotation.get().get("description", String.class));
        }
        updateStatistics(getAgent().report);
      }
      publish(TestMessageType.TEST_STARTED, data);

      super.beforeAction(actionName, annotations);
    }

    @Override
    public void afterAction(String actionName, List<Annotation> annotations) {
      finishAction(actionName, annotations, null);
    }

    @Override
    public void afterAction(
        String actionName, List<Annotation> annotations, Throwable failure) {
      finishAction(actionName, annotations, failure);
    }

    private void finishAction(
        String actionName, List<Annotation> annotations, Throwable failure) {
      if (annotations.stream().noneMatch(annotation -> "test".equals(annotation.getName()))
          || data == null) {
        super.afterAction(actionName, annotations);
        return;
      }
      var completionFailure = failure;
      for (var testContext : contexts) {
        if (testContext.getConfiguration().getPersistence() == Persistence.ONE_OFF) {
          try {
            testContext.close();
          } catch (Throwable closeFailure) {
            if (completionFailure == null) {
              completionFailure = closeFailure;
            } else {
              completionFailure.addSuppressed(closeFailure);
            }
          }
        }
      }
      synchronized (getAgent().report) {
        data.put("end", System.currentTimeMillis());
        if (completionFailure != null) {
          data.put("stacktrace", Utils.Exceptions.stackTrace(completionFailure));
        }
        updateTestOutcome(data, completionFailure);
        updateStatistics(getAgent().report);
      }
      publish(TestMessageType.TEST_FINISHED, data);

      super.afterAction(actionName, annotations);
    }

    /**
     * Called after every assertion evaluation, before a failed assertion is propagated.
     *
     * @param assertion the complete semantic assertion bean
     * @param success whether evaluation and comparison succeeded
     * @param exception the evaluation/comparison failure, or {@code null} on success
     */
    public void assertionEvaluated(
        KActorsStatement.Assert.Assertion assertion, boolean success, Throwable exception) {
      var assertionData = DomainObject.create();
      if (data == null) {
        return;
      }
      assertionData.put(DomainObject.TYPE, "assertion");
      assertionData.put(DomainObject.URN, assertion.getSourceCode());
      assertionData.put("outcome", success);
      if (exception != null) {
        assertionData.put("stacktrace", Utils.Exceptions.stackTrace(exception));
      }
      // TODO fish "success" and "fail" metadata from assertion (probably attached to the statement(s) in it), add
      //  the relevant one (depending on outcome) as description
      assertionData.put("end", System.currentTimeMillis());
      synchronized (getAgent().report) {
        data.getChildren().add(assertionData);
      }
    }

    private void publish(TestMessageType type, DomainObject payload) {
      getAgent()
          .publishAgentMessage(
              getAgent().getUrn(),
              Message.MessageType.CustomAgentMessage,
              new RuntimeAgent.CustomMessage(type.constant(), payload));
    }

    private static void updateTestOutcome(DomainObject test, Throwable failure) {
      long passed =
          test.getChildren().stream()
              .filter(assertion -> assertion.get("outcome", false))
              .count();
      long failed = test.getChildren().size() - passed;
      test.put("assertions", test.getChildren().size());
      test.put("assertionsPassed", passed);
      test.put("assertionsFailed", failed);
      test.put("outcome", failure == null && failed == 0);
    }

    private static void updateStatistics(DomainObject report) {
      long finished = report.getChildren().stream().filter(test -> test.get("end") != null).count();
      long passed =
          report.getChildren().stream()
              .filter(test -> test.get("end") != null && test.get("outcome", false))
              .count();
      long assertions =
          report.getChildren().stream().mapToLong(test -> test.getChildren().size()).sum();
      long assertionsPassed =
          report.getChildren().stream()
              .flatMap(test -> test.getChildren().stream())
              .filter(assertion -> assertion.get("outcome", false))
              .count();
      report.put("tests", report.getChildren().size());
      report.put("testsFinished", finished);
      report.put("testsPassed", passed);
      report.put("testsFailed", finished - passed);
      report.put("assertions", assertions);
      report.put("assertionsPassed", assertionsPassed);
      report.put("assertionsFailed", assertions - assertionsPassed);
    }

    @Override
    public SessionScope getSession() {
      return session;
    }

    @Override
    public ContextScope getContext() {
      return context;
    }

    @Override
    public TestCaseScope withId(long actionId) {
      return new TestCaseScope(this, actionId);
    }
  }

  protected SessionScope scope;
  protected DomainObject report;
  private final boolean parallelTests;

  /**
   * Run the generated {@code @test} actions in declaration order, or concurrently when the
   * testcase declares the boolean {@code parallel} property.
   *
   * <p>Each test action reports its own failure through {@link TestCaseScope}. Ordinary test
   * failures are therefore isolated at this suite boundary instead of failing the testcase agent.
   * Every declared test is attempted in both modes. Parallel tests use one virtual thread per
   * action and the method waits until every finite test has completed. Emitter tests only start
   * their emitter and leave testcase termination to the normal agent lifecycle.
   */
  @SafeVarargs
  protected final Object runTests(Supplier<Object>... tests) {
    Object result = VOID_VALUE;
    if (!runTestsInParallel()) {
      for (var test : tests) {
        try {
          result = test.get();
        } catch (Throwable failure) {
          rethrowIfFatal(failure);
          // The generated action boundary has already recorded this failed test in the report.
        }
      }
      return result;
    }

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures =
          Arrays.stream(tests)
              .map(test -> CompletableFuture.supplyAsync(test, executor))
              .toList();
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
          .handle((ignored, failure) -> null)
          .join();

      for (var future : futures) {
        try {
          result = future.join();
        } catch (CompletionException exception) {
          var cause = exception.getCause() == null ? exception : exception.getCause();
          rethrowIfFatal(cause);
          // All futures have completed and each failed action has already updated the report.
        }
      }
      return result;
    }
  }

  private static void rethrowIfFatal(Throwable failure) {
    if (failure instanceof VirtualMachineError virtualMachineError) {
      throw virtualMachineError;
    }
    if (failure instanceof ThreadDeath threadDeath) {
      throw threadDeath;
    }
  }

  /**
   * Whether generated test actions should run concurrently. Generated testcase classes override
   * this with the compile-time property so their convenience constructors retain the behavior;
   * the instance property remains the fallback for manually constructed subclasses.
   */
  protected boolean runTestsInParallel() {
    return parallelTests;
  }

  public TestCaseBase(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
    this.scope = scope;
    this.parallelTests = runsTestsInParallel(behavior);
    this.report = initializeReport(behavior);
  }

  private DomainObject initializeReport(KActorsBehavior behavior) {
    return DomainObject.create(
        DomainObject.TYPE,
        "testcase",
        DomainObject.NAME,
        behavior == null ? null : behavior.getUrn(),
        DomainObject.DESCRIPTION,
        behavior == null ? null : behavior.getDescription(),
        "testcase",
        behavior == null ? null : behavior.getUrn(),
        "owner",
        scope == null || scope.getUser() == null ? null : scope.getUser().getUsername(),
        "runtime",
        scope == null || scope.getService(RuntimeService.class) == null
            ? null
            : scope.getService(RuntimeService.class).serviceName(),
        "version",
        behavior == null ? null : behavior.getVersion(),
        "parallel",
        parallelTests,
        "klab-version",
        Version.CURRENT);
  }

  protected TestCaseBase(
      KActorsBehavior behavior,
      SessionScope scope,
      Observation observation,
      org.integratedmodelling.klab.api.scope.Scope creationScope) {
    super(behavior, scope, observation, creationScope);
    this.scope = scope;
    this.parallelTests = runsTestsInParallel(behavior);
    this.report = initializeReport(behavior);
  }

  private static boolean runsTestsInParallel(KActorsBehavior behavior) {
    if (behavior == null || behavior.getProperties() == null) {
      return false;
    }
    return Boolean.TRUE.equals(behavior.getProperties().get("parallel"));
  }

  @Override
  protected void beforeTermination(Object detail) {
    synchronized (report) {
      report.put("end", System.currentTimeMillis());
      TestCaseScope.updateStatistics(report);
    }
    publishAgentMessage(
        getUrn(),
        Message.MessageType.CustomAgentMessage,
        new RuntimeAgent.CustomMessage(TestMessageType.TESTCASE_FINISHED.constant(), report));
  }

  @Override
  protected AgentScope initializeScope() {
    return new TestCaseScope(this, sessionScope(), contextScope());
  }

  @Override
  protected void assertValue(Object actual, Object expected) {
    super.assertValue(actual, expected);
  }

  @Override
  protected void assertionEvaluated(
      AgentScope scope,
      KActorsStatement.Assert.Assertion assertion,
      boolean success,
      Throwable exception) {
    if (scope instanceof TestCaseScope testCaseScope) {
      testCaseScope.assertionEvaluated(assertion, success, exception);
    }
  }

//  public void runTest(Consumer<TestCaseScope> test) {
//    // TODO
//  }
}
