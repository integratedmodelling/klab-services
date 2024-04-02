package org.integratedmodelling.klab.modeler.panels;

import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.PanelView;

public abstract class BasePanelAdvisor<T> implements PanelView<T> {

    @Override
    public void close() {

    }

    @Override
    public void show() {

    }

    @Override
    public void hide() {

    }

    @Override
    public void enable() {

    }

    public void focus() {

    }

    @Override
    public void disable() {

    }

    @Override
    public boolean isShown() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
