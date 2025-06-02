package org.integratedmodelling.klab.modeler.views.controllers;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.ServicesViewController;

import java.util.List;

/** We need no storage because the service status is the one in the engine. */
public class ServicesViewControllerImpl extends AbstractUIViewController<ServicesView>
    implements ServicesViewController {

  Multimap<KlabService.Type, KlabService.ServiceCapabilities> availableServices =
      ArrayListMultimap.create();

  public ServicesViewControllerImpl(UIController controller) {
    super(controller);
  }

  @Override
  public void engineStatusChanged(Engine.Status status) {
    view().engineStatusChanged(status);
  }

  @Override
  public void serviceStatusNotified(KlabService service, KlabService.ServiceStatus status) {
    view().notifyServiceStatus(service, status);
  }

}
