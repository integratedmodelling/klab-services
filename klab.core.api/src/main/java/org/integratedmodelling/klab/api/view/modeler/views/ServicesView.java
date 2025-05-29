package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.view.View;

public interface ServicesView extends View {

  /**
   * Called when any of the services in the engine scope reports its status. The view should react
   * by updating the monitored status, if any. This may come at frequent, regular intervals.
   *
   * @param status
   */
  void notifyServiceStatus(KlabService service, KlabService.ServiceStatus status);

  /**
   * Called when the state of any services has changed to the point of making the engine
   * configuration different. Other service notification methods should be called independently of
   * this one.
   *
   * @param status
   */
  void engineStatusChanged(Engine.Status status);
}
