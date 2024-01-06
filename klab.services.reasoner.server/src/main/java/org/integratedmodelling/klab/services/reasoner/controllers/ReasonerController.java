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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;

@RestController
public class ReasonerController {

    @Autowired
    private Reasoner reasoner;

    /**
     *
     * GET /resolve/concept
     * 
     * @param definition
     * @return
     */
    @GetMapping(ServicesAPI.REASONER.RESOLVE_CONCEPT)
    public @ResponseBody Concept resolveConcept(@PathVariable String definition) {
        return reasoner.resolveConcept(definition);
    }

    /**
     * /resolve/observable
     * 
     * @param definition
     * @return
     */
    @GetMapping(ServicesAPI.REASONER.RESOLVE_OBSERVABLE)
    public @ResponseBody Observable resolveObservable(@PathVariable String definition) {
        return reasoner.resolveObservable(definition);
    }

    /**
     * POST /subsumes
     * 
     * @param conceptImpl
     * @param other
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.SUBSUMES)
    public boolean subsumes(@RequestBody Concept[] concepts) {
        return reasoner.subsumes(concepts[0], concepts[1]);
    }

    /**
     * POST /operands
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.OPERANDS)
    public @ResponseBody Collection<Concept> operands(@RequestBody Concept target) {
        return reasoner.operands(target);
    }

    /**
     * POST /children
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.CHILDREN)
    public @ResponseBody Collection<Concept> children(@RequestBody Concept target) {
        return reasoner.children(target);
    }

    /**
     * POST /parents
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.PARENTS)
    public @ResponseBody Collection<Concept> parents(@RequestBody Concept target) {
        return reasoner.parents(target);
    }

    /**
     * POST /parent
     * 
     * @param c
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.PARENT)
    public @ResponseBody Concept parent(@RequestBody Concept c) {
        return reasoner.parent(c);
    }

    /**
     * POST /allchildren
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.ALL_CHILDREN)
    public @ResponseBody Collection<Concept> allChildren(@RequestBody Concept target) {
        return reasoner.allChildren(target);
    }

    /**
     * POST /allparents
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.ALL_PARENTS)
    public @ResponseBody Collection<Concept> allParents(@RequestBody Concept target) {
        return reasoner.allParents(target);
    }

    /**
     * POST /closure
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.CLOSURE)
    public @ResponseBody Collection<Concept> closure(@RequestBody Concept target) {
        return reasoner.closure(target);
    }

    @PostMapping(ServicesAPI.REASONER.CORE_OBSERVABLE)
    public @ResponseBody Concept coreObservable(@RequestBody Concept first) {
        return reasoner.coreObservable(first);
    }

    @PostMapping(ServicesAPI.REASONER.SPLIT_OPERATORS)
    public @ResponseBody Pair<Concept, List<SemanticType>> splitOperators(@RequestBody Concept concept) {
        return reasoner.splitOperators(concept);
    }

    @ApiOperation("Asserted or semantic distance between two concepts. If asserted is false (default) the asserted "
            + " distance will be returned as an integer. Otherwise, the semantic distance will be computed and "
            + "the input data array may contain a third concept to compute the distance in its context.")
    @PostMapping(ServicesAPI.REASONER.DISTANCE)
    public int assertedDistance(@RequestBody Concept[] concepts, @RequestParam(defaultValue = "false") boolean asserted) {
        return asserted
                ? reasoner.assertedDistance(concepts[0], concepts[1])
                : reasoner.semanticDistance(concepts[0], concepts[1], concepts.length == 2 ? null : concepts[2]);
    }

    @PostMapping(ServicesAPI.REASONER.ROLES)
    public @ResponseBody Collection<Concept> roles(@RequestBody Concept concept,
            @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directRoles(concept) : reasoner.roles(concept);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_ROLE)
    public boolean hasRole(@RequestBody Concept[] concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.hasDirectRole(concept[0], concept[1]) : reasoner.hasRole(concept[0], concept[1]);
    }

//    @PostMapping(ServicesAPI.REASONER.CONTEXT)
//    public @ResponseBody Concept directContext(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
//        return direct ? reasoner.directContext(concept) : reasoner.context(concept);
//    }

    @PostMapping(ServicesAPI.REASONER.INHERENT)
    public @ResponseBody Concept inherent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directInherent(concept) : reasoner.inherent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.GOAL)
    public @ResponseBody Concept goal(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directGoal(concept) : reasoner.goal(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COOCCURRENT)
    public @ResponseBody Concept cooccurrent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directCooccurrent(concept) : reasoner.cooccurrent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.CAUSANT)
    public Concept causant(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directCausant(concept) : reasoner.causant(concept);
    }

    @PostMapping(ServicesAPI.REASONER.CAUSED)
    public Concept caused(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directCaused(concept) : reasoner.caused(concept);
    }

    @PostMapping(ServicesAPI.REASONER.ADJACENT)
    public Concept adjacent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directAdjacent(concept) : reasoner.adjacent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COMPRESENT)
    public Concept compresent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directCompresent(concept) : reasoner.compresent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIVE_TO)
    public Concept relativeTo(@RequestBody Concept concept) {
        return reasoner.relativeTo(concept);
    }

    @PostMapping(ServicesAPI.REASONER.TRAITS)
    public Collection<Concept> traits(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directTraits(concept) : reasoner.traits(concept);
    }

    @PostMapping(ServicesAPI.REASONER.IDENTITIES)
    public Collection<Concept> identities(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directIdentities(concept) : reasoner.identities(concept);
    }

    @PostMapping(ServicesAPI.REASONER.ATTRIBUTES)
    public Collection<Concept> attributes(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directAttributes(concept) : reasoner.attributes(concept);
    }

    @PostMapping(ServicesAPI.REASONER.REALMS)
    public Collection<Concept> realms(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.directRealms(concept) : reasoner.realms(concept);
    }

    @PostMapping(ServicesAPI.REASONER.BASE_PARENT_TRAIT)
    public Concept baseParentTrait(@RequestBody Concept trait) {
        return reasoner.baseParentTrait(trait);
    }

    @PostMapping(ServicesAPI.REASONER.BASE_OBSERVABLE)
    public Concept baseObservable(@RequestBody Concept observable) {
        return reasoner.baseObservable(observable);
    }

    @PostMapping(ServicesAPI.REASONER.RAW_OBSERVABLE)
    public Concept rawObservable(@RequestBody Concept observable) {
        return reasoner.rawObservable(observable);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_TRAIT)
    public boolean hasTrait(Semantics type, Concept trait, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.hasDirectTrait(type, trait) : reasoner.hasTrait(type, trait);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_PARENT_ROLE)
    public boolean hasParentRole(@RequestBody Concept o1, Concept t) {
        return reasoner.hasParentRole(o1, t);
    }

    public String displayName(@RequestBody Concept semantics) {
        return reasoner.displayName(semantics);
    }

    public String displayLabel(@RequestBody Concept concept) {
        return reasoner.displayLabel(concept);
    }

    public String style(@RequestBody Concept concept) {
        return reasoner.style(concept);
    }

    @PostMapping(ServicesAPI.REASONER.SEMANTIC_TYPE)
    public SemanticType observableType(@RequestBody Concept observable, @RequestParam(defaultValue = "false") boolean acceptTraits) {
        return reasoner.observableType(observable, acceptTraits);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCE)
    public Concept relationshipSource(@RequestBody Concept relationship) {
        return reasoner.relationshipSource(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCES)
    public Collection<Concept> relationshipSources(@RequestBody Concept relationship) {
        return reasoner.relationshipSources(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGET)
    public Concept relationshipTarget(@RequestBody Concept relationship) {
        return reasoner.relationshipTarget(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGETS)
    public Collection<Concept> relationshipTargets(@RequestBody Concept relationship) {
        return reasoner.relationshipTargets(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.NEGATED)
    public Concept negated(@RequestBody Concept concept) {
        return reasoner.negated(concept);
    }

    @PostMapping(ServicesAPI.REASONER.SATISFIABLE)
    public boolean satisfiable(@RequestBody Concept ret) {
        return reasoner.satisfiable(ret);
    }

    @PostMapping(ServicesAPI.REASONER.DOMAIN)
    public Semantics domain(@RequestBody Concept conceptImpl) {
        return reasoner.domain(conceptImpl);
    }

    @PostMapping(ServicesAPI.REASONER.APPLICABLE)
    public Collection<Concept> applicableObservables(@RequestBody Concept main) {
        return reasoner.applicableObservables(main);
    }

    @PostMapping(ServicesAPI.REASONER.DESCRIBED)
    public Concept describedType(@RequestBody Concept concept) {
        return reasoner.describedType(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COMPATIBLE)
    public boolean compatible(@RequestBody Concept[]args) {
        return reasoner.compatible(args[0], args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.CONTEXTUALLY_COMPATIBLE)
    public boolean contextuallyCompatible(@RequestBody Concept[] args) {
        return reasoner.contextuallyCompatible(args[0], args[1], args[2]);
    }

    @PostMapping(ServicesAPI.REASONER.OCCURRENT)
    public boolean occurrent(@RequestBody Concept concept) {
        return reasoner.occurrent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.LGC)
    public Concept leastGeneralCommon(@RequestBody Collection<Concept> cc) {
        return reasoner.leastGeneralCommon(cc);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED_BY)
    public boolean affectedBy(@RequestBody Concept[] args) {
        return reasoner.affectedBy(args[0], args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.CREATED_BY)
    public boolean createdBy(@RequestBody Concept[] args) {
        return reasoner.createdBy(args[0], args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED_OR_CREATED)
    public Collection<Concept> affectedOrCreated(@RequestBody Concept semantics) {
        return reasoner.affectedOrCreated(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED)
    public Collection<Concept> affected(@RequestBody Concept semantics) {
        return reasoner.affected(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.CREATED)
    public Collection<Concept> created(@RequestBody Concept semantics) {
        return reasoner.created(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.ROLES_FOR)
    public Collection<Concept> rolesFor(@RequestBody Concept[] args) {
        return reasoner.rolesFor(args[0], args.length == 1 ? null : args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLE)
    public Concept impliedRole(@RequestBody Concept[] args) {
        return reasoner.impliedRole(args[0], args.length == 1 ? null : args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLES)
    public @ResponseBody Collection<Concept> impliedRoles(@RequestBody Concept role,
            @RequestParam(defaultValue = "false") boolean includeRelationshipEndpoints) {
        return reasoner.impliedRoles(role, includeRelationshipEndpoints);
    }

}
