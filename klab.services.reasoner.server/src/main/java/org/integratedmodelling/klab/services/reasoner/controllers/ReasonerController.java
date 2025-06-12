package org.integratedmodelling.klab.services.reasoner.controllers;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Reasoner API", description = "API for semantic reasoning operations")
public class ReasonerController {

  @Autowired private ReasonerServer reasoner;

  /**
   * POST /resolve/concept from URN
   *
   * @param definition
   * @return
   */
  @Operation(summary = "Resolve concept from URN", 
            description = "Resolves a concept from its URN definition")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Concept resolved successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid concept definition")
  })
  @PostMapping(ServicesAPI.REASONER.RESOLVE_CONCEPT)
  public @ResponseBody Concept resolveConcept(
      @Parameter(description = "Concept definition") @RequestBody String definition,
      @Parameter(description = "Use alternative resolution method") @RequestParam(name = "alt", required = false) boolean alternative) {
    if (alternative) {
      var syntax =
          reasoner
              .klabService()
              .serviceScope()
              .getService(ResourcesService.class)
              .retrieveConcept(definition);
      if (syntax != null) {
        return SemanticsBuilder.create(syntax, reasoner.klabService()).buildConcept();
      }
    }
    return reasoner.klabService().resolveConcept(definition);
  }

  @Operation(summary = "Compute observation strategies", 
            description = "Infers strategies for observing the specified resolution request")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Strategies computed successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid resolution request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized")
  })
  @PostMapping(ServicesAPI.REASONER.COMPUTE_OBSERVATION_STRATEGIES)
  public @ResponseBody List<ObservationStrategy> inferStrategies(
      @Parameter(description = "Resolution request") @RequestBody ResolutionRequest request, 
      Principal principal) {
    if (principal instanceof EngineAuthorization authorization) {
      var contextScope =
          authorization
              .getScope(ContextScope.class)
              .withResolutionConstraints(
                  request.getResolutionConstraints().toArray(new ResolutionConstraint[0]));
      return reasoner
          .klabService()
          .computeObservationStrategies(request.getObservation(), contextScope);
    }
    throw new KlabInternalErrorException("Unexpected implementation of request authorization");
  }

  /**
   * /resolve/observable
   *
   * @param definition
   * @return
   */
  @Operation(summary = "Resolve observable", 
            description = "Resolves an observable from its definition")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Observable resolved successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid observable definition")
  })
  @PostMapping(ServicesAPI.REASONER.RESOLVE_OBSERVABLE)
  public @ResponseBody Observable resolveObservable(@Parameter(description = "Observable definition") @RequestBody String definition) {
    return reasoner.klabService().resolveObservable(definition);
  }

  //  @PostMapping(ServicesAPI.REASONER.DECLARE_OBSERVABLE)
  //  public @ResponseBody Observable declareObservable(@RequestBody DeclarationRequest request) {
  //    return request.getObservableDeclaration().getPattern() == null
  //        ? reasoner.klabService().declareObservable(request.getObservableDeclaration())
  //        : reasoner
  //            .klabService()
  //            .declareObservable(request.getObservableDeclaration(),
  // request.getPatternVariables());
  //  }
  //
  //  @PostMapping(ServicesAPI.REASONER.DECLARE_CONCEPT)
  //  public @ResponseBody Concept declareConcept(@RequestBody DeclarationRequest request) {
  //    return request.getConceptDeclaration().isPattern()
  //        ? reasoner.klabService().declareConcept(request.getConceptDeclaration())
  //        : reasoner
  //            .klabService()
  //            .declareConcept(request.getConceptDeclaration(), request.getPatternVariables());
  //  }

  /**
   * POST /subsumes
   *
   * @param concepts
   * @return
   */
  @Operation(summary = "Check if one concept subsumes another", 
            description = "Determines if the first concept subsumes the second concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Subsumption check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.SUBSUMES)
  public boolean subsumes(@Parameter(description = "Array of two concepts to check") @RequestBody Concept[] concepts) {
    return reasoner.klabService().is(concepts[0], concepts[1]);
  }

  /**
   * POST /matches
   *
   * @param concepts
   * @return
   */
  @Operation(summary = "Check if concepts match", 
            description = "Determines if the first concept matches the second concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Match check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.MATCHES)
  public boolean matches(@Parameter(description = "Array of two concepts to check") @RequestBody Concept[] concepts) {
    return reasoner.klabService().match(concepts[0], concepts[1]);
  }

  /**
   * POST /operands
   *
   * @param target
   * @return
   */
  @Operation(summary = "Get concept operands", 
            description = "Retrieves the operands of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Operands retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.OPERANDS)
  public @ResponseBody Collection<Concept> operands(@Parameter(description = "Target concept") @RequestBody Concept target) {
    return reasoner.klabService().operands(target);
  }

  /**
   * POST /children
   *
   * @param target
   * @return
   */
  @Operation(summary = "Get concept children", 
            description = "Retrieves the direct children of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Children retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CHILDREN)
  public @ResponseBody Collection<Concept> children(@Parameter(description = "Target concept") @RequestBody Concept target) {
    return reasoner.klabService().children(target);
  }

  /**
   * POST /parents
   *
   * @param target
   * @return
   */
  @Operation(summary = "Get concept parents", 
            description = "Retrieves the direct parents of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Parents retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.PARENTS)
  public @ResponseBody Collection<Concept> parents(@Parameter(description = "Target concept") @RequestBody Concept target) {
    return reasoner.klabService().parents(target);
  }

  /**
   * POST /parent
   *
   * @param c
   * @return
   */
  @Operation(summary = "Get concept parent", 
            description = "Retrieves the direct parent of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Parent retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.PARENT)
  public @ResponseBody Concept parent(@Parameter(description = "Target concept") @RequestBody Concept c) {
    return reasoner.klabService().parent(c);
  }

  /**
   * POST /allchildren
   *
   * @param target
   * @return
   */
  @Operation(summary = "Get all concept children", 
            description = "Retrieves all children (direct and indirect) of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "All children retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.ALL_CHILDREN)
  public @ResponseBody Collection<Concept> allChildren(@Parameter(description = "Target concept") @RequestBody Concept target) {
    return reasoner.klabService().allChildren(target);
  }

  /**
   * POST /allparents
   *
   * @param target
   * @return
   */
  @Operation(summary = "Get all concept parents", 
            description = "Retrieves all parents (direct and indirect) of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "All parents retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.ALL_PARENTS)
  public @ResponseBody Collection<Concept> allParents(@Parameter(description = "Target concept") @RequestBody Concept target) {
    return reasoner.klabService().allParents(target);
  }

  /**
   * POST /closure
   *
   * @param target
   * @return
   */
  @Operation(summary = "Get concept closure", 
            description = "Retrieves the closure (all related concepts) of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Closure retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CLOSURE)
  public @ResponseBody Collection<Concept> closure(@Parameter(description = "Target concept") @RequestBody Concept target) {
    return reasoner.klabService().closure(target);
  }

  /**
   * Get the core observable of a concept
   */
  @Operation(summary = "Get core observable", 
            description = "Retrieves the core observable of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Core observable retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CORE_OBSERVABLE)
  public @ResponseBody Concept coreObservable(@Parameter(description = "Target concept") @RequestBody Concept first) {
    return reasoner.klabService().coreObservable(first);
  }

  /**
   * Split operators from a concept
   */
  @Operation(summary = "Split operators", 
            description = "Splits operators from the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Operators split successfully")
  })
  @PostMapping(ServicesAPI.REASONER.SPLIT_OPERATORS)
  public @ResponseBody Pair<Concept, List<SemanticType>> splitOperators(
      @Parameter(description = "Target concept") @RequestBody Concept concept) {
    return reasoner.klabService().splitOperators(concept);
  }

  /**
   * Asserted or semantic distance between two concepts. If asserted is false (default)
   * the asserted distance will be returned as an integer. Otherwise, the semantic distance will
   * be computed and the input data array may contain a third concept to compute the distance in its
   * context.
   */
  @Operation(summary = "Calculate distance between concepts", 
            description = "Calculates the asserted or semantic distance between two concepts")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Distance calculated successfully")
  })
  @PostMapping(ServicesAPI.REASONER.DISTANCE)
  public int assertedDistance(
      @Parameter(description = "Array of concepts (2 or 3)") @RequestBody Concept[] concepts,
      @Parameter(description = "Whether to use asserted distance") @RequestParam(name = "asserted", defaultValue = "false") boolean asserted) {
    return asserted
        ? reasoner.klabService().assertedDistance(concepts[0], concepts[1])
        : reasoner
            .klabService()
            .semanticDistance(concepts[0], concepts[1], concepts.length == 2 ? null : concepts[2]);
  }

  @PostMapping(ServicesAPI.REASONER.ROLES)
  public @ResponseBody Collection<Concept> roles(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directRoles(concept)
        : reasoner.klabService().roles(concept);
  }

  @PostMapping(ServicesAPI.REASONER.HAS_ROLE)
  public boolean hasRole(
      @RequestBody Concept[] concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().hasDirectRole(concept[0], concept[1])
        : reasoner.klabService().hasRole(concept[0], concept[1]);
  }

  //    @PostMapping(ServicesAPI.REASONER.CONTEXT)
  //    public @ResponseBody Concept directContext(@RequestBody Concept concept, @RequestParam
  //    (defaultValue = "false") boolean direct) {
  //        return direct ? reasoner.klabService().directContext(concept) : reasoner.klabService()
  //        .context(concept);
  //    }

  @PostMapping(ServicesAPI.REASONER.INHERENT)
  public @ResponseBody Concept inherent(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directInherent(concept)
        : reasoner.klabService().inherent(concept);
  }

  @PostMapping(ServicesAPI.REASONER.GOAL)
  public @ResponseBody Concept goal(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directGoal(concept)
        : reasoner.klabService().goal(concept);
  }

  @PostMapping(ServicesAPI.REASONER.COOCCURRENT)
  public @ResponseBody Concept cooccurrent(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCooccurrent(concept)
        : reasoner.klabService().cooccurrent(concept);
  }

  @PostMapping(ServicesAPI.REASONER.CAUSANT)
  public Concept causant(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCausant(concept)
        : reasoner.klabService().causant(concept);
  }

  @PostMapping(ServicesAPI.REASONER.CAUSED)
  public Concept caused(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCaused(concept)
        : reasoner.klabService().caused(concept);
  }

  @PostMapping(ServicesAPI.REASONER.ADJACENT)
  public Concept adjacent(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directAdjacent(concept)
        : reasoner.klabService().adjacent(concept);
  }

  @PostMapping(ServicesAPI.REASONER.COMPRESENT)
  public Concept compresent(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCompresent(concept)
        : reasoner.klabService().compresent(concept);
  }

  @PostMapping(ServicesAPI.REASONER.RELATIVE_TO)
  public Concept relativeTo(@RequestBody Concept concept) {
    return reasoner.klabService().relativeTo(concept);
  }

  @PostMapping(ServicesAPI.REASONER.TRAITS)
  public Collection<Concept> traits(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directTraits(concept)
        : reasoner.klabService().traits(concept);
  }

  @PostMapping(ServicesAPI.REASONER.IDENTITIES)
  public Collection<Concept> identities(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directIdentities(concept)
        : reasoner.klabService().identities(concept);
  }

  @PostMapping(ServicesAPI.REASONER.ATTRIBUTES)
  public Collection<Concept> attributes(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directAttributes(concept)
        : reasoner.klabService().attributes(concept);
  }

  @PostMapping(ServicesAPI.REASONER.REALMS)
  public Collection<Concept> realms(
      @RequestBody Concept concept,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directRealms(concept)
        : reasoner.klabService().realms(concept);
  }

  @PostMapping(ServicesAPI.REASONER.LEXICAL_ROOT)
  public Concept baseParentTrait(@RequestBody Concept trait) {
    return reasoner.klabService().lexicalRoot(trait);
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
  public boolean hasTrait(
      Semantics type,
      Concept trait,
      @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().hasDirectTrait(type, trait)
        : reasoner.klabService().hasTrait(type, trait);
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
  public SemanticType observableType(
      @RequestBody Concept observable,
      @RequestParam(name = "acceptTraits", defaultValue = "false") boolean acceptTraits) {
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
  public boolean compatible(@RequestBody Concept[] args) {
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
  public @ResponseBody Collection<Concept> impliedRoles(
      @RequestBody Concept role,
      @RequestParam(name = "includeRelationshipEndpoints", defaultValue = "false")
          boolean includeRelationshipEndpoints) {
    return reasoner.klabService().impliedRoles(role, includeRelationshipEndpoints);
  }
}
