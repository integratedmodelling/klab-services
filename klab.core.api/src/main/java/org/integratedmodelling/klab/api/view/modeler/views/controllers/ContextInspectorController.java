package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.views.ContextInspector;

@UIViewController(value = UIReactor.Type.ContextInspector, viewType = ContextInspector.class)
public interface ContextInspectorController extends ViewController<ContextInspector> {
}
