package org.integratedmodelling.klab.modeler.views.controllers;

import org.integratedmodelling.common.view.AbstractUIViewController;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.view.modeler.Modeler;
import org.integratedmodelling.klab.api.view.modeler.panels.DistributionView;
import org.integratedmodelling.klab.api.view.modeler.panels.DistributionViewController;

import java.util.HashSet;
import java.util.Set;

public class DistributionViewImplController extends AbstractUIViewController<DistributionView> implements DistributionViewController {


    Set<Distribution> distributionSet = new HashSet<>();
    Distribution currentDistribution = null;

    public DistributionViewImplController(Modeler controller) {
        super(controller);
    }

    @Override
    public void distributionAvailable(Distribution distribution) {
        distributionSet.add(distribution);
        if (currentDistribution == null) {
            selectDistribution(distribution);
        }
    }

    @Override
    public void selectDistribution(Distribution distribution) {
        this.currentDistribution = distribution;
        getController().dispatch(this, UIEvent.DistributionSelected, distribution);
    }

    @Override
    public void synchronizationStatus(Distribution distribution) {

    }
}
