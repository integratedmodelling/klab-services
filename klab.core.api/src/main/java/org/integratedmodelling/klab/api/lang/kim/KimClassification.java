package org.integratedmodelling.klab.api.lang.kim;

import java.io.Serializable;
import java.util.List;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;

public interface KimClassification extends Serializable {

    /**
     * Get the classifiers paired with the concept each represents. Matching should be done
     * sequentially and cached as appropriate.
     * 
     * @return
     */
    List<PairImpl<KimConcept, KimClassifier>> getClassifiers();

    /**
     * True if this was declared and validated as a discretization.
     * 
     * @return true if discretization
     */
    boolean isDiscretization();
}
