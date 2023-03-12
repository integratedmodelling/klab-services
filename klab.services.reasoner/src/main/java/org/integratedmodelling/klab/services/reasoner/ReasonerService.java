package org.integratedmodelling.klab.services.reasoner;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.integratedmodelling.kim.api.IKimConcept.Type;
import org.integratedmodelling.klab.Roles;
import org.integratedmodelling.klab.Traits;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.IConcept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimScope;
import org.integratedmodelling.klab.api.lang.kim.KimStatement;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.services.reasoner.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticTranslator;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.integratedmodelling.klab.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@Service
public class ReasonerService implements Reasoner, Reasoner.Admin {

    private static final long serialVersionUID = 380622027752591182L;


    /**
     * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
     * 
     * If passed to {@link #isCompatible(IConcept, IConcept, int)}, different realms will not
     * determine incompatibility.
     */
    static public final int ACCEPT_REALM_DIFFERENCES = 0x01;

    /**
     * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
     * 
     * If passed to {@link #isCompatible(IConcept, IConcept, int)}, only types that have the exact
     * same core type will be accepted.
     */
    static public final int REQUIRE_SAME_CORE_TYPE = 0x02;

    /**
     * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
     * 
     * If passed to {@link #isCompatible(IConcept, IConcept, int)}, types with roles that are more
     * general of the roles in the first concept will be accepted.
     */
    static public final int USE_ROLE_PARENT_CLOSURE = 0x04;

    /**
     * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
     * 
     * If passed to {@link #isCompatible(IConcept, IConcept, int)}, types with traits that are more
     * general of the traits in the first concept will be accepted.
     */
    static public final int USE_TRAIT_PARENT_CLOSURE = 0x08;

    /**
     * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
     * 
     * If passed to {@link #isCompatible(IConcept, IConcept, int)} causes acceptance of subjective
     * traits for observables.
     */
    static public final int ACCEPT_SUBJECTIVE_OBSERVABLES = 0x10;
    
    private String url;
    private String localName;

    transient private ResourceProvider resourceService;
    transient private Authentication authenticationService;
    transient private SemanticTranslator semanticTranslator;
    transient private ReasonerConfiguration configuration;
    transient private ServiceScope scope;

    // transient private

    /**
     * Caches for concepts and observables, linked to the URI in the corresponding {@link KimScope}.
     */
    LoadingCache<String, Concept> concepts = CacheBuilder.newBuilder()
            // .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Concept>(){
                public Concept load(String key) {
                    KimConcept parsed = resourceService.resolveConcept(key);
                    return semanticTranslator.defineConcept(parsed);
                }
            });

    LoadingCache<String, Observable> observables = CacheBuilder.newBuilder()
            // .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Observable>(){
                public Observable load(String key) { // no checked exception
                    KimObservable parsed = resourceService.resolveObservable(key);
                    return semanticTranslator.defineObservable(parsed);
                }
            });

    @Autowired
    public ReasonerService(Authentication authenticationService, ResourceProvider resourceService,
            SemanticTranslator semanticTranslator) {

        this.authenticationService = authenticationService;
        this.scope = authenticationService.authenticateService(this);

        OWL.INSTANCE.initialize(this.scope);

        this.resourceService = resourceService;
        this.semanticTranslator = semanticTranslator;

        Services.INSTANCE.setReasoner(this);
        File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "reasoner.yaml");
        if (config.exists()) {
            configuration = Utils.YAML.load(config, ReasonerConfiguration.class);
        }
    }

    private void saveConfiguration() {
        File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "reasoner.yaml");
        Utils.YAML.save(this.configuration, config);
    }

    @Override
    public Concept defineConcept(KimConceptStatement statement, Scope scope) {
        return semanticTranslator.defineConcept(statement, scope);
    }

    @Override
    public Concept resolveConcept(String definition) {
        try {
            return concepts.get(definition);
        } catch (ExecutionException e) {
            return errorConcept(definition);
        }
    }

    @Override
    public Observable resolveObservable(String definition) {
        try {
            return observables.get(definition);
        } catch (ExecutionException e) {
            return errorObservable(definition);
        }
    }

    private Observable errorObservable(String definition) {
        // TODO Auto-generated method stub
        return null;
    }

    private Concept errorConcept(String definition) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> operands(Semantics target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> children(Semantics target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> parents(Semantics target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> allChildren(Semantics target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> allParents(Semantics target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> closure(Semantics target) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int semanticDistance(Semantics target) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int semanticDistance(Semantics target, Semantics context) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Concept coreObservable(Semantics first) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> traits(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int assertedDistance(Semantics kConcept, Semantics t) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean hasTrait(Semantics concept, Concept t) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> roles(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasRole(Semantics concept, Concept t) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Concept directContext(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept context(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directInherent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept inherent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directGoal(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept goal(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCooccurrent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCausant(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCaused(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directAdjacent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCompresent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directRelativeTo(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept cooccurrent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept causant(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept caused(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept adjacent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept compresent(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept relativeTo(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object displayLabel(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String style(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Capabilities getCapabilities() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> identities(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> attributes(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> realms(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept baseParentTrait(Semantics trait) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasParentTrait(Semantics type, Concept trait) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> directTraits(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept negated(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Class<? extends Observation> observationClass(Observable observable) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SemanticType observableType(Observable observable, boolean acceptTraits) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept relationshipSource(Semantics relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> relationshipSources(Semantics relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept relationshipTarget(Semantics relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> relationshipTargets(Semantics relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean satisfiable(Semantics ret) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> applicableObservables(Concept main) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> directRoles(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean loadKnowledge(ResourceSet resources, Scope scope) {

        boolean ret = true;
        for (Resource namespace : resources.getNamespaces()) {
            ResourceProvider service = resources.getServices().get(namespace.getServiceId());
            KimNamespace parsed = service.resolveNamespace(namespace.getResourceUrn(),
                    /* TODO scope of owning user - should come from authentication service */ null);
            if (!parsed.isErrors()) {
                for (KimStatement statement : parsed.getStatements()) {
                    if (statement instanceof KimConceptStatement) {
                        defineConcept((KimConceptStatement) statement, scope);
                    } else if (statement instanceof KimSymbolDefinition) {
                        // TODO RDF but only with supporting semantic info
                    }
                }
            } else {
                ret = false;
                break;
            }
        }
        return ret;
    }

    @Override
    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    @Override
    public boolean subsumes(Semantics conceptImpl, Semantics other) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Semantics domain(Semantics conceptImpl) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept declareConcept(KimConcept conceptDeclaration) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observable declareObservable(KimObservable observableDeclaration) {
        // TODO Auto-generated method stub
        return null;
    }

    

    @Override
    public boolean compatible(Semantics o1, Semantics o2) {
        return compatible(o1, o2, 0);
    }

//    @Override
    public boolean compatible(Semantics o1, Semantics o2, int flags) {

        if (o1 == o2 || o1.equals(o2)) {
            return true;
        }

        boolean mustBeSameCoreType = (flags & REQUIRE_SAME_CORE_TYPE) != 0;
        boolean useRoleParentClosure = (flags & USE_ROLE_PARENT_CLOSURE) != 0;
        // boolean acceptRealmDifferences = (flags & ACCEPT_REALM_DIFFERENCES) != 0;

        // TODO unsupported
        boolean useTraitParentClosure = (flags & USE_TRAIT_PARENT_CLOSURE) != 0;

        if ((!o1.is(SemanticType.OBSERVABLE) || !o2.is(SemanticType.OBSERVABLE)) && !(o1.is(SemanticType.CONFIGURATION) && o2.is(SemanticType.CONFIGURATION))) {
            return false;
        }

        Concept core1 = coreObservable(o1);
        Concept core2 = coreObservable(o2);

        if (core1 == null || core2 == null || !(mustBeSameCoreType ? core1.equals(core2) : core1.is(core2))) {
            return false;
        }

        Concept cc1 = context(o1);
        Concept cc2 = context(o2);

        // candidate may have no context; if both have them, they must be compatible
        if (cc1 == null && cc2 != null) {
            return false;
        }
        if (cc1 != null && cc2 != null) {
            if (!compatible(cc1, cc2, ACCEPT_REALM_DIFFERENCES)) {
                return false;
            }
        }

        Concept ic1 = inherent(o1);
        Concept ic2 = inherent(o2);

        // same with inherency
        if (ic1 == null && ic2 != null) {
            return false;
        }
        if (ic1 != null && ic2 != null) {
            if (!compatible(ic1, ic2)) {
                return false;
            }
        }

        for (Concept t : traits(o2)) {
            boolean ok = hasTrait(o1, t);
            if (!ok && useTraitParentClosure) {
                ok = hasParentTrait(o1, t);
            }
            if (!ok) {
                return false;
            }
        }

        for (Concept t : roles(o2)) {
            boolean ok = hasRole(o1, t);
            if (!ok && useRoleParentClosure) {
                ok = hasParentRole(o1, t);
            }
            if (!ok) {
                return false;
            }
        }

        return true;
    }
    
    @Override
    public boolean hasParentRole(Semantics o1, Concept t) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
        boolean ret = compatible(context1, context2, 0);
        if (!ret && occurrent(context1)) {
            ret = affectedBy(focus, context1);
            Concept itsContext = context(context1);
            if (!ret) {
                if (itsContext != null) {
                    ret = compatible(itsContext, context2);
                }
            }
        }
        return ret;
    }

    @Override
    public boolean occurrent(Semantics context1) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean affectedBy(Semantics focus, Semantics context1) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Concept baseObservable(Semantics observable) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept rawObservable(Semantics observable) {
        // TODO Auto-generated method stub
        return null;
    }

}
