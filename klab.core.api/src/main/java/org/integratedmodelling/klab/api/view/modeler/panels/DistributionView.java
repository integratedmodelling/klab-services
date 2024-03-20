package org.integratedmodelling.klab.api.view.modeler.panels;

import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.View;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.annotations.UIEventHandler;
import org.integratedmodelling.klab.api.view.annotations.UIView;

/**
 * Interact with and show the status of the current software distribution, if any. If a distribution is
 * available (even if not present on the filesystem) this panel should make it possible to download it. Once a
 * distribution is loaded (selected), it should expose synchronize buttons and the like at the UI's
 * discretion. The main user event is {@link #selectDistribution(Distribution)} which
 */
@UIView(value = UIReactor.Type.DistributionView, target = Distribution.class)
public interface DistributionView extends View {

    /**
     * Set up the UI for the passed distribution. If we have one already and this is different, make it
     * possible to choose.
     *
     * @param distribution
     */
    @UIEventHandler
    void distributionAvailable(Distribution distribution);

    /**
     * Call at least once with the first distribution that becomes available (or the configured one if so).
     * This is the function that should trigger use of the distribution by the engine. Even if the
     * distribution is being used, selecting this again would reset the local services and restart, for
     * example after an update.
     *
     * @param distribution a valid distribution
     */
    @UIActionHandler(value = UIAction.SelectDistribution)
    void selectDistribution(Distribution distribution);

    @UIEventHandler
    void synchronizationStatus(Distribution distribution /* TODO needs the status */);
}
