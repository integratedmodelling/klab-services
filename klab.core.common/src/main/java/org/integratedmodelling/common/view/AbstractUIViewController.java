package org.integratedmodelling.common.view;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.view.*;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

import java.awt.*;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private Set<Class<? extends PanelView<?>>> panelViewImplementations = new HashSet<>();

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

    @Override
    public <P, T1 extends PanelView<P>> T1 openPanel(Class<T1> panelType, P payload) {
        var cls = (Class<? extends PanelView<P>>) getPanelViewClass(panelType);
        if (cls == null) {
            controller.engine().serviceScope().warn("cannot find panel class implementation for " + panelType.getCanonicalName() + ": ignoring panel open request");
        }
        return (T1) controller.openPanel(cls, payload);
    }

    /**
     * Find the first registered candidate to implement the passed panel view class. If there are no
     * candidates but the passed class is suitable and concrete, return the argument, otherwise return null.
     *
     * @param candidate
     * @return a registered panel view implementation or null
     */
    protected Class<? extends PanelView<?>> getPanelViewClass(Class<? extends PanelView<?>> candidate) {
        for (var cls : panelViewImplementations) {
            if (candidate.isAssignableFrom(cls)) {
                return cls;
            }
        }
        boolean ok =
                (candidate.isInterface() || candidate.isArray() || candidate.isAnnotation() || (candidate.getModifiers() & Modifier.PUBLIC) == 0);
        return ok ? candidate : null;
    }

    @Override
    public void registerPanelView(Class<? extends PanelView<?>> panelViewClass) {
        if (panelViewClass.isInterface() || panelViewClass.isArray() || panelViewClass.isAnnotation() || (panelViewClass.getModifiers() & Modifier.PUBLIC) == 0) {
            throw new KlabIllegalArgumentException("Class " + panelViewClass + " is unsuitable as a panel " +
                    "view implementation");
        }
        panelViewImplementations.add(panelViewClass);
    }

    private void checkPendingEvents() {
        ((AbstractUIController) controller).dispatchPendingTasks(this);
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
