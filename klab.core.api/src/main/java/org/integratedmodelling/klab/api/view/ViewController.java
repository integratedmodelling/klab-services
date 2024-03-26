package org.integratedmodelling.klab.api.view;

/**
 * Base class for all view controllers in a UI. The correspondent views are shown in the UI permanently and
 * reconfigure themselves based on the UI events they receive, unlike the "panels" that are shown one-off to
 * edit a specific object.
 * <p>
 * The ViewController registers a View of a specified type, and becomes active from that moment on. The
 * controller implementation will drive the view, so that any view implementation only needs to implement
 * the interface.
 */
public interface ViewController<T extends View> extends UIReactor {

    void registerView(T view);

}
