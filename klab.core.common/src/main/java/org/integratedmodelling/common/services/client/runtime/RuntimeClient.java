package org.integratedmodelling.common.services.client.runtime;

import org.integratedmodelling.common.graph.Graph;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.common.services.client.GraphQLClient;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryBuilder;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextRequest;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class RuntimeClient extends ServiceClient implements RuntimeService {

    private GraphQLClient graphClient;

    public RuntimeClient() {
        super(Type.RUNTIME);
    }

    public RuntimeClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.RUNTIME, url, identity, services, listeners);
    }

    public RuntimeClient(URL url) {
        super(url);
    }

    @Override
    public boolean releaseScope(Scope scope) {
        return false;
    }

    @Override
    protected void establishConnection() {
        super.establishConnection();
        this.graphClient = new GraphQLClient(this.getUrl() + ServicesAPI.RUNTIME.DIGITAL_TWIN_GRAPH);
    }

    @Override
    public String registerSession(SessionScope scope) {
        return client.get(ServicesAPI.RUNTIME.CREATE_SESSION, String.class, "name", scope.getName());
    }

    @Override
    public String registerContext(ContextScope scope) {

        ContextRequest request = new ContextRequest();
        request.setName(scope.getName());

        var runtime = scope.getService(RuntimeService.class);

        // The runtime needs to use our resolver(s) and resource service(s), as long as they're accessible.
        // The reasoner can be the runtime's own unless we have locked worldview projects.
        for (var service : scope.getServices(ResourcesService.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResourceServices().add(serviceClient.getUrl());
                }
            }
        }
        for (var service : scope.getServices(Resolver.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResolverServices().add(serviceClient.getUrl());
                }
            }
        }

        if (isLocal() && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        return client.withScope(scope.getParentScope()).post(ServicesAPI.RUNTIME.CREATE_CONTEXT, request,
                String.class);
    }

    @Override
    public String observe(ContextScope scope, Object... resolvables) {

        String name = null;
        Geometry geometry = Geometry.EMPTY;
        Observable observable = null;
        String resourceUrn;
        String modelUrn;
        Geometry observerGeometry = null;
        String defaultValue = null;

        if (resolvables != null) {
            for (Object o : resolvables) {
                if (o instanceof Observable obs) {
                    observable = obs;
                } else if (o instanceof Geometry geom) {
                    if (geometry == null) {
                        geometry = geom;
                    } else {
                        observerGeometry = geom;
                    }
                } else if (o instanceof String string) {
                    if (name == null) {
                        name = string;
                    } else {
                        defaultValue = string;
                    }
                } else if (o instanceof Urn urn) {
                    resourceUrn = urn.getUrn();
                } else if (o instanceof KimSymbolDefinition symbol) {
                    // must be an "observation" class
                    if ("observation".equals(symbol.getDefineClass()) && symbol.getValue() instanceof Map<?,?> definition) {
//                        semantics: earth:Region
//                        space: {
//                            shape: "EPSG:4326 POLYGON((33.796 -7.086, 35.946 -7.086, 35.946 -9.41, 33.796 -9.41, 33.796 -7.086))"
//                            grid: 1.km
//                        }
//                        //	year: 2010 ...or...
//                        time: {
//                            year: 2010
//                            step: 1.day
//                        }
                        name = symbol.getName();
                        if (definition.containsKey("semantics")) {
                            observable = scope.getService(Reasoner.class).resolveObservable(definition.get("semantics").toString());
                        }
                        if (definition.containsKey("space") || definition.containsKey("time")) {
                            var geometryBuilder = Geometry.builder();
                            if (definition.containsKey("space")) {
                                var spaceBuilder = geometryBuilder.space();
                                if (definition.get("space") instanceof Map<?,?> spaceDefinition) {
                                    if (spaceDefinition.containsKey("shape")) {
                                        spaceBuilder.shape(spaceDefinition.get("shape").toString());
                                    }
                                    if (spaceDefinition.containsKey("grid")) {
                                        spaceBuilder.resolution(spaceDefinition.get("grid").toString());
                                    }
                                    // TODO add bounding box etc
                                }
                                geometryBuilder = spaceBuilder.build();
                            }
                            if (definition.containsKey("time")) {
                                var timeBuilder = geometryBuilder.time();
                                if (definition.get("time") instanceof Map<?,?> timeDefinition) {
                                    if (timeDefinition.containsKey("year")) {
                                        // TODO everything
                                    }
                                }
                                geometryBuilder = timeBuilder.build();
                            }
                            geometry = geometryBuilder.build();
                        }

                    }
                } else if (o instanceof KimModel model) {
                    // send the model URN and extract the observable
                    observable =  scope.getService(Reasoner.class).declareObservable(model.getObservables().get(0));
                    modelUrn = model.getUrn();
                }
            }
        }

        // TODO ensure we have all info to proceed

        // TODO if we have no geometry and it's a dependent, use the observer's scale if any, otherwise empty is OK

        // TODO if we have no name, use the observable

        /*
        build a mutation query and send to the digital twin endpoint of the runtime
        ObservationInput(String name, String observable, String geometry, String defaultValue, String
        observerGeometry) {
         */
        Graph.ObservationInput request = new Graph.ObservationInput(name, observable.getUrn(),
                geometry.encode(), defaultValue, observerGeometry == null ? null : observerGeometry.encode());

        return graphClient.query(Graph.Queries.OBSERVE, String.class, scope, "observation", request);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, RuntimeCapabilitiesImpl.class);
    }

}
