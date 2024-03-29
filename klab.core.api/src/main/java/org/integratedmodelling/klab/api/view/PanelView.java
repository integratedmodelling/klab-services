package org.integratedmodelling.klab.api.view;

/**
 * A PanelView is a closeable view. Not sure we should implement Closeable given how this works.
 */
public interface PanelView extends View {

    /**
     * Panels can close, which should completely dispose of the view and release any resources.
     */
    void close();
}
