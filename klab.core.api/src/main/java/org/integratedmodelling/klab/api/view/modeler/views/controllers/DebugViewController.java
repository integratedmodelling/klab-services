package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIViewController;
import org.integratedmodelling.klab.api.view.modeler.views.DebugView;

@UIViewController(value = UIReactor.Type.DebugView, viewType = DebugView.class)
public interface DebugViewController extends ViewController<DebugView> {
}
