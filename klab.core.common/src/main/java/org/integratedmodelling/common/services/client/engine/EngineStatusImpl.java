package org.integratedmodelling.common.services.client.engine;

import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.services.KlabService;

import java.util.HashMap;
import java.util.Map;

public class EngineStatusImpl implements Engine.Status {

    private Map<KlabService.Type, KlabService.ServiceStatus> serviceStatusMap = new HashMap<>();
    private boolean operational;

    @Override
    public boolean isOperational() {
        return operational;
    }

    @Override
    public KlabService.ServiceStatus getServiceStatus(KlabService.Type serviceType) {
        // TODO return inop service status if null
        return serviceStatusMap.get(serviceType);
    }

    public static Engine.Status inop() {
        return new EngineStatusImpl();
    }

    public void setOperational(boolean operational) {
        this.operational = operational;
    }

    public Map<KlabService.Type, KlabService.ServiceStatus> getServiceStatusMap() {
        return serviceStatusMap;
    }

    public void setServiceStatusMap(Map<KlabService.Type, KlabService.ServiceStatus> serviceStatusMap) {
        this.serviceStatusMap = serviceStatusMap;
    }

    public static boolean equals(Engine.Status s1, Engine.Status s2) {
        // TODO
        return false;
    }

}
