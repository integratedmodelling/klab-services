package org.integratedmodelling.klab.api.view;

public interface View extends UI {

    void show();

    void hide();

    /**
     * Views in the enabled state permit all allowed interaction with their target.
     */
    void enable();

    void disable();

    boolean isShown();

    boolean isEnabled();

}
