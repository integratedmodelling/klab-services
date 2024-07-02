package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.net.URL;
import java.util.*;

public class ResourcesCapabilitiesImpl extends AbstractServiceCapabilities implements ResourcesService.Capabilities {

    private KlabService.Type type;
    private boolean worldviewProvider;
    private String adoptedWorldview;
    private List<String> workspaceNames = new ArrayList<>();
    private Set<CRUDOperation> permissions = EnumSet.of(CRUDOperation.READ);
    private List<Notification> serviceNotifications = new ArrayList<>();

    @Override
    public KlabService.Type getType() {
        return type;
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

    public void setWorldviewProvider(boolean worldviewProvider) {
        this.worldviewProvider = worldviewProvider;
    }

    public List<Notification> getServiceNotifications() {
        return serviceNotifications;
    }

    public void setServiceNotifications(List<Notification> serviceNotifications) {
        this.serviceNotifications = serviceNotifications;
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
