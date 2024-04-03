package org.integratedmodelling.common.view;

import org.integratedmodelling.klab.api.view.PanelController;
import org.integratedmodelling.klab.api.view.PanelView;
import org.integratedmodelling.klab.api.view.UIController;

import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractUIPanelController<T, V extends PanelView<T>> implements PanelController<T, V> {

    UIController controller;
    private final AtomicReference<V> panel = new AtomicReference<>();

    protected AbstractUIPanelController(UIController controller) {
        this.controller = controller;
    }

    @Override
    public V panel() {
        return panel.get();
    }

    @Override
    public UIController getController() {
        return controller;
    }

    @Override
    public void setPanel(PanelView<T> panel) {
        this.panel.set((V) panel);
    }

    @Override
    public void close() {
        this.controller.closePanel(this);
    }

}
