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

    /*
    TODO put the "Resolution"  enum of Observable here and add a method to retrieve it. The Observable should
    have a quick way of checking if it's a pattern (i.e. there is at least one component concept with a Resolution
    different from null) and the reasoner should have quick ways to match a pattern to another observable (probably
    just using the semantic distance but checking only the explicit clauses) and "incarnate" a pattern into other
    observables contextually.
     */
}
