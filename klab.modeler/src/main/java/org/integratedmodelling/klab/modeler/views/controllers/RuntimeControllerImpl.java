package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.views.RuntimeView;
import org.integratedmodelling.klab.api.view.modeler.views.controllers.RuntimeViewController;

public class RuntimeControllerImpl extends AbstractUIViewController<RuntimeView>
    implements RuntimeViewController {

  public RuntimeControllerImpl(UIController controller) {
    super(controller);
  }

  @Override
  public void contextCreated(ContextScope contextScope, RuntimeService service) {
    view().notifyNewDigitalTwin(contextScope, service);
  }

  @Override
  public void digitalTwinModified(DigitalTwin digitalTwin, Message change) {
    view().notifyDigitalTwinModified(digitalTwin, change);
  }
}
