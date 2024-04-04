package org.integratedmodelling.klab.api.view;

/**
 * Panels are used to configure one specific object and are brought up and removed as needed, normally
 * occupying the central part of the UI or a modal window.
 */
public interface PanelController<T, V extends PanelView<T>> extends UIReactor {

    PanelView<T> panel();

    /**
     * Loading the object handled must also show the panel and bring it in focus, which should have an OK/Save
     * action associated that hides the panel if it's implemented as a modal.
     *
     * @param payload the object handled by this panel.
     */
    void load(T payload);

    /**
     * Submit the object after successful editing/saving. Not called if the panel is canceled.
     *
     * @return
     */
    T submitOnSave();

    /**
     * Close the panel, which removes if from any catalogue. The UIController will remove the controller from
     * the reactors so that it gets garbage collected. The view initiates the closing action by calling this
     * method on the controller and should handle any dirty state appropriately before doing so.
     * <p>
     * This will call {@link PanelView#close()} and return its return value. If that is false, the closing
     * action is rejected.
     */
    boolean close();

    /**
     * Mandatory method for the controller to inject the view when the controller is created.
     *
     * @param panel
     */
    void setPanel(PanelView<T> panel);

    /**
     * Tell the view to come to the fore as appropriate.
     */
    void bringForward();

    /**
     * The object handled, inserted by the {@link UIController} when the panel is opened successfully.
     *
     * @return the payload. Never null.
     */
    T getPayload();
}
