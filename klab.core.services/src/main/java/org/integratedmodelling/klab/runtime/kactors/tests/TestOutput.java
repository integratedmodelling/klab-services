package org.integratedmodelling.klab.runtime.kactors.tests;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.kactors.actors.ContextActor;
import org.integratedmodelling.klab.runtime.kactors.actors.Inspector;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.TestScope;
import org.integratedmodelling.klab.runtime.kactors.compiler.TestCaseBase;

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
public abstract class TestOutput extends TestCaseBase {

  /** Rule: generated fields start with underscore; generated local variables end with underscore */
  private final ContextActor contextActorInstance;

  private Observation _observation1;
  private Observable _observable1;

  @Override
  protected void runTests() {
    /* contents generated */
    runTest(this::action_t1);
  }

  public TestOutput(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
    /* initialize global actors */
    contextActorInstance = new ContextActor(scope);
    /* init any observations and observables */
    // TODO the observation from URN
    _observable1 = scope.getService(Reasoner.class).resolveObservable("geography:StreamGradient");

    // TODO start with a validator step with all the objects required, possibly including versions
    // of
    //  k.LAB and projects
  }

  /**
   * Needs a wrap() function that wraps a generated action and catches exceptions or other return
   * values, based on the allowed matches. It should return a result packet and take one (null if
   * root) along with the function to run. The code should be translated so that any return values,
   * etc. are set into the return packet.
   *
   * <p>The packet is the local execution scope and also contains all local variables and other
   * state.
   *
   * <p>Each action and sub-action gets compiled into a member function that gets called within the
   * wrapper in the main logic.
   *
   * <p>An app's main() uses a specialized scope that builds the interface and sends it out on
   * return. In a test, all test environment is controlled by the special TestScope. Wrap() returns a
   * Future<Scope> that can be chained as needed.
   *
   * @param testScope
   */
  void action_t1(TestScope testScope /* , ... any parameters */) {

    /* initialize closeables used within an action, in a try-with-resources block */
//    try (var inspector = new Inspector(testScope, scope)) {
//      inspector.record(
//          Map.of(
//              "a", resolveIdentifier("dio", testScope), "b", resolveIdentifier("can", testScope)));

      /*
       * The compiled pattern for any asynchronous action. Always return the result of handle() and pass it to anything that
       * follows, handling any match actions in thenApply.
//       */
//      CompletableFuture.supplyAsync(contextActorInstance::newContext)
//          .handle(
//              (s, t) ->
//                  testScope.handle(
//                      t, this, null /* TODO compile the lexical scope in */, ContextScope.class, s))
//          .thenApply(
//              dt -> {
//                CompletableFuture.supplyAsync(() -> dt.submit(_observation1))
//                    .handle(
//                        (result, t) ->
//                            testScope.handle(
//                                t,
//                                this,
//                                null /* TODO compile the lexical scope in */,
//                                Observation.class,
//                                result))
//                    .thenApply(
//                        obs1_ -> {
//
//                          // the remaining piece. ACHTUNG must always check for an empty result of
//                          // handle()!
//                          if (obs1_.isEmpty()) {
//                            return obs1_;
//                          }
//
//                          var observation =
//                              DigitalTwin.createObservation(dt, _observable1, obs1_.getGeometry());
//                          var childScope = dt.within(observation);
//                          CompletableFuture.supplyAsync(() -> childScope.submit(observation))
//                              .handle(
//                                  (result, t) ->
//                                      testScope.handle(
//                                          t,
//                                          this,
//                                          null /* TODO compile the lexical scope in */,
//                                          Observation.class,
//                                          result));
//                          return observation;
//                        });
//                return dt;
//              });
//
//    } catch (IOException e) {
//      testScope.handle(e, this, null /* TODO */, Void.class);
//    }
  }
}
