package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategies;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;

import java.util.ArrayList;
import java.util.List;

public class KimObservationStrategiesImpl extends KimDocumentImpl<KimObservationStrategy> implements KimObservationStrategies {

    private List<KimObservationStrategy> statements = new ArrayList<>();

    @Override
    public List<KimObservationStrategy> getStatements() {
        return this.statements;
    }

    public void setStatements(List<KimObservationStrategy> statements) {
        this.statements = statements;
    }
}
