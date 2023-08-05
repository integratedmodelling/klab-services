package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.groovy.util.Maps;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimInstance;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimScope;
import org.integratedmodelling.klab.api.lang.kim.KimStatement;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.resolver.Resolution.ResolutionType;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.knowledge.InstanceImpl;
import org.integratedmodelling.klab.knowledge.ModelImpl;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resolver.dataflow.DataflowImpl;
import org.integratedmodelling.klab.utils.Parameters;
import org.springframework.beans.factory.annotation.Autowired;

public class ResolverService extends BaseService implements Resolver {

    /**
     * TODO this should be modifiable at the scope level
     */
    private static double MINIMUM_WORTHWHILE_CONTRIBUTION = 0.15;

    /*
     * The knowledge repository. Models and instances are built and kept in the resolver upon input
     * from the resource services. For now we keep everything in memory.
     * 
     * Version of the latest loaded object is kept for everything, including namespaces
     */
    Map<String, Version> urnToVersion = Collections.synchronizedMap(new HashMap<>());
    Map<String, Model> models = Collections.synchronizedMap(new HashMap<>());
    Map<String, Instance> instances = Collections.synchronizedMap(new HashMap<>());
    Parameters<String> defines = Parameters.createSynchronized();

    @Autowired
    public ResolverService(Authentication authentication, ServiceScope scope) {
        super(scope);
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

    /**
     * Top-level resolution, resolve and return an independent resolution graph. This creates a new
     * resolution graph which will contain any observations that were already resolved within the
     * context observation in the scope, if any.
     * 
     * FIXME this should not take mergeInto but create it and return it, handling the various cases
     * (models etc.) as additional resolutions merged into it. So if Instance, make it then resolve
     * its observable and merge; if model, make it for the observable and merge the resolution of
     * the model. The first condition should be in each observeXxxx method.
     * 
     * @param knowledge
     * @param scale
     * @return
     */
    public Resolution resolve(Knowledge knowledge, ContextScope scope) {

        Observable observable = null;
        Scale scale = scope.getGeometry();

        switch(Knowledge.classify(knowledge)) {
        case CONCEPT:
            observable = Observable.promote((Concept) knowledge);
            break;
        case INSTANCE:
            observable = ((Instance) knowledge).getObservable();
            scale = ((Instance) knowledge).getScale();
            scope = scope.withResolutionNamespace(((Instance) knowledge).getNamespace());
            break;
        case MODEL:
            observable = ((Model) knowledge).getObservables().get(0);
            break;
        case OBSERVABLE:
            observable = (Observable) knowledge;
            break;
        case RESOURCE:
            // break; for now just refuse it
        default:
            // throw new KIllegalStateException("knowledge " + knowledge + " is not
            // resolvable");
            break;
        }

        if (scale == null || scale.isEmpty()) {
            throw new KIllegalStateException("cannot resolve " + knowledge + " without a focal scale in the context");
        } else if (observable == null) {
            throw new KIllegalStateException("cannot establish an observable to resolve for " + knowledge);
        } else if (!(knowledge instanceof Instance) && !observable.getDescriptionType().isInstantiation()
                && scope.getContextObservation() == null) {
            throw new KIllegalStateException(
                    "cannot resolve the non-autonomous observable " + knowledge + " without a context observation");
        }

        ResolutionImpl ret = new ResolutionImpl(observable, scale, scope);
        if (knowledge instanceof Model) {
            resolveModel((Model) knowledge, scale, scope.withResolutionNamespace(((Model) knowledge).getNamespace()), ret);
        } else {
            resolveObservable(observable, scale, scope, ret, null);
        }

        return ret;

    }

    /**
     * We always resolve an observable first. If resolving a model, create observable graph and
     * resolve the model directly without further check.
     * 
     * @param observable
     * @param parent
     * @return
     */
    Coverage resolveObservable(Observable observable, Scale scale, ContextScope scope, ResolutionImpl parent, Model parentModel) {

        /**
         * Make graph merging parent Set coverage to scale, 0; Strategies/models: foreach model:
         * resolve to new graph for same observable and add coverage; merge(union) if gain is
         * significant break when models are finished or coverage is complete if graph.coverage is
         * sufficient, merge into parent at parent model (root if null) return coverage is
         * sufficient
         */
        Coverage ret = Coverage.create(scale, 0.0);

        // infinite recursion is nice but wastes time
        if (parent.checkResolving(observable)) {
            return Coverage.universal();
        }

        // done already, nothing to do here
        if (parent.getResolved(observable) != null) {
            return Coverage.universal();
        }

        // see what the reasoner thinks of this observable
        for (ObservationStrategy strategy : scope.getService(Reasoner.class).inferStrategies(observable, scope)) {
            switch(strategy.getType()) {
            case DEREIFYING:
                // resolve the deferral, then merge with deferral
                break;
            case DIRECT:
                for (Model model : queryModels(observable, scope)) {
                    ResolutionImpl resolution = resolveModel(model, scale, scope.withResolutionNamespace(model.getNamespace()),
                            parent);
                    ret = ret.merge(resolution.getCoverage(), LogicalConnector.UNION);
                    if (ret.getGain() < MINIMUM_WORTHWHILE_CONTRIBUTION) {
                        continue;
                    }
                    parent.merge(model, parentModel, ret, observable, ResolutionType.DIRECT);
                    if (parent.getCoverage().isComplete()) {
                        break;
                    }
                }
            case RESOLVED:
                break;
            }
        }

        return ret;
    }

    /**
     * Parent is for the observable, model gets added if it contributes, then its dependencies
     * 
     * @param model
     * @param scale
     * @param scope
     * @param parent
     * @return
     */
    ResolutionImpl resolveModel(Model model, Scale scale, ContextScope scope, ResolutionImpl parent) {

        ResolutionImpl ret = new ResolutionImpl(scope, parent);
        Coverage coverage = Coverage.create(scale, 1.0);
        if (!model.getCoverage().isEmpty()) {
            coverage = coverage.merge(model.getCoverage(), LogicalConnector.INTERSECTION);
        }
        for (Observable dependency : model.getDependencies()) {
            Coverage depcoverage = resolveObservable(dependency, scale, scope, parent, model);
            coverage = coverage.merge(depcoverage, LogicalConnector.INTERSECTION);
            if (coverage.isEmpty()) {
                break;
            }
        }
        return ret.withCoverage(coverage);
    }

    @Override
    public Dataflow<Observation> compile(Knowledge knowledge, Resolution resolution, ContextScope scope) {
        DataflowImpl ret = new DataflowImpl();
        // TODO
        return ret;
    }
    @Override
    public <T extends Knowledge> T resolveKnowledge(String urn, Class<T> knowledgeClass, Scope scope) {

        Knowledge ret = null;

        var resources = scope.getService(ResourcesService.class);
        var reasoner = scope.getService(Reasoner.class);

        switch(Urn.classify(urn)) {
        case KIM_OBJECT:
            ResourceSet set = resources.resolve(urn, scope);
            if (set.getResults().size() == 1) {
                ret = loadKnowledge(set, scope);
            }
            break;
        case OBSERVABLE:
            ret = reasoner.resolveObservable(urn);
            break;
        case RESOURCE:
            var resource = resources.resolveResource(urn, scope);
            // TODO make a KimModelStatement that observes this.
            break;
        case REMOTE_URL:
        case UNKNOWN:
            scope.error("cannot resolve URN '" + urn + "' to observable knowledge");
            break;
        }
        return (T) ret;
    }

    /**
     * Return the first resource in results, or null.
     * 
     * @param set
     * @param scope
     * @return
     */
    private Knowledge loadKnowledge(ResourceSet set, Scope scope) {
        List<Knowledge> result = loadResourceSet(set, scope);
        return result.size() > 0 ? result.get(0) : null;
    }

    /**
     * Load all the knowledge in the set from the respective services in scope, including resolving
     * components if any.
     * 
     * @param set
     * @param scope
     * @return
     */
    private List<Knowledge> loadResourceSet(ResourceSet set, Scope scope) {
        List<Knowledge> ret = new ArrayList<>();
        for (Resource namespace : set.getNamespaces()) {
            loadNamespace(namespace, scope);
        }
        for (Resource result : set.getResults()) {
            switch(result.getKnowledgeClass()) {
            case APPLICATION:
                break;
            case BEHAVIOR:
                break;
            case COMPONENT:
                break;
            case CONCEPT:
                break;
            case INSTANCE:
                Instance instance = instances.get(result.getResourceUrn());
                if (instance != null) {
                    ret.add(instance);
                }
                break;
            case MODEL:
                Model model = models.get(result.getResourceUrn());
                if (model != null) {
                    ret.add(model);
                }
                break;
            case NAMESPACE:
                break;
            case OBSERVABLE:
                break;
            case PROJECT:
                break;
            case RESOURCE:
                break;
            case SCRIPT:
                break;
            case TESTCASE:
                break;
            default:
                break;

            }
        }
        return ret;
    }

    private void loadNamespace(Resource namespaceResource, Scope scope) {
        Version existing = urnToVersion.get(namespaceResource.getResourceUrn());
        if (existing != null && existing.compatible(namespaceResource.getResourceVersion())) {
            return;
        }
        var resources = scope.getService(ResourcesService.class);
        var namespace = resources.resolveNamespace(namespaceResource.getResourceUrn(), scope);
        if (namespace != null) {
            for (KimStatement statement : namespace.getStatements()) {
                if (statement instanceof KimModel) {
                    Model model = loadModel((KimModel) statement, scope);
                    models.put(model.getUrn(), model);
                } else if (statement instanceof KimInstance) {
                    Instance instance = loadInstance((KimInstance) statement, scope);
                    instances.put(instance.getUrn(), instance);
                }
            }
            // TODO defines
        }
    }

    private Instance loadInstance(KimInstance statement, Scope scope) {

        var reasoner = scope.getService(Reasoner.class);

        InstanceImpl instance = new InstanceImpl();
        instance.setNamespace(statement.getNamespace());
        instance.getAnnotations().addAll(statement.getAnnotations());
        instance.setObservable(reasoner.declareObservable(statement.getObservable()));
        instance.setUrn(statement.getNamespace() + "." + statement.getName());
        instance.setMetadata(statement.getMetadata());
        instance.setScale(createScaleFromBehavior(statement.getBehavior(), scope));

        for (KimObservable state : statement.getStates()) {
            instance.getStates().add(reasoner.declareObservable(state));
        }
        for (KimScope child : statement.getChildren()) {
            instance.getChildren().add(loadInstance((KimInstance) child, scope));
        }

        return instance;
    }

    private Scale createScaleFromBehavior(KimBehavior behavior, Scope scope) {

        Scale scale = null;
        List<Extent<?>> extents = new ArrayList<>();
        var languageService = Configuration.INSTANCE.getService(Language.class);

        if (behavior != null) {
            for (ServiceCall call : behavior.getExtentFunctions()) {
                var ext = languageService.execute(call, scope, Object.class);
                if (ext instanceof Scale) {
                    scale = (Scale) ext;
                } else if (ext instanceof Geometry) {
                    scale = Scale.create((Geometry) ext);
                } else if (ext instanceof Extent) {
                    extents.add((Extent<?>) ext);
                } else {
                    throw new KIllegalStateException("the call to " + call.getName() + " did not produce a scale or an extent");
                }
            }
        }
        if (scale != null) {
            for (Extent<?> extent : extents) {
                scale = scale.mergeExtent(extent);
            }
        } else if (!extents.isEmpty()) {
            scale = Scale.create(extents);
        } else {
            scale = Scale.empty();
        }

        return scale;
    }

    private Model loadModel(KimModel statement, Scope scope) {

        var reasoner = scope.getService(Reasoner.class);

        ModelImpl model = new ModelImpl();

        model.getAnnotations().addAll(statement.getAnnotations());
        for (KimObservable observable : statement.getObservables()) {
            model.getObservables().add(reasoner.declareObservable(observable));
        }
        for (KimObservable observable : statement.getDependencies()) {
            model.getDependencies().add(reasoner.declareObservable(observable));
        }

        model.setUrn(statement.getNamespace() + "." + statement.getName());
        model.setMetadata(statement.getMetadata());
        model.getComputation().addAll(statement.getContextualization());
        model.setUrn(statement.getNamespace() + "." + statement.getName());
        model.setNamespace(statement.getNamespace());
        model.getActions().addAll(statement.getBehavior().getActions());
        model.setCoverage(createScaleFromBehavior(statement.getBehavior(), scope));

        return model;
    }

    /**
     * Query all the resource servers available in the scope to find the models that can observe the
     * passed observable. The result should be ranked in decreasing order of fit to the context and
     * the RESOLUTION_SCORE ranking should be in their metadata.
     * 
     * @param observable
     * @param scope
     * @return
     */
    @Override
    public List<Model> queryModels(Observable observable, ContextScope scope) {

        var ret = new ArrayList<Model>();
        var resources = scope.getService(ResourcesService.class);

        // TODO use and merge all available resource services
        ResourceSet models = resources.queryModels(observable, scope);
        loadResourceSet(models, scope);
        for (ResourceSet.Resource urn : models.getResults()) {
            ret.add(this.models.get(urn.getResourceUrn()));
        }

        // TODO prioritize, dioporco

        return ret;
    }

    @Override
    public void initializeService() {

        /*
         * Components
         */
        Set<String> extensionPackages = new LinkedHashSet<>();
        extensionPackages.add("org.integratedmodelling.klab.runtime");
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
    }


}
