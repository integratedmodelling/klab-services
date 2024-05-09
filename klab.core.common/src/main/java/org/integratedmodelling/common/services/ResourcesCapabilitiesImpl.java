package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;

import java.util.*;

public class ResourcesCapabilitiesImpl implements ResourcesService.Capabilities {

    private KlabService.Type type;
    private String localName;
    private String serviceName;
    private String serviceId;
    private String serverId;
    private boolean worldviewProvider;
    private String adoptedWorldview;
    private List<String> workspaceNames = new ArrayList<>();
    private Set<CRUDOperation> permissions = EnumSet.of(CRUDOperation.READ);

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

    @Override
    public boolean isWorldviewProvider() {
        return worldviewProvider;
    }

    @Override
    public String getAdoptedWorldview() {
        return adoptedWorldview;
    }

    @Override
    public List<String> getWorkspaceNames() {
        return workspaceNames;
    }

    @Override
    public Set<CRUDOperation> getPermissions() {
        return permissions;
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

    public void setWorldviewProvider(boolean worldviewProvider) {
        this.worldviewProvider = worldviewProvider;
    }

    public void setAdoptedWorldview(String adoptedWorldview) {
        this.adoptedWorldview = adoptedWorldview;
    }

    public void setWorkspaceNames(List<String> workspaceNames) {
        this.workspaceNames = workspaceNames;
    }

    public void setPermissions(Set<CRUDOperation> permissions) {
        this.permissions = permissions;
    }
}
