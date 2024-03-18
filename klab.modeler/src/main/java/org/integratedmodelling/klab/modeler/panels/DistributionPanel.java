package org.integratedmodelling.klab.modeler.panels;

import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.view.Panel;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIView;

@UIView(UIReactor.Type.DistributionPanel)
public interface DistributionPanel extends Panel<Distribution> {


}
