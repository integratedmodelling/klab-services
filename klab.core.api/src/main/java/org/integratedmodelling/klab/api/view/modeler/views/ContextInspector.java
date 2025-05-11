package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.view.View;

/**
 * Linked to the runtime service. Gets enabled/disabled if the current runtime goes offline. If
 * other controllers switch the current runtime, {@link #setRuntime(RuntimeService.Capabilities)}
 * will be called to reset the view.
 * @deprecated
 */
public interface ContextInspector extends View {

  /**
   * Tune in to the passed service.
   *
   * @param capabilities
   */
  void setRuntime(RuntimeService.Capabilities capabilities);
}
