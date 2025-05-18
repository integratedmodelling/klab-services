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

  private final ContextActor contextActorInstance;
  private Observation _obs1;

  @Override
  protected void runTests() {
    /* contents generated */
    runTest(this::action_t1);
  }

  public TestOutput(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
    /* initialize global actors */
    contextActorInstance = new ContextActor(scope);
    /* init any observations */
  }

  void action_t1(TestScope testScope) {

    /* initialize closeables used within an action, in a try-with-resources block */
    try (var inspector = new Inspector(testScope, scope)) {
      inspector.record(Map.of("a", resolveIdentifier("dio"), "b", resolveIdentifier("can")));

      /*
       * The compiled pattern for any asynchronous action. Always return the result of handle() and pass it to anything that
       * follows, handling any match actions in thenApply.
       */
      CompletableFuture.supplyAsync(() -> contextActorInstance.newContext())
          .handle(
              (s, t) ->
                  testScope.handle(
                      t, this, null /* TODO compile the lexical scope in */, ContextScope.class, s))
          .thenApply(
              dt -> {
                CompletableFuture.supplyAsync(() -> dt.observe(_obs1))
                    .handle(
                        (result, t) ->
                            testScope.handle(
                                t,
                                this,
                                null /* TODO compile the lexical scope in */,
                                Observation.class,
                                result))
                    .thenApply(
                        obs1_ -> {
                          // TODO the remaining piece. ACHTUNG may get the empty result of handle()
                          return obs1_;
                        });
                return dt;
              });
    } catch (IOException e) {
      testScope.handle(e, this, null /* TODO */, Void.class);
    }
  }
}
