package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.views.EventViewer;

/**
 * Subscribes to events from different sources and shows notifications appropriately. It should only react to
 * external events and not trigger actions with effects outside the UI itself, except possibly modify the
 * current or default logging levels.
 */
@UIViewController(value = UIReactor.Type.EventViewer, viewType = EventViewer.class, receives = UIReactor.UIEvent.Notification)
public interface EventViewerController extends ViewController<EventViewer> {

    /**
     *
     * @param notification
     * @param service
     */
    void notificationReceived(Notification notification, KlabService service);

    @UIEventHandler(UIEvent.EngineStatusChanged)
    void engineStatusChanged(Engine.Status status);

}
