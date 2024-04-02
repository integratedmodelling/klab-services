package org.integratedmodelling.klab.api.view;

/**
 * A PanelView is a closeable view that handles a payload object. The implementing class constructors will
 * receive the controller if they are declared to receive one.
 */
public interface PanelView<T> extends View {

    /**
     * Panels can close, which should completely dispose of the view and release any resources.
     */
    void close();

    /**
     * Panel is already open but a UI event has asked to open it. If appropriate, bring the panel view
     * forward.
     */
    void focus();

    /**
     * Panels will always load a payload upon creation and registration
     *
     * @param payload
     */
    void load(T payload);
}
