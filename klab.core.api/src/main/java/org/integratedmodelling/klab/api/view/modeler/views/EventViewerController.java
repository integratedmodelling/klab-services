package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIView;

/**
 * Subscribes to events from different sources and shows notifications appropriately. It should only react to
 * external events and not trigger actions with effects outside the UI itself, except possibly modify the
 * current or default logging levels.
 */
@UIView(value = UIReactor.Type.EventViewer, receives = UIReactor.UIEvent.Notification)
public interface EventViewerController extends ViewController<EventViewer> {

    /**
     *
     * @param notification
     * @param service
     */
    void notificationReceived(Notification notification, KlabService service);


}
