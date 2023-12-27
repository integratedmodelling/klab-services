package org.integratedmodelling.klab.api.knowledge;

import java.util.Set;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

public interface Concept extends Semantics {

    /**
     * @return
     */
    Set<SemanticType> getType();

    /**
     * This returns null in "normal" concepts, while a concept qualified with <code>any</code>, <code>all</code> or
     * <code>no</code> will return, respectively, {@link LogicalConnector#UNION}, {@link LogicalConnector#INTERSECTION} or ,
     * {@link LogicalConnector#EXCLUSION}.
     *
     * @return
     */
    LogicalConnector getQualifier();

}
