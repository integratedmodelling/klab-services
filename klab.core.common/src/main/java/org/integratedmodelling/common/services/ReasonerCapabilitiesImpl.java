package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReasonerCapabilitiesImpl implements Reasoner.Capabilities {
    private KlabService.Type type;
    private String localName;
    private String serviceName;
    private String serviceId;
    private String serverId;

    @Override
    public KlabService.Type getType() {
        return type;
    }

    @Override
    public String getLocalName() {
        return localName;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    @Override
    public String getServerId() {
        return serverId;
    }

    public void setType(KlabService.Type type) {
        this.type = type;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

}
