package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.lang.ServiceCall;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The syntactic peer of a k.LAB namespace.
 * 
 * @author ferdinando.villa
 *
 */
public interface KimNamespace extends KlabDocument<KlabStatement> {

	/**
	 * Return all the namespaces that this should not be mixed with during
	 * resolution or scenario setting.
	 *
	 * @return IDs of namespaces we do not agree with
	 */
	Collection<String> getDisjointNamespaces();

	/**
	 * 
	 * @return
	 */
	Map<String, Object> getDefines();

	/**
	 * True if declared as a scenario.
	 * 
	 * @return
	 */
	boolean isScenario();

	/**
	 * If this is a script, return its ID (either specified in a run annotation or
	 * the file name). Otherwise return null.
	 * 
	 * @return the script ID or null.
	 */
	String getScriptId();

	/**
	 * If this is a test case, return its ID (either specified in a run annotation
	 * or the file name). Otherwise return null.
	 * 
	 * @return the test case ID or null.
	 */
	String getTestCaseId();

	/**
	 * Bound to a worldview, therefore used as a script or sidecar file.
	 * 
	 * @return
	 */
	boolean isWorldviewBound();

	/**
	 * If functions were given to constrain the namespace to a scale, return them.
	 * 
	 * @return
	 */
	List<ServiceCall> getExtents();

	/**
	 * A list of the imported namespace IDs matched to a (possibly null) list of
	 * identifiers imported from each.
	 * 
	 * @return
	 */
	Map<String, List<String>> getImports();

}
