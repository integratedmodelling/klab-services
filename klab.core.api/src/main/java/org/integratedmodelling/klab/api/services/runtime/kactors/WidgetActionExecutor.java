package org.integratedmodelling.klab.api.services.runtime.kactors;

import org.integratedmodelling.klab.api.lang.kactors.beans.ViewComponent;

/**
 * An action executor that builds and maintains a UI widget. Messages should
 * modify the internal representation of the widget so that the status of an
 * application can be reconstructed and saved at any moment.
 * 
 * @author Ferd
 *
 */
public interface WidgetActionExecutor extends ActionExecutor {

	ViewComponent getViewComponent();

	void setInitializedComponent(ViewComponent viewComponent);

}
