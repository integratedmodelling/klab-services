package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;

/**
 * All k.LAB assets have a URN, a version, and metadata.
 * 
 * @author Ferd
 *
 */
public interface KlabAsset extends Serializable {
    
    /**
     * Anything that represents knowledge must return a stable, unique identifier that can be
     * resolved back to the original or to an identical object. Only {@link Resource} must use
     * proper URN syntax; for other types of knowledge may use expressions or paths.
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
    
}
