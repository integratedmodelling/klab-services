package org.integratedmodelling.common.view;

import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.View;
import org.integratedmodelling.klab.api.view.ViewController;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The default abstract ancestor for views builds upon the annotations found on the class and collaborates
 * with the routing process implemented in the {@link AbstractUIController}.
 */
public abstract class AbstractUIViewController<T extends View> implements ViewController<T> {

    UIController controller;
    private AtomicReference<T> view = new AtomicReference<>();

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
        this.view.set(view);
        // schedule a one-time task waiting a couple second, then check with the controller for pending
        // events accumulated before the view was available.
        if (view != null && controller instanceof AbstractUIController) {
            try (var exec = Executors.newScheduledThreadPool(1)) {
                final ScheduledFuture<?> schedule = exec.schedule(this::checkPendingEvents, 2,
                        TimeUnit.SECONDS);
            }
        }
    }

    private void checkPendingEvents() {
        ((AbstractUIController)controller).dispatchPendingTasks(this);
    }

    /**
     * Get the view. NOTE: may be null depending on when {@link #registerView(View)} gets called. If so,
     * messages should be stored and replayed when the view is available.
     *
     * @return the view or null
     */
    protected T view() {
        return view.get();
    }


}
