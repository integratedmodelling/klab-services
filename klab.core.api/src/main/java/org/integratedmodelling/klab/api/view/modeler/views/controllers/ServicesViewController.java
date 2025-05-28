package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.views.ServicesView;

/**
 * Service chooser at a minimum should give access to every service available in the engine, reflect
 * their state (at least availability or not) and potentially give UI users options to interact more
 * if the services can be locally or remotely administrated. The main UI user action that must be
 * supported is the choice of one service per category as the "focal"/current service for the engine
 * and for other views..
 */
@UIViewController(
    value = UIReactor.Type.ServiceChooser,
    viewType = ServicesView.class,
    target = Engine.class)
public interface ServicesViewController extends ViewController<ServicesView> {

  @UIEventHandler(UIEvent.EngineStatusChanged)
  void engineStatusChanged(Engine.Status status);

  @UIEventHandler(UIEvent.ServiceStatus)
  void serviceStatusNotified(KlabService service, KlabService.ServiceStatus status);

  @UIActionHandler(value = UIReactor.UIAction.FocusService, sends = UIEvent.ServiceFocused)
  void focusService(KlabService.ServiceCapabilities service);
}
