package org.integratedmodelling.klab.runtime.kactors.tests;

import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.actors.TestCaseBase;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * TEMPORARY CLASS FOR TESTING Example worked out translation of a k.Actors script <verbatim>
 * testcase staging.vxii.basic
 *
 * @test action t1: inspector.record(events={ a: dio b: can }) context.new: dt -> (
 *     dt.observe('staging.vxii.basic.testregion'): dt.observe({{earth:StreamGradient}}) ),
 *     inspector.verify </verbatim>
 */
public class TestOutput extends TestCaseBase {

  ContextActor contextActorInstance;
  Observation observation1;

  @Override
  protected void runTests() {
    runTest(this::actionT1);
    contextActorInstance = new ContextActor(scope);
  }

  public TestOutput(SessionScope scope) {
    super(scope);
  }

  void actionT1(TestScope testScope) {

    // anything mentioning the inspector should be given an individual one instantiated within a
    // try-with-resources block
    try (var inspector = new Inspector(testScope, scope)) {
      inspector.record(Map.of("a", resolveIdentifier("dio"), "b", resolveIdentifier("can")));
      CompletableFuture.supplyAsync(() -> contextActorInstance.newContext())
          .exceptionally(
              t -> {
                //                testScope.record(t, statement_here) NEEDS LEXICAL CONTEXT established at runtime
                return null;
              })
          .thenApply(
              contextScope -> {
                CompletableFuture.supplyAsync(() -> contextScope.observe(observation1))
                    .exceptionally(
                        t -> {
                          /*testScope.record(t);*/
                          return null;
                        })
                    .thenApply(
                        observation -> {
                            // TODO the remaining piece
                          return observation;
                        });
                return contextScope;
              });
    } catch (IOException e) {
      //                testScope.record(t, statement_here)
    }
  }
}
