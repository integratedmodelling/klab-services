package org.integratedmodelling.klab.api.modeler.views;

import org.integratedmodelling.klab.api.modeler.ModelerView;
import org.integratedmodelling.klab.api.modeler.UIReactor;
import org.integratedmodelling.klab.api.modeler.annotations.UIView;

@UIView(UIReactor.Type.DebugView)
public interface DebugView extends ModelerView {
}
