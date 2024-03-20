package org.integratedmodelling.klab.modeler.views;

import org.integratedmodelling.common.view.AbstractUIView;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.api.view.modeler.panels.DistributionView;

public class DistributionViewImpl extends AbstractUIView implements DistributionView {


    public DistributionViewImpl(Modeler controller) {
        super(controller);
    }

    @Override
    public void distributionAvailable(Distribution distribution) {

    }

    @Override
    public void selectDistribution(Distribution distribution) {

    }

    @Override
    public void synchronizationStatus(Distribution distribution) {

    }
}
