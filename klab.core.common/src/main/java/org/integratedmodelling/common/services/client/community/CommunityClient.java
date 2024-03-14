package org.integratedmodelling.common.services.client.community;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.Community;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;

public class CommunityClient extends ServiceClient implements Community {

    public CommunityClient(Type serviceType) {
        super(serviceType);
    }

    public CommunityClient(URL url, Identity identity, List<ServiceReference> services) {
        super(Type.COMMUNITY, url, identity, services);
    }

    public CommunityClient(URL url) {
        super(url);
    }

    @Override
    public ServiceCapabilities capabilities() {
        return null;
    }

    @Override
    public String getServiceName() {
        return null;
    }
}
