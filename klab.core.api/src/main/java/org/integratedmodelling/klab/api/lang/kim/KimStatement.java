package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.Statement;

/**
 * 
 * @author Ferd
 *
 */
public interface KimStatement extends Statement, KimScope {

    enum Scope {
        PUBLIC, PRIVATE, PROJECT_PRIVATE
    }

    /**
     * Documentation metadata is the content of the @documentation annotation if present.
     * @return the documentation
     */
    Parameters<String> getDocumentationMetadata();

    /**
     * The namespace ID for this object. Coincides with getName() if this is a IKimNamespace.
     * 
     * @return
     */
    String getNamespace();

    /**
     * Scope can be declared for namespaces and models. Default is public or whatever the containing
     * namespace scope is. Concepts unfortunately cannot be scoped with current infrastructure.
     * 
     * @return
     */
    Scope getScope();

}
