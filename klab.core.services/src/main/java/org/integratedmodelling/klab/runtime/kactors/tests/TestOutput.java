package org.integratedmodelling.klab.runtime.kactors.tests;

import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.actors.TestCaseBase;

/**
 * TEMPORARY CLASS FOR TESTING
 * Example worked out translation of a k.Actors script
 * <verbatim>
 * testcase staging.vxii.basic
 *
 * @test
 * action t1:
 * 	inspector.record(events={
 * 		a: dio
 * 		b: can
 * 	    })
 * 	context.new: dt -> (
 * 		dt.observe('staging.vxii.basic.testregion'):
 * 			dt.observe({{earth:StreamGradient}})
 * 	),
 * 	inspector.verify
 * </verbatim>
 */
public class TestOutput extends TestCaseBase {

    public TestOutput(SessionScope scope) {super(scope); }

}
