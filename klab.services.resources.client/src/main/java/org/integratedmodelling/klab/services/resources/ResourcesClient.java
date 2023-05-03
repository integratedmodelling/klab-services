package org.integratedmodelling.klab.services.resources;

import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

public class ResourcesClient implements ResourceProvider {

    private static final long serialVersionUID = 4305387731730961701L;

    public ResourcesClient(String url) {
        // TODO Auto-generated constructor stub
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
    public ServiceScope scope() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean shutdown() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Capabilities getCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet projects(Collection<String> projects, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet project(String projectName, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ResourceSet model(String modelName, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KimNamespace resolveNamespace(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KActorsBehavior resolveBehavior(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Resource resolveResource(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KimObservable resolveObservable(String definition) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KimConcept resolveConcept(String definition) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Resource contextualizeResource(Resource originalResource, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KlabData contextualize(Resource contextualizedResource, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KdlDataflow resolveDataflow(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<KimNamespace> dependents(String namespaceId) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<KimNamespace> precursors(String namespaceId) {
        // TODO Auto-generated method stub
        return null;
    }

}