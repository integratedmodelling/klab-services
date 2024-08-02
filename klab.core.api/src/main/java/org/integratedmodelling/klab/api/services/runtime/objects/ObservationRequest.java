//package org.integratedmodelling.klab.api.services.runtime.objects;
//
//import org.integratedmodelling.klab.api.collections.Parameters;
//
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * This is sent to the runtime by a client. As a response, the runtime which creates the requested observation
// * in an unresolved state, initiates resolution on the indicated resolver with authentication and context
// * scope (including observer and any resolution options) as specified by the
// * {@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} header and the state of current context,
// * and returns the observation ID which is also the ID of the resolution task started.
// * <p>
// * The URN and data parameters are alternatives: if the URN is passed, the data are empty, otherwise the URN
// * is empty and the data specify an observation to be created and resolved. The URN (concept,  model or, in
// * developer environments, resource) must be resolvable by the runtime using the services in the request, so
// * if it's a local URN the runtime must be local, and no local services should be sent over to a remote
// * runtime. In both cases, the runtime responds with the ID of an observation and starts resolving it using
// * the specified resolver, which must authorize the same user that issued the request.
// */
//public class ObservationRequest {
//
//    private List<URL> resolverUrls = new ArrayList<>();
//    private List<URL> resourcesUrls = new ArrayList<>();
//    private String urn;
//    private Parameters<String> data = Parameters.create();
//
//    public List<URL> getResolverUrls() {
//        return resolverUrls;
//    }
//
//    public void setResolverUrls(List<URL> resolverUrls) {
//        this.resolverUrls = resolverUrls;
//    }
//
//    public List<URL> getResourcesUrls() {
//        return resourcesUrls;
//    }
//
//    public void setResourcesUrls(List<URL> resourcesUrls) {
//        this.resourcesUrls = resourcesUrls;
//    }
//
//    public String getUrn() {
//        return urn;
//    }
//
//    public void setUrn(String urn) {
//        this.urn = urn;
//    }
//
//    public Parameters<String> getData() {
//        return data;
//    }
//
//    public void setData(Parameters<String> data) {
//        this.data = data;
//    }
//}
//
