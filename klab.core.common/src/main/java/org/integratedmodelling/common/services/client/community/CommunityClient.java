package org.integratedmodelling.common.services.client.community;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Community;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;

public class CommunityClient extends ServiceClient implements Community {

    public CommunityClient(Type serviceType) {
        super(serviceType);
    }

    public CommunityClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.COMMUNITY, url, identity, services, listeners);
    }

    public CommunityClient(URL url) {
        super(url);
    }

    @Override
    public ServiceCapabilities capabilities(Scope scope) {
        return null;
    }

    @Override
    public String getServiceName() {
        return null;
    }
}
