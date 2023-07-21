package org.integratedmodelling.klab.testing.exceptions;

import org.integratedmodelling.klab.api.exceptions.KException;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.testing.TestCases;

/**
 * Base class for a runtime exception that will build a test case when thrown,
 * using the methods in {@link TestCases}.
 * 
 * @author Ferd
 *
 */
public abstract class KlabTestCaseBuildingException extends KException {

	private static final long serialVersionUID = -1317044146075559015L;

	protected ContextScope scope;
	// TODO figure out the test case name

	public KlabTestCaseBuildingException(ContextScope scope) {
		this.scope = scope;
		createTestCase();
	}

	protected abstract void createTestCase();

}
