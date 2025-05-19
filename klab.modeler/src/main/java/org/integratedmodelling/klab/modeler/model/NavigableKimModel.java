package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;

import java.util.List;

public class NavigableKimModel extends NavigableKlabStatement<KimModel> implements KimModel {

    public NavigableKimModel(KimModel asset, NavigableKlabAsset<?> parent) {
        super(asset, parent);
    }

    @Override
    public List<KimObservable> getDependencies() {
        return delegate.getDependencies();
    }

    @Override
    public List<KimObservable> getObservables() {
        return delegate.getObservables();
    }

    @Override
    public boolean isInactive() {
        return delegate.isInactive();
    }

    @Override
    public Artifact.Type getType() {
        return delegate.getType();
    }

    @Override
    public List<String> getResourceUrns() {
        return delegate.getResourceUrns();
    }

    @Override
    public boolean isLearningModel() {
        return delegate.isLearningModel();
    }

    @Override
    public List<Contextualizable> getContextualization() {
        return delegate.getContextualization();
    }

    @Override
    public Geometry getCoverage() {
        return delegate.getCoverage();
    }

    @Override
    public String getDocstring() {
        return delegate.getDocstring();
    }
}
