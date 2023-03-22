package org.integratedmodelling.klab.services.reasoner.controllers;

import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Reasoner.Capabilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReasonerController {

    @Autowired
    private Reasoner reasoner;

    /**
     * GET /capabilities
     * @return
     */
    @GetMapping(ServicesAPI.CAPABILITIES)
    public Capabilities getCapabilities() {
        return reasoner.getCapabilities();
    }

    /**
     *
     * GET /resolve/concept
     * @param definition
     * @return
     */
    @GetMapping(ServicesAPI.REASONER.RESOLVE_CONCEPT)
    public @ResponseBody Concept resolveConcept(@PathVariable String definition) {
        return reasoner.resolveConcept(definition);
    }

    /**
     * /resolve/observable
     * @param definition
     * @return
     */
    @GetMapping(ServicesAPI.REASONER.RESOLVE_OBSERVABLE)
    public @ResponseBody Observable resolveObservable(@PathVariable String definition) {
        return reasoner.resolveObservable(definition);
    }
    
    /**
     * POST /subsumes
     * @param conceptImpl
     * @param other
     * @return
     */
    public boolean subsumes(Semantics conceptImpl, Semantics other) {
        return reasoner.subsumes(conceptImpl, other);
    }

    /**
     * POST /operands
     * @param target
     * @return
     */
    public Collection<Concept> operands(Semantics target) {
        return reasoner.operands(target);
    }

    /**
     * POST /children
     * @param target
     * @return
     */
    public Collection<Concept> children(Semantics target) {
        return reasoner.children(target);
    }

    /**
     * POST /parents
     * @param target
     * @return
     */
    public Collection<Concept> parents(Semantics target) {
        return reasoner.parents(target);
    }

    /**
     * POST /parent
     * @param c
     * @return
     */
    public Concept parent(Semantics c) {
        return reasoner.parent(c);
    }

    /**
     * POST /allchildren
     * @param target
     * @return
     */
    public Collection<Concept> allChildren(Semantics target) {
        return reasoner.allChildren(target);
    }

    /**
     * POST /allparents
     * 
     * @param target
     * @return
     */
    public Collection<Concept> allParents(Semantics target) {
        return reasoner.allParents(target);
    }

    /**
     * POST /closure
     * @param target
     * @return
     */
    public Collection<Concept> closure(Semantics target) {
        return reasoner.closure(target);
    }

    /**
     * POST distance
     * @param target
     * @param other
     * @return
     */
    public int semanticDistance(Semantics target, Semantics other, Semantics context) {
        return reasoner.semanticDistance(target, other, context);
    }

    public Concept coreObservable(Semantics first) {
        return reasoner.coreObservable(first);
    }

    public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
        return reasoner.splitOperators(concept);
    }

    public int assertedDistance(Semantics from, Semantics to) {
        return reasoner.assertedDistance(from, to);
    }

    public Collection<Concept> roles(Semantics concept) {
        return reasoner.roles(concept);
    }

    public boolean hasRole(Semantics concept, Concept role) {
        return reasoner.hasRole(concept, role);
    }

    public Concept directContext(Semantics concept) {
        return reasoner.directContext(concept);
    }

    public Concept context(Semantics concept) {
        return reasoner.context(concept);
    }

    public Concept directInherent(Semantics concept) {
        return reasoner.directInherent(concept);
    }

    public Concept inherent(Semantics concept) {
        return reasoner.inherent(concept);
    }

    public Concept directGoal(Semantics concept) {
        return reasoner.directGoal(concept);
    }

    public Concept goal(Semantics concept) {
        return reasoner.goal(concept);
    }

    public Concept directCooccurrent(Semantics concept) {
        return reasoner.directCooccurrent(concept);
    }

    public Concept directCausant(Semantics concept) {
        return reasoner.directCausant(concept);
    }

    public Concept directCaused(Semantics concept) {
        return reasoner.directCaused(concept);
    }

    public Concept directAdjacent(Semantics concept) {
        return reasoner.directAdjacent(concept);
    }

    public Concept directCompresent(Semantics concept) {
        return reasoner.directCompresent(concept);
    }

    public Concept directRelativeTo(Semantics concept) {
        return reasoner.directRelativeTo(concept);
    }

    public Concept cooccurrent(Semantics concept) {
        return reasoner.cooccurrent(concept);
    }

    public Concept causant(Semantics concept) {
        return reasoner.causant(concept);
    }

    public Concept caused(Semantics concept) {
        return reasoner.caused(concept);
    }

    public Concept adjacent(Semantics concept) {
        return reasoner.adjacent(concept);
    }

    public Concept compresent(Semantics concept) {
        return reasoner.compresent(concept);
    }

    public Concept relativeTo(Semantics concept) {
        return reasoner.relativeTo(concept);
    }

    public Collection<Concept> traits(Semantics concept) {
        return reasoner.traits(concept);
    }

    public Collection<Concept> identities(Semantics concept) {
        return reasoner.identities(concept);
    }

    public Collection<Concept> attributes(Semantics concept) {
        return reasoner.attributes(concept);
    }

    public Collection<Concept> realms(Semantics concept) {
        return reasoner.realms(concept);
    }

    public Concept baseParentTrait(Semantics trait) {
        return reasoner.baseParentTrait(trait);
    }

    public Concept baseObservable(Semantics observable) {
        return reasoner.baseObservable(observable);
    }

    public Concept rawObservable(Semantics observable) {
        return reasoner.rawObservable(observable);
    }

    public boolean hasTrait(Semantics type, Concept trait) {
        return reasoner.hasTrait(type, trait);
    }

    public boolean hasDirectTrait(Semantics type, Concept trait) {
        return reasoner.hasDirectTrait(type, trait);
    }

    public boolean hasParentRole(Semantics o1, Concept t) {
        return reasoner.hasParentRole(o1, t);
    }

    public Collection<Concept> directTraits(Semantics concept) {
        return reasoner.directTraits(concept);
    }

    public Collection<Concept> directRoles(Semantics concept) {
        return reasoner.directRoles(concept);
    }

    public String displayName(Semantics semantics) {
        return reasoner.displayName(semantics);
    }

    public String displayLabel(Semantics concept) {
        return reasoner.displayLabel(concept);
    }

    public String style(Concept concept) {
        return reasoner.style(concept);
    }

    public SemanticType observableType(Semantics observable, boolean acceptTraits) {
        return reasoner.observableType(observable, acceptTraits);
    }

    public Concept relationshipSource(Semantics relationship) {
        return reasoner.relationshipSource(relationship);
    }

    public Collection<Concept> relationshipSources(Semantics relationship) {
        return reasoner.relationshipSources(relationship);
    }

    public Concept relationshipTarget(Semantics relationship) {
        return reasoner.relationshipTarget(relationship);
    }

    public Collection<Concept> relationshipTargets(Semantics relationship) {
        return reasoner.relationshipTargets(relationship);
    }

    public Concept negated(Concept concept) {
        return reasoner.negated(concept);
    }

    public boolean satisfiable(Semantics ret) {
        return reasoner.satisfiable(ret);
    }

    public Semantics domain(Semantics conceptImpl) {
        return reasoner.domain(conceptImpl);
    }

    public Collection<Concept> applicableObservables(Concept main) {
        return reasoner.applicableObservables(main);
    }

    public Concept describedType(Semantics concept) {
        return reasoner.describedType(concept);
    }

    public boolean compatible(Semantics concept, Semantics other) {
        return reasoner.compatible(concept, other);
    }

    public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
        return reasoner.contextuallyCompatible(focus, context1, context2);
    }

    public boolean occurrent(Semantics concept) {
        return reasoner.occurrent(concept);
    }

    public Concept leastGeneralCommon(Collection<Concept> cc) {
        return reasoner.leastGeneralCommon(cc);
    }

    public boolean affectedBy(Semantics affected, Semantics affecting) {
        return reasoner.affectedBy(affected, affecting);
    }

    public boolean createdBy(Semantics affected, Semantics affecting) {
        return reasoner.createdBy(affected, affecting);
    }

    public Collection<Concept> affectedOrCreated(Semantics semantics) {
        return reasoner.affectedOrCreated(semantics);
    }

    public Collection<Concept> affected(Semantics semantics) {
        return reasoner.affected(semantics);
    }

    public Collection<Concept> created(Semantics semantics) {
        return reasoner.created(semantics);
    }

    public Collection<Concept> rolesFor(Concept observable, Concept context) {
        return reasoner.rolesFor(observable, context);
    }

    public Concept impliedRole(Concept baseRole, Concept contextObservable) {
        return reasoner.impliedRole(baseRole, contextObservable);
    }

    public Collection<Concept> impliedRoles(Concept role, boolean includeRelationshipEndpoints) {
        return reasoner.impliedRoles(role, includeRelationshipEndpoints);
    }
    
    
    
}
