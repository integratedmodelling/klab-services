package org.integratedmodelling.klab.api.lang.kim;

import java.util.List;

import org.integratedmodelling.klab.api.collections.impl.Range;
import org.integratedmodelling.klab.api.knowledge.Artifact;

/**
 * Syntactic bean for a k.IM classifier, used in both classifications and lookup tables.
 * 
 * @author ferdinando.villa
 *
 */
public interface KimClassifier extends KimStatement {

    boolean isCatchAll();

    boolean isCatchAnything();

    boolean isNegated();

    KimConcept getConceptMatch();

    Double getNumberMatch();

    Boolean getBooleanMatch();

    List<KimClassifier> getClassifierMatches();

    Range getIntervalMatch();

    boolean isNullMatch();

    KimExpression getExpressionMatch();

    String getStringMatch();

    List<KimConcept> getConceptMatches();

    KimQuantity getQuantityMatch();

    KimDate getDateMatch();

    /**
     * The type of the object incarnated by this classifier
     * 
     * @return
     */
    Artifact.Type getType();

}
