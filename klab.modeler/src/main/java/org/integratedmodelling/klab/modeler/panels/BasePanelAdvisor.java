package org.integratedmodelling.klab.modeler.panels;

import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.PanelView;

public abstract class BasePanelAdvisor<T> implements PanelView<T> {

    private final PanelController<T, PanelView<T>> controller;

    protected BasePanelAdvisor(PanelController<T, PanelView<T>> controller) {
        this.controller = controller;
    }

    public PanelController<T, PanelView<T>> controller() {
        return this.controller;
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
