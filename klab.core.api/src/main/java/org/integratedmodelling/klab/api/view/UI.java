package org.integratedmodelling.klab.api.view;

import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Tag interface for the top-level, view-side UI object. The main {@link UIController} representing the
 * top-level application will take the UI as parameter and dispatch any events that are registered by methods
 * in its interface. The controller may not have a UI, in which case it should just assume that all choices
 * are confirmed and user-level logging is not requested.
 */
public interface UI {

    /**
     * Alert is a notification that should be shown and acknowledged by the UI user. Parameters in the
     * notification determine the display mode.
     *
     * @param notification
     */
    void alert(Notification notification);

    /**
     * Respond yes/no/cancel to the passed notification.
     *
     * @param notification
     * @return
     */
    boolean confirm(Notification notification);

    /**
     * Log is any other notification, which may or may not be logged or displayed but should be unobtrusively
     * handled.
     *
     * @param notification
     */
    void log(Notification notification);

    /**
     * Clean up whatever workspace we are looking at, with open editors and panels etc, restoring the basic
     * view. Call when main service changes determine a full-scale view reset.
     */
    void cleanWorkspace();

}