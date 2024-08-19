package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;

public class NavigableKimSymbolDefinition extends NavigableKlabStatement<KimSymbolDefinition> implements KimSymbolDefinition {

    public NavigableKimSymbolDefinition(KimSymbolDefinition asset, NavigableKlabAsset<?> parent) {
        super(asset, parent);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDefineClass() {
        return delegate.getDefineClass();
    }

    @Override
    public Object getValue() {
        return delegate.getValue();
    }
}
