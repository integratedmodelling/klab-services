package org.integratedmodelling.klab.api.view.modeler.views.controllers;

import org.integratedmodelling.klab.api.view.ViewController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;
import org.integratedmodelling.klab.api.view.modeler.views.DebugView;

@UIView(UIReactor.Type.DebugView)
public interface DebugViewController extends ViewController<DebugView> {
}
