package org.integratedmodelling.common.services.client.reasoner;

import com.google.common.collect.Lists;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * TODO the reasoner client should cache a configurable amount of info so to minimize the likely frequent
 * back-and-forth with the server. Best candidates are the RESOLVE/DECLARE endpoints and the COMPATIBLE
 * ones. We could also compile full info at x-level inheritance about each concept/observable and cache
 * that instead of asking for frequent atomic ops, maybe even based on frequency and/or memory available to
 * the client.
 */
public class ReasonerClient extends ServiceClient implements Reasoner, Reasoner.Admin {


    public ReasonerClient() {
        super(Type.REASONER);
    }

    public ReasonerClient(URL url, Identity identity) {
        super(Type.REASONER, url, identity, List.of());
    }

//    public ReasonerClient(Identity identity, List<ServiceReference> services) {
//        super(Type.REASONER, identity, services);
//    }

    public ReasonerClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.REASONER, url, identity, services, listeners);
    }

    public ReasonerClient(URL url) {
        super(url);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, ReasonerCapabilitiesImpl.class);
    }

    @Override
    public Concept resolveConcept(String definition) {
        return client.get(ServicesAPI.REASONER.RESOLVE_CONCEPT, Concept.class, "definition", definition);
    }

    @Override
    public Observable resolveObservable(String definition) {
        return client.get(ServicesAPI.REASONER.RESOLVE_OBSERVABLE, Observable.class, "definition",
                definition);
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
    public boolean subsumes(Semantics conceptImpl, Semantics other) {
        return client.post(ServicesAPI.REASONER.SUBSUMES, List.of(conceptImpl.asConcept(),
                other.asConcept()), Boolean.class);
    }

    @Override
    public Collection<Concept> operands(Semantics target) {
        return client.postCollection(ServicesAPI.REASONER.OPERANDS, target.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> children(Semantics target) {
        return client.postCollection(ServicesAPI.REASONER.CHILDREN, target.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> parents(Semantics target) {
        return client.postCollection(ServicesAPI.REASONER.PARENTS, target.asConcept(), Concept.class);
    }

    @Override
    public Builder observableBuilder(Observable observableImpl) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept parent(Semantics target) {
        return client.post(ServicesAPI.REASONER.PARENTS, target.asConcept(), Concept.class);
    }

    @Override
    public Concept compose(Collection<Concept> concepts, LogicalConnector connector) {
        return null;
    }

    @Override
    public Collection<Concept> allChildren(Semantics target) {
        return client.postCollection(ServicesAPI.REASONER.ALL_CHILDREN, target.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> allParents(Semantics target) {
        return client.postCollection(ServicesAPI.REASONER.ALL_PARENTS, target.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> closure(Semantics target) {
        return client.postCollection(ServicesAPI.REASONER.CLOSURE, target.asConcept(), Concept.class);
    }

    @Override
    public int semanticDistance(Semantics target, Semantics other) {
        return client.post(ServicesAPI.REASONER.DISTANCE, Lists.newArrayList(target.asConcept(),
                other.asConcept(), null), Integer.class);
    }

    @Override
    public int semanticDistance(Semantics target, Semantics other, Semantics context) {
        return client.post(ServicesAPI.REASONER.DISTANCE, Lists.newArrayList(target.asConcept(),
                other.asConcept(), context.asConcept()), Integer.class);
    }

    @Override
    public Concept coreObservable(Semantics target) {
        return client.post(ServicesAPI.REASONER.CORE_OBSERVABLE, target.asConcept(), Concept.class);
    }

    @Override
    public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int assertedDistance(Semantics from, Semantics to) {
        // TODO
        return 0;
    }

    @Override
    public Collection<Concept> roles(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.ROLES, concept.asConcept(), Concept.class);
    }

    @Override
    public boolean hasRole(Semantics concept, Concept role) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean hasDirectRole(Semantics concept, Concept role) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Concept directInherent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.INHERENT, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept inherent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.INHERENT, concept.asConcept(), Concept.class);
    }

    @Override
    public Concept directGoal(Semantics concept) {
        return client.post(ServicesAPI.REASONER.GOAL, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept goal(Semantics concept) {
        return client.post(ServicesAPI.REASONER.GOAL, concept.asConcept(), Concept.class);
    }

    @Override
    public Concept directCooccurrent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.COOCCURRENT, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept directCausant(Semantics concept) {
        return client.post(ServicesAPI.REASONER.CAUSANT, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept directCaused(Semantics concept) {
        return client.post(ServicesAPI.REASONER.CAUSED, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept directAdjacent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.ADJACENT, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept directCompresent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.COMPRESENT, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept directRelativeTo(Semantics concept) {
        return client.post(ServicesAPI.REASONER.RELATIVE_TO, concept.asConcept(), Concept.class, "direct",
                "true");
    }

    @Override
    public Concept cooccurrent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.COOCCURRENT, concept.asConcept(), Concept.class);
    }

    @Override
    public Concept causant(Semantics concept) {
        return client.post(ServicesAPI.REASONER.CAUSANT, concept.asConcept(), Concept.class);
    }

    @Override
    public Concept caused(Semantics concept) {
        return client.post(ServicesAPI.REASONER.CAUSED, concept.asConcept(), Concept.class);
    }

    @Override
    public Concept adjacent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.ADJACENT, concept.asConcept(), Concept.class);
    }

    @Override
    public Concept compresent(Semantics concept) {
        return client.post(ServicesAPI.REASONER.COMPRESENT, concept.asConcept(), Concept.class);
    }

    @Override
    public Concept relativeTo(Semantics concept) {
        return client.post(ServicesAPI.REASONER.RELATIVE_TO, concept.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> traits(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.TRAITS, concept.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> identities(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.IDENTITIES, concept.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> directIdentities(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.IDENTITIES, concept.asConcept(), Concept.class,
                "direct", "true");
    }

    @Override
    public Collection<Concept> directAttributes(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.ATTRIBUTES, concept.asConcept(), Concept.class,
                "direct", "true");
    }

    @Override
    public Collection<Concept> attributes(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.ATTRIBUTES, concept.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> realms(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.REALMS, concept.asConcept(), Concept.class);
    }

    @Override
    public Collection<Concept> directRealms(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.REALMS, concept.asConcept(), Concept.class,
                "direct", "true");
    }

    @Override
    public Concept baseParentTrait(Semantics trait) {
        return client.post(ServicesAPI.REASONER.BASE_PARENT_TRAIT, trait.asConcept(), Concept.class);
    }

    @Override
    public Concept baseObservable(Semantics observable) {
        return client.post(ServicesAPI.REASONER.BASE_OBSERVABLE, observable.asConcept(), Concept.class);
    }

    @Override
    public Concept rawObservable(Semantics observable) {
        return client.post(ServicesAPI.REASONER.RAW_OBSERVABLE, observable.asConcept(), Concept.class);
    }

    @Override
    public boolean hasTrait(Semantics type, Concept trait) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean hasDirectTrait(Semantics type, Concept trait) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean hasParentRole(Semantics o1, Concept t) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> directTraits(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.TRAITS, concept.asConcept(), Concept.class,
                "direct", "true");
    }

    @Override
    public Collection<Concept> directRoles(Semantics concept) {
        return client.postCollection(ServicesAPI.REASONER.ROLES, concept.asConcept(), Concept.class,
                "direct", "true");
    }

    @Override
    public String displayName(Semantics semantics) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String displayLabel(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String style(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SemanticType observableType(Semantics observable, boolean acceptTraits) {
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
        return client.postCollection(ServicesAPI.REASONER.RELATIONSHIP_SOURCES, relationship.asConcept(),
                Concept.class);
    }

    @Override
    public Concept relationshipTarget(Semantics relationship) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> relationshipTargets(Semantics relationship) {
        return client.postCollection(ServicesAPI.REASONER.RELATIONSHIP_TARGETS, relationship.asConcept(),
                Concept.class);
    }

    @Override
    public Concept negated(Concept concept) {
        return client.post(ServicesAPI.REASONER.NEGATED, concept.asConcept(),
                Concept.class);
    }

    @Override
    public boolean satisfiable(Semantics concept) {
        return client.post(ServicesAPI.REASONER.SATISFIABLE, concept.asConcept(),
                Boolean.class);
    }

    @Override
    public Semantics domain(Semantics concept) {
        return client.post(ServicesAPI.REASONER.DOMAIN, concept.asConcept(),
                Concept.class);
    }

    @Override
    public Collection<Concept> applicableObservables(Concept concept) {
        return client.postCollection(ServicesAPI.REASONER.APPLICABLE, concept.asConcept(),
                Concept.class);
    }

    @Override
    public Concept describedType(Semantics concept) {
        return client.post(ServicesAPI.REASONER.DESCRIBED, concept.asConcept(),
                Concept.class);
    }

    @Override
    public boolean compatible(Semantics concept, Semantics other) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean occurrent(Semantics concept) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Concept leastGeneralCommon(Collection<Concept> cc) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean affectedBy(Semantics affected, Semantics affecting) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean createdBy(Semantics affected, Semantics affecting) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> affectedOrCreated(Semantics semantics) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> affected(Semantics semantics) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> created(Semantics semantics) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean match(Semantics candidate, Semantics pattern) {
        return false;
    }

    @Override
    public boolean match(Semantics candidate, Semantics pattern, Map<Concept, Concept> matches) {
        return false;
    }

    @Override
    public <T extends Semantics> T concretize(T pattern, Map<Concept, Concept> concreteConcepts) {
        return null;
    }

    @Override
    public <T extends Semantics> T concretize(T pattern, List<Concept> concreteConcepts) {
        return null;
    }

    @Override
    public Collection<Concept> rolesFor(Concept observable, Concept context) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept impliedRole(Concept baseRole, Concept contextObservable) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> impliedRoles(Concept role, boolean includeRelationshipEndpoints) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<ObservationStrategyObsolete> inferStrategies(Observable observable, ContextScope scope) {
        // TODO Auto-generated method stub
        return null;
    }

//    @Override
//    public boolean hasDistributedInherency(Concept c) {
//        // TODO Auto-generated method stub
//        return false;
//    }

    @Override
    public Collection<Concept> collectComponents(Concept concept, Collection<SemanticType> type) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept replaceComponent(Concept original, Map<Concept, Concept> replacements) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept buildConcept(ObservableBuildStrategy builder) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observable buildObservable(ObservableBuildStrategy builder) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean resolves(Semantics toResolve, Semantics candidate, Semantics context) {
        // TODO Auto-generated method stub
        return false;
    }


    @Override
    public boolean loadKnowledge(Worldview worldview) {
        return client.post(ServicesAPI.REASONER.ADMIN.LOAD_KNOWLEDGE, worldview, Boolean.class);
    }

    @Override
    public boolean updateKnowledge(ResourceSet changes) {
        return client.post(ServicesAPI.REASONER.ADMIN.UPDATE_KNOWLEDGE, changes, Boolean.class);
    }

    @Override
    public Concept defineConcept(KimConceptStatement statement) {
        return client.post(ServicesAPI.REASONER.ADMIN.DEFINE_CONCEPT, statement, Concept.class);
    }

    @Override
    public boolean exportNamespace(String namespace, File directory) {
        // TODO
        return false;
    }

}
