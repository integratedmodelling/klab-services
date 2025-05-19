package org.integratedmodelling.klab.testing;

/**
 * Helper singleton that will build self-consistent test cases in a configured
 * directory as a response to specific kinds of exceptions. The runtime has an
 * admin method to retrieve any test cases built, which expect to be run within
 * the classpath of klab.services.runtime.
 * 
 * @author Ferd
 *
 */
public enum TestCases {

	INSTANCE;

	String packageName;
	String outputDirectory;

}
