package org.integratedmodelling.klab.api.knowledge;

import java.util.Set;

import org.integratedmodelling.klab.api.data.Metadata;

public interface Concept extends Semantics {

    /**
     * 
     * @return
     */
    Set<SemanticType> getType();

    /**
     * 
     * @return
     */
    Metadata getMetadata();

//    /**
//     * If our semantic type is UNION or INTERSECTION, return the operands of the logical connector.
//     * Otherwise return the singleton of this object.
//     * 
//     * @return
//     */
//    Collection<Concept> operands();
//    
//    Collection<Concept> children();
//
//    Concept parent();
//
//    Collection<Concept> parents();
//    
//    Collection<Concept> allChildren();
//    
//    Collection<Concept> allParents();
//    
//    Collection<Concept> closure();

    
}
