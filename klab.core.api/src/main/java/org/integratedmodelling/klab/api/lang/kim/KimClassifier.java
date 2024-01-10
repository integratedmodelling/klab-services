package org.integratedmodelling.klab.api.lang.kim;

import java.io.Serializable;
import java.util.List;

import org.integratedmodelling.klab.api.data.mediation.impl.RangeImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.lang.ExpressionCode;

/**
 * Syntactic bean for a k.IM classifier, used in both classifications and lookup tables.
 * 
 * @author ferdinando.villa
 *
 */
public interface KimClassifier extends Serializable {

    boolean isCatchAll();

    boolean isCatchAnything();

    boolean isNegated();

    KimConcept getConceptMatch();

    Double getNumberMatch();

    Boolean getBooleanMatch();

    List<KimClassifier> getClassifierMatches();

    RangeImpl getIntervalMatch();

    boolean isNullMatch();

    ExpressionCode getExpressionMatch();

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
