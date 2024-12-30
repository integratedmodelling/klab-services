package org.integratedmodelling.common.services.client.reasoner;

import com.google.common.collect.Lists;
import org.integratedmodelling.common.authentication.scope.MessagingChannelImpl;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.DeclarationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.MessagingChannel;
import org.integratedmodelling.klab.api.services.runtime.objects.ScopeRequest;
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

    public ReasonerClient(URL url, Identity identity, KlabService owner, Parameters<Engine.Setting> settings) {
        super(Type.REASONER, url, identity,List.of(), settings, owner);
        // TODO check why the server key is wrong
    }

    public ReasonerClient(URL url, Identity identity, List<ServiceReference> services, Parameters<Engine.Setting> settings, BiConsumer<Channel,
            Message>... listeners) {
        super(Type.REASONER, url, identity, settings, services, listeners);
    }

    @Override
    public Capabilities capabilities(Scope scope) {
        return client.get(ServicesAPI.CAPABILITIES, ReasonerCapabilitiesImpl.class);
    }

    @Override
    public Concept resolveConcept(String definition) {
        return client.post(ServicesAPI.REASONER.RESOLVE_CONCEPT, definition, Concept.class);
    }

    @Override
    public Observable resolveObservable(String definition) {
        return client.post(ServicesAPI.REASONER.RESOLVE_OBSERVABLE, definition, Observable.class);
    }

    @Override
    public Concept declareConcept(KimConcept conceptDeclaration) {
        DeclarationRequest request = new DeclarationRequest();
        request.setConceptDeclaration(conceptDeclaration);
        return client.post(ServicesAPI.REASONER.DECLARE_CONCEPT, request, Concept.class);
    }

    @Override
    public Observable declareObservable(KimObservable observableDeclaration) {
        DeclarationRequest request = new DeclarationRequest();
        request.setObservableDeclaration(observableDeclaration);
        return client.post(ServicesAPI.REASONER.DECLARE_OBSERVABLE, request, Observable.class);
    }

    @Override
    public Concept declareConcept(KimConcept conceptDeclaration,
                                  Map<String, Object> patternVariables) {
        DeclarationRequest request = new DeclarationRequest();
        request.setConceptDeclaration(conceptDeclaration);
        request.getPatternVariables().putAll(patternVariables);
        return client.post(ServicesAPI.REASONER.DECLARE_CONCEPT, request, Concept.class);
    }

    @Override
    public Observable declareObservable(KimObservable observableDeclaration,
                                        Map<String, Object> patternVariables) {
        DeclarationRequest request = new DeclarationRequest();
        request.setObservableDeclaration(observableDeclaration);
        request.getPatternVariables().putAll(patternVariables);
        return client.post(ServicesAPI.REASONER.DECLARE_OBSERVABLE, request, Observable.class);
    }

    @Override
    public boolean is(Semantics conceptImpl, Semantics other) {
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
                other.asConcept(), context == null ? null : context.asConcept()), Integer.class);
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
        return client.post(ServicesAPI.REASONER.COMPATIBLE, List.of(concept, other), Boolean.class);
    }

    @Override
    public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
        return client.post(ServicesAPI.REASONER.CONTEXTUALLY_COMPATIBLE, List.of(focus, context1, context2)
                , Boolean.class);
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
        return List.of();
    }

    @Override
    public Collection<Concept> affected(Semantics semantics) {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public Collection<Concept> created(Semantics semantics) {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public boolean match(Semantics candidate, Semantics pattern) {
        return client.post(ServicesAPI.REASONER.MATCHES, List.of(candidate.asConcept(),
                pattern.asConcept()), Boolean.class);
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
    public List<ObservationStrategy> computeObservationStrategies(Observation observation,
                                                                  ContextScope scope) {
        ResolutionRequest resolutionRequest = new ResolutionRequest();
        resolutionRequest.setObservation(observation);
        resolutionRequest.getResolutionConstraints().addAll(scope.getResolutionConstraints());
        return client.withScope(scope).postCollection(ServicesAPI.REASONER.COMPUTE_OBSERVATION_STRATEGIES,
                resolutionRequest, ObservationStrategy.class);
    }

    @Override
    public Collection<Concept> collectComponents(Concept concept, Collection<SemanticType> type) {
        // TODO Auto-generated method stub
        return List.of();
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
    public ResourceSet loadKnowledge(Worldview worldview, Scope scope) {
        return client.post(ServicesAPI.REASONER.ADMIN.LOAD_KNOWLEDGE, worldview, ResourceSet.class);
    }

    @Override
    public ResourceSet updateKnowledge(ResourceSet changes, UserScope scope) {
        return client.post(ServicesAPI.REASONER.ADMIN.UPDATE_KNOWLEDGE, changes, ResourceSet.class);
    }

    @Override
    public Concept defineConcept(KimConceptStatement statement, Scope scope) {
        return client.post(ServicesAPI.REASONER.ADMIN.DEFINE_CONCEPT, statement, Concept.class);
    }

    @Override
    public boolean exportNamespace(String namespace, File directory) {
        // TODO
        return false;
    }

    /**
     * When called as a slave from a service, add the sessionId parameter to build a peer scope at the remote
     * service side.
     *
     * @param scope a client scope that should record the ID for future communication. If the ID is null, the
     *              call has failed.
     * @return
     */
    @Override
    public String registerSession(SessionScope scope) {

        ScopeRequest request = new ScopeRequest();
        request.setName(scope.getName());

        var hasMessaging =
                scope.getParentScope() instanceof MessagingChannel messagingChannel && messagingChannel.hasMessaging();

        for (var service : scope.getServices(ResourcesService.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResourceServices().add(serviceClient.getUrl());
                }
            }
        }

        for (var service : scope.getServices(Resolver.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResolverServices().add(serviceClient.getUrl());
                }
            }
        }

        if (isLocal() && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        if (getOwnerService() != null) {
            switch (getOwnerService()) {
                case Resolver resolver -> request.getResolverServices().add(resolver.getUrl());
                case RuntimeService runtimeService ->
                        request.getRuntimeServices().add(runtimeService.getUrl());
                case ResourcesService resourcesService ->
                        request.getResourceServices().add(resourcesService.getUrl());
                case Reasoner reasoner -> request.getReasonerServices().add(reasoner.getUrl());
                default -> {
                }
            }
        }

        if (hasMessaging) {
            // TODO setup desired request. This will send no header and use the defaults.
            // Resolver should probably only catch events and errors.
        }

        var ret = client.withScope(scope.getParentScope()).post(ServicesAPI.CREATE_SESSION, request,
                String.class, "id", scope instanceof ServiceSideScope serviceSideScope ?
                                    serviceSideScope.getId() : null);

        var brokerURI = client.getResponseHeader(ServicesAPI.MESSAGING_URN_HEADER);
        if (brokerURI != null && scope instanceof MessagingChannelImpl messagingChannel) {
            var queues = getQueuesFromHeader(scope,
                    client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            messagingChannel.setupMessaging(brokerURI, ret, queues);
        }

        return ret;
    }

    /**
     * When called as a slave from a service, add the sessionId parameter to build a peer scope at the remote
     * service side.
     *
     * @param scope a client scope that should record the ID for future communication. If the ID is null, the
     *              call has failed.
     * @return
     */
    @Override
    public String registerContext(ContextScope scope) {

        ScopeRequest request = new ScopeRequest();
        request.setName(scope.getName());

        var hasMessaging =
                scope.getParentScope() instanceof MessagingChannel messagingChannel && messagingChannel.hasMessaging();

        // The runtime needs to use our resolver(s) and resource service(s), as long as they're accessible.
        // The reasoner can be the runtime's own unless we have locked worldview projects.
        for (var service : scope.getServices(ResourcesService.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResourceServices().add(serviceClient.getUrl());
                }
            }
        }

        for (var service : scope.getServices(Resolver.class)) {
            if (service instanceof ServiceClient serviceClient) {
                // we only send a local URL if we're local ourselves
                if (!serviceClient.isLocal() || (serviceClient.isLocal() && isLocal())) {
                    request.getResolverServices().add(serviceClient.getUrl());
                }
            }
        }

        if (isLocal() && scope.getService(Reasoner.class) instanceof ServiceClient reasonerClient && reasonerClient.isLocal()) {
            request.getReasonerServices().add(reasonerClient.getUrl());
        }

        if (getOwnerService() != null) {
            switch (getOwnerService()) {
                case Resolver resolver -> request.getResolverServices().add(resolver.getUrl());
                case RuntimeService runtimeService ->
                        request.getRuntimeServices().add(runtimeService.getUrl());
                case ResourcesService resourcesService ->
                        request.getResourceServices().add(resourcesService.getUrl());
                case Reasoner reasoner -> request.getReasonerServices().add(reasoner.getUrl());
                default -> {
                }
            }
        }

        if (hasMessaging) {
            // TODO setup desired request. This will send no header and use the defaults.
            // Resolver should probably only catch events and errors.
        }

        var ret = client.withScope(scope.getParentScope()).post(ServicesAPI.CREATE_CONTEXT, request,
                String.class, "id", scope instanceof ServiceSideScope serviceSideScope ?
                                    serviceSideScope.getId() : null);

        if (hasMessaging) {
            var queues = getQueuesFromHeader(scope,
                    client.getResponseHeader(ServicesAPI.MESSAGING_QUEUES_HEADER));
            if (scope instanceof MessagingChannelImpl messagingChannel) {
                messagingChannel.setupMessagingQueues(ret, queues);
            }
        }

        return ret;
    }

}
