package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.lang.kim.KimLiteral;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;

import java.util.List;
import java.util.Map;

public class NavigableObservationStrategy extends NavigableKlabStatement<KimObservationStrategy> implements KimObservationStrategy {

    public NavigableObservationStrategy(KimObservationStrategy asset, NavigableKlabAsset<?> parent) {
        super(asset, parent);
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public int getRank() {
        return delegate.getRank();
    }

    @Override
    public List<List<Filter>> getFilters() {
        return delegate.getFilters();
    }

    @Override
    public Map<String, Filter> getMacroVariables() {
        return delegate.getMacroVariables();
    }

    @Override
    public List<Operation> getOperations() {
        return delegate.getOperations();
    }
}
