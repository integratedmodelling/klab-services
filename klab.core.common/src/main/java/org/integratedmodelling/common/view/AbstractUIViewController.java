package org.integratedmodelling.common.view;

import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.View;
import org.integratedmodelling.klab.api.view.ViewController;

/**
 * The default abstract ancestor for views builds upon the annotations found on the class and collaborates
 * with the routing process implemented in the {@link AbstractUIController}.
 */
public abstract class AbstractUIViewController<T extends View> implements ViewController<T> {

    UIController controller;
    private T view;

    protected AbstractUIViewController(UIController controller) {
        this.controller = controller;
    }

    public UIController getController() {
        return controller;
    }

    public <S extends KlabService> S service(KlabService.ServiceCapabilities capabilities,
                                             Class<S> serviceClass) {
        if (capabilities == null) {
            return null;
        }
        return ((AbstractUIController) controller).serviceById(capabilities.getServiceId(),
                serviceClass);
    }

    public void registerView(T view) {
        this.view = view;
    }

    protected T view() {
        assert (view != null);
        return view;
    }

    /**
     * The default onEvent does nothing as the routing mechanism should take care of everything.
     *
     * @param event
     * @param payload
     */
    @Override
    public void onEvent(UIEvent event, Object payload) {

    }

}
