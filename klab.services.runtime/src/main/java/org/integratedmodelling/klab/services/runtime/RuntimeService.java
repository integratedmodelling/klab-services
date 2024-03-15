package org.integratedmodelling.klab.services.runtime;

import org.apache.groovy.util.Maps;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.services.runtime.tasks.ObservationTask;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Future;

public class RuntimeService extends BaseService
        implements
        org.integratedmodelling.klab.api.services.RuntimeService,
        org.integratedmodelling.klab.api.services.RuntimeService.Admin {

    /**
     * The runtime maintains a "digital twin" per each context ID in its purvey. The contexts must release resources in
     * the runtime when they go out of scope.
     */
    Map<String, DigitalTwin> digitalTwins = Collections.synchronizedMap(new HashMap<>());
    private String hardwareSignature = org.integratedmodelling.common.utils.Utils.Strings.hash(Utils.OS.getMACAddress());
    // TODO connect to runtime.yaml configuration
    private String serviceId;

    public RuntimeService(ServiceScope scope, ServiceStartupOptions options) {
        super(scope, Type.RUNTIME, options);
    }

    @Override
    public void initializeService() {

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceInitializing, capabilities().toString());

        /*
         * Components
         */
        Set<String> extensionPackages = new LinkedHashSet<>();
        extensionPackages.add("org.integratedmodelling.klab.runtime");
        extensionPackages.add("org.integratedmodelling.klab.runtime.temporary");
        /*
         * Check for updates, load and scan all new plug-ins, returning the main packages to scan
         */
        extensionPackages.addAll(Configuration.INSTANCE.updateAndLoadComponents("resolver"));

        /*
         * Scan all packages registered under the parent package of all k.LAB services. TODO all
         * assets from there should be given default permissions (or those encoded with their
         * annotations) that are exposed to the admin API.
         */
        for (String pack : extensionPackages) {
            Configuration.INSTANCE.scanPackage(pack, Maps.of(Library.class, Configuration.INSTANCE.LIBRARY_LOADER));
        }

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceAvailable, capabilities());

    }

    @Override
    public URL getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean shutdown() {

        serviceScope().send(Message.MessageClass.ServiceLifecycle, Message.MessageType.ServiceUnavailable, capabilities());

        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities() {

            @Override
            public Type getType() {
                return Type.RUNTIME;
            }

            @Override
            public String getLocalName() {
                return localName;
            }

            @Override
            public String getServiceName() {
                return "Runtime";
            }

            @Override
            public String getServiceId() {
                return serviceId;
            }

            @Override
            public String getServerId() {
                return hardwareSignature == null ? null : ("RUNTIME_" + hardwareSignature);
            }
        };
    }

    @Override
    public Future<Observation> run(Dataflow<Observation> dataflow, ContextScope scope) {
        return new ObservationTask(dataflow, scope, getDigitalTwin(scope), true);
    }

    @Override
    public Collection<Observation> children(ContextScope scope, Observation rootObservation) {
        var digitalTwin = getDigitalTwin(scope);
        return digitalTwin == null ? Collections.emptyList() : digitalTwin.getLogicalChildren(rootObservation);
    }

    @Override
    public Observation parent(ContextScope scope, Observation rootObservation) {
        var digitalTwin = getDigitalTwin(scope);
        return digitalTwin.getLogicalParent(rootObservation);
    }

    private DigitalTwin getDigitalTwin(ContextScope scope) {
        DigitalTwin ret = digitalTwins.get(scope.getIdentity().getId());
        if (ret == null) {
            ret = new DigitalTwin(scope);
            digitalTwins.put(scope.getIdentity().getId(), ret);
        }
        return ret;
    }

    @Override
    public Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting) {
        Map<String, String> ret = new HashMap<>();
        return ret;
    }

    /**
     * Ensure that we have the runtime support for the passed service call. If we need a component to serve it, check
     * that the scope has access to it and load it if necessary as a background process. Return all the relevant
     * notifications which will be passed to clients. If one or more error notifications are return, the service call is
     * invalid and any dataflow it is part of is in error.
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

    @Override
    public boolean releaseScope(ContextScope scope) {
        var dt = this.digitalTwins.remove(scope.getIdentity().getId());
        if (dt != null) {
            try {
                dt.close();
            } catch (IOException e) {
                throw new KlabInternalErrorException(e);
            }
        }
        return dt != null;
    }

}
