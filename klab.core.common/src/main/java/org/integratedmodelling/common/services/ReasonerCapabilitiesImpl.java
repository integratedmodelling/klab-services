package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReasonerCapabilitiesImpl extends AbstractServiceCapabilities implements Reasoner.Capabilities {

    private KlabService.Type type;
    private String worldviewId;

    @Override
    public KlabService.Type getType() {
        return type;
    }

    public void setType(KlabService.Type type) {
        this.type = type;
    }

    @Override
    public String getWorldviewId() {
        return worldviewId;
    }

    public void setWorldviewId(String worldviewId) {
        this.worldviewId = worldviewId;
    }
}
