package org.integratedmodelling.klab.api.lang.kactors.extensions;

import org.integratedmodelling.klab.api.lang.kactors.beans.ViewComponent;

/**
 * An action executor that builds a UI widget
 * 
 * @author Ferd
 *
 */
public interface WidgetActionExecutor extends ActionExecutor {

	ViewComponent getViewComponent();

	void setInitializedComponent(ViewComponent viewComponent);

}
