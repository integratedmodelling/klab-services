package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.Statement;

/**
 * 
 * @author Ferd
 *
 */
public interface KimStatement extends Statement, KimScope {

	/**
	 * Scope is relevant to models and namespaces, where it affects resolution of
	 * models.
	 * 
	 * @author Ferd
	 *
	 */
	public enum Scope {

		PUBLIC, PRIVATE, PROJECT_PRIVATE;

		public Scope narrowest(Scope... scopes) {
			Scope ret = scopes == null || scopes.length == 0 ? null : scopes[0];
			if (ret != null) {
				for (int i = 1; i < scopes.length; i++) {
					if (scopes[i].ordinal() < ret.ordinal()) {
						ret = scopes[i];
					}
				}
			}
			return ret;
		}
	}

	/**
	 * Documentation metadata is the content of the @documentation annotation if
	 * present.
	 * 
	 * @return the documentation
	 */
	Parameters<String> getDocumentationMetadata();

	/**
	 * The namespace ID for this object. For a KimNamespace it's also the official
	 * name (there is no getName()).
	 * 
	 * @return
	 */
	String getNamespace();

	/**
	 * Scope can be declared for namespaces and models. Default is public or
	 * whatever the containing namespace scope is. Concepts unfortunately cannot be
	 * scoped with current infrastructure.
	 * 
	 * @return
	 */
	Scope getScope();

}
