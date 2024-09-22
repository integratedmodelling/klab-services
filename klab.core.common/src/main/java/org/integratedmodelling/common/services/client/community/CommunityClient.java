package org.integratedmodelling.common.services.client.community;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Community;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;

public class CommunityClient extends ServiceClient implements Community {

    public CommunityClient(Type serviceType, Parameters<Engine.Setting> settings) {
        super(serviceType, settings);
    }

    public CommunityClient(URL url, Identity identity, List<ServiceReference> services,
                           Parameters<Engine.Setting> settings, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.COMMUNITY, url, identity, settings, services, listeners);
    }

    public CommunityClient(URL url, Parameters<Engine.Setting> settings) {
        super(url, settings);
    }

    @Override
    public ServiceCapabilities capabilities(Scope scope) {
        return null;
    }

    @Override
    public String registerSession(SessionScope sessionScope) {
        return null;
    }

    @Override
    public String registerContext(ContextScope contextScope) {
        return null;
    }

    @Override
    public String getServiceName() {
        return null;
    }
}
