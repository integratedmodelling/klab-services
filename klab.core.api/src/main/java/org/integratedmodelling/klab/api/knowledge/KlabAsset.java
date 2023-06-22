package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.Annotation;

/**
 * All k.LAB assets have a URN, a version, and metadata.
 * 
 * @author Ferd
 *
 */
public interface KlabAsset extends Serializable {

	public enum KnowledgeClass {
		CONCEPT, OBSERVABLE, MODEL, INSTANCE, RESOURCE, NAMESPACE, BEHAVIOR, SCRIPT, TESTCASE, APPLICATION, COMPONENT, PROJECT
	}

	/**
	 * Anything that represents knowledge must return a stable, unique identifier
	 * that can be resolved back to the original or to an identical object. Only
	 * {@link Resource} must use proper URN syntax; for other types of knowledge may
	 * use expressions or paths.
	 * 
	 * @return the unique identifier that specifies this.
	 */
	public String getUrn();

	/**
	 * This should never be null.
	 * 
	 * @return
	 */
	Version getVersion();

	/**
	 * Never null, possibly empty.
	 * 
	 * @return
	 */
	Metadata getMetadata();

	/**
	 * Annotations are possible in most cases, either through the language for
	 * language objects or imported from them.
	 * 
	 * @return
	 */
	List<Annotation> getAnnotations();

}
