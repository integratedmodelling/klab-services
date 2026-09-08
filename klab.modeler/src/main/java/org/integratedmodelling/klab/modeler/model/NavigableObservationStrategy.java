package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.lang.kim.KimLiteral;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimObservationPlan;

import java.util.List;

public class NavigableObservationStrategy extends NavigableKlabStatement<KimObservationStrategy>
    implements KimObservationStrategy {

  public NavigableObservationStrategy(KimObservationStrategy asset, NavigableKlabAsset<?> parent) {
    super(asset, parent);
  }

  @Override
  public String getDescription() {
    return delegate.getDescription();
  }

  @Override
  public int getRank() {
    return delegate.getRank();
  }

  @Override public int getModelVersion() { return delegate.getModelVersion(); }
  @Override public KimObservationPlan.Source getSource() { return delegate.getSource(); }
  @Override public KimObservationPlan.StrategySelection getSelection() { return delegate.getSelection(); }
  @Override public List<KimObservationPlan.StrategySetup> getSetup() { return delegate.getSetup(); }
  @Override public KimObservationPlan.PlanBody getPlan() { return delegate.getPlan(); }

  @Override
  public Type getType() {
    return delegate.getType();
  }
}
