package org.integratedmodelling.klab.runtime.kactors.tests;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.actors.ContextActor;
import org.integratedmodelling.klab.runtime.kactors.actors.Inspector;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.TestScope;
import org.integratedmodelling.klab.runtime.kactors.compiler.TestCaseBase;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * TEMPORARY CLASS FOR TESTING Manually worked out translation of a k.Actors script
 *
 * <pre>
 * testcase staging.vxii.basic
 *
 * @test
 * action t1:
 * 	inspector.record(events={
 * 		a: dio
 * 		b: can
 * 	})
 * 	context.new: dt -> (
 * 		dt.observe('staging.vxii.basic.testregion'):
 * 			dt.observe({{earth:StreamGradient}})
 * 	)
 * </pre>
 *
 */
public class TestOutput extends TestCaseBase {

  ContextActor contextActorInstance;
  Observation _obs1;

  @Override
  protected void runTests() {
    runTest(this::actionT1);
    contextActorInstance = new ContextActor(scope);
  }

  public TestOutput(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }

  void actionT1(TestScope testScope) {

    // anything mentioning the inspector should be given an individual one instantiated within a
    // try-with-resources block
    try (var inspector = new Inspector(testScope, scope)) {
      inspector.record(Map.of("a", resolveIdentifier("dio"), "b", resolveIdentifier("can")));

      /*
       * The pattern for any asynchronous action. Always return the result of handle() and pass it to anything that
       * follows, handling any match actions in thenApply.
       */
      CompletableFuture.supplyAsync(() -> contextActorInstance.newContext())
          .handle((s, t) -> testScope.handle(t, this, null /* TODO */, ContextScope.class, s))
          .thenApply(
              dt -> {
                CompletableFuture.supplyAsync(() -> dt.observe(_obs1))
                    .handle(
                        (result, t) ->
                            testScope.handle(t, this, null /* TODO */, Observation.class, result))
                    .thenApply(
                        obs1_ -> {
                          // TODO the remaining piece
                          return obs1_;
                        });
                return dt;
              });
    } catch (IOException e) {
      testScope.handle(e, this, null /* TODO */, Void.class);
    }
  }
}
