package org.integratedmodelling.klab.api.view;

/**
 * A PanelView is a view that handles a payload object. It can be opened by the main {@link UIController} and
 * is closed by a view-side action by calling the close() method on the {@link PanelController} that will be
 * injected in its constructor. Implementing classes must receive an appropriately typed controller as a
 * constructor parameter, or they won't be able to be closed at the controller side.
 */
public interface PanelView<T> extends View {


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
