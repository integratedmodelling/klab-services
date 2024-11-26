package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.runtime.Channel;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A delegating base scope that has provisions to create embedded services for situations where the network
 * has no usable services.
 */
public abstract class AbstractServiceDelegatingScope extends AbstractDelegatingScope implements ServiceScope {

    private AtomicBoolean maintenanceMode = new AtomicBoolean(true);
    private AtomicBoolean atomicOperationMode = new AtomicBoolean(false);
    private Locality locality = Locality.EMBEDDED;

    public AbstractServiceDelegatingScope(Channel messageBus) {
        super(messageBus);
    }

    @Override
    public Locality getLocality() {
        return this.locality;
    }

    @Override
    public boolean isAvailable() {
        return !maintenanceMode.get();
    }

    @Override
    public boolean isBusy() {
        return atomicOperationMode.get();
    }

    public void setLocality(Locality locality) {
        this.locality = locality;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode.set(maintenanceMode);
    }

    public void setAtomicOperationMode(boolean atomicOperationMode) {
        this.atomicOperationMode.set(atomicOperationMode);
    }

}
