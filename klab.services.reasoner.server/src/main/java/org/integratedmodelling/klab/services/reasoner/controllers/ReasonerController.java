package org.integratedmodelling.klab.services.reasoner.controllers;

import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReasonerController {

    @Autowired
    private ReasonerServer reasoner;

    /**
     *
     * GET /resolve/concept
     * 
     * @param definition
     * @return
     */
    @GetMapping(ServicesAPI.REASONER.RESOLVE_CONCEPT)
    public @ResponseBody Concept resolveConcept(@PathVariable("definition") String definition) {
        return reasoner.klabService().resolveConcept(definition);
    }

    /**
     * /resolve/observable
     * 
     * @param definition
     * @return
     */
    @GetMapping(ServicesAPI.REASONER.RESOLVE_OBSERVABLE)
    public @ResponseBody Observable resolveObservable(@PathVariable("definition") String definition) {
        return reasoner.klabService().resolveObservable(definition);
    }

    /**
     * POST /subsumes
     * 
     * @param concepts
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.SUBSUMES)
    public boolean subsumes(@RequestBody Concept[] concepts) {
        return reasoner.klabService().subsumes(concepts[0], concepts[1]);
    }

    /**
     * POST /operands
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.OPERANDS)
    public @ResponseBody Collection<Concept> operands(@RequestBody Concept target) {
        return reasoner.klabService().operands(target);
    }

    /**
     * POST /children
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.CHILDREN)
    public @ResponseBody Collection<Concept> children(@RequestBody Concept target) {
        return reasoner.klabService().children(target);
    }

    /**
     * POST /parents
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.PARENTS)
    public @ResponseBody Collection<Concept> parents(@RequestBody Concept target) {
        return reasoner.klabService().parents(target);
    }

    /**
     * POST /parent
     * 
     * @param c
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.PARENT)
    public @ResponseBody Concept parent(@RequestBody Concept c) {
        return reasoner.klabService().parent(c);
    }

    /**
     * POST /allchildren
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.ALL_CHILDREN)
    public @ResponseBody Collection<Concept> allChildren(@RequestBody Concept target) {
        return reasoner.klabService().allChildren(target);
    }

    /**
     * POST /allparents
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.ALL_PARENTS)
    public @ResponseBody Collection<Concept> allParents(@RequestBody Concept target) {
        return reasoner.klabService().allParents(target);
    }

    /**
     * POST /closure
     * 
     * @param target
     * @return
     */
    @PostMapping(ServicesAPI.REASONER.CLOSURE)
    public @ResponseBody Collection<Concept> closure(@RequestBody Concept target) {
        return reasoner.klabService().closure(target);
    }

    @PostMapping(ServicesAPI.REASONER.CORE_OBSERVABLE)
    public @ResponseBody Concept coreObservable(@RequestBody Concept first) {
        return reasoner.klabService().coreObservable(first);
    }

    @PostMapping(ServicesAPI.REASONER.SPLIT_OPERATORS)
    public @ResponseBody Pair<Concept, List<SemanticType>> splitOperators(@RequestBody Concept concept) {
        return reasoner.klabService().splitOperators(concept);
    }

//    @ApiOperation("Asserted or semantic distance between two concepts. If asserted is false (default) the asserted "
//            + " distance will be returned as an integer. Otherwise, the semantic distance will be computed and "
//            + "the input data array may contain a third concept to compute the distance in its context.")
    @PostMapping(ServicesAPI.REASONER.DISTANCE)
    public int assertedDistance(@RequestBody Concept[] concepts, @RequestParam(defaultValue = "false") boolean asserted) {
        return asserted
                ? reasoner.klabService().assertedDistance(concepts[0], concepts[1])
                : reasoner.klabService().semanticDistance(concepts[0], concepts[1], concepts.length == 2 ? null : concepts[2]);
    }

    @PostMapping(ServicesAPI.REASONER.ROLES)
    public @ResponseBody Collection<Concept> roles(@RequestBody Concept concept,
            @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directRoles(concept) : reasoner.klabService().roles(concept);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_ROLE)
    public boolean hasRole(@RequestBody Concept[] concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().hasDirectRole(concept[0], concept[1]) : reasoner.klabService().hasRole(concept[0], concept[1]);
    }

//    @PostMapping(ServicesAPI.REASONER.CONTEXT)
//    public @ResponseBody Concept directContext(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
//        return direct ? reasoner.klabService().directContext(concept) : reasoner.klabService().context(concept);
//    }

    @PostMapping(ServicesAPI.REASONER.INHERENT)
    public @ResponseBody Concept inherent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directInherent(concept) : reasoner.klabService().inherent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.GOAL)
    public @ResponseBody Concept goal(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directGoal(concept) : reasoner.klabService().goal(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COOCCURRENT)
    public @ResponseBody Concept cooccurrent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directCooccurrent(concept) : reasoner.klabService().cooccurrent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.CAUSANT)
    public Concept causant(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directCausant(concept) : reasoner.klabService().causant(concept);
    }

    @PostMapping(ServicesAPI.REASONER.CAUSED)
    public Concept caused(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directCaused(concept) : reasoner.klabService().caused(concept);
    }

    @PostMapping(ServicesAPI.REASONER.ADJACENT)
    public Concept adjacent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directAdjacent(concept) : reasoner.klabService().adjacent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COMPRESENT)
    public Concept compresent(@RequestBody Concept concept, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directCompresent(concept) : reasoner.klabService().compresent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIVE_TO)
    public Concept relativeTo(@RequestBody Concept concept) {
        return reasoner.klabService().relativeTo(concept);
    }

    @PostMapping(ServicesAPI.REASONER.TRAITS)
    public Collection<Concept> traits(@RequestBody Concept concept, @RequestParam(name="direct", defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directTraits(concept) : reasoner.klabService().traits(concept);
    }

    @PostMapping(ServicesAPI.REASONER.IDENTITIES)
    public Collection<Concept> identities(@RequestBody Concept concept, @RequestParam(name="direct", defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directIdentities(concept) : reasoner.klabService().identities(concept);
    }

    @PostMapping(ServicesAPI.REASONER.ATTRIBUTES)
    public Collection<Concept> attributes(@RequestBody Concept concept, @RequestParam(name="direct", defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directAttributes(concept) : reasoner.klabService().attributes(concept);
    }

    @PostMapping(ServicesAPI.REASONER.REALMS)
    public Collection<Concept> realms(@RequestBody Concept concept, @RequestParam(name="direct", defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().directRealms(concept) : reasoner.klabService().realms(concept);
    }

    @PostMapping(ServicesAPI.REASONER.BASE_PARENT_TRAIT)
    public Concept baseParentTrait(@RequestBody Concept trait) {
        return reasoner.klabService().baseParentTrait(trait);
    }

    @PostMapping(ServicesAPI.REASONER.BASE_OBSERVABLE)
    public Concept baseObservable(@RequestBody Concept observable) {
        return reasoner.klabService().baseObservable(observable);
    }

    @PostMapping(ServicesAPI.REASONER.RAW_OBSERVABLE)
    public Concept rawObservable(@RequestBody Concept observable) {
        return reasoner.klabService().rawObservable(observable);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_TRAIT)
    public boolean hasTrait(Semantics type, Concept trait, @RequestParam(defaultValue = "false") boolean direct) {
        return direct ? reasoner.klabService().hasDirectTrait(type, trait) : reasoner.klabService().hasTrait(type, trait);
    }

    @PostMapping(ServicesAPI.REASONER.HAS_PARENT_ROLE)
    public boolean hasParentRole(@RequestBody Concept o1, Concept t) {
        return reasoner.klabService().hasParentRole(o1, t);
    }

    public String displayName(@RequestBody Concept semantics) {
        return reasoner.klabService().displayName(semantics);
    }

    public String displayLabel(@RequestBody Concept concept) {
        return reasoner.klabService().displayLabel(concept);
    }

    public String style(@RequestBody Concept concept) {
        return reasoner.klabService().style(concept);
    }

    @PostMapping(ServicesAPI.REASONER.SEMANTIC_TYPE)
    public SemanticType observableType(@RequestBody Concept observable, @RequestParam(defaultValue = "false") boolean acceptTraits) {
        return reasoner.klabService().observableType(observable, acceptTraits);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCE)
    public Concept relationshipSource(@RequestBody Concept relationship) {
        return reasoner.klabService().relationshipSource(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCES)
    public Collection<Concept> relationshipSources(@RequestBody Concept relationship) {
        return reasoner.klabService().relationshipSources(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGET)
    public Concept relationshipTarget(@RequestBody Concept relationship) {
        return reasoner.klabService().relationshipTarget(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGETS)
    public Collection<Concept> relationshipTargets(@RequestBody Concept relationship) {
        return reasoner.klabService().relationshipTargets(relationship);
    }

    @PostMapping(ServicesAPI.REASONER.NEGATED)
    public Concept negated(@RequestBody Concept concept) {
        return reasoner.klabService().negated(concept);
    }

    @PostMapping(ServicesAPI.REASONER.SATISFIABLE)
    public boolean satisfiable(@RequestBody Concept ret) {
        return reasoner.klabService().satisfiable(ret);
    }

    @PostMapping(ServicesAPI.REASONER.DOMAIN)
    public Semantics domain(@RequestBody Concept conceptImpl) {
        return reasoner.klabService().domain(conceptImpl);
    }

    @PostMapping(ServicesAPI.REASONER.APPLICABLE)
    public Collection<Concept> applicableObservables(@RequestBody Concept main) {
        return reasoner.klabService().applicableObservables(main);
    }

    @PostMapping(ServicesAPI.REASONER.DESCRIBED)
    public Concept describedType(@RequestBody Concept concept) {
        return reasoner.klabService().describedType(concept);
    }

    @PostMapping(ServicesAPI.REASONER.COMPATIBLE)
    public boolean compatible(@RequestBody Concept[]args) {
        return reasoner.klabService().compatible(args[0], args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.CONTEXTUALLY_COMPATIBLE)
    public boolean contextuallyCompatible(@RequestBody Concept[] args) {
        return reasoner.klabService().contextuallyCompatible(args[0], args[1], args[2]);
    }

    @PostMapping(ServicesAPI.REASONER.OCCURRENT)
    public boolean occurrent(@RequestBody Concept concept) {
        return reasoner.klabService().occurrent(concept);
    }

    @PostMapping(ServicesAPI.REASONER.LGC)
    public Concept leastGeneralCommon(@RequestBody Collection<Concept> cc) {
        return reasoner.klabService().leastGeneralCommon(cc);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED_BY)
    public boolean affectedBy(@RequestBody Concept[] args) {
        return reasoner.klabService().affectedBy(args[0], args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.CREATED_BY)
    public boolean createdBy(@RequestBody Concept[] args) {
        return reasoner.klabService().createdBy(args[0], args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED_OR_CREATED)
    public Collection<Concept> affectedOrCreated(@RequestBody Concept semantics) {
        return reasoner.klabService().affectedOrCreated(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.AFFECTED)
    public Collection<Concept> affected(@RequestBody Concept semantics) {
        return reasoner.klabService().affected(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.CREATED)
    public Collection<Concept> created(@RequestBody Concept semantics) {
        return reasoner.klabService().created(semantics);
    }

    @PostMapping(ServicesAPI.REASONER.ROLES_FOR)
    public Collection<Concept> rolesFor(@RequestBody Concept[] args) {
        return reasoner.klabService().rolesFor(args[0], args.length == 1 ? null : args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLE)
    public Concept impliedRole(@RequestBody Concept[] args) {
        return reasoner.klabService().impliedRole(args[0], args.length == 1 ? null : args[1]);
    }

    @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLES)
    public @ResponseBody Collection<Concept> impliedRoles(@RequestBody Concept role,
            @RequestParam(defaultValue = "false") boolean includeRelationshipEndpoints) {
        return reasoner.klabService().impliedRoles(role, includeRelationshipEndpoints);
    }

}
