//package org.integratedmodelling.klab.modeler.views.controllers;
//
//import org.integratedmodelling.common.view.AbstractUIViewController;
//import org.integratedmodelling.klab.api.engine.distribution.DistributionObsolete;
//import org.integratedmodelling.klab.api.view.modeler.Modeler;
//import org.integratedmodelling.klab.api.view.modeler.views.DistributionView;
//import org.integratedmodelling.klab.api.view.modeler.views.controllers.DistributionViewController;
//
//import java.util.HashSet;
//import java.util.Set;
//
//public class DistributionViewImplController extends AbstractUIViewController<DistributionView>
//    implements DistributionViewController {
//
//  Set<DistributionObsolete> distributionSet = new HashSet<>();
//  DistributionObsolete currentDistribution = null;
//
//  public DistributionViewImplController(Modeler controller) {
//    super(controller);
//  }
//
//  @Override
//  public void distributionAvailable(DistributionObsolete distribution) {
//    distributionSet.add(distribution);
//    if (currentDistribution == null) {
//      selectDistribution(distribution);
//    }
//    view().notifyDistribution(distribution);
//  }
//
//  @Override
//  public void selectDistribution(DistributionObsolete distribution) {
//    this.currentDistribution = distribution;
//    getController().dispatch(this, UIEvent.DistributionSelected, distribution);
//  }
//
//  @Override
//  public void synchronizationStatus(DistributionObsolete distribution) {}
//}
