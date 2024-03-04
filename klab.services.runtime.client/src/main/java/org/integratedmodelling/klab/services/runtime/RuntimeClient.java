package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.utils.Utils;

import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Future;

public class RuntimeClient extends ServiceClient implements RuntimeService {

    public RuntimeClient(URL url) {
        super(url);
    }


    @Override
    public String getLocalName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isLocal() {
        String serverId = Utils.Strings.hash(Utils.OS.getMACAddress());
        return (capabilities().getServerId() == null && serverId == null) ||
                (capabilities().getServerId() != null && capabilities().getServerId().equals("RUNTIME_" + serverId));
    }

    @Override
    public boolean shutdown() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean releaseScope(ContextScope scope) {
        return false;
    }

    @Override
    public Capabilities capabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Future<Observation> run(Dataflow<Observation> dataflow, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Observation> children(ContextScope scope, Observation rootObservation) {
        return null;
    }

    @Override
    public Observation parent(ContextScope scope, Observation rootObservation) {
        return null;
    }

}
