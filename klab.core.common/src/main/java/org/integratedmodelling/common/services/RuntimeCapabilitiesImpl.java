package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RuntimeCapabilitiesImpl extends AbstractServiceCapabilities implements RuntimeService.Capabilities {

    private KlabService.Type type;
    private URI brokerURI;

    @Override
    public KlabService.Type getType() {
        return type;
    }

    public void setType(KlabService.Type type) {
        this.type = type;
    }

    @Override
    public URI getBrokerURI() {
        return brokerURI;
    }

    public void setBrokerURL(URI brokerURI) {
        this.brokerURI = brokerURI;
    }
}
