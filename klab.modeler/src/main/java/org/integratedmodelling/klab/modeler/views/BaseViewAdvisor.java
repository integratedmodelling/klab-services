package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.view.View;


/**
 * Basic view class without actual view operations. Can be extended by real views that will override the view
 * methods. The derived classes in this package provide basic action/reaction functionalities that maintain an
 * internal knowledge model.
 */
public class BaseViewAdvisor implements View {

    @Override
    public void show() {
        // do nothing
    }

    @Override
    public void hide() {
        throw new KlabIllegalStateException("Base view advisor cannot be hidden");
    }

    @Override
    public void enable() {
        // do nothing
    }

    @Override
    public void disable() {
        throw new KlabIllegalStateException("Base view advisor cannot be disabled");
    }

    @Override
    public boolean isShown() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
