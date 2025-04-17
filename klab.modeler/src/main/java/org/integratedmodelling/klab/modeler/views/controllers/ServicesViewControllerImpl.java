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

  //    @Override
  //    public void serviceAvailable(KlabService.ServiceCapabilities service) {
  //        // TODO add to engine, select if first or configured default
  //        view().servicesConfigurationChanged(service, RunningInstance.Status.RUNNING);
  //    }
  //
  //    @Override
  //    public void serviceUnavailable(KlabService.ServiceCapabilities service) {
  //        // TODO add to engine, select if first or configured default
  //        view().servicesConfigurationChanged(service, RunningInstance.Status.STOPPED);
  //    }
  //
  //    @Override
  //    public void serviceStarting(KlabService.ServiceCapabilities service) {
  //        // TODO add to engine, select if first or configured default
  //        view().servicesConfigurationChanged(service, RunningInstance.Status.WAITING);
  //    }
  //
  //    @Override
  //    public void serviceStatus(KlabService.ServiceStatus status) {
  //        // TODO add to engine, select if first or configured default
  //        view().notifyServiceStatus(status);
  //    }
  //
  //    @Override
  //    public void reasoningAvailable(Reasoner.Capabilities capabilities) {
  //        view().reasoningAvailable(capabilities);
  //    }

  @Override
  public void engineStatusChanged(Engine.Status status) {

    /*
    Notify any services we haven't seen before. The status will do the rest.
     */
    for (var type :
        List.of(
            KlabService.Type.REASONER,
            KlabService.Type.RESOURCES,
            KlabService.Type.RUNTIME,
            KlabService.Type.RESOLVER)) {

      var capabilities = status.getServicesCapabilities().get(type);
      if (capabilities == null) {
        continue;
      }

      var exists = false;
      if (availableServices.containsKey(type)) {
        for (var service : availableServices.get(type)) {
          if (service.getServiceId().equals(capabilities.getServiceId())) {
            exists = true;
            break;
          }
        }
      }

      if (!exists) {
        view().servicesConfigurationChanged(capabilities);
      }
    }

    // overall change in status
    view().engineStatusChanged(status);
  }

  @Override
  public void serviceStatusNotified(KlabService.ServiceStatus status) {
    view().notifyServiceStatus(status);
  }

  @Override
  public void selectService(KlabService.ServiceCapabilities service) {
    // set the engine's current service. TODO call actions only if the service has changed (return
    //  a boolean). Currently not working with this logic as the current service may not have been
    /// notified.
    getController().setDefaultService(service);
    getController().dispatch(this, UIEvent.ServiceSelected, service);
  }

  @Override
  public void focusService(KlabService.ServiceCapabilities service) {
    // inform all other views
    view().serviceFocusChanged(service);
    getController().dispatch(this, UIEvent.ServiceFocused, service);
  }
}
