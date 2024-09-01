package org.integratedmodelling.klab.services.reasoner;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

import java.util.ArrayList;
import java.util.List;

public class ObservationStrategyImpl implements ObservationStrategy {

    private String urn;
    private List<Filter> filters = new ArrayList<>();
    private class Filter {

    }

    public ObservationStrategyImpl(KimObservationStrategy strategy, Reasoner reasoner) {

    }

    @Override
    public int rank() {
        return 0;
    }

    @Override
    public boolean matches(Observable observable, ContextScope scope) {
        return false;
    }

    @Override
    public int getCost(Observable observable, ContextScope scope) {
        return 0;
    }

    @Override
    public String getUrn() {
        return "";
    }

    @Override
    public Metadata getMetadata() {
        return null;
    }
}
