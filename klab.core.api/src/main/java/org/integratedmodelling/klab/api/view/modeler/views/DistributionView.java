package org.integratedmodelling.klab.api.view.modeler.views;

import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.view.View;

public interface DistributionView extends View {

    void notifyDistribution(Distribution distribution);
}
