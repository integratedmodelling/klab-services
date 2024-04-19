package org.integratedmodelling.common.services.client.reasoner;

import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.rest.ServiceReference;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ReasonerClient extends ServiceClient implements Reasoner, Reasoner.Admin {

    public ReasonerClient() {
        super(Type.REASONER);
    }

    public ReasonerClient(Identity identity, List<ServiceReference> services) {
        super(Type.REASONER, identity, services);
    }

    public ReasonerClient(URL url, Identity identity, List<ServiceReference> services, BiConsumer<Channel, Message>... listeners) {
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observable resolveObservable(String definition) {
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
    public boolean subsumes(Semantics conceptImpl, Semantics other) {
        // TODO Auto-generated method stub
        return false;
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
    public Builder observableBuilder(Observable observableImpl) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept parent(Semantics c) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept compose(Collection<Concept> concepts, LogicalConnector connector) {
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
    public int semanticDistance(Semantics target, Semantics other) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int semanticDistance(Semantics target, Semantics other, Semantics context) {
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
    public int assertedDistance(Semantics from, Semantics to) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Collection<Concept> roles(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
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

    //	@Override
    //	public Concept directContext(Semantics concept) {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}

    //	@Override
    //	public Concept context(Semantics concept) {
    //		// TODO Auto-generated method stub
    //		return null;
    //	}

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
    public Collection<Concept> traits(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> identities(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> directIdentities(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> directAttributes(Semantics concept) {
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
    public Collection<Concept> directRealms(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept baseParentTrait(Semantics trait) {
        // TODO Auto-generated method stub
        return null;
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> directRoles(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
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
    public Concept negated(Concept concept) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean satisfiable(Semantics ret) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Semantics domain(Semantics conceptImpl) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> applicableObservables(Concept main) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept describedType(Semantics concept) {
        // TODO Auto-generated method stub
        return null;
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

    @Override
    public boolean hasDistributedInherency(Concept c) {
        // TODO Auto-generated method stub
        return false;
    }

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
