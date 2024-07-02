package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.services.Community;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;

import java.net.URL;


public class CommunityCapabilitiesImpl extends AbstractServiceCapabilities implements Community.Capabilities {

    private KlabService.Type type;

    @Override
    public KlabService.Type getType() {
        return type;
    }

    public void setType(KlabService.Type type) {
        this.type = type;
    }
}
