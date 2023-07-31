package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.integratedmodelling.klab.api.knowledge.Semantics;
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
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.resolver.ResolutionGraph;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.knowledge.InstanceImpl;
import org.integratedmodelling.klab.knowledge.ModelImpl;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resolver.dataflow.DataflowService;
import org.integratedmodelling.klab.services.resolver.resolution.ResolutionGraphImpl;
import org.integratedmodelling.klab.services.resolver.resolution.ResolutionGraphImpl.ResolutionImpl;
import org.integratedmodelling.klab.utils.Parameters;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Sets;

public class ResolverService extends BaseService implements Resolver {

    private static final String RESOLUTION_KEY = "#_KLAB_RESOLUTION";

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

    /*
     * TODO autowire? For now only a "service" by name. Need to expose Resolution at the API level
     * for this to change.
     */
    private DataflowService dataflowService = new DataflowService();

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

    @Override
    public ResolutionGraph computeResolutionGraph(Knowledge resolvable, ContextScope scope) {

        ResolutionGraphImpl resolutionGraph = getResolution(scope);

        if (isDependent(resolvable) && scope.getResolutionObservation() == null) {
            scope.error(
                    new KIllegalStateException("cannot observe dependent knowledge without a direct observation in the scope"));
        } else {
            resolve(resolutionGraph.newResolution(resolvable, resolutionScale(resolvable, scope)));
        }
        return resolutionGraph;
    }

    @Override
    public Dataflow<Observation> computeDataflow(ResolutionGraph resolutionGraph) {
        if (resolutionGraph.getCoverage().isComplete()) {
            return dataflowService.compile(resolutionGraph);
        }
        return Dataflow.empty(Observation.class);
    }

    @Override
    public Dataflow<Observation> resolve(Knowledge resolvable, ContextScope scope) {

        ResolutionGraphImpl resolutionGraph = getResolution(scope);
        Dataflow<Observation> dataflow = scope.getDataflow();

        if (isDependent(resolvable) && scope.getResolutionObservation() == null) {
            scope.error(
                    new KIllegalStateException("cannot observe dependent knowledge without a direct observation in the scope"));
        } else {

            ResolutionImpl resolution = resolve(resolutionGraph.newResolution(resolvable, resolutionScale(resolvable, scope)));
            if (resolution.isComplete()) {
                scope.info(
                        "resolution of " + resolvable + " completed with coverage = " + resolution.getCoverage().getCoverage());
                return dataflowService.compile(resolution, dataflow);
            } else {
                scope.info("resolution of " + resolvable + " failed: coverage = " + resolution.getCoverage().getCoverage());
            }
        }

        return Dataflow.empty(Observation.class);
    }

    private Scale resolutionScale(Knowledge resolvable, ContextScope scope) {
        if (resolvable instanceof Instance) {
            return ((Instance) resolvable).getScale();
        }
        return scope.getGeometry();
    }

    private boolean isDependent(Knowledge resolvable) {
        if (resolvable instanceof Semantics) {
            return !((Semantics) resolvable).is(SemanticType.COUNTABLE);
        }
        return false;
    }

    private ResolutionGraphImpl getResolution(ContextScope scope) {
        if (!scope.getData().containsKey(RESOLUTION_KEY)) {
            scope.setData(RESOLUTION_KEY, new ResolutionGraphImpl(scope));
        }
        return scope.getData().get(RESOLUTION_KEY, ResolutionGraphImpl.class);
    }

    /**
     * Top-level: resolve the observable that's already in the node and put a model and a coverage
     * in it. Return the same node with updated data.
     * 
     * @param node
     * @return
     */
    private ResolutionImpl resolve(ResolutionImpl node) {

        for (Observable observable : resolveAbstractPredicates(node.getObservable(), node.resolutionGraph())) {
            node.resolve(resolveConcrete(node.newResolution(observable, LogicalConnector.INTERSECTION)), Resolution.Type.DIRECT);
            if (!node.getCoverage().isRelevant()) {
                break;
            }
        }

        return node;
    }

    private ResolutionImpl resolveConcrete(ResolutionImpl node) {

        // check for pre-resolved in this branch
        ResolutionImpl previous = node.getResolution(node.observable);
        if (previous != null) {
            return previous;
        }

        for (ObservationStrategy strategy : node.scope().getService(Reasoner.class).inferStrategies(node.getObservable(),
                node.scope())) {
            switch(strategy.getType()) {
            case DEREIFYING:
                // resolve the deferral, then call deferring(deferredquality) on the result of
                // node.resolve()
                break;
            case DIRECT:
                for (Model model : queryModels(node.getObservable(), node.scope())) {
                    ResolutionImpl modelResolution = resolveModel(model,
                            node.newResolution(node.getObservable(), LogicalConnector.UNION));
                    if (modelResolution.isRelevant()) {
                        node.resolve(modelResolution, Resolution.Type.DIRECT);
                    }
                    if (node.getCoverage().isComplete()) {
                        break;
                    }
                }
                break;
            case RESOLVED:
                break;
            }
        }

        return node;
    }

    private ResolutionImpl resolveModel(Model model, ResolutionImpl node) {
        for (Observable observable : model.getDependencies()) {
            ResolutionImpl dependencyResolution = resolve(node.newResolution(observable, LogicalConnector.INTERSECTION));
            if (dependencyResolution.isRelevant()) {
                node.resolve(dependencyResolution, Resolution.Type.DIRECT);
            } else if (!observable.isOptional()) {
                return node;
            }
        }

        if (node.isRelevant()) {
            node.accept(model);
        }

        return node;
    }

    private Collection<Observable> resolveAbstractPredicates(Observable observable, ResolutionGraphImpl resolution) {

        Set<Observable> ret = new LinkedHashSet<>();

        var reasoner = resolution.getScope().getService(Reasoner.class);
        var abstractTraits = reasoner.collectComponents(observable.getSemantics(),
                EnumSet.of(SemanticType.ABSTRACT, SemanticType.TRAIT));

        if (abstractTraits.isEmpty()) {
            ret.add(observable);
        } else {
            // TODO

            Map<Concept, Set<Concept>> incarnated = new LinkedHashMap<>();
            for (Concept role : abstractTraits) {

                if (role.is(SemanticType.ROLE)) {
                    // Collection<Concept> known = scope.getRoles().get(role);
                    // if (known == null || known.isEmpty()) {
                    // continue;
                    // }
                    // incarnated.put(role, new HashSet<>(known));
                }
            }

            boolean done = false;
            for (Concept predicate : abstractTraits) {
                if (!incarnated.containsKey(predicate)) {
                    /*
                     * resolve in current scope: keep the inherency from the original concept
                     */
                    var builder = Observable.promote(predicate).builder(resolution.getScope());
                    if (reasoner.inherent(observable) != null) {
                        builder = builder.of(reasoner.inherent(observable));
                    }
                    Concept context = reasoner.context(observable);
                    if (context != null) {
                        builder = builder.within(context);
                    }
                    Observable pobs = (Observable) builder.buildObservable();

                    // /*
                    // * Create a new scope to avoid leaving a trace in the main resolution trunk
                    // (the
                    // * main scope is still unresolved) but set it to the current context and model
                    // * so that scale, context and resolution namespace are there.
                    // */
                    // ResolutionScope rscope = ResolutionScope
                    // .create((Subject) scope.getContext(), scope.getMonitor(),
                    // scope.getScenarios())
                    // .withModel(scope.getModel());
                    //
                    // // this accepts empty resolutions, so check that we have values in the
                    // resulting
                    // // scope.
                    // ResolutionScope oscope = resolveConcrete(pobs, rscope,
                    // pobs.getResolvedPredicates(),
                    // pobs.getResolvedPredicatesContext(), Mode.RESOLUTION);
                    //
                    // if (oscope.getCoverage().isComplete()) {
                    //
                    // done = true;
                    //
                    // // TODO set in the contextualization strategy as "orphan" for the root
                    // // resolution dataflow if that's not yet defined.
                    // Dataflow dataflow = Dataflows.INSTANCE.compile(NameGenerator.shortUUID(),
                    // oscope, null);
                    // dataflow.setDescription("Resolution of abstract predicate " +
                    // predicate.getDefinition());
                    //
                    // scope.addPredicateResolutionDataflow(dataflow);
                    //
                    // dataflow.run(oscope.getCoverage().copy(),
                    // scope.getRootContextualizationScope());
                    //
                    // /*
                    // * Get the traits from the scope, add to set. Scope is only created if
                    // * resolution succeeds, so check.
                    // */
                    // Collection<IConcept> predicates = scope.getRootContextualizationScope() ==
                    // null ? null
                    // : scope.getRootContextualizationScope().getConcreteIdentities(predicate);
                    //
                    // if (predicates != null && !predicates.isEmpty()) {
                    // // use a stable order so that the reporting system can know when the
                    // // last one is contextualized
                    // incarnated.put(predicate, new LinkedHashSet<>(predicates));
                    // } else if (predicate.is(Type.IDENTITY)) {
                    // /*
                    // * not being able to incarnate a required identity stops resolution; not being
                    // * able to incarnate a role does not.
                    // */
                    // return null;
                    // }
                    // }
                }
            }

            if (done) {
                scope.info("Abstract observable " + observable + " was resolved to:");
            }

            List<Set<Concept>> concepts = new ArrayList<>(incarnated.values());
            for (List<Concept> incarnation : Sets.cartesianProduct(concepts)) {

                Map<Concept, Concept> resolved = new HashMap<>();
                int i = 0;
                for (Concept orole : incarnated.keySet()) {
                    resolved.put(orole, incarnation.get(i++));
                }
                // Observable result = Observable.concretize(observable, resolved, incarnated);
                // scope.info(" " + result);
                // ret.add(result);
            }
        }

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
        ModelImpl model = new ModelImpl();

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
        for (ResourceSet.Resource urn : models.getResults()) {
            ret.add(makeModel(resources.model(urn.getResourceUrn(), scope)));
        }
        return ret;
    }

    private Model makeModel(ResourceSet model) {
        ModelImpl ret = new ModelImpl();
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
