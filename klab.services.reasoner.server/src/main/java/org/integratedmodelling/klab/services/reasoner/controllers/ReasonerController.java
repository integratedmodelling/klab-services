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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;

@RestController
public class ReasonerController {

    @Autowired
    private Reasoner reasoner;

    /**
     * GET /capabilities
     * @return
     */
    @ApiOperation(value = "Obtain the reasoner service capabilities")
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
    @PostMapping(ServicesAPI.REASONER.SUBSUMES)
    public boolean subsumes(Semantics conceptImpl, Semantics other) {
        return reasoner.subsumes(conceptImpl, other);
    }

    /**
     * POST /operands
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.OPERANDS)
    public Collection<Concept> operands(Semantics target) {
        return reasoner.operands(target);
    }

    /**
     * POST /children
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.CHILDREN)
    public Collection<Concept> children(Semantics target) {
        return reasoner.children(target);
    }

    /**
     * POST /parents
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.PARENTS)
    public Collection<Concept> parents(Semantics target) {
        return reasoner.parents(target);
    }

    /**
     * POST /parent
     * @param c
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.PARENT)
    public Concept parent(Semantics c) {
        return reasoner.parent(c);
    }

    /**
     * POST /allchildren
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.ALL_CHILDREN)
    public Collection<Concept> allChildren(Semantics target) {
        return reasoner.allChildren(target);
    }

    /**
     * POST /allparents
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.ALL_PARENTS)
    public Collection<Concept> allParents(Semantics target) {
        return reasoner.allParents(target);
    }

    /**
     * POST /closure
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.CLOSURE)
    public Collection<Concept> closure(Semantics target) {
        return reasoner.closure(target);
    }


    @PostMapping(ServicesAPI.REASONER.CORE_OBSERVABLE)
    public Concept coreObservable(Semantics first) {
        return reasoner.coreObservable(first);
    }

    @PostMapping(ServicesAPI.REASONER.SPLIT_OPERATORS)
    public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
        return reasoner.splitOperators(concept);
    }

    @PostMapping(ServicesAPI.REASONER.DISTANCE)
    public int assertedDistance(Semantics[] concepts, Semantics context, boolean asserted) {
        return asserted ? reasoner.assertedDistance(concepts[0], concepts[1]) : reasoner.semanticDistance(concepts[0], concepts[1], context);
    }

    @PostMapping(ServicesAPI.REASONER.ROLES)
    public Collection<Concept> roles(Semantics concept, boolean direct) {
        return direct ? reasoner.directRoles(concept) : reasoner.roles(concept);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_ROLE)
    public boolean hasRole(Semantics concept, Concept role, boolean direct) {
        return direct ? reasoner.hasDirectRole(concept, role) : reasoner.hasRole(concept, role);
    }

    @PostMapping(ServicesAPI.REASONER.CONTEXT)
    public Concept directContext(Semantics concept, boolean direct) {
        return direct ? reasoner.directContext(concept) : reasoner.context(concept);
    }

    @PostMapping(ServicesAPI.REASONER.INHERENT)
    public Concept inherent(Semantics concept, boolean direct) {
        return direct ? reasoner.directInherent(concept) : reasoner.inherent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.GOAL)
    public Concept goal(Semantics concept, boolean direct) {
        return direct ? reasoner.directGoal(concept) : reasoner.goal(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COOCCURRENT)
    public Concept cooccurrent(Semantics concept, boolean direct) {
        return direct ? reasoner.directCooccurrent(concept) : reasoner.cooccurrent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.CAUSANT)
    public Concept causant(Semantics concept, boolean direct) {
        return direct ? reasoner.directCausant(concept) : reasoner.causant(concept);
    }

    @PostMapping(ServicesAPI.REASONER.CAUSED)
    public Concept caused(Semantics concept, boolean direct) {
        return direct ? reasoner.directCaused(concept) : reasoner.caused(concept);
    }

    @PostMapping(ServicesAPI.REASONER.ADJACENT)
    public Concept adjacent(Semantics concept, boolean direct) {
        return direct ? reasoner.directAdjacent(concept) : reasoner.adjacent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COMPRESENT)
    public Concept compresent(Semantics concept, boolean direct) {
        return direct ? reasoner.directCompresent(concept) : reasoner.compresent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIVE_TO)
    public Concept relativeTo(Semantics concept) {
        return reasoner.relativeTo(concept);
    }

    @PostMapping(ServicesAPI.REASONER.TRAITS)
    public Collection<Concept> traits(Semantics concept, boolean direct) {
        return direct ? reasoner.directTraits(concept) : reasoner.traits(concept);
    }

    @PostMapping(ServicesAPI.REASONER.IDENTITIES)
    public Collection<Concept> identities(Semantics concept, boolean direct) {
        return direct ? reasoner.directIdentities(concept) : reasoner.identities(concept);
    }

    @PostMapping(ServicesAPI.REASONER.ATTRIBUTES)
    public Collection<Concept> attributes(Semantics concept, boolean direct) {
        return direct ? reasoner.directAttributes(concept) : reasoner.attributes(concept);
    }

    @PostMapping(ServicesAPI.REASONER.REALMS)
    public Collection<Concept> realms(Semantics concept, boolean direct) {
        return direct ? reasoner.directRealms(concept) : reasoner.realms(concept);
    }

    @PostMapping(ServicesAPI.REASONER.BASE_PARENT_TRAIT)
    public Concept baseParentTrait(Semantics trait) {
        return reasoner.baseParentTrait(trait);
    }

    @PostMapping(ServicesAPI.REASONER.BASE_OBSERVABLE)
    public Concept baseObservable(Semantics observable) {
        return reasoner.baseObservable(observable);
    }

    @PostMapping(ServicesAPI.REASONER.RAW_OBSERVABLE)
    public Concept rawObservable(Semantics observable) {
        return reasoner.rawObservable(observable);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_TRAIT)
    public boolean hasTrait(Semantics type, Concept trait, boolean direct) {
        return direct ? reasoner.hasDirectTrait(type, trait) : reasoner.hasTrait(type, trait);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_PARENT_ROLE)
    public boolean hasParentRole(Semantics o1, Concept t) {
        return reasoner.hasParentRole(o1, t);
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

    @PostMapping(ServicesAPI.REASONER.SEMANTIC_TYPE)
    public SemanticType observableType(Semantics observable, boolean acceptTraits) {
        return reasoner.observableType(observable, acceptTraits);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCE)
    public Concept relationshipSource(Semantics relationship) {
        return reasoner.relationshipSource(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCES)
    public Collection<Concept> relationshipSources(Semantics relationship) {
        return reasoner.relationshipSources(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGET)
    public Concept relationshipTarget(Semantics relationship) {
        return reasoner.relationshipTarget(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGETS)
    public Collection<Concept> relationshipTargets(Semantics relationship) {
        return reasoner.relationshipTargets(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.NEGATED)
    public Concept negated(Concept concept) {
        return reasoner.negated(concept);
    }

    @PostMapping(ServicesAPI.REASONER.SATISFIABLE)
    public boolean satisfiable(Semantics ret) {
        return reasoner.satisfiable(ret);
    }

    @PostMapping(ServicesAPI.REASONER.DOMAIN)
    public Semantics domain(Semantics conceptImpl) {
        return reasoner.domain(conceptImpl);
    }

    @PostMapping(ServicesAPI.REASONER.APPLICABLE)
    public Collection<Concept> applicableObservables(Concept main) {
        return reasoner.applicableObservables(main);
    }

    @PostMapping(ServicesAPI.REASONER.DESCRIBED)
    public Concept describedType(Semantics concept) {
        return reasoner.describedType(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COMPATIBLE)
    public boolean compatible(Semantics focus, Semantics other) {
        return reasoner.compatible(focus, other);
    }
    
    @PostMapping(ServicesAPI.REASONER.CONTEXTUALLY_COMPATIBLE)
    public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
        return reasoner.contextuallyCompatible(focus, context1, context2);
    }

    @PostMapping(ServicesAPI.REASONER.OCCURRENT)
    public boolean occurrent(Semantics concept) {
        return reasoner.occurrent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.LGC)
    public Concept leastGeneralCommon(Collection<Concept> cc) {
        return reasoner.leastGeneralCommon(cc);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED_BY)
    public boolean affectedBy(Semantics affected, Semantics affecting) {
        return reasoner.affectedBy(affected, affecting);
    }

    @PostMapping(ServicesAPI.REASONER.CREATED_BY)
    public boolean createdBy(Semantics affected, Semantics affecting) {
        return reasoner.createdBy(affected, affecting);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED_OR_CREATED)
    public Collection<Concept> affectedOrCreated(Semantics semantics) {
        return reasoner.affectedOrCreated(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED)
    public Collection<Concept> affected(Semantics semantics) {
        return reasoner.affected(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.CREATED)
    public Collection<Concept> created(Semantics semantics) {
        return reasoner.created(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.ROLES_FOR)
    public Collection<Concept> rolesFor(Concept observable, Concept context) {
        return reasoner.rolesFor(observable, context);
    }

    @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLE)
    public Concept impliedRole(Concept baseRole, Concept contextObservable) {
        return reasoner.impliedRole(baseRole, contextObservable);
    }

    @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLES)
    public Collection<Concept> impliedRoles(Concept role, boolean includeRelationshipEndpoints) {
        return reasoner.impliedRoles(role, includeRelationshipEndpoints);
    }
    
    
    
}
