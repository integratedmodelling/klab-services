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
     * @param payload the object handled by this panel. If it's a creation, it should be null.
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
     */
    void close();

    /**
     * Mandatory method for the controller to inject the view when the controller is created.
     *
     * @param panel
     */
    void setPanel(PanelView<T> panel);

}
