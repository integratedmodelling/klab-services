package org.integratedmodelling.klab.modeler.model;

import java.io.Serial;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

//import org.eclipse.core.resources.IContainer;
//import org.eclipse.core.resources.IFile;
//import org.eclipse.core.resources.IFolder;
//import org.eclipse.core.resources.IProject;
//import org.eclipse.core.resources.IResource;
//import org.eclipse.core.resources.IWorkspace;
//import org.eclipse.core.resources.IWorkspaceRoot;
//import org.eclipse.core.runtime.IAdaptable;
//import org.eclipse.ui.model.IWorkbenchAdapter;
//import org.eclipse.ui.model.IWorkbenchAdapter2;
//import org.eclipse.ui.model.IWorkbenchAdapter3;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableContainer;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableFolder;
//import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
//import org.integratedmodelling.klab.ide.Activator;
//import org.integratedmodelling.klab.ide.KlabAdapterFactory;
//import org.integratedmodelling.klab.ide.KlabAdapterFactory;

/**
 * Adaptable wrappers for the knowledge tree. Their equality is assessed in terms of their navigational
 * position and URN, not the contents of the delegate asset.
 *
 * @param <T>
 */
public abstract class NavigableKlabAsset<T extends KlabAsset> implements NavigableAsset {

    @Serial
    private static final long serialVersionUID = -2326835089185461220L;

    protected T delegate;
    protected NavigableAsset parent;
    protected String path;
    private List<? extends NavigableAsset> children;
    protected Metadata localMetadata = Metadata.create();

    public NavigableKlabAsset(String pathElement, NavigableKlabAsset<?> parent) {
        this.parent = parent;
        this.path = (parent == null ? "" : (parent.path + ":")) + pathElement;
        this.children = createChildren();
    }


    public NavigableKlabAsset(T asset, NavigableKlabAsset<?> parent) {
        this.delegate = asset;
        this.parent = parent;
        this.path = (parent == null ? "" : (parent.path + ":")) + asset.getUrn();
        this.children = createChildren();
    }

    protected abstract List<? extends NavigableAsset> createChildren();

    public T getDelegate() {
        return delegate;
    }

    @Override
    public Metadata getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public String getUrn() {
        return delegate.getUrn();
    }

    public NavigableContainer root() {
        NavigableAsset ret = this;
        while (((NavigableKlabAsset) ret).parent != null) {
            ret = ((NavigableKlabAsset) ret).parent;
        }
        return (NavigableContainer) ret;
    }

    public final List<? extends NavigableAsset> children() {
        return children;
    }

    protected boolean updateChild(KlabAsset asset) {
        for (var child : children()) {
            if (child instanceof NavigableKlabAsset<?> childAsset) {
                if (childAsset.is(asset)) {
                    childAsset.update(asset);
                    return true;
                } else if (childAsset.canContain(asset)) {
                    if (childAsset.updateChild(asset)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void update(KlabAsset asset) {
        this.delegate = (T) asset;
        this.children = createChildren();
    }

    protected boolean addChild(KlabAsset asset) {

        if (asset == null) {
            return false;
        }

        // TODO

        return true;
    }

    protected void removeChild(KlabAsset asset) {

    }

    protected boolean is(KlabAsset asset) {
        return this.delegate.getClass().isAssignableFrom(asset.getClass()) && delegate.getUrn().equals(asset.getUrn());
    }

    private boolean canContain(KlabAsset asset) {

        if (this instanceof NavigableFolder) {
            return true;
        }

        switch (delegate) {
            case Project project -> {
                return asset instanceof KlabDocument || asset instanceof KlabStatement;
            }
            case Workspace workspace -> {
                return asset instanceof Project || asset instanceof KlabDocument /*|| asset instanceof
                KlabStatement*/;
            }
            case Worldview worldview -> {
                return asset instanceof KimOntology;
            }
            case KlabDocument<?> document -> {
                return asset instanceof KlabStatement;
            }
            default -> {
            }
        }
        return false;
    }

    public NavigableAsset parent() {
        return parent;
    }

    @Override
    public <T extends NavigableAsset> T parent(Class<T> parentClass) {
        if (this.parent == null) {
            return null;
        }
        if (this.parent != null && parentClass.isAssignableFrom(this.parent.getClass())) {
            return (T) this.parent;
        }
        return this.parent.parent(parentClass);
    }

    @Override
    public String toString() {
        return "<" + path + ">";
    }

    public String getPath() {
        return this.path;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NavigableKlabAsset<?> other = (NavigableKlabAsset<?>) obj;
        return Objects.equals(path, other.path);
    }

    public NavigableAsset getParent() {
        return parent;
    }

    public void setParent(NavigableAsset parent) {
        this.parent = parent;
    }

    public Metadata localMetadata() {
        return localMetadata;
    }

    public void setLocalMetadata(Metadata localMetadata) {
        this.localMetadata = localMetadata;
    }

    public boolean mergeChanges(ResourceSet changes, Scope scope) {
        boolean ret = false;
        for (var change : Utils.Collections.join(changes.getOntologies(), changes.getNamespaces(),
                changes.getObservationStrategies(), changes.getBehaviors())) {
            if (applyChange(change, scope)) {
                ret = true;
            }
        }
        return ret;
    }

    private KlabAsset resolveAsset(KnowledgeClass type, String urn, ResourcesService service, Scope scope) {
        return switch (type) {
            case RESOURCE -> service.resolveResource(urn, scope);
            case NAMESPACE -> service.resolveNamespace(urn, scope);
            case BEHAVIOR, SCRIPT, TESTCASE, APPLICATION -> service.resolveBehavior(urn, scope);
            case ONTOLOGY -> service.resolveOntology(urn, scope);
            case OBSERVATION_STRATEGY_DOCUMENT -> service.resolveObservationStrategyDocument(urn, scope);
            case COMPONENT ->
                    throw new KlabUnimplementedException("resolving components within navigable assets");
            case PROJECT -> service.resolveProject(urn, scope);
            default -> throw new KlabUnimplementedException("resolving unsupported type " + type + " of " +
                    "navigable assets");
        };
    }

    private boolean applyChange(ResourceSet.Resource change, Scope scope) {

        switch (change.getOperation()) {
            case CREATE -> {
                var service = scope.getService(change.getServiceId(), ResourcesService.class);
                return addChild(resolveAsset(change.getKnowledgeClass(), change.getResourceUrn(), service,
                        scope));
            }
            case DELETE -> {
                var asset = findAsset(change.getResourceUrn(), KlabAsset.class, change.getKnowledgeClass());
                if (asset instanceof NavigableKlabAsset<?> navigableKlabAsset) {
                    var parent = navigableKlabAsset.parent;
                    navigableKlabAsset.children =
                            children.stream().filter(child -> !child.getUrn().equals(change.getResourceUrn())).toList();
                    return true;
                }
            }
            case UPDATE -> {
                var service = scope.getService(change.getServiceId(), ResourcesService.class);
                var physicalChanges = updateChild(resolveAsset(change.getKnowledgeClass(),
                        change.getResourceUrn(), service,
                        scope));
                var asset = findAsset(change.getResourceUrn(),  NavigableKlabDocument.class, change.getKnowledgeClass());
                var metadataChanges = asset != null && asset.mergeMetadata(change.getMetadata(),
                        change.getNotifications());
                return physicalChanges || metadataChanges;
            }
            case UPDATE_METADATA -> {
                var asset = findAsset(change.getResourceUrn(), NavigableKlabDocument.class, change.getKnowledgeClass());
                return asset.mergeMetadata(change.getMetadata(), change.getNotifications());
            }
        }

        return false;
    }

    @Override
    public <T extends KlabAsset> T findAsset(String resourceUrn,
                                             Class<T> assetClass, KnowledgeClass... assetType) {

        if (assetType == null || assetType.length == 0) {
            return null;
        }

        var match = EnumSet.noneOf(KnowledgeClass.class);
        match.addAll(Arrays.asList(assetType));

        // breadth-first as we normally would use this for documents
        for (var child : this.children) {
            if (match.contains(KlabAsset.classify(child)) && resourceUrn.equals(child.getUrn())) {
                return (T) child;
            }
        }

        for (var child : this.children) {
            var ret = child.findAsset(resourceUrn, assetClass, assetType);
            if (ret != null) {
                return ret;
            }
        }

        return null;
    }

}