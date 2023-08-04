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
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
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

        // ResolutionGraphImpl ret = new ResolutionGraphImpl(knowledge, scope);

        // if (ret.vertexSet().contains(knowledge)) {
        // return ret;
        // }

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
            // throw new KIllegalStateException("knowledge " + knowledge + " is not resolvable");
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
        resolveObservable(observable, scale, scope, ret, null);
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
    boolean resolveObservable(Observable observable, Scale scale, ContextScope scope, ResolutionImpl parent, Model parentModel) {

        /**
         * Parent must be not null being resolved? return false; has been resolved? return true;
         * 
         * Make graph merging parent Set coverage to scale, 0; Strategies/models: foreach model:
         * resolve to new graph for same observable and add coverage; merge(union) if gain is
         * significant break when models are finished or coverage is complete if graph.coverage is
         * sufficient, merge into parent at parent model (root if null) return coverage is
         * sufficient
         */

        // stack overflows are nice but bother users
        if (parent.isResolving(observable)) {
            return false;
        }

        // done already, nothing to do here
        if (parent.getResolved(observable) != null) {
            return true;
        }

        // see what the reasoner thinks of this observable
        for (ObservationStrategy strategy : scope.getService(Reasoner.class).inferStrategies(observable, scope)) {
            switch(strategy.getType()) {
            case DEREIFYING:
                // resolve the deferral, then merge with deferral
                break;
            case DIRECT:

                Coverage coverage = Coverage.empty();
                for (Model model : queryModels(observable, scope)) {
                    if (coverage.isEmpty()) {
                        coverage = Coverage.create(scale, 1.0);
                    }
                    ResolutionImpl resolution = resolveModel(model, scale, scope.withResolutionNamespace(model.getNamespace()),
                            parent);
                    coverage.merge(resolution.getCoverage(), LogicalConnector.INTERSECTION);
                    if (coverage.getGain() < MINIMUM_WORTHWHILE_CONTRIBUTION) {
                        continue;
                    }
                    parent.merge(model, resolution, observable, LogicalConnector.UNION, ResolutionType.DIRECT);
                    if (parent.getCoverage().isComplete()) {
                        break;
                    }
                }
                return coverage.isRelevant();
            case RESOLVED:
                break;
            }
        }

        return false;
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
        for (Observable dependency : model.getDependencies()) {
            if (!resolveObservable(dependency, scale, scope, parent, model)) {
                break;
            }
            // if (resolution.getCoverage().isRelevant() || !dependency.isOptional()) {
            // ret.merge(resolution, dependency, LogicalConnector.INTERSECTION);
        }
        return ret;
    }

    // /**
    // * Resolve a model's dependencies.
    // *
    // * @param model
    // * @param scale
    // * @param parent
    // * @return
    // */
    // private ResolutionGraphImpl resolveModel(Model model, ContextScope scope, ResolutionGraphImpl
    // parent) {
    //
    // ResolutionGraphImpl ret = new ResolutionGraphImpl(model, scope, parent);
    // if (parent.isResolving(model)) {
    // return ret.setEmpty();
    // }
    //
    // for (Observable dependency : model.getDependencies()) {
    // ResolutionGraphImpl resolution = resolveObservable(dependency, scope, parent);
    // if (resolution.getCoverage().isRelevant() || !dependency.isOptional()) {
    // ret.merge(resolution, dependency, LogicalConnector.INTERSECTION);
    // }
    // }
    // return ret;
    // }

    // /**
    // * Resolve an observable to a model. If the applicable observation strategy is a deferral,
    // leave
    // * the deferred observable for later.
    // *
    // * @param promote
    // * @param scale
    // * @param parent
    // * @return
    // */
    // private ResolutionGraphImpl resolveObservable(Observable observable, ContextScope scope,
    // ResolutionGraphImpl parent) {
    //
    // ResolutionGraphImpl ret = new ResolutionGraphImpl(observable, scope, parent);
    //
    // if (parent.isResolving(observable)) {
    // return ret.setEmpty();
    // }
    //
    // for (ObservationStrategy strategy :
    // scope.getService(Reasoner.class).inferStrategies(observable, scope)) {
    // switch(strategy.getType()) {
    // case DEREIFYING:
    // // resolve the deferral, then merge with deferral
    // break;
    // case DIRECT:
    // for (Model model : queryModels(observable, scope)) {
    // ResolutionGraphImpl modelResolution = resolveModel(model, scope, ret);
    // // Coverage contribution =
    // // ret.getCoverage().merge(modelResolution.getCoverage(),
    // // LogicalConnector.UNION);
    // // if (contribution.getGain() >= MINIMUM_WORTHWHILE_CONTRIBUTION) {
    // parent.merge(modelResolution, observable, LogicalConnector.UNION);
    // // }
    // if (parent.getCoverage().isComplete()) {
    // break;
    // }
    // }
    // break;
    // case RESOLVED:
    // break;
    // }
    // }
    //
    // return ret;
    //
    // }

    @Override
    public Dataflow<Observation> compile(Knowledge knowledge, Resolution resolution, ContextScope scope) {
        DataflowImpl ret = new DataflowImpl();
        // TODO
        return ret;
    }

    // private ResolutionImpl resolveConcrete(ResolutionImpl node) {
    //
    // // check for pre-resolved in this branch
    // ResolutionImpl previous = node.getResolution(node.observable);
    // if (previous != null) {
    // return previous;
    // }
    //
    // for (ObservationStrategy strategy :
    // node.scope().getService(Reasoner.class).inferStrategies(node.getObservable(),
    // node.scope())) {
    // switch(strategy.getType()) {
    // case DEREIFYING:
    // // resolve the deferral, then call deferring(deferredquality) on the result of
    // // node.resolve()
    // break;
    // case DIRECT:
    // for (Model model : queryModels(node.getObservable(), node.scope())) {
    // ResolutionImpl modelResolution = resolveModel(model,
    // node.newResolution(node.getObservable(), LogicalConnector.UNION));
    // if (modelResolution.isRelevant()) {
    // node.resolve(modelResolution, Resolution.Type.DIRECT);
    // }
    // if (node.getCoverage().isComplete()) {
    // break;
    // }
    // }
    // break;
    // case RESOLVED:
    // break;
    // }
    // }
    //
    // return node;
    // }
    //
    // private ResolutionImpl resolveModel(Model model, ResolutionImpl node) {
    // for (Observable observable : model.getDependencies()) {
    // ResolutionImpl dependencyResolution = resolve(node.newResolution(observable,
    // LogicalConnector.INTERSECTION));
    // if (dependencyResolution.isRelevant()) {
    // node.resolve(dependencyResolution, Resolution.Type.DIRECT);
    // } else if (!observable.isOptional()) {
    // return node;
    // }
    // }
    //
    // if (node.isRelevant()) {
    // node.accept(model);
    // }
    //
    // return node;
    // }
    //
    // private Collection<Observable> resolveAbstractPredicates(Observable observable,
    // ResolutionGraphImpl resolution) {
    //
    // Set<Observable> ret = new LinkedHashSet<>();
    //
    // var reasoner = resolution.getScope().getService(Reasoner.class);
    // var abstractTraits = reasoner.collectComponents(observable.getSemantics(),
    // EnumSet.of(SemanticType.ABSTRACT, SemanticType.TRAIT));
    //
    // if (abstractTraits.isEmpty()) {
    // ret.add(observable);
    // } else {
    // // TODO
    //
    // Map<Concept, Set<Concept>> incarnated = new LinkedHashMap<>();
    // for (Concept role : abstractTraits) {
    //
    // if (role.is(SemanticType.ROLE)) {
    // // Collection<Concept> known = scope.getRoles().get(role);
    // // if (known == null || known.isEmpty()) {
    // // continue;
    // // }
    // // incarnated.put(role, new HashSet<>(known));
    // }
    // }
    //
    // boolean done = false;
    // for (Concept predicate : abstractTraits) {
    // if (!incarnated.containsKey(predicate)) {
    // /*
    // * resolve in current scope: keep the inherency from the original concept
    // */
    // var builder = Observable.promote(predicate).builder(resolution.getScope());
    // if (reasoner.inherent(observable) != null) {
    // builder = builder.of(reasoner.inherent(observable));
    // }
    // Concept context = reasoner.context(observable);
    // if (context != null) {
    // builder = builder.within(context);
    // }
    // Observable pobs = (Observable) builder.buildObservable();
    //
    // // /*
    // // * Create a new scope to avoid leaving a trace in the main resolution trunk
    // // (the
    // // * main scope is still unresolved) but set it to the current context and model
    // // * so that scale, context and resolution namespace are there.
    // // */
    // // ResolutionScope rscope = ResolutionScope
    // // .create((Subject) scope.getContext(), scope.getMonitor(),
    // // scope.getScenarios())
    // // .withModel(scope.getModel());
    // //
    // // // this accepts empty resolutions, so check that we have values in the
    // // resulting
    // // // scope.
    // // ResolutionScope oscope = resolveConcrete(pobs, rscope,
    // // pobs.getResolvedPredicates(),
    // // pobs.getResolvedPredicatesContext(), Mode.RESOLUTION);
    // //
    // // if (oscope.getCoverage().isComplete()) {
    // //
    // // done = true;
    // //
    // // // TODO set in the contextualization strategy as "orphan" for the root
    // // // resolution dataflow if that's not yet defined.
    // // Dataflow dataflow = Dataflows.INSTANCE.compile(NameGenerator.shortUUID(),
    // // oscope, null);
    // // dataflow.setDescription("Resolution of abstract predicate " +
    // // predicate.getDefinition());
    // //
    // // scope.addPredicateResolutionDataflow(dataflow);
    // //
    // // dataflow.run(oscope.getCoverage().copy(),
    // // scope.getRootContextualizationScope());
    // //
    // // /*
    // // * Get the traits from the scope, add to set. Scope is only created if
    // // * resolution succeeds, so check.
    // // */
    // // Collection<IConcept> predicates = scope.getRootContextualizationScope() ==
    // // null ? null
    // // : scope.getRootContextualizationScope().getConcreteIdentities(predicate);
    // //
    // // if (predicates != null && !predicates.isEmpty()) {
    // // // use a stable order so that the reporting system can know when the
    // // // last one is contextualized
    // // incarnated.put(predicate, new LinkedHashSet<>(predicates));
    // // } else if (predicate.is(Type.IDENTITY)) {
    // // /*
    // // * not being able to incarnate a required identity stops resolution; not being
    // // * able to incarnate a role does not.
    // // */
    // // return null;
    // // }
    // // }
    // }
    // }
    //
    // if (done) {
    // scope.info("Abstract observable " + observable + " was resolved to:");
    // }
    //
    // List<Set<Concept>> concepts = new ArrayList<>(incarnated.values());
    // for (List<Concept> incarnation : Sets.cartesianProduct(concepts)) {
    //
    // Map<Concept, Concept> resolved = new HashMap<>();
    // int i = 0;
    // for (Concept orole : incarnated.keySet()) {
    // resolved.put(orole, incarnation.get(i++));
    // }
    // // Observable result = Observable.concretize(observable, resolved, incarnated);
    // // scope.info(" " + result);
    // // ret.add(result);
    // }
    // }
    //
    // return ret;
    // }
    //
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

    // /**
    // * Top-level 1: resolve a direct observation.
    // *
    // * @param observation
    // * @return
    // */
    // Resolution resolve(DirectObservation observation) {
    // Resolution resolution = resolve(observation.getObservable(), observation.getScale());
    // return null;
    // }
    //
    // /**
    // * Top-level 2: resolve an observable (must be instantiation) in scale.
    // *
    // * @param observable
    // * @param scale
    // * @return
    // */
    // Resolution resolve(Observable observable, Scale scale) {
    // return null;
    // }

}
