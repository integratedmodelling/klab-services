package org.integratedmodelling.klab.api.knowledge.observation.scale;

import java.util.Collection;

import org.integratedmodelling.klab.api.knowledge.Authority;
import org.integratedmodelling.klab.api.knowledge.Concept;

/**
 * Enumerated extent, which details a coverage over a conceptual space, either the semantic closure
 * of a base identity or an authority.
 * 
 * @author Ferd
 *
 */
public interface KEnumeratedExtent extends KExtent {

    /**
     * 
     * @return
     */
    Authority getAuthority();

    /**
     * 
     * @return
     */
    Concept getBaseIdentity();

    /**
     * Return all the concepts that make up the extent of this domain.
     * 
     * @return
     */
    Collection<Concept> getExtension();
}
