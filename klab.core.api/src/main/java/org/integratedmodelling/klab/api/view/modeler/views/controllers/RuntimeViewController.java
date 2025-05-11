package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.views.RuntimeView;

@UIViewController(value = UIReactor.Type.ContextView, viewType = RuntimeView.class)
public interface RuntimeViewController extends ViewController<RuntimeView> {

  @UIEventHandler(UIEvent.ContextCreated)
  void contextCreated(ContextScope contextScope, RuntimeService service);

  @UIEventHandler(UIEvent.DigitalTwinModified)
  void digitalTwinModified(DigitalTwin digitalTwin, Message change);
}
