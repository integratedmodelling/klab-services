package org.integratedmodelling.klab.api.lang.impl.kim;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

public class KimClassificationImpl /*extends KimStatementImpl*/ implements KimClassification {

    private static final long serialVersionUID = 2314681226321826507L;
    
    private boolean discretization;
    private List<PairImpl<KimConcept, KimClassifier>> classifiers = new ArrayList<>();

    @Override
    public boolean isDiscretization() {
        return discretization;
    }

    @Override
    public List<PairImpl<KimConcept, KimClassifier>> getClassifiers() {
        return classifiers;
    }

    public void setDiscretization(boolean discretization) {
        this.discretization = discretization;
    }

    public void setClassifiers(List<PairImpl<KimConcept, KimClassifier>> classifiers) {
        this.classifiers = classifiers;
    }

}
