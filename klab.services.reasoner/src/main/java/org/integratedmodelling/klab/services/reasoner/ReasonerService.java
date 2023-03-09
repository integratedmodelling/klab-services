package org.integratedmodelling.klab.services.reasoner;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
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
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.services.reasoner.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticTranslator;
import org.integratedmodelling.klab.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

@Service
public class ReasonerService implements Reasoner, Reasoner.Admin {

    private static final long serialVersionUID = 380622027752591182L;

    private String url;

    transient private ResourceProvider resourceService;
    transient private SemanticTranslator semanticTranslator;
    transient private ReasonerConfiguration configuration;

    /**
     * Caches for concepts and observables, linked to the URI in the corresponding
     * {@link KimScope}.
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

    private String localName;

    @Autowired
    public ReasonerService(ResourceProvider resourceService, SemanticTranslator semanticTranslator) {
        this.resourceService = resourceService;
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
    public Concept defineConcept(KimConceptStatement statement) {
        return null;
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
    public Concept coreObservable(Concept first) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Pair<Concept, List<SemanticType>> splitOperators(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> traits(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int assertedDistance(Concept kConcept, Concept t) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean hasTrait(Concept concept, Concept t) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> roles(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasRole(Concept concept, Concept t) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Concept directContext(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept context(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directInherent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept inherent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directGoal(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept goal(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCooccurrent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCausant(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCaused(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directAdjacent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directCompresent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept directRelativeTo(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept cooccurrent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept causant(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept caused(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept adjacent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept compresent(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept relativeTo(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object displayLabel(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object codeName(Semantics concept) {
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
    public Collection<Concept> identities(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> attributes(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> realms(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept baseParentTrait(Concept trait) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean hasParentTrait(Concept type, Concept trait) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> directTraits(Concept concept) {
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
    public Concept relationshipSource(Concept relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> relationshipSources(Concept relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept relationshipTarget(Concept relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> relationshipTargets(Concept relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean satisfiable(Concept ret) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> applicableObservables(Concept main) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> directRoles(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean loadKnowledge(ResourceSet resources) {

        boolean ret = true;
        for (Resource namespace : resources.getNamespaces()) {
            ResourceProvider service = resources.getServices().get(namespace.getServiceId());
            KimNamespace parsed = service.resolveNamespace(namespace.getResourceUrn(),
                    /* TODO scope of owning user - should come from authentication service */ null);
            if (!parsed.isErrors()) {
                for (KimStatement statement : parsed.getStatements()) {
                    if (statement instanceof KimConceptStatement) {
                        defineConcept((KimConceptStatement)statement);
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

}
