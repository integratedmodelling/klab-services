package org.integratedmodelling.klab.services.reasoner;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;

public class ReasonerClient implements Reasoner {

    private static final long serialVersionUID = -3969112162251127910L;
    
    String url;
    
    public ReasonerClient(String url) {
        this.url = url;
    }

    @Override
    public String getUrl() {
        // TODO Auto-generated method stub
        return this.url;
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
    public Capabilities capabilities() {
        // TODO Auto-generated method stub
        return null;
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
    public boolean shutdown() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public List<ObservationStrategy> inferStrategies(Observable observable, ContextScope scope) {
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

}
