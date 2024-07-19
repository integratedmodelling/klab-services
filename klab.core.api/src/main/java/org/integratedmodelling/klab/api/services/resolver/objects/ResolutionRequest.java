package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;

import java.net.URL;
import java.util.*;

/**
 * TODO revise - this should be sent with the context by the runtime to the resolver, to produce a dataflow.
 */
public class ResolutionRequest {

    private URL resolverUrl;
    private String urn;
    private Parameters<String> data = Parameters.create();

    public URL getResolverUrl() {
        return resolverUrl;
    }

    public void setResolverUrl(URL resolverUrl) {
        this.resolverUrl = resolverUrl;
    }

    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public Parameters<String> getData() {
        return data;
    }

    public void setData(Parameters<String> data) {
        this.data = data;
    }
}

