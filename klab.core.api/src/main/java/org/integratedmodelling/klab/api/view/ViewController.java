package org.integratedmodelling.klab.api.view;

/**
 * Base class for all view controllers in a UI. The correspondent views are shown in the UI permanently and
 * reconfigure themselves based on the UI events they receive, unlike the "panels" that are shown one-off to
 * edit a specific object.
 * <p>
 * The ViewController registers a View of a specified type, and becomes active from that moment on. The
 * controller implementation will drive the view, so that any view implementation only needs to implement the
 * interface.
 */
public interface ViewController<T extends View> extends UIReactor {

    /**
     * Calls {@link UIController#openPanel(Class, Object)} after looking up the panel class implementing a
     * passed interface, which must have been registered with the view controller. If the implementing class
     * is known in advance, this or the main controller's can be called with the same results. If the
     * controller implementation is not under the scope of the calling function, use
     * {@link #registerPanelView(Class)} upon view initialization and call this with the panel interface as
     * argument.
     *
     * @param panelType
     * @param payload
     * @param <P>
     * @param <T>
     * @return the opened panel or null.
     */
    <P, T extends PanelView<P>> T openPanel(Class<T> panelType, P payload);

    /**
     * Register the concrete view class for this controller. The view must self-register with its controller
     * upon creation. Obtain the controller through {@link UIController#viewController(Class)} and check for
     * null.
     *
     * @param view
     */
    void registerView(T view);


    /**
     * If the view controller opens any panels through their interface, the concrete panel view class must be
     * registered in advance. Failing to do so will result in panels not being opened and a warning to the
     * engine scope. Registering two classes that extend the same {@link PanelView} type has undefined
     * results. Registering an interface or abstract class throws a KlabIllegalArgument exception.
     *
     * @param panelViewClass
     * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the class is
     *                                                                                  unsuitable
     */
    void registerPanelView(Class<? extends PanelView<?>> panelViewClass);
}
