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
     * The 'collective' character ('each' ...) belongs to the observable and not to the concept, but for
     * consistent parsing we must add it to the Concept and propagate it to the observable. When the Concept
     * is used alone, the collective character should be ignored as it is an attribute of the observation
     * (determining the {@link DescriptionType#INSTANTIATION}) and does not affect the semantics.
     *
     * @return
     */
    boolean isCollective();

    /**
     * This returns null in "normal" concepts, while a concept qualified with <code>any</code>,
     * <code>all</code> or
     * <code>no</code> will return, respectively, {@link LogicalConnector#UNION},
     * {@link LogicalConnector#INTERSECTION} or , {@link LogicalConnector#EXCLUSION}.
     *
     * @return
     */
    LogicalConnector getQualifier();

}
