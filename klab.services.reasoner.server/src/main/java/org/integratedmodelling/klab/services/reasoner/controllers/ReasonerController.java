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

  
  
  @Operation(summary = "Get concept roles", 
            description = "Retrieves the roles of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Roles retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.ROLES)
  public @ResponseBody Collection<Concept> roles(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct roles only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directRoles(concept)
        : reasoner.klabService().roles(concept);
  }

  @Operation(summary = "Check if concept has role", 
            description = "Determines if the first concept has the second concept as a role")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Role check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.HAS_ROLE)
  public boolean hasRole(
      @Parameter(description = "Array of two concepts to check") @RequestBody Concept[] concept,
      @Parameter(description = "Whether to check direct roles only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().hasDirectRole(concept[0], concept[1])
        : reasoner.klabService().hasRole(concept[0], concept[1]);
  }

  @Operation(summary = "Get concept inherent", 
            description = "Retrieves the inherent concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Inherent concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.INHERENT)
  public @ResponseBody Concept inherent(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct inherent only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directInherent(concept)
        : reasoner.klabService().inherent(concept);
  }

  @Operation(summary = "Get concept goal", 
            description = "Retrieves the goal concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Goal concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.GOAL)
  public @ResponseBody Concept goal(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct goal only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directGoal(concept)
        : reasoner.klabService().goal(concept);
  }

  @Operation(summary = "Get cooccurrent concept", 
            description = "Retrieves the cooccurrent concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Cooccurrent concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.COOCCURRENT)
  public @ResponseBody Concept cooccurrent(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct cooccurrent only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCooccurrent(concept)
        : reasoner.klabService().cooccurrent(concept);
  }

  @Operation(summary = "Get causant concept", 
            description = "Retrieves the causant concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Causant concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CAUSANT)
  public Concept causant(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct causant only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCausant(concept)
        : reasoner.klabService().causant(concept);
  }

  @Operation(summary = "Get caused concept", 
            description = "Retrieves the caused concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Caused concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CAUSED)
  public Concept caused(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct caused only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCaused(concept)
        : reasoner.klabService().caused(concept);
  }

  @Operation(summary = "Get adjacent concept", 
            description = "Retrieves the adjacent concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Adjacent concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.ADJACENT)
  public Concept adjacent(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct adjacent only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directAdjacent(concept)
        : reasoner.klabService().adjacent(concept);
  }

  @Operation(summary = "Get compresent concept", 
            description = "Retrieves the compresent concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Compresent concept retrieved successfully") 
  })
  @PostMapping(ServicesAPI.REASONER.COMPRESENT)
  public Concept compresent(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct compresent only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directCompresent(concept)
        : reasoner.klabService().compresent(concept);
  }

  @Operation(summary = "Get relative concept", 
            description = "Retrieves the relative concept of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Relative concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.RELATIVE_TO)
  public Concept relativeTo(
      @Parameter(description = "Target concept") @RequestBody Concept concept) {
    return reasoner.klabService().relativeTo(concept);
  }

  @Operation(summary = "Get concept traits", 
            description = "Retrieves the traits of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Traits retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.TRAITS)
  public Collection<Concept> traits(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct traits only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directTraits(concept)
        : reasoner.klabService().traits(concept);
  }

  @Operation(summary = "Get concept identities", 
            description = "Retrieves the identities of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Identities retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.IDENTITIES)
  public Collection<Concept> identities(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct identities only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directIdentities(concept)
        : reasoner.klabService().identities(concept);
  }

  @Operation(summary = "Get concept attributes", 
            description = "Retrieves the attributes of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Attributes retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.ATTRIBUTES)
  public Collection<Concept> attributes(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct attributes only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directAttributes(concept)
        : reasoner.klabService().attributes(concept);
  }

  @Operation(summary = "Get concept realms", 
            description = "Retrieves the realms of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Realms retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.REALMS)
  public Collection<Concept> realms(
      @Parameter(description = "Target concept") @RequestBody Concept concept,
      @Parameter(description = "Whether to get direct realms only") @RequestParam(name = "direct", defaultValue = "false") boolean direct) {
    return direct
        ? reasoner.klabService().directRealms(concept)
        : reasoner.klabService().realms(concept);
  }

  @Operation(
      summary = "Get lexical root",
      description = "Retrieves the lexical root (base parent trait) of the specified concept")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Lexical root retrieved successfully")
      })
  @PostMapping(ServicesAPI.REASONER.LEXICAL_ROOT)
  public Concept baseParentTrait(
      @Parameter(description = "Target concept") @RequestBody Concept trait) {
    return reasoner.klabService().lexicalRoot(trait);
  }

  @Operation(
      summary = "Get base observable",
      description = "Retrieves the base observable of the specified concept")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Base observable retrieved successfully")
      })
  @PostMapping(ServicesAPI.REASONER.BASE_OBSERVABLE)
  public Concept baseObservable(
      @Parameter(description = "Target observable") @RequestBody Concept observable) {
    return reasoner.klabService().baseObservable(observable);
  }

  @Operation(
      summary = "Get raw observable",
      description = "Retrieves the raw observable of the specified concept")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Raw observable retrieved successfully")
      })
  @PostMapping(ServicesAPI.REASONER.RAW_OBSERVABLE)
  public Concept rawObservable(
      @Parameter(description = "Target observable") @RequestBody Concept observable) {
    return reasoner.klabService().rawObservable(observable);
  }

  @Operation(
      summary = "Check if type has trait",
      description = "Determines if the semantic type has the specified trait")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Trait check completed successfully")
      })
  @PostMapping(ServicesAPI.REASONER.HAS_TRAIT)
  public boolean hasTrait(
      @Parameter(description = "Semantic type") Semantics type,
      @Parameter(description = "Trait to check") Concept trait,
      @Parameter(description = "Whether to check direct traits only")
          @RequestParam(name = "direct", defaultValue = "false")
          boolean direct) {
    return direct
        ? reasoner.klabService().hasDirectTrait(type, trait)
        : reasoner.klabService().hasTrait(type, trait);
  }

  @Operation(
      summary = "Check for parent role",
      description = "Determines if the concept has the specified parent role")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Parent role check completed successfully")
      })
  @PostMapping(ServicesAPI.REASONER.HAS_PARENT_ROLE)
  public boolean hasParentRole(
      @Parameter(description = "Target concept") @RequestBody Concept o1,
      @Parameter(description = "Role to check") Concept t) {
    return reasoner.klabService().hasParentRole(o1, t);
  }

  @Operation(
      summary = "Get display name",
      description = "Retrieves the display name of the specified concept")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Display name retrieved successfully")
      })
  public String displayName(
      @Parameter(description = "Target concept") @RequestBody Concept semantics) {
    return reasoner.klabService().displayName(semantics);
  }

  @Operation(
      summary = "Get display label",
      description = "Retrieves the display label of the specified concept")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Display label retrieved successfully")
      })
  public String displayLabel(
      @Parameter(description = "Target concept") @RequestBody Concept concept) {
    return reasoner.klabService().displayLabel(concept);
  }

  @Operation(summary = "Get style", description = "Retrieves the style of the specified concept")
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "Style retrieved successfully")})
  public String style(@Parameter(description = "Target concept") @RequestBody Concept concept) {
    return reasoner.klabService().style(concept);
  }

  @Operation(
      summary = "Get semantic type",
      description = "Retrieves the semantic type of the specified observable")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Semantic type retrieved successfully")
      })
  @PostMapping(ServicesAPI.REASONER.SEMANTIC_TYPE)
  public SemanticType observableType(
      @Parameter(description = "Target observable") @RequestBody Concept observable,
      @Parameter(description = "Whether to accept traits")
          @RequestParam(name = "acceptTraits", defaultValue = "false")
          boolean acceptTraits) {
    return reasoner.klabService().observableType(observable, acceptTraits);
  }

  @Operation(summary = "Get relationship source", 
            description = "Retrieves the source concept of the specified relationship")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Relationship source retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCE)
  public Concept relationshipSource(@Parameter(description = "Target relationship") @RequestBody Concept relationship) {
    return reasoner.klabService().relationshipSource(relationship);
  }

  @Operation(summary = "Get relationship sources",
            description = "Retrieves all source concepts of the specified relationship")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Relationship sources retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_SOURCES)
  public Collection<Concept> relationshipSources(@Parameter(description = "Target relationship") @RequestBody Concept relationship) {
    return reasoner.klabService().relationshipSources(relationship);
  }

  @Operation(summary = "Get relationship target",
            description = "Retrieves the target concept of the specified relationship") 
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Relationship target retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGET)
  public Concept relationshipTarget(@Parameter(description = "Target relationship") @RequestBody Concept relationship) {
    return reasoner.klabService().relationshipTarget(relationship);
  }

  @Operation(summary = "Get relationship targets",
            description = "Retrieves all target concepts of the specified relationship")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Relationship targets retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.RELATIONSHIP_TARGETS) 
  public Collection<Concept> relationshipTargets(@Parameter(description = "Target relationship") @RequestBody Concept relationship) {
    return reasoner.klabService().relationshipTargets(relationship);
  }

  @Operation(summary = "Get negated concept",
            description = "Retrieves the negated form of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Negated concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.NEGATED)
  public Concept negated(@Parameter(description = "Target concept") @RequestBody Concept concept) {
    return reasoner.klabService().negated(concept);
  }

  @Operation(summary = "Check if concept is satisfiable",
            description = "Determines if the specified concept is satisfiable")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Satisfiability check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.SATISFIABLE)
  public boolean satisfiable(@Parameter(description = "Target concept") @RequestBody Concept ret) {
    return reasoner.klabService().satisfiable(ret);
  }

  @Operation(summary = "Get concept domain",
            description = "Retrieves the semantic domain of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Domain retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.DOMAIN)
  public Semantics domain(@Parameter(description = "Target concept") @RequestBody Concept conceptImpl) {
    return reasoner.klabService().domain(conceptImpl);
  }

  @Operation(summary = "Get applicable observables",
            description = "Retrieves all observables that are applicable to the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Applicable observables retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.APPLICABLE)
  public Collection<Concept> applicableObservables(@Parameter(description = "Target concept") @RequestBody Concept main) {
    return reasoner.klabService().applicableObservables(main);
  }

  @Operation(summary = "Get described type",
            description = "Retrieves the described type of the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Described type retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.DESCRIBED)
  public Concept describedType(@Parameter(description = "Target concept") @RequestBody Concept concept) {
    return reasoner.klabService().describedType(concept);
  }

  @Operation(summary = "Check if concepts are compatible",
            description = "Determines if two concepts are compatible with each other")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Compatibility check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.COMPATIBLE)
  public boolean compatible(@Parameter(description = "Array of two concepts to check") @RequestBody Concept[] args) {
    return reasoner.klabService().compatible(args[0], args[1]);
  }

  @Operation(summary = "Check if concepts are contextually compatible",
            description = "Determines if two concepts are compatible in the context of a third concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Contextual compatibility check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CONTEXTUALLY_COMPATIBLE)
  public boolean contextuallyCompatible(@Parameter(description = "Array of three concepts to check") @RequestBody Concept[] args) {
    return reasoner.klabService().contextuallyCompatible(args[0], args[1], args[2]);
  }

  @Operation(summary = "Check if concept is occurrent",
            description = "Determines if the specified concept is occurrent")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Occurrent check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.OCCURRENT)
  public boolean occurrent(@Parameter(description = "Target concept") @RequestBody Concept concept) {
    return reasoner.klabService().occurrent(concept);
  }

  @Operation(summary = "Get least general common concept",
            description = "Retrieves the least general concept that is common to all input concepts")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Least general common concept retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.LGC)
  public Concept leastGeneralCommon(@Parameter(description = "Collection of concepts") @RequestBody Collection<Concept> cc) {
    return reasoner.klabService().leastGeneralCommon(cc);
  }

  @Operation(summary = "Check if concept is affected by another",
            description = "Determines if the first concept is affected by the second concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Affected check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.AFFECTED_BY)
  public boolean affectedBy(@Parameter(description = "Array of two concepts to check") @RequestBody Concept[] args) {
    return reasoner.klabService().affectedBy(args[0], args[1]);
  }

  @Operation(summary = "Check if concept is created by another",
            description = "Determines if the first concept is created by the second concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Creation check completed successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CREATED_BY)
  public boolean createdBy(@Parameter(description = "Array of two concepts to check") @RequestBody Concept[] args) {
    return reasoner.klabService().createdBy(args[0], args[1]);
  }

  @Operation(summary = "Get affected or created concepts",
            description = "Retrieves all concepts that are affected or created by the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Affected or created concepts retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.AFFECTED_OR_CREATED)
  public Collection<Concept> affectedOrCreated(@Parameter(description = "Target concept") @RequestBody Concept semantics) {
    return reasoner.klabService().affectedOrCreated(semantics);
  }

  @Operation(summary = "Get affected concepts",
            description = "Retrieves all concepts that are affected by the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Affected concepts retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.AFFECTED)
  public Collection<Concept> affected(@Parameter(description = "Target concept") @RequestBody Concept semantics) {
    return reasoner.klabService().affected(semantics);
  }

  @Operation(summary = "Get created concepts",
            description = "Retrieves all concepts that are created by the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Created concepts retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.CREATED)
  public Collection<Concept> created(@Parameter(description = "Target concept") @RequestBody Concept semantics) {
    return reasoner.klabService().created(semantics);
  }

  @Operation(summary = "Get roles for concept",
            description = "Retrieves all roles for the specified concept, optionally in context of another concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Roles retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.ROLES_FOR)
  public Collection<Concept> rolesFor(@Parameter(description = "Array of one or two concepts") @RequestBody Concept[] args) {
    return reasoner.klabService().rolesFor(args[0], args.length == 1 ? null : args[1]);
  }

  @Operation(summary = "Get implied role",
            description = "Retrieves the implied role for the specified concept, optionally in context of another concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Implied role retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLE)
  public Concept impliedRole(@Parameter(description = "Array of one or two concepts") @RequestBody Concept[] args) {
    return reasoner.klabService().impliedRole(args[0], args.length == 1 ? null : args[1]);
  }

  @Operation(summary = "Get implied roles",
            description = "Retrieves all implied roles for the specified concept")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Implied roles retrieved successfully")
  })
  @PostMapping(ServicesAPI.REASONER.IMPLIED_ROLES)
  public @ResponseBody Collection<Concept> impliedRoles(
      @Parameter(description = "Target role concept") @RequestBody Concept role,
      @Parameter(description = "Whether to include relationship endpoints") @RequestParam(name = "includeRelationshipEndpoints", defaultValue = "false")
          boolean includeRelationshipEndpoints) {
    return reasoner.klabService().impliedRoles(role, includeRelationshipEndpoints);
  }
}
