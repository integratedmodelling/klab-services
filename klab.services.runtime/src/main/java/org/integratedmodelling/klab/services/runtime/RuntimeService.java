package org.integratedmodelling.klab.services.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.runtime.tasks.ObservationTask;

public class RuntimeService extends BaseService
        implements
            org.integratedmodelling.klab.api.services.RuntimeService,
            org.integratedmodelling.klab.api.services.RuntimeService.Admin {

    public RuntimeService(Authentication testAuthentication, ServiceScope scope) {
        // TODO Auto-generated constructor stub
        super(scope);
    }

    @Override
    public void initializeService() {
    }

    @Override
    public String getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getLocalName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean shutdown() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Capabilities capabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Future<Observation> run(Dataflow<Observation> dataflow, ContextScope scope) {
        return new ObservationTask(dataflow, scope, true);
    }

    @Override
    public Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting) {
        Map<String, String> ret = new HashMap<>();
        return ret;
    }

    /**
     * Ensure that we have the runtime support for the passed service call. If we need a component
     * to serve it, check that the scope has access to it and load it if necessary as a background
     * process. Return all the relevant notifications which will be passed to clients. If one or
     * more error notifications are return, the service call is invalid and any dataflow it is part
     * of is in error.
     * 
     * @param call
     * @param scope
     * @return any notifications. Empty mean "all OK for execution".
     */
    public Collection<Notification> validateServiceCall(ServiceCall call, Scope scope) {
        List<Notification> ret = new ArrayList<>();
        // TODO
        return ret;
    }

}
