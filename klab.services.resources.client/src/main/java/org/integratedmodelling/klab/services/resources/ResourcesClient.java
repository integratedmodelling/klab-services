package org.integratedmodelling.klab.services.resources;

import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.data.KlabData;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kdl.KdlDataflow;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.api.utils.Utils;

import java.net.URL;
import java.util.Collection;
import java.util.List;

public class ResourcesClient extends ServiceClient implements ResourcesService {

    private static final long serialVersionUID = 4305387731730961701L;

    public ResourcesClient(URL url) {
        super(url);
    }

    @Override
    public String getLocalName() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isLocal() {
        String serverId = Utils.Strings.hash(Utils.OS.getMACAddress());
        return (capabilities().getServerId() == null && serverId == null) ||
                (capabilities().getServerId() != null && capabilities().getServerId().equals("RESOURCES_" + serverId));
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
    public KimOntology resolveOntology(String urn, Scope scope) {
        return null;
    }

    @Override
    public KimObservationStrategyDocument resolveObservationStrategyDocument(String urn, Scope scope) {
        return null;
    }

    @Override
    public Collection<Workspace> listWorkspaces() {
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
    public Workspace resolveWorkspace(String urn, Scope scope) {
        return null;
    }

    @Override
    public ResourceSet resolveServiceCall(String name, Scope scope) {
        // TODO
        return null;
    }

    @Override
    public KimObservable resolveObservable(String definition) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KimConcept.Descriptor describeConcept(String conceptUrn) {
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
    public Worldview getWorldview() {
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

    @Override
    public ResourceSet queryModels(Observable observable, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

	@Override
	public List<String> queryResources(String urnPattern, KnowledgeClass... resourceTypes) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ResourceStatus resourceStatus(String urn, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Project resolveProject(String projectName, Scope scope) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Coverage modelGeometry(String modelUrn) throws KlabIllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public KActorsBehavior readBehavior(URL url) {
		// TODO Auto-generated method stub
		return null;
	}

    @Override
    public ResourceSet resolve(String urn, Scope scope) {
        // TODO Auto-generated method stub
        return null;
    }

}
