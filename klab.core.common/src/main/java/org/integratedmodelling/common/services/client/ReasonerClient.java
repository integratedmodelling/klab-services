package org.integratedmodelling.common.services.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;

/**
 * TODO the reasoner client should cache a configurable amount of info so to minimize the likely
 * frequent back-and-forth with the server. Best candidates are the RESOLVE/DECLARE endpoints and
 * the COMPATIBLE ones. We could also compile full info at x-level inheritance about each
 * concept/observable and cache that instead of asking for frequent atomic ops, maybe even based on
 * frequency and/or memory available to the client.
 */
public class ReasonerClient extends BaseServiceClient implements Reasoner, Reasoner.Admin {

  // TODO link to configuration for debugging
  private boolean useCaches = true;
  private volatile Capabilities capabilities;

  private record BinaryKey(long revision, Concept first, Concept second) {}

  private record DistanceKey(long revision, Concept target, Concept other, Concept context) {}

  /** Caches for concepts and observables. */
  private final Cache<String, Concept> concepts =
      Caffeine.newBuilder().maximumSize(2_000).recordStats().build();

  private final Cache<String, Observable> observables =
      Caffeine.newBuilder().maximumSize(2_000).recordStats().build();

  private final Cache<BinaryKey, Boolean> subsumption =
      Caffeine.newBuilder().maximumSize(20_000).recordStats().build();

  private final Cache<DistanceKey, Integer> semanticDistances =
      Caffeine.newBuilder().maximumSize(10_000).recordStats().build();

  ReasonerClient(
      ServiceClientCatalog.ClientMonitor monitor,
      Scope userScope,
      Settings settings,
      BiConsumer<ServiceStatus, Boolean>... statusListeners) {
    super(monitor, userScope, settings, statusListeners);
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    Capabilities latest = getCapabilities(scope, ReasonerCapabilitiesImpl.class);
    Capabilities previous = capabilities;
    if (previous != null && previous.getKnowledgeRevision() != latest.getKnowledgeRevision()) {
      invalidateCaches();
    }
    capabilities = latest;
    return latest;
  }

  @Override
  public Concept resolveConcept(String definition) {
    if (!useCaches) {
      return resolveConceptInternal(removeExcessParentheses(definition));
    }
    String normalized = removeExcessParentheses(definition);
    return concepts.get(
        normalized,
        key -> {
          Concept ret = resolveConceptInternal(key);
          return ret == null ? Concept.nothing() : ret;
        });
  }

  @Override
  public Observable resolveObservable(String definition) {
    if (!useCaches) {
      return resolveObservableInternal(removeExcessParentheses(definition));
    }
    String normalized = removeExcessParentheses(definition);
    return observables.get(
        normalized,
        key -> {
          Observable ret = resolveObservableInternal(key);
          return ret == null ? Observable.nothing(null) : ret;
        });
  }

  private String removeExcessParentheses(String definition) {
    definition = definition.trim();
    while (definition.startsWith("(") && definition.endsWith(")")) {
      definition = definition.substring(1, definition.length() - 1);
    }
    return definition;
  }

  public Concept resolveConceptInternal(String definition) {
    return client.post(ServicesAPI.REASONER.RESOLVE_CONCEPT, definition, Concept.class);
  }

  public Observable resolveObservableInternal(String definition) {
    return client.post(ServicesAPI.REASONER.RESOLVE_OBSERVABLE, definition, Observable.class);
  }

  @Override
  public boolean is(Semantics conceptImpl, Semantics other) {
    var key = new BinaryKey(currentRevision(), conceptImpl.asConcept(), other.asConcept());
    return subsumption.get(
        key,
        ignored ->
            client.post(
                ServicesAPI.REASONER.SUBSUMES,
                List.of(conceptImpl.asConcept(), other.asConcept()),
                Boolean.class));
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
  public Concept parent(Semantics target) {
    return client.post(ServicesAPI.REASONER.PARENT, target.asConcept(), Concept.class);
  }

  @Override
  public Concept compose(Collection<Concept> concepts, LogicalConnector connector) {
    if (concepts.isEmpty()) {
      return Concept.nothing();
    }
    if (concepts.size() == 1) {
      return concepts.iterator().next();
    }
    String operator =
        switch (connector) {
          case UNION -> " or ";
          case INTERSECTION -> " and ";
          default ->
              throw new KlabIllegalArgumentException("Unsupported semantic connector " + connector);
        };
    return resolveConcept(
        concepts.stream()
            .map(Concept::getUrn)
            .collect(java.util.stream.Collectors.joining(operator)));
  }

  @Override
  public Collection<Concept> allChildren(Semantics target) {
    return client.postCollection(
        ServicesAPI.REASONER.ALL_CHILDREN, target.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> allParents(Semantics target) {
    return client.postCollection(
        ServicesAPI.REASONER.ALL_PARENTS, target.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> closure(Semantics target) {
    return client.postCollection(ServicesAPI.REASONER.CLOSURE, target.asConcept(), Concept.class);
  }

  @Override
  public int semanticDistance(Semantics target, Semantics other) {
    return semanticDistance(target, other, null);
  }

  @Override
  public int semanticDistance(Semantics target, Semantics other, Semantics context) {
    Concept contextConcept = context == null ? null : context.asConcept();
    var key =
        new DistanceKey(currentRevision(), target.asConcept(), other.asConcept(), contextConcept);
    int distance =
        semanticDistances.get(
            key,
            ignored ->
                client.post(
                    ServicesAPI.REASONER.DISTANCE,
                    Lists.newArrayList(target.asConcept(), other.asConcept(), contextConcept),
                    Integer.class));
    if (distance < 0) {
      return distance;
    }
    int observableDistance = observableDistance(target, other);
    return observableDistance < 0 ? observableDistance : distance + observableDistance;
  }

  @Override
  public Concept coreObservable(Semantics target) {
    return client.post(ServicesAPI.REASONER.CORE_OBSERVABLE, target.asConcept(), Concept.class);
  }

  @Override
  public Concept baseSubstantialType(Semantics concept, Scope scope) {
    // TODO this is called frequently for the same type, should cache
    return client
        // using the user scope because it's guaranteed to be there even just after the context
        // scope was created, to avoid missing the user scope because it's being broadcast at the
        // moment of the call
        .withScope(scope.getParentScope(Scope.Type.USER, UserScope.class))
        .post(ServicesAPI.REASONER.CORE_SUBSTANTIAL, concept.asConcept(), Concept.class);
  }

  @Override
  public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {
    throw new KlabUnimplementedException(
        "The split-operator response needs a typed transport object");
  }

  @Override
  public int assertedDistance(Semantics from, Semantics to) {
    return client.post(
        ServicesAPI.REASONER.DISTANCE,
        Lists.newArrayList(from.asConcept(), to.asConcept()),
        Integer.class,
        "asserted",
        "true");
  }

  @Override
  public Collection<Concept> roles(Semantics concept) {
    return client.postCollection(ServicesAPI.REASONER.ROLES, concept.asConcept(), Concept.class);
  }

  @Override
  public boolean hasRole(Semantics concept, Concept role) {
    return client.post(
        ServicesAPI.REASONER.HAS_ROLE, List.of(concept.asConcept(), role), Boolean.class);
  }

  @Override
  public boolean hasDirectRole(Semantics concept, Concept role) {
    return client.post(
        ServicesAPI.REASONER.HAS_ROLE,
        List.of(concept.asConcept(), role),
        Boolean.class,
        "direct",
        "true");
  }

  @Override
  public Concept directInherent(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.INHERENT, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept inherent(Semantics concept) {
    return client.post(ServicesAPI.REASONER.INHERENT, concept.asConcept(), Concept.class);
  }

  @Override
  public Concept directGoal(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.GOAL, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept goal(Semantics concept) {
    return client.post(ServicesAPI.REASONER.GOAL, concept.asConcept(), Concept.class);
  }

  @Override
  public Concept directCooccurrent(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.COOCCURRENT, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept directCausant(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.CAUSANT, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept directCaused(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.CAUSED, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept directAdjacent(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.ADJACENT, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept directCompresent(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.COMPRESENT, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept directRelativeTo(Semantics concept) {
    return client.post(
        ServicesAPI.REASONER.RELATIVE_TO, concept.asConcept(), Concept.class, "direct", "true");
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
    return client.postCollection(
        ServicesAPI.REASONER.IDENTITIES, concept.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> directIdentities(Semantics concept) {
    return client.postCollection(
        ServicesAPI.REASONER.IDENTITIES, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Collection<Concept> directAttributes(Semantics concept) {
    return client.postCollection(
        ServicesAPI.REASONER.ATTRIBUTES, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Collection<Concept> attributes(Semantics concept) {
    return client.postCollection(
        ServicesAPI.REASONER.ATTRIBUTES, concept.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> realms(Semantics concept) {
    return client.postCollection(ServicesAPI.REASONER.REALMS, concept.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> directRealms(Semantics concept) {
    return client.postCollection(
        ServicesAPI.REASONER.REALMS, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Concept lexicalRoot(Semantics trait) {
    return client.post(ServicesAPI.REASONER.LEXICAL_ROOT, trait.asConcept(), Concept.class);
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
    return client.post(
        ServicesAPI.REASONER.HAS_TRAIT, List.of(type.asConcept(), trait), Boolean.class);
  }

  @Override
  public boolean hasDirectTrait(Semantics type, Concept trait) {
    return client.post(
        ServicesAPI.REASONER.HAS_TRAIT,
        List.of(type.asConcept(), trait),
        Boolean.class,
        "direct",
        "true");
  }

  @Override
  public boolean hasParentRole(Semantics o1, Concept t) {
    return client.post(
        ServicesAPI.REASONER.HAS_PARENT_ROLE, List.of(o1.asConcept(), t), Boolean.class);
  }

  @Override
  public Collection<Concept> directTraits(Semantics concept) {
    return client.postCollection(
        ServicesAPI.REASONER.TRAITS, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public Collection<Concept> directRoles(Semantics concept) {
    return client.postCollection(
        ServicesAPI.REASONER.ROLES, concept.asConcept(), Concept.class, "direct", "true");
  }

  @Override
  public String displayName(Semantics semantics) {
    return semantics.displayName();
  }

  @Override
  public String displayLabel(Semantics concept) {
    return concept.displayLabel();
  }

  @Override
  public String style(Concept concept) {
    throw new KlabUnimplementedException("Reasoner concept styling is not supported");
  }

  @Override
  public SemanticType observableType(Semantics observable, boolean acceptTraits) {
    if (observable instanceof Observable o && o.getArtifactType() == Artifact.Type.VOID) {
      return SemanticType.NOTHING;
    }
    var types = java.util.EnumSet.copyOf(observable.asConcept().getType());
    types.retainAll(SemanticType.BASE_MODELABLE_TYPES);
    if (types.size() != 1) {
      throw new KlabIllegalArgumentException("Not an observable semantic type: " + observable);
    }
    return types.iterator().next();
  }

  @Override
  public Concept relationshipSource(Semantics relationship) {
    return relationshipSources(relationship).stream().findFirst().orElse(null);
  }

  @Override
  public Collection<Concept> relationshipSources(Semantics relationship) {
    return client.postCollection(
        ServicesAPI.REASONER.RELATIONSHIP_SOURCES, relationship.asConcept(), Concept.class);
  }

  @Override
  public Concept relationshipTarget(Semantics relationship) {
    return relationshipTargets(relationship).stream().findFirst().orElse(null);
  }

  @Override
  public Collection<Concept> relationshipTargets(Semantics relationship) {
    return client.postCollection(
        ServicesAPI.REASONER.RELATIONSHIP_TARGETS, relationship.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> resolving(Semantics relationship) {
    return client.postCollection(
        ServicesAPI.REASONER.RESOLVING, relationship.asConcept(), Concept.class);
  }

  @Override
  public Concept negated(Concept concept) {
    return client.post(ServicesAPI.REASONER.NEGATED, concept.asConcept(), Concept.class);
  }

  @Override
  public boolean satisfiable(Semantics concept) {
    return client.post(ServicesAPI.REASONER.SATISFIABLE, concept.asConcept(), Boolean.class);
  }

  @Override
  public Semantics domain(Semantics concept) {
    return client.post(ServicesAPI.REASONER.DOMAIN, concept.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> applicableObservables(Concept concept) {
    return client.postCollection(
        ServicesAPI.REASONER.APPLICABLE, concept.asConcept(), Concept.class);
  }

  @Override
  public Concept describedType(Semantics concept) {
    return client.post(ServicesAPI.REASONER.DESCRIBED, concept.asConcept(), Concept.class);
  }

  @Override
  public boolean compatible(Semantics concept, Semantics other) {
    return client.post(ServicesAPI.REASONER.COMPATIBLE, List.of(concept, other), Boolean.class);
  }

  @Override
  public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
    return client.post(
        ServicesAPI.REASONER.CONTEXTUALLY_COMPATIBLE,
        List.of(focus, context1, context2),
        Boolean.class);
  }

  @Override
  public boolean occurrent(Semantics concept) {
    return client.post(ServicesAPI.REASONER.OCCURRENT, concept.asConcept(), Boolean.class);
  }

  @Override
  public Concept leastGeneralCommon(Collection<Concept> cc) {
    return client.post(ServicesAPI.REASONER.LGC, cc, Concept.class);
  }

  @Override
  public boolean affectedBy(Semantics affected, Semantics affecting) {
    return client.post(
        ServicesAPI.REASONER.AFFECTED_BY,
        List.of(affected.asConcept(), affecting.asConcept()),
        Boolean.class);
  }

  @Override
  public boolean createdBy(Semantics affected, Semantics affecting) {
    return client.post(
        ServicesAPI.REASONER.CREATED_BY,
        List.of(affected.asConcept(), affecting.asConcept()),
        Boolean.class);
  }

  @Override
  public Collection<Concept> affectedOrCreated(Semantics semantics) {
    return client.postCollection(
        ServicesAPI.REASONER.AFFECTED_OR_CREATED, semantics.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> affected(Semantics semantics) {
    return client.postCollection(
        ServicesAPI.REASONER.AFFECTED, semantics.asConcept(), Concept.class);
  }

  @Override
  public Collection<Concept> created(Semantics semantics) {
    return client.postCollection(
        ServicesAPI.REASONER.CREATED, semantics.asConcept(), Concept.class);
  }

  @Override
  public boolean match(Semantics candidate, Semantics pattern) {
    return client.post(
        ServicesAPI.REASONER.MATCHES,
        List.of(candidate.asConcept(), pattern.asConcept()),
        Boolean.class);
  }

  @Override
  public boolean match(Semantics candidate, Semantics pattern, Map<Concept, Concept> matches) {
    if (match(candidate, pattern) && !pattern.isAbstract()) {
      return true;
    }
    throw new KlabUnimplementedException(
        "Generic semantic matching with captured substitutions is not implemented");
  }

  @Override
  public <T extends Semantics> T concretize(T pattern, Map<Concept, Concept> concreteConcepts) {
    String declaration = pattern.getUrn();
    for (var replacement : concreteConcepts.entrySet()) {
      declaration =
          declaration.replace(replacement.getKey().getUrn(), replacement.getValue().getUrn());
    }
    @SuppressWarnings("unchecked")
    T ret =
        (T)
            (pattern instanceof Observable
                ? resolveObservable(declaration)
                : resolveConcept(declaration));
    return ret;
  }

  @Override
  public <T extends Semantics> T concretize(T pattern, List<Concept> concreteConcepts) {
    throw new KlabUnimplementedException(
        "Inference-based generic concretization is not implemented");
  }

  @Override
  public Collection<Concept> rolesFor(Concept observable, Concept context) {
    return client.postCollection(
        ServicesAPI.REASONER.ROLES_FOR,
        context == null ? List.of(observable) : List.of(observable, context),
        Concept.class);
  }

  @Override
  public Concept impliedRole(Concept baseRole, Concept contextObservable) {
    return client.post(
        ServicesAPI.REASONER.IMPLIED_ROLE,
        contextObservable == null ? List.of(baseRole) : List.of(baseRole, contextObservable),
        Concept.class);
  }

  @Override
  public Collection<Concept> impliedRoles(Concept role, boolean includeRelationshipEndpoints) {
    return client.postCollection(
        ServicesAPI.REASONER.IMPLIED_ROLES,
        role,
        Concept.class,
        "includeRelationshipEndpoints",
        Boolean.toString(includeRelationshipEndpoints));
  }

  @Override
  public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {
    throw new KlabUnimplementedException(
        "Semantic search is not exposed by the remote reasoner controller");
  }

  @Override
  public List<ObservationStrategy> computeObservationStrategies(
      Observation observation, ContextScope scope) {
    ResolutionRequest resolutionRequest = new ResolutionRequest();
    resolutionRequest.setObservation(observation);
    resolutionRequest.getResolutionConstraints().addAll(scope.getResolutionConstraints());
    if (scope.getContextObservation() != null && scope.getContextObservation().getId() < 0) {
      resolutionRequest
          .getResolutionConstraints()
          .add(
              ResolutionConstraint.of(
                  ResolutionConstraint.Type.UnresolvedContextObservation,
                  scope.getContextObservation()));
    }
    return client
        .withScope(scope)
        .postCollection(
            ServicesAPI.REASONER.COMPUTE_OBSERVATION_STRATEGIES,
            resolutionRequest,
            ObservationStrategy.class);
  }

  @Override
  public IdentificationStrategy computeIdentificationStrategies(
      Observable observable, ContextScope scope) {
    ResolutionRequest resolutionRequest = new ResolutionRequest();
    resolutionRequest.setObservable(observable);
    resolutionRequest.getResolutionConstraints().addAll(scope.getResolutionConstraints());
    if (scope.getContextObservation() != null && scope.getContextObservation().getId() < 0) {
      resolutionRequest
          .getResolutionConstraints()
          .add(
              ResolutionConstraint.of(
                  ResolutionConstraint.Type.UnresolvedContextObservation,
                  scope.getContextObservation()));
    }
    return client
        .withScope(scope)
        .post(
            ServicesAPI.REASONER.COMPUTE_IDENTIFICATION_STRATEGY,
            resolutionRequest,
            IdentificationStrategy.class);
  }

  @Override
  public Concept buildConcept(ObservableBuildStrategy builder, Scope scope) {
    throw new KlabUnimplementedException(
        "Observable build strategies are not exposed by the remote reasoner controller");
  }

  @Override
  public Observable buildObservable(ObservableBuildStrategy builder, Scope scope) {
    throw new KlabUnimplementedException(
        "Observable build strategies are not exposed by the remote reasoner controller");
  }

  @Override
  public boolean resolves(Semantics toResolve, Semantics candidate, Semantics context) {
    return semanticDistance(toResolve, candidate, context) >= 0;
  }

  @Override
  public ResourceSet loadKnowledge(Worldview worldview, Scope scope) {
    var ret = client.post(ServicesAPI.REASONER.LOAD_KNOWLEDGE, worldview, ResourceSet.class);
    invalidateCaches();
    return ret;
  }

  private void invalidateCaches() {
    concepts.invalidateAll();
    observables.invalidateAll();
    subsumption.invalidateAll();
    semanticDistances.invalidateAll();
  }

  private long currentRevision() {
    Capabilities current = capabilities;
    return current == null ? 0L : current.getKnowledgeRevision();
  }

  private int observableDistance(Semantics target, Semantics other) {
    if (!(target instanceof Observable targetObservable)
        || !(other instanceof Observable otherObservable)) {
      return 0;
    }
    int distance = 0;
    Concept targetObserver = targetObservable.getObserverSemantics();
    Concept otherObserver = otherObservable.getObserverSemantics();
    if (targetObserver != null) {
      if (otherObserver == null) {
        return -50;
      }
      int observerDistance = assertedDistance(otherObserver, targetObserver);
      if (observerDistance < 0) {
        return -50;
      }
      distance += observerDistance;
    } else if (otherObserver != null) {
      distance++;
    }
    if (targetObservable.getContextualization() != null
        && (otherObservable.getContextualization() == null
            || !otherObservable.is(targetObservable.getContextualization()))) {
      return -50;
    }
    var targetMediator = targetObservable.mediator();
    var otherMediator = otherObservable.mediator();
    if (targetMediator != null) {
      if (otherMediator == null || !targetMediator.isCompatible(otherMediator)) {
        return -50;
      }
      if (!targetMediator.equals(otherMediator)) {
        distance++;
      }
    } else if (otherMediator != null) {
      distance++;
    }
    return distance;
  }

  @Override
  public ResourceSet updateKnowledge(ResourceSet changes, UserScope scope) {
    var ret = client.post(ServicesAPI.REASONER.UPDATE_KNOWLEDGE, changes, ResourceSet.class);
    invalidateCaches();
    return ret;
  }

  @Override
  public Concept defineConcept(KimConceptStatement statement, Scope scope) {
    return client.post(ServicesAPI.REASONER.DEFINE_CONCEPT, statement, Concept.class);
  }

  @Override
  public boolean exportNamespace(String namespace, File directory) {
    throw new KlabUnimplementedException(
        "A remote reasoner cannot export into a client-local directory");
  }
}
