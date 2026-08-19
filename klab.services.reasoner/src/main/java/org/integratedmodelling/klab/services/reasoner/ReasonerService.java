package org.integratedmodelling.klab.services.reasoner;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.IntelligentMap;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.ReasonerCapabilitiesImpl;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.ServiceClientCatalog;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ApplicableConcept;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils.CamelCase;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.indexing.Indexer;
import org.integratedmodelling.klab.indexing.SemanticExpression;
import org.integratedmodelling.klab.runtime.language.KimObservableVisitor;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.configuration.ReasonerConfiguration.ProjectConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.integratedmodelling.klab.services.reasoner.owl.Ontology;
import org.integratedmodelling.klab.services.reasoner.owl.Vocabulary;
import org.integratedmodelling.klab.utilities.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReasonerService extends BaseService implements Reasoner, Reasoner.Admin {

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, different realms will not
   * determine incompatibility.
   */
  public static final int ACCEPT_REALM_DIFFERENCES = 0x01;

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, only types that have the exact
   * same core type will be accepted.
   */
  public static final int REQUIRE_SAME_CORE_TYPE = 0x02;

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, types with roles that are more
   * general of the roles in the first concept will be accepted.
   */
  public static final int USE_ROLE_PARENT_CLOSURE = 0x04;

  /**
   * Flag for {@link #compatible(Semantics, Semantics, int)}.
   *
   * <p>If passed to {@link #compatible(Semantics, Semantics, int)}, types with traits that are more
   * general of the traits in the first concept will be accepted.
   */
  public static final int USE_TRAIT_PARENT_CLOSURE = 0x08;

  private final AtomicBoolean consistent = new AtomicBoolean(true);
  private final AtomicLong knowledgeRevision = new AtomicLong();

  private record SubsumptionKey(long revision, Concept concept, Concept other) {}

  private ReasonerConfiguration configuration = new ReasonerConfiguration();
  private final Map<String, String> coreConceptPeers = new HashMap<>();
  private final Map<Concept, Emergence> emergent = new HashMap<>();
  private final IntelligentMap<Set<Emergence>> emergence;
  private ObservationReasoner observationReasoner;
  private Worldview worldview;
  private SyntacticMatcher syntacticMatcher;
  private SemanticMatcher semanticMatcher;
  private List<Notification> advisories = new ArrayList<>();

  /** Caches for concepts and observables. */
  private final Cache<String, Concept> concepts =
      Caffeine.newBuilder().maximumSize(5_000).recordStats().build();

  private final Cache<String, Observable> observables =
      Caffeine.newBuilder().maximumSize(5_000).recordStats().build();

  private final Cache<SubsumptionKey, Boolean> subsumption =
      Caffeine.newBuilder().maximumSize(20_000).recordStats().build();

  Indexer indexer;

  /**
   * Cache for ongoing requests expires in 10 minutes. CHECK this may be less and become
   * configurable.
   */
  private Cache<Integer, SemanticExpression> semanticExpressions =
      Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).build();

  private final OWL owl;
  private final String hardwareSignature = Utils.Names.getHardwareId();
  static Pattern internalConceptPattern = Pattern.compile("[A-Z]+_[0-9]+");

  public boolean derived(Semantics c) {
    return internalConceptPattern.matcher(c.getName()).matches();
  }

  public OWL owl() {
    return owl;
  }

  public long knowledgeRevision() {
    return knowledgeRevision.get();
  }

  private void invalidateSemanticCaches() {
    knowledgeRevision.incrementAndGet();
    concepts.invalidateAll();
    observables.invalidateAll();
    subsumption.invalidateAll();
    if (semanticMatcher != null) {
      semanticMatcher.resetCaches();
    }
    if (syntacticMatcher != null) {
      syntacticMatcher.resetCaches();
    }
  }

  private Concept nothingConcept(String urn) {
    if (this.owl != null) {
      return this.owl.nothing(urn);
    }
    var ret = new ConceptImpl();
    ret.setNonSemanticId(ConceptImpl.NOTHING_ID);
    ret.setUrn("owl:Nothing");
    ret.setNamespace("owl");
    ret.getType().add(SemanticType.NOTHING);
    return ret;
  }

  /**
   * An emergence is the appearance of an observation triggered by another, under the assumptions
   * stated in the worldview. It applies to processes and relationships and its emergent observable
   * can be a configuration, subject or process.
   *
   * @author Ferd
   */
  public class Emergence {

    public Set<Concept> triggerObservables = new LinkedHashSet<>();
    public Concept emergentObservable;
    public String namespaceId;

    public Set<Observation> matches(Concept relationship, ContextScope scope) {

      for (Concept trigger : triggerObservables) {
        Set<Observation> ret = new HashSet<>();
        checkScope(trigger, makeObservationCatalog(scope), relationship, ret);
        if (!ret.isEmpty()) {
          return ret;
        }
      }

      return Collections.emptySet();
    }

    private Map<Observable, Observation> makeObservationCatalog(ContextScope scope) {
      Map<Observable, Observation> ret = new HashMap<>();
      //      for (var observation : scope.query(Observation.class)) {
      //        ret.put(observation.getObservable(), observation);
      //      }
      return ret;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + getEnclosingInstance().hashCode();
      result = prime * result + Objects.hash(emergentObservable, namespaceId, triggerObservables);
      return result;
    }

    private Object getEnclosingInstance() {
      return ReasonerService.this;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      Emergence other = (Emergence) obj;
      if (!getEnclosingInstance().equals(other.getEnclosingInstance())) return false;
      return Objects.equals(emergentObservable, other.emergentObservable)
          && Objects.equals(namespaceId, other.namespaceId)
          && Objects.equals(triggerObservables, other.triggerObservables);
    }

    /*
     * current observable must be one of the triggers, any others need to be in
     * scope
     */
    private void checkScope(
        Concept trigger,
        Map<Observable, Observation> map,
        Concept relationship,
        Set<Observation> obs) {
      if (trigger.is(SemanticType.UNION)) {
        for (Concept trig : operands(trigger)) {
          checkScope(trig, map, relationship, obs);
        }
      } else if (trigger.is(SemanticType.INTERSECTION)) {
        for (Concept trig : operands(trigger)) {
          Set<Observation> oobs = new HashSet<>();
          checkScope(trig, map, relationship, oobs);
          if (oobs.isEmpty()) {
            obs = oobs;
          }
        }
      } else {
        Observation a = map.get(trigger);
        if (a != null) {
          obs.add(a);
        }
      }
    }
  }

  @Autowired
  public ReasonerService(ServiceScope scope, ServiceStartupOptions options) {
    super(scope, Type.REASONER, options);
    this.owl = new OWL(scope);
    this.indexer = new Indexer(scope);
    this.emergence = new IntelligentMap<>(scope);
    readConfiguration(options);
    setComponentRegistry();
    ServiceConfiguration.INSTANCE.setMainService(this);
  }

  private void readConfiguration(ServiceStartupOptions options) {
    File config = BaseService.getFileInConfigurationDirectory(options, "reasoner.yaml");
    if (config.exists() && config.length() > 0 && !options.isClean()) {
      try {
        this.configuration =
            org.integratedmodelling.common.utils.Utils.YAML.load(
                config, ReasonerConfiguration.class);
      } catch (Exception e) {
        Logging.INSTANCE.warn("Configuration file is being reset after corruption was detected");
        Utils.Files.deleteQuietly(config);
        this.configuration = new ReasonerConfiguration();
        this.configuration.setServiceId(UUID.randomUUID().toString());
        Utils.YAML.save(this.configuration, config);
      }
    } else {
      // make an empty config
      this.configuration = new ReasonerConfiguration();
      this.configuration.setServiceId(UUID.randomUUID().toString());
      saveConfiguration();
    }
    // for the local client to know when the service is off
    super.setRuntimeLockfile(this.configuration.getServiceId());
  }

  @Override
  public boolean initializeService() {

    Logging.INSTANCE.setSystemIdentifier("Reasoner service: ");
    for (ProjectConfiguration authority : configuration.getAuthorities()) {
      loadAuthority(authority);
    }

    this.observationReasoner = new ObservationReasoner(this);
    this.syntacticMatcher = new SyntacticMatcher(this);
    this.semanticMatcher = new SemanticMatcher(this);

    return true;
  }

  @Override
  public void runAdditionalTimedTasks() {
    try {
      checkWorldview();
    } catch (Throwable t) {
      Logging.INSTANCE.error(t);
    }
  }

  /**
   * TODO this must become more dynamic and integrate existing worldview contributors. Priority must
   * be given to local sourcing (already built into ResourceSet but local may come in later).
   *
   * @return
   */
  boolean checkWorldview() {

    if (this.worldview != null) {
      return true;
    }

    var ret = false;
    for (var resources : serviceScope().getServices(ResourcesService.class)) {

      /*
       * FIXME this makes the local reasoner only initialize from a local resources service, which
       *  is correct if the modeler is able to operate on the worldview, but it should be
       *  configurable or linked to groups.
       */
      if (Utils.URLs.isLocalHost(this.getUrl()) && !Utils.URLs.isLocalHost(resources.getUrl())) {
        continue;
      }

      if (resources.status().isAvailable()
          && resources.capabilities(serviceScope()).isWorldviewProvider()) {

        // FIXME switch to using the full service list and use the updated worldview retrieval
        // logic.
        // FIXME set up a worldview update check at a configurable interval in timedTasks

        int maxAttempts = 5;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
          // Worldview discovery is public during service startup, so no user scope exists yet.
          worldview = resources.list(Worldview.class, null).stream().findFirst().orElse(null);
          if (worldview != null) {
            serviceScope().info("Worldview retrieved after " + attempt + " attempts");
            break;
          }
          try {
            Thread.sleep(1000); // wait 1 second before retry
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
          }
        }
        var notifications = loadKnowledge(worldview, serviceScope());
        if (!Utils.Resources.hasErrors(notifications)) {
          setOperational(true);
          ret = true;
          serviceScope().info("Worldview loaded into local reasoner");

          // TODO if there were previous logical notifications they should be deleted now

          /*
          We stop at the first worldview that loads. All available worldiews should be
          synchronized and mirrored automatically, and two services with different worldviews
          accessible to the same reasoner is a configuration abomination that should never happen.
           */
          break;
        }
      }
    }
    return ret;
  }

  @Override
  public boolean operationalizeService() {
    return worldview != null;
  }

  @SuppressWarnings("unchecked")
  private void loadAuthority(ProjectConfiguration authority) {
    if (authority.getUrl().startsWith("classpath:")) {
      try {
        Logging.INSTANCE.info(
            "loading authority " + authority.getProject() + " from local " + "classpath");
        Class<? extends Authority> cls =
            (Class<? extends Authority>)
                Class.forName(authority.getUrl().substring(("classpath" + ":").length()));
        ServiceConfiguration.INSTANCE.registerAuthority(cls.getDeclaredConstructor().newInstance());
        Logging.INSTANCE.info(
            "Authority "
                + authority.getProject()
                + " ready for "
                + (authority.isServe() ? "global" : "local")
                + " use");
      } catch (Exception e) {
        Logging.INSTANCE.error(e);
      }
    }
  }

  private void saveConfiguration() {
    File config = BaseService.getFileInConfigurationDirectory(startupOptions, "reasoner.yaml");
    org.integratedmodelling.common.utils.Utils.YAML.save(this.configuration, config);
  }

  @Override
  public Concept defineConcept(KimConceptStatement statement, Scope scope) {
    return build(
        statement,
        this.owl.requireOntology(statement.getNamespace(), OWL.DEFAULT_ONTOLOGY_PREFIX),
        null,
        scope);
  }

  @Override
  public ServiceStatus status() {
    var ret = super.status();
    ret.getAdvisories().addAll(this.advisories);
    if (!this.consistent.get()) {
      ret.getAdvisories().add(Notification.error("Reasoner knowledge base is inconsistent"));
    }
    return ret;
  }

  @Override
  public Concept resolveConcept(String definition) {
    Objects.requireNonNull(definition, "definition");
    return concepts.get(definition, this::resolveConceptInternal);
  }

  @Override
  public Observable resolveObservable(String definition) {
    Objects.requireNonNull(definition, "definition");
    return observables.get(definition, this::resolveObservableInternal);
  }

  public Concept resolveConceptInternal(String definition) {
    Concept ret = null;
    if (Urn.isAtomicConcept(definition)) {
      ret = owl.getConcept(definition);
    } else {
      // FIXME this should use all services and resolve in user scope
      KimConcept parsed =
          serviceScope().getService(ResourcesService.class).declareConcept(definition);
      if (parsed != null) {
        ret = declareConcept(parsed);
      }
    }
    return ret == null ? owl.nothing(definition) : ret;
  }

  public Observable resolveObservableInternal(String definition) {
    Observable ret = null;
    // FIXME the service should be passed
    KimObservable parsed =
        serviceScope().getService(ResourcesService.class).declareObservable(definition);
    if (parsed != null) {
      ret = declareObservable(parsed);
    }
    return ret == null ? Observable.nothing("owl:Nothing") : ret;
  }

  @Override
  public Collection<Concept> operands(Semantics target) {
    List<Concept> ret = new ArrayList<>();
    if (target.is(SemanticType.UNION) || target.is(SemanticType.INTERSECTION)) {
      ret.addAll(this.owl.getOperands(target.asConcept()));
    } else {
      ret.add(target.asConcept());
    }

    return ret;
  }

  @Override
  public Collection<Concept> children(Semantics target) {
    return this.owl.getChildren(target.asConcept());
  }

  public Map<Concept, Collection<Observation>> emergentResolvables(
      Observation trigger, ContextScope scope) {

    Map<Concept, Collection<Observation>> ret = new HashMap<>();
    Collection<Emergence> emergents = this.emergence.get(trigger.getObservable().getSemantics());

    // if (!(scope instanceof IRuntimeScope) || ((IRuntimeScope)
    // scope).getActuator() == null) {
    // return Collections.emptyMap();
    // }
    //
    // Mode mode = ((IRuntimeScope) scope).getActuator().getMode();
    //
    // /*
    // * Skip a search in the map if we can't trigger anything.
    // */
    // if (!trigger.getObservable().is(Type.QUALITY)
    // && !(trigger.getObservable().is(Type.RELATIONSHIP) && mode ==
    // Mode.INSTANTIATION)) {
    // return Collections.emptyMap();
    // }
    //
    // Map<IConcept, Collection<IObservation>> ret = new HashMap<>();
    // Collection<Emergence> emergents =
    // this.emergence.get(trigger.getObservable().getType());
    //
    // if (emergents != null) {
    //
    // for (Emergence emergent : emergents) {
    //
    // Collection<IObservation> match =
    // emergent.matches(trigger.getObservable().getType(),
    // (IRuntimeScope) scope);
    //
    // /*
    // * if process or configuration, update and skip if the scope already contains
    // * the emergent observation
    // */
    // if (emergent.emergentObservable.is(Type.PROCESS)
    // || emergent.emergentObservable.is(Type.CONFIGURATION)) {
    // if (((IRuntimeScope) scope).getCatalog()
    // .get(new ObservedConcept(emergent.emergentObservable)) != null) {
    // /*
    // * TODO update with the new observation(s)! API to be defined
    // */
    // if (((IDirectObservation) trigger).getOriginatingPattern() != null) {
    // ((IDirectObservation) trigger).getOriginatingPattern().update(trigger);
    // return ret;
    // }
    // }
    // }
    //
    // ret.put(emergent.emergentObservable, match);
    // }
    // }
    return ret;
  }

  @Override
  public Collection<Concept> parents(Semantics target) {
    return this.owl.getParents(target.asConcept());
  }

  @Override
  public Collection<Concept> allChildren(Semantics target) {
    Set<Concept> ret = collectChildren(target, new HashSet<>());
    ret.remove(target.asConcept());
    return ret;
  }

  private Set<Concept> collectChildren(Semantics target, Set<Concept> hashSet) {

    for (Concept c : children(target)) {
      if (hashSet.add(c)) {
        collectChildren(c, hashSet);
      }
    }
    return hashSet;
  }

  @Override
  public Collection<Concept> allParents(Semantics target) {
    return allParentsInternal(target, new HashSet<Concept>());
  }

  private Collection<Concept> allParentsInternal(Semantics target, Set<Concept> seen) {

    Set<Concept> concepts = new HashSet<>();

    if (seen.contains(target.asConcept())) {
      return concepts;
    }

    seen.add(target.asConcept());

    for (Concept c : parents(target)) {
      concepts.add(c);
      concepts.addAll(allParentsInternal(c, seen));
    }

    return concepts;
  }

  @Override
  public Collection<Concept> closure(Semantics target) {
    return this.owl.getSemanticClosure(target.asConcept());
  }

  @Override
  public Concept baseSubstantialType(Semantics concept, Scope scope) {
    Concept original = concept == null ? null : concept.asConcept();
    if (original == null) {
      return nothingConcept("null substantial concept");
    }

    if (!SemanticType.isEnumerableSubstantial(original.getType())) {
      return nothingConcept(original.getUrn());
    }

    try {
      var builder =
          SemanticsBuilder.create(original, this, scope == null ? serviceScope() : scope)
              .without(SemanticRole.TRAIT)
              .without(SemanticRole.ROLE)
              .without(SemanticRole.modifiers());
      /*
       * TODO recognize individual identities and add their lexical root. This used to iterate
       * directIdentities(concept), but the loop had no effect and forced an additional OWL lookup.
       */
      Concept ret = builder.buildConcept();
      return ensureCountableSubstantial(
          ret == null || ret.is(SemanticType.NOTHING) ? original : ret);
    } catch (RuntimeException t) {
      Logging.INSTANCE.warn(
          "Could not establish base substantial type for " + original.getUrn() + ": " + t);
      return ensureCountableSubstantial(original);
    }
  }

  private Concept ensureCountableSubstantial(Concept concept) {
    if (concept != null
        && SemanticType.isEnumerableSubstantial(concept.getType())
        && !concept.is(SemanticType.COUNTABLE)) {
      Logging.INSTANCE.warn(
          "Restoring missing COUNTABLE semantic type on substantial {}", concept.getUrn());
      concept.getType().add(SemanticType.COUNTABLE);
    }
    return concept;
  }

  @Override
  public boolean resolves(Semantics toResolve, Semantics other, Semantics context) {

    /*
    TODO if these are observables, the observer also must be considered and matched by semantic distance.

    TODO the observable now just carries an Observer identity but NOT a contract about the type of
     observer it
    accepts. This is necessary for models to be able to declare their observers before they are actually
     observed.

          In each observable, the actual Observer (if semantic) takes over the contract when matching. This,
          with the
          fact that the observer may be a mere Identity, makes for a pretty complicated comparison.

    The observer instance is an Identity - if that's also a DirectObservation use semantics, otherwise
    match with
    equals().

     An incoming without observer will match one with, but not the other way around unless the observers
      are
     compatible.
     */

    return semanticDistance(toResolve, other, context) >= 0;
  }

  /*
   * TODO this is used in resolution and can be work, so it should cache
   *
   * FIXME concrete observables must remain the same; inherent and clauses should be generalized to all parents
   * FIXME value operators should be all added back as they are
   *
   * @param semantics
   * @return
   */
  @Override
  public Collection<Concept> resolving(Semantics semantics) {

    var concept = semantics.asConcept();
    Set<Concept> ret = new LinkedHashSet<>();

    ret.add(concept);

    var base =
        SemanticsBuilder.create(concept.asConcept(), this, serviceScope())
            .without(SemanticRole.INHERENT)
            .without(SemanticRole.modifiers())
            .buildConcept();

    var inherent = directInherent(semantics);
    if (inherent != null) {
      ret.addAll(
          allParents(inherent).stream()
              .map(
                  ctx -> SemanticsBuilder.create(base, this, serviceScope()).of(ctx).buildConcept())
              .toList());
    }

    // TODO do the same with the clauses
    for (var role : SemanticClause.values()) {}

    return ret;
  }

  @Override
  public int semanticDistance(Semantics target, Semantics other) {
    return semanticDistance(target, other, null);
  }

  @Override
  public int semanticDistance(Semantics target, Semantics other, Semantics context) {
    int distance = semanticMatcher.semanticDistance(target, other, context);
    if (distance < 0) {
      return distance;
    }
    int observableDistance = observableDistance(target, other);
    return observableDistance < 0 ? observableDistance : distance + observableDistance;
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
      int observerDistance = semanticMatcher.assertedDistance(otherObserver, targetObserver);
      if (observerDistance < 0) {
        return -50;
      }
      distance += observerDistance;
    } else if (otherObserver != null) {
      distance++;
    }

    if (targetObservable.getContextualization() != null) {
      if (otherObservable.getContextualization() == null
          || !otherObservable.is(targetObservable.getContextualization())) {
        return -50;
      }
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
  public Concept coreObservable(Semantics first) {
    Concept ret = first.asConcept();
    Set<String> visited = new HashSet<>();
    while (ret != null && visited.add(ret.getUrn())) {
      String next = ret.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY, String.class);
      if (next == null) {
        return ret;
      }
      ret = resolveConcept(next);
    }
    return ret == null ? nothingConcept(first.getUrn()) : ret;
  }

  @Override
  public Pair<Concept, List<SemanticType>> splitOperators(Semantics concept) {

    Concept cret = concept.asConcept();
    List<SemanticType> types = new ArrayList<>();
    Set<SemanticType> type = Sets.intersection(cret.getType(), SemanticType.OPERATOR_TYPES);

    while (type.size() > 0) {
      types.add(type.iterator().next());
      Concept ccret = describedType(cret);
      if (ccret == null) {
        break;
      } else {
        cret = ccret;
      }
      type = Sets.intersection(cret.getType(), SemanticType.OPERATOR_TYPES);
    }

    return Pair.of(cret, types);
  }

  @Override
  public Concept describedType(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Collection<Concept> traits(Semantics concept) {
    Set<Concept> ret = new HashSet<>();
    ret.addAll(
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY)));
    ret.addAll(
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY)));
    ret.addAll(
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY)));
    return ret;
  }

  @Override
  public int assertedDistance(Semantics from, Semantics to) {
    return semanticMatcher.assertedDistance(from, to);
  }

  @Override
  public boolean hasTrait(Semantics concept, Concept trait) {
    for (Concept c : traits(concept)) {
      if (is(c, trait)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Collection<Concept> roles(Semantics concept) {
    return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ROLE_PROPERTY));
  }

  @Override
  public boolean hasRole(Semantics concept, Concept role) {
    for (Concept c : roles(concept)) {
      if (is(c, role)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Concept directInherent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_INHERENT_TO_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept inherent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_INHERENT_TO_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept directGoal(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_PURPOSE_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept goal(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_PURPOSE_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept directCooccurrent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.OCCURS_DURING_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept directCausant(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSANT_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept directCaused(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSED_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept directAdjacent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_ADJACENT_TO_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept directCompresent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_COMPRESENT_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept directRelativeTo(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_COMPARED_TO_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept cooccurrent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.OCCURS_DURING_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept causant(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSANT_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept caused(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_CAUSED_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept adjacent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_ADJACENT_TO_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept compresent(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_COMPRESENT_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public Concept relativeTo(Semantics concept) {
    Collection<Concept> cls =
        this.owl.getRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.IS_COMPARED_TO_PROPERTY));
    return cls.isEmpty() ? null : cls.iterator().next();
  }

  @Override
  public String displayLabel(Semantics concept) {
    String ret = displayName(concept);
    if (!ret.contains(" ")) {
      ret = StringUtils.capitalize(CamelCase.toLowerCase(ret, ' '));
    }
    return ret;
  }

  @Override
  public String displayName(Semantics semantics) {
    return semantics instanceof Observable
        ? observableDisplayName((Observable) semantics)
        : conceptDisplayName(semantics.asConcept());
  }

  private String conceptDisplayName(Concept t) {

    String ret = t.getMetadata().get(NS.DISPLAY_LABEL_PROPERTY, String.class);

    if (ret == null) {
      ret = t.getMetadata().get(Metadata.DC_LABEL, String.class);
    }
    if (ret == null) {
      ret = t.getName();
    }
    if (ret.startsWith("i")) {
      ret = ret.substring(1);
    }
    return ret;
  }

  private String observableDisplayName(Observable o) {

    StringBuilder ret = new StringBuilder(conceptDisplayName(o.asConcept()));

    //    for (Pair<ValueOperator, Object> operator : o.getValueOperators()) {
    //
    //      ret.append(StringUtils.capitalize(operator.getFirst().declaration.replace(' ', '_')));
    //
    //      if (operator.getSecond() instanceof KimConcept concept) {
    //        ret.append(conceptDisplayName(declareConcept(concept)));
    //      } else if (operator.getSecond() instanceof KimObservable observable) {
    //        ret.append(observableDisplayName(declareObservable(observable)));
    //      } else {
    //        ret.append("_").append(operator.getSecond().toString().replace(' ', '_'));
    //      }
    //    }
    return ret.toString();
  }

  @Override
  public String style(Concept concept) {
    throw new KlabUnimplementedException("Reasoner concept styling is not supported");
  }

  @Override
  public Capabilities capabilities(Scope scope) {

    var ret = new ReasonerCapabilitiesImpl();

    ret.setWorldviewId(worldview == null ? null : worldview.getWorldviewId());
    ret.setServiceName(serviceName);
    ret.setType(Type.REASONER);
    ret.setUrl(getUrl());
    ret.setServerId(hardwareSignature == null ? null : ("REASONER_" + hardwareSignature));
    ret.setServiceId(configuration.getServiceId());
    ret.getExportSchemata().putAll(ResourceTransport.INSTANCE.getExportSchemata());
    ret.getImportSchemata().putAll(ResourceTransport.INSTANCE.getImportSchemata());
    ret.getComponents().addAll(getComponentRegistry().getComponents(scope));
    ret.setConsistent(this.consistent.get());
    ret.setKnowledgeRevision(knowledgeRevision());
    return ret;
  }

  @Override
  public String serviceId() {
    return configuration.getServiceId();
  }

  @Override
  public Collection<Concept> identities(Semantics concept) {
    return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY));
  }

  @Override
  public Collection<Concept> attributes(Semantics concept) {
    return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY));
  }

  @Override
  public Collection<Concept> realms(Semantics concept) {
    return this.owl.getRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY));
  }

  @Override
  public Concept lexicalRoot(Semantics trait) {

    if (CoreOntology.isCore(trait.asConcept())) {
      return trait.asConcept();
    }

    String orig = trait.getMetadata().get(CoreOntology.NS.ORIGINAL_TRAIT, String.class);
    if (orig != null) {
      trait = this.owl.getConcept(orig);
    }

    /*
     * there should only be one of these or none.
     */
    if (trait.getMetadata().get(NS.BASE_DECLARATION) != null) {
      return (Concept) trait;
    }

    for (Concept c : parents(trait)) {
      Concept r = lexicalRoot(c);
      if (r != null) {
        return r;
      }
    }

    return null;
  }

  @Override
  public boolean hasDirectTrait(Semantics type, Concept trait) {

    for (Concept c : directTraits(type)) {
      if (is(trait, c)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean hasDirectRole(Semantics type, Concept trait) {
    for (Concept c : directRoles(type)) {
      if (is(trait, c)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Collection<Concept> directTraits(Semantics concept) {
    Set<Concept> ret = new HashSet<>();
    ret.addAll(
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY)));
    ret.addAll(
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY)));
    ret.addAll(
        this.owl.getDirectRestrictedClasses(
            concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY)));
    return ret;
  }

  @Override
  public Collection<Concept> directAttributes(Semantics concept) {
    return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY));
  }

  @Override
  public Collection<Concept> directIdentities(Semantics concept) {
    return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY));
  }

  @Override
  public Collection<Concept> directRealms(Semantics concept) {
    return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY));
  }

  @Override
  public Concept negated(Concept concept) {
    return this.owl.makeNegation(concept.asConcept());
  }

  @Override
  public SemanticType observableType(Semantics observable, boolean acceptTraits) {
    if (observable instanceof Observable
        && ((Observable) observable).getArtifactType().equals(Artifact.Type.VOID)) {
      return SemanticType.NOTHING;
    }
    Set<SemanticType> type = EnumSet.copyOf(observable.asConcept().getType());
    type.retainAll(SemanticType.BASE_MODELABLE_TYPES);
    if (type.size() != 1) {
      throw new IllegalArgumentException(
          "trying to extract the observable type from non-observable " + observable);
    }
    return type.iterator().next();
  }

  @Override
  public Concept relationshipSource(Semantics relationship) {
    Collection<Concept> ret = relationshipSources(relationship);
    return ret.isEmpty() ? null : ret.iterator().next();
  }

  @Override
  public Collection<Concept> relationshipSources(Semantics relationship) {
    return org.integratedmodelling.common.utils.Utils.Collections.join(
        this.owl.getDirectRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_SOURCE_PROPERTY)),
        this.owl.getRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_SOURCE_PROPERTY)));
  }

  @Override
  public Concept relationshipTarget(Semantics relationship) {
    Collection<Concept> ret = relationshipTargets(relationship);
    return ret.isEmpty() ? null : ret.iterator().next();
  }

  @Override
  public Collection<Concept> relationshipTargets(Semantics relationship) {
    return org.integratedmodelling.common.utils.Utils.Collections.join(
        this.owl.getDirectRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_DESTINATION_PROPERTY)),
        this.owl.getRestrictedClasses(
            relationship.asConcept(), this.owl.getProperty(NS.IMPLIES_DESTINATION_PROPERTY)));
  }

  @Override
  public boolean satisfiable(Semantics ret) {
    return this.owl.isSatisfiable(ret);
  }

  @Override
  public Collection<Concept> applicableObservables(Concept main) {
    return this.owl.getRestrictedClasses(main, this.owl.getProperty(NS.APPLIES_TO_PROPERTY));
  }

  @Override
  public Collection<Concept> directRoles(Semantics concept) {
    return this.owl.getDirectRestrictedClasses(
        concept.asConcept(), this.owl.getProperty(NS.HAS_ROLE_PROPERTY));
  }

  @Override
  public ResourceSet loadKnowledge(Worldview worldview, Scope scope) {

    List<Notification> ret = new ArrayList<>();

    // this remains the only service whose initialization depends on others, so we set it up
    // manually when the specific conditions for initialization are met
    if (observationReasoner == null) {
      if (!initializeService()) {
        Logging.INSTANCE.error(
            "failed to initialize the observation reasoner and related services");
        return ResourceSet.empty(Notification.error("Failed to initialize reasoner"));
      }
      setInitialized(true);
    }

    scope = getScopeManager().collectMessagePayload(scope, Notification.class, ret);

    if (worldview == null || worldview.isEmpty()) {
      return ResourceSet.empty();
    }

    invalidateSemanticCaches();
    this.worldview = worldview;
    this.observationReasoner = new ObservationReasoner(this);

    this.owl.initialize(worldview.getOntologies().getFirst());
    for (KimOntology ontology : worldview.getOntologies()) {
      for (var statement : ontology.getStatements()) {
        defineConcept(statement, scope);
      }
      this.owl.registerWithReasoner(ontology);
    }
    this.owl.flushReasoner();
    for (var strategyDocument : worldview.getObservationStrategies()) {
      for (var strategy : strategyDocument.getStatements()) {
        observationReasoner.registerStrategy(strategy);
      }
    }
    observationReasoner.initializeStrategies();

    // assess consistent status and if consistent, set operational
    boolean logicallyConsistent = !this.owl.isOn() || this.owl.isConsistent();
    this.consistent.set(!Utils.Notifications.hasErrors(ret) && logicallyConsistent);
    if (!logicallyConsistent) {
      ret.add(Notification.error("Reasoner knowledge base is logically inconsistent"));
    }
    this.advisories.addAll(ret);

    setOperational(this.consistent.get());

    return Utils.Resources.createFromLexicalNotifications(ret);
  }

  @Override
  public synchronized ResourceSet updateKnowledge(ResourceSet changes, UserScope scope) {

    var ownResources = scope.getService(ResourcesService.class);
    Map<URL, ResourcesService> services = new HashMap<>();

    serviceScope().setMaintenanceMode(true);

    invalidateSemanticCaches();

    boolean inconsistent = false;

    try {
      /*
      release all ontologies first. This should not be necessary but it prevents a NPE in case there are
      forward references - which the syntax should flag as errors, but doesn't at the moment.
       */
      for (var resource : changes.getOntologies()) {
        var ontology = this.owl.getOntology(resource.getResourceUrn());
        if (ontology != null) {
          this.owl.releaseOntology(ontology);
        }
      }

      for (var resource : changes.getOntologies()) {

        var resourceService = ownResources;
        if (!resourceService.capabilities(scope).getServiceId().equals(resource.getServiceId())) {
          resourceService =
              services.computeIfAbsent(
                  changes.getServices().get(resource.getServiceId()),
                  url ->
                      ServiceClientCatalog.INSTANCE.getService(
                          url, settingsForSlaveServices, scope, ResourcesService.class));
        }

        var notifications = new ArrayList<Notification>();
        var parsingScope =
            getScopeManager().collectMessagePayload(scope, Notification.class, notifications);
        var ontology =
            resourceService.retrieve(resource.getResourceUrn(), KimOntology.class, parsingScope);
        for (var statement : ontology.getStatements()) {
          defineConcept(statement, parsingScope);
        }
        this.owl.registerWithReasoner(ontology);
        resource.getNotifications().addAll(notifications);

        if (Utils.Notifications.hasErrors(notifications)) {
          inconsistent = true;
        }
      }

      for (var resource : changes.getObservationStrategies()) {

        var resourceService = ownResources;
        if (!resourceService.capabilities(scope).getServiceId().equals(resource.getServiceId())) {
          resourceService =
              services.computeIfAbsent(
                  changes.getServices().get(resource.getServiceId()),
                  url ->
                      ServiceClientCatalog.INSTANCE.getService(
                          url, settingsForSlaveServices, scope, ResourcesService.class));
        }

        var notifications = new ArrayList<Notification>();
        var parsingScope =
            getScopeManager().collectMessagePayload(scope, Notification.class, notifications);
        var observationStrategyDocument =
            resourceService.retrieve(
                resource.getResourceUrn(), KimObservationStrategyDocument.class, parsingScope);

        observationReasoner.releaseNamespace(observationStrategyDocument.getUrn());
        for (var strategy : observationStrategyDocument.getStatements()) {
          observationReasoner.registerStrategy(strategy);
        }
        observationReasoner.initializeStrategies();

        resource.getNotifications().addAll(notifications);
      }
      this.owl.flushReasoner();
      if (this.owl.isOn() && !this.owl.isConsistent()) {
        inconsistent = true;
        this.advisories.add(
            Notification.error("Reasoner knowledge base is logically inconsistent"));
      }
    } catch (Throwable t) {
      inconsistent = true;
      Logging.INSTANCE.error("failed to update knowledge", t);
      this.advisories.add(Notification.error(t));
    } finally {
      serviceScope().setMaintenanceMode(false);
    }

    this.consistent.set(!inconsistent);

    return changes;
  }

  @Override
  public boolean is(Semantics concept, Semantics other) {

    Objects.requireNonNull(concept, "concept");
    Objects.requireNonNull(other, "other");

    if (concept == other || concept.equals(other)) {
      return true;
    }

    var key = new SubsumptionKey(knowledgeRevision(), concept.asConcept(), other.asConcept());
    return subsumption.get(key, ignored -> computeSubsumption(concept, other));
  }

  private boolean computeSubsumption(Semantics concept, Semantics other) {

    if (concept.asConcept().isCollective() != other.asConcept().isCollective()) {
      return false;
    }

    // non-semantic concepts can only be the same thing.
    if (concept instanceof ConceptImpl concept1 && concept1.getNonSemanticId() < 0) {
      return other instanceof ConceptImpl concept2 && concept2.getUrn().equals(concept1.getUrn());
    }

    /*
     * first use "isn't" based on the enum types to quickly cut out those that don't
     * match. Also works with concepts in different ontologies that have the same
     * definition.
     */
    if (inWorldview(concept, other)) {
      var fundamentalType = SemanticType.fundamentalType(other.asConcept().getType());
      if (fundamentalType != null
          && !Sets.intersection(concept.asConcept().getType(), other.asConcept().getType())
              .contains(fundamentalType)) {
        return false;
      }
    }

    /*
     * Speed up checking for logical expressions without forcing the reasoner to
     * compute complex logics.
     */
    Boolean logicalResult = logicalSubsumption(concept, other, this::operands, this::is);
    if (logicalResult != null) {
      return logicalResult;
    }

    if (this.owl.isOn()) {
      return this.owl.is(concept.asConcept(), other.asConcept());
    }
    Collection<Concept> collection = allParents(concept);
    return collection.contains(other.asConcept());
  }

  static Boolean logicalSubsumption(
      Semantics concept,
      Semantics other,
      Function<Semantics, Collection<Concept>> operandProvider,
      BiPredicate<Semantics, Semantics> subsumption) {
    if (concept.is(SemanticType.UNION)) {
      for (Concept operand : operandProvider.apply(concept)) {
        if (!subsumption.test(operand, other)) {
          return false;
        }
      }
      return true;
    }
    if (concept.is(SemanticType.INTERSECTION)) {
      for (Concept operand : operandProvider.apply(concept)) {
        if (subsumption.test(operand, other)) {
          return true;
        }
      }
    }
    return null;
  }

  @Override
  public Semantics domain(Semantics conceptImpl) {
    if (conceptImpl == null) {
      return null;
    }
    ArrayDeque<Concept> queue = new ArrayDeque<>();
    Set<Concept> visited = new HashSet<>();
    queue.add(conceptImpl.asConcept());
    while (!queue.isEmpty()) {
      Concept current = queue.removeFirst();
      if (!visited.add(current)) {
        continue;
      }
      if (current.is(SemanticType.DOMAIN)) {
        return current;
      }
      queue.addAll(parents(current));
    }
    return null;
  }

  public Concept declareConcept(KimConcept conceptDeclaration) {
    return declare(
        conceptDeclaration,
        this.owl.requireOntology(conceptDeclaration.getNamespace()),
        serviceScope());
  }

  public Observable declareObservable(KimObservable observableDeclaration) {
    return declare(
        observableDeclaration,
        this.owl.requireOntology(observableDeclaration.getSemantics().getNamespace()),
        serviceScope());
  }

  public Observable declareObservable(
      KimObservable observableDeclaration, Map<String, Object> patternVariables) {

    if (observableDeclaration.getPattern() == null) {
      return declareObservable(observableDeclaration);
    }
    String urn = observableDeclaration.getPattern();
    for (var key : observableDeclaration.getPatternVariables()) {
      var value = patternVariables.get(key);
      if (value == null) {
        return null;
      }
      String valueCode =
          switch (value) {
            case KimConcept kimConcept -> /*"(" + */ kimConcept.getUrn() /* + ")"*/;
            case KimObservable kimConcept -> /*"(" + */ kimConcept.getUrn() /*+ ")"*/;
            case Concept kimConcept -> /*"(" + */ kimConcept.getUrn() /* + ")"*/;
            case Observable kimConcept -> /*"(" + */ kimConcept.getUrn() /* + ")"*/;
            case String string -> "\"" + Utils.Escape.forDoubleQuotedString(string, false) + "\"";
            default -> value.toString();
          };
      urn = urn.replace("$:" + key, valueCode);
    }
    return resolveObservable(urn);
  }

  //  @Override
  public Concept declareConcept(
      KimConcept observableDeclaration, Map<String, Object> patternVariables) {

    if (!observableDeclaration.isPattern()) {
      return declareConcept(observableDeclaration);
    }
    String urn = observableDeclaration.getUrn();
    for (var key : observableDeclaration.getPatternVariables()) {
      var value = patternVariables.get(key);
      if (value == null) {
        return null;
      }
      String valueCode =
          switch (value) {
            case KimConcept kimConcept -> "(" + kimConcept.getUrn() + ")";
            case KimObservable kimConcept -> "(" + kimConcept.getUrn() + ")";
            case Concept kimConcept -> "(" + kimConcept.getUrn() + ")";
            case Observable kimConcept -> "(" + kimConcept.getUrn() + ")";
            case String string -> "\"" + Utils.Escape.forDoubleQuotedString(string, false) + "\"";
            default -> value.toString();
          };
      urn = urn.replace("$:" + key, valueCode);
    }
    return resolveConcept(urn);
  }

  @Override
  public boolean compatible(Semantics o1, Semantics o2) {
    return compatible(o1, o2, 0);
  }

  // @Override
  public boolean compatible(Semantics o1, Semantics o2, int flags) {

    if (o1 == o2 || o1.equals(o2)) {
      return true;
    }

    boolean mustBeSameCoreType = (flags & REQUIRE_SAME_CORE_TYPE) != 0;
    boolean useRoleParentClosure = (flags & USE_ROLE_PARENT_CLOSURE) != 0;
    // boolean acceptRealmDifferences = (flags & ACCEPT_REALM_DIFFERENCES) != 0;

    // TODO unsupported
    boolean useTraitParentClosure = (flags & USE_TRAIT_PARENT_CLOSURE) != 0;

    /**
     * The check of fundamental types is only performed when both concepts are inside the worldview.
     */
    if (inWorldview(o1, o2)) {
      if ((!o1.is(SemanticType.OBSERVABLE) || !o2.is(SemanticType.OBSERVABLE))
          && !(o1.is(SemanticType.CONFIGURATION) && o2.is(SemanticType.CONFIGURATION))) {
        return false;
      }
    }

    /**
     * first compatibility check is a simple subsumption if o1 is abstract, or a full core
     * observability check if not.
     */
    if (o2.isAbstract()) {

      if (is(o2, o1)) {
        return false;
      }

    } else {

      Concept core1 = coreObservable(o1);
      Concept core2 = coreObservable(o2);

      if (core1 == null
          || core2 == null
          || !(mustBeSameCoreType ? core1.equals(core2) : is(core1, core2))) {
        return false;
      }
    }

    Concept ic1 = inherent(o1);
    Concept ic2 = inherent(o2);

    // same with inherency
    if (ic1 == null && ic2 != null) {
      return false;
    }
    if (ic1 != null && ic2 != null) {
      if (!compatible(ic1, ic2)) {
        return false;
      }
    }

    for (Concept t : traits(o2)) {
      boolean ok = hasTrait(o1, t);
      if (!ok && useTraitParentClosure) {
        ok = hasDirectTrait(o1, t);
      }
      if (!ok) {
        return false;
      }
    }

    for (Concept t : roles(o2)) {
      boolean ok = hasRole(o1, t);
      if (!ok && useRoleParentClosure) {
        ok = hasParentRole(o1, t);
      }
      if (!ok) {
        return false;
      }
    }

    return true;
  }

  /**
   * True if the concept comes from a loaded worldview. The alternative is that it comes from a core
   * imported ontology, and possibly (in the future) from a conceptual extent ontology.
   *
   * @param semantics
   * @return
   */
  private boolean inWorldview(Semantics... semantics) {
    for (Object o : semantics) {
      if (switch (o) {
        case ConceptImpl concept -> concept.getType().isEmpty();
        case KimConceptImpl concept -> concept.getType().isEmpty();
        case ObservableImpl observable -> observable.getSemantics().getType().isEmpty();
        case KimObservableImpl observable -> observable.getSemantics().getType().isEmpty();
        default -> false;
      }) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean hasParentRole(Semantics o1, Concept t) {
    for (Concept role : roles(o1)) {
      if (is(t, role)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
    boolean ret = compatible(context1, context2, 0);
    if (!ret && occurrent(context1)) {
      ret = affectedBy(focus, context1);
      Concept itsContext = inherent(context1);
      if (!ret) {
        if (itsContext != null) {
          ret = compatible(itsContext, context2);
        }
      }
    }
    return ret;
  }

  @Override
  public boolean occurrent(Semantics context1) {
    return context1 != null
        && (context1.is(SemanticType.PROCESS) || context1.is(SemanticType.EVENT));
  }

  @Override
  public Collection<Concept> affectedOrCreated(Semantics semantics) {
    Set<Concept> ret = new HashSet<>();
    for (Concept c :
        this.owl.getRestrictedClasses(
            semantics.asConcept(), this.owl.getProperty(NS.AFFECTS_PROPERTY))) {
      if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
        ret.add(c);
      }
    }
    for (Concept c :
        this.owl.getRestrictedClasses(
            semantics.asConcept(), this.owl.getProperty(NS.CREATES_PROPERTY))) {
      if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
        ret.add(c);
      }
    }
    return ret;
  }

  @Override
  public Collection<Concept> affected(Semantics semantics) {
    Set<Concept> ret = new HashSet<>();
    for (Concept c :
        this.owl.getRestrictedClasses(
            semantics.asConcept(), this.owl.getProperty(NS.AFFECTS_PROPERTY))) {
      if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
        ret.add(c);
      }
    }
    return ret;
  }

  @Override
  public Collection<Concept> created(Semantics semantics) {
    Set<Concept> ret = new HashSet<>();
    for (Concept c :
        this.owl.getRestrictedClasses(
            semantics.asConcept(), this.owl.getProperty(NS.CREATES_PROPERTY))) {
      if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
        ret.add(c);
      }
    }
    return ret;
  }

  @Override
  public boolean match(Semantics candidate, Semantics pattern) {
    return syntacticMatcher.match(candidate, pattern);
  }

  @Override
  public boolean match(Semantics candidate, Semantics pattern, Map<Concept, Concept> matches) {
    if (matches == null) {
      throw new KlabIllegalArgumentException("The generic match result map cannot be null");
    }
    if (match(candidate, pattern) && !pattern.isAbstract()) {
      return true;
    }
    throw new KlabUnimplementedException(
        "Generic semantic matching with captured substitutions is not implemented");
  }

  @Override
  public <T extends Semantics> T concretize(T pattern, Map<Concept, Concept> concreteConcepts) {
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(concreteConcepts, "concreteConcepts");
    String declaration = pattern.getUrn();
    for (var replacement : concreteConcepts.entrySet()) {
      declaration =
          declaration.replace(replacement.getKey().getUrn(), replacement.getValue().getUrn());
    }
    Semantics ret =
        pattern instanceof Observable
            ? resolveObservable(declaration)
            : resolveConcept(declaration);
    @SuppressWarnings("unchecked")
    T typed = (T) ret;
    return typed;
  }

  @Override
  public <T extends Semantics> T concretize(T pattern, List<Concept> concreteConcepts) {
    throw new KlabUnimplementedException(
        "Inference-based generic concretization is not implemented");
  }

  @Override
  public boolean affectedBy(Semantics affected, Semantics affecting) {
    Concept described = describedType(affected);
    for (Concept c : affected(affecting)) {
      if (is(affected, c) || (described != null && is(described, c))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean createdBy(Semantics affected, Semantics affecting) {
    Concept described = describedType(affected);
    if (described != null && is(described, affecting)) {
      return true;
    }
    for (Concept c : created(affecting)) {
      if (is(affected, c) || (described != null && is(described, c))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Concept baseObservable(Semantics c) {

    if (c instanceof Concept concept) {
      return concept;
    }

    Collection<Concept> traits = directTraits(c);
    Collection<Concept> roles = directRoles(c);
    if (traits.isEmpty() && roles.isEmpty() && derived(c)) {
      return c.asConcept();
    }

    return baseObservable(parent(c));
  }

  @Override
  public Concept parent(Semantics c) {
    Collection<Concept> parents = this.owl.getParents(c.asConcept());
    return parents.isEmpty() ? null : parents.iterator().next();
  }

  @Override
  public Concept compose(Collection<Concept> concepts, LogicalConnector connector) {

    if (connector == LogicalConnector.EXCLUSION || connector == LogicalConnector.DISJOINT_UNION) {
      throw new KlabIllegalArgumentException(
          "Reasoner::compose: connector " + connector + " not " + "supported");
    }
    if (concepts.size() == 1) {
      return concepts.iterator().next();
    }
    if (concepts.size() > 1) {
      return connector == LogicalConnector.UNION
          ? this.owl.getUnion(
              concepts,
              this.owl.getOntology(concepts.iterator().next().getNamespace()),
              concepts.iterator().next().getType())
          : this.owl.getIntersection(
              concepts,
              this.owl.getOntology(concepts.iterator().next().getNamespace()),
              concepts.iterator().next().getType());
    }
    return owl.getNothing();
  }

  @Override
  public Concept rawObservable(Semantics observable) {
    String def = observable.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY, String.class);
    Concept ret = observable.asConcept();
    if (def != null) {
      ret = resolveConcept(def);
    }
    return ret;
  }

  /*
   * --- non-API
   */

  /*
   * Record correspondence of core concept peers to worldview concepts. Called by
   * KimValidator for later use at namespace construction.
   */
  public void setWorldviewPeer(String coreConcept, String worldviewConcept) {
    coreConceptPeers.put(worldviewConcept, coreConcept);
  }

  public Concept build(
      KimConceptStatement concept,
      Ontology ontology,
      KimConceptStatement kimObject,
      Scope monitor) {

    try {

      if (concept.isAlias() || concept.getUpperConceptDefined() != null) {

        /*
         * can only have 'is' or 'equals' X; for core concepts 'is' means 'equals', and we use the
         * statement to establish the semantic type.
         */
        Concept parent = null;
        if (concept.getUpperConceptDefined() != null) {
          parent = this.owl.getConcept(concept.getUpperConceptDefined());
          if (parent == null) {
            monitor.error(
                "Core concept " + concept.getUpperConceptDefined() + " is unknown", concept);
          } else {
            parent.getType().addAll(concept.getType());
          }
        } else if (concept.getDeclaredParent() != null) {
          parent = declareConcept(concept.getDeclaredParent());
        }

        if (parent != null) {
          ontology.addDelegateConcept(concept.getUrn(), ontology.getName(), parent);
        }

        return null;
      }

      Concept ret = buildInternal(concept, ontology, kimObject, monitor);

      if (ret != null) {

        Concept upperConceptDefined = null;
        if (concept.getDeclaredParent() == null) {
          Concept parent = null;
          if (concept.getUpperConceptDefined() != null) {
            upperConceptDefined = parent = this.owl.getConcept(concept.getUpperConceptDefined());
            if (parent == null) {
              monitor.error(
                  "Core concept " + concept.getUpperConceptDefined() + " is " + "unknown", concept);
            }
          } else {
            parent = this.owl.getCoreOntology().getCoreType(concept.getType());
            if (coreConceptPeers.containsKey(ret.toString())) {
              // ensure that any non-trivial core inheritance is dealt with
              // appropriately
              parent = this.owl.getCoreOntology().alignCoreInheritance(ret);
            }
          }

          if (parent != null) {
            ontology.add(
                Axiom.SubClass(parent.getNamespace() + ":" + parent.getName(), ret.getName()));
          }
        }

        createProperties(ret, ontology);
        ontology.define();

        if (coreConceptPeers.containsKey(ret.toString()) && upperConceptDefined != null
        /* && "true".equals(upperConceptDefined.getMetadata().get(NS.IS_CORE_KIM_TYPE,
        "false")*/ ) {
          // TODO revise - use core ontology statements only
          this.owl.getCoreOntology().setAsCoreType(ret);
        }
      }

      return ret;

    } catch (Throwable e) {
      monitor.error(e, concept);
    }
    return null;
  }

  private Concept buildInternal(
      final KimConceptStatement concept,
      Ontology ontology,
      KimConceptStatement kimObject,
      final Scope monitor) {

    Concept main = null;
    String mainId = concept.getUrn();

    ontology.add(
        Axiom.ClassAssertion(
            mainId,
            concept.getType().stream()
                .map((c) -> SemanticType.valueOf(c.name()))
                .collect(Collectors.toSet())));

    // set the k.IM definition
    ontology.add(
        Axiom.AnnotationAssertion(
            mainId, NS.CONCEPT_DEFINITION_PROPERTY, ontology.getName() + ":" + concept.getUrn()));

    // and the reference name
    ontology.add(
        Axiom.AnnotationAssertion(
            mainId,
            NS.REFERENCE_NAME_PROPERTY,
            OWL.getCleanFullId(ontology.getName(), concept.getUrn())));

    if (concept.getType().contains(SemanticType.NOTHING)) {
      monitor.error("Declaration is inconsistent or uses unknown concepts", concept);
      return null;
    }

    if (concept.getDocstring() != null) {
      ontology.add(
          Axiom.AnnotationAssertion(mainId, Vocabulary.RDFS_COMMENT, concept.getDocstring()));
    }

    if (kimObject == null) {
      ontology.add(Axiom.AnnotationAssertion(mainId, NS.BASE_DECLARATION, "true"));
    }

    /*
     * basic attributes subjective deniable internal uni/bidirectional
     * (relationship)
     */
    if (concept.isAbstract() || concept.getNamespace().equals(CoreOntology.CORE_ONTOLOGY_NAME)) {
      ontology.add(Axiom.AnnotationAssertion(mainId, CoreOntology.NS.IS_ABSTRACT, "true"));
    }

    ontology.define();
    main = ontology.getConcept(mainId);

    indexer.index(concept);

    if (concept.getDeclaredParent() != null) {

      //            List<Concept> concepts = new ArrayList<>();
      //            for (KimConcept pdecl : parent.getConcepts()) {
      Concept declared = declare(concept.getDeclaredParent(), ontology, monitor);
      if (declared == null || declared.is(SemanticType.NOTHING)) {
        monitor.error(
            "parent declaration "
                + concept.getDeclaredParent().getUrn()
                + " does not identify known concepts",
            concept.getDeclaredParent());
        return null;
      } else {
        ontology.add(Axiom.SubClass(declared.getNamespace() + ":" + declared.getName(), mainId));
      }
      //                concepts.add(declared);
      //            }
      //
      //            if (concepts.size() == 1) {
      //
      //            }
      /* else {
          Concept expr = null;
          switch (parent.getConnector()) {
              case INTERSECTION:
                  expr = this.owl.getIntersection(concepts, ontology, concepts.get(0).getType());
                  break;
              case UNION:
                  expr = this.owl.getUnion(concepts, ontology, concepts.get(0).getType());
                  break;
              case FOLLOWS:
                  expr = this.owl.getConsequentialityEvent(concepts, ontology);
                  break;
              default:
                  // won't happen
                  break;
          }
          if (concept.isAlias()) {
              ontology.addDelegateConcept(mainId, ontology.getName(), expr);
          } else {
              ontology.add(Axiom.SubClass(expr.getNamespace() + ":" + expr.getName(), mainId));
          }
      }*/
      ontology.define();
    }

    for (var child : concept.getChildren()) {
      try {
        // KimConceptStatement chobj = kimObject == null ? null : new
        // KimConceptStatement((IKimConceptStatement) child);
        Concept childConcept =
            buildInternal((KimConceptStatement) child, ontology, concept, /*
                         * monitor instanceof ErrorNotifyingMonitor ? ((ErrorNotifyingMonitor)
                         * monitor).contextualize(child) :
                         */ monitor);
        if (childConcept != null) {
          ontology.add(Axiom.SubClass(mainId, childConcept.getName()));
          ontology.define();
        }
        // kimObject.getChildren().add(chobj);
      } catch (Throwable e) {
        monitor.error(e, child);
      }
    }

    for (KimConcept inherited : concept.getTraitsInherited()) {
      Concept trait = declare(inherited, ontology, monitor);
      if (trait == null || trait.is(SemanticType.NOTHING)) {
        monitor.error(
            "inherited " + inherited.getName() + " does not identify known concepts", inherited);
        // return null;
      } else {
        this.owl.addTrait(main, trait, ontology);
      }
    }

    // TODO all the rest: creates, ....
    for (KimConcept affected : concept.getQualitiesAffected()) {
      Concept quality = declare(affected, ontology, monitor);
      if (quality == null || quality.is(SemanticType.NOTHING)) {
        monitor.error(
            "affected " + affected.getName() + " does not identify known concepts", affected);
      } else {
        this.owl.restrictSome(
            main, this.owl.getProperty(CoreOntology.NS.AFFECTS_PROPERTY), quality, ontology);
      }
    }

    for (KimConcept required : concept.getRequiredIdentities()) {
      Concept quality = declare(required, ontology, monitor);
      if (quality == null || quality.is(SemanticType.NOTHING)) {
        monitor.error(
            "required " + required.getName() + " does not identify known concepts", required);
      } else {
        this.owl.restrictSome(
            main, this.owl.getProperty(NS.REQUIRES_IDENTITY_PROPERTY), quality, ontology);
      }
    }

    for (KimConcept affected : concept.getObservablesCreated()) {
      Concept quality = declare(affected, ontology, monitor);
      if (quality == null || quality.is(SemanticType.NOTHING)) {
        monitor.error(
            "created " + affected.getName() + " does not identify known concepts", affected);
      } else {
        this.owl.restrictSome(main, this.owl.getProperty(NS.CREATES_PROPERTY), quality, ontology);
      }
    }

    for (ApplicableConcept link : concept.getSubjectsLinked()) {
      if (link.getOriginalObservable() == null && link.getSource() != null) {
        // relationship source->target
        this.owl.defineRelationship(
            main,
            declare(link.getSource(), ontology, monitor),
            declare(link.getTarget(), ontology, monitor),
            ontology);
      } else {
        // TODO
      }
    }

    if (!concept.getEmergenceTriggers().isEmpty()) {
      List<Concept> triggers = new ArrayList<>();
      for (KimConcept trigger : concept.getEmergenceTriggers()) {
        triggers.add(declare(trigger, ontology, monitor));
      }
      registerEmergent(main, triggers);
    }

    // if (kimObject != null) {
    // kimObject.set(main);
    // }

    return main;
  }

  /**
   * Arrange a set of concepts into the collection of the most specific members of each concept
   * hierarchy therein.
   *
   * <p>TODO/FIXME not exposed, as I'm not sure this one is useful or intuitive enough.
   *
   * @param cc
   * @return least general
   */
  public Collection<Concept> leastGeneral(Collection<Concept> cc) {

    Set<Concept> ret = new HashSet<>();
    for (Concept c : cc) {
      List<Concept> ccs = new ArrayList<>(ret);
      boolean set = false;
      for (Concept kn : ccs) {
        if (is(c, kn)) {
          ret.remove(kn);
          ret.add(c);
          set = true;
        } else if (is(kn, c)) {
          set = true;
        }
      }
      if (!set) {
        ret.add(c);
      }
    }
    return ret;
  }

  /**
   * Return the most specific ancestor that the concepts in the passed collection have in common, or
   * null if none.
   *
   * @param cc
   * @return
   */
  @Override
  public Concept leastGeneralCommon(Collection<Concept> cc) {

    Concept ret = null;
    Iterator<Concept> ii = cc.iterator();

    if (ii.hasNext()) {

      ret = ii.next();

      if (ret != null)
        while (ii.hasNext()) {
          ret = this.owl.getLeastGeneralCommonConcept(ret, ii.next());
          if (ret == null) break;
        }
    }

    return ret;
  }

  /*
   * Register the triggers and each triggering concept in the emergence map.
   */
  public boolean registerEmergent(Concept configuration, Collection<Concept> triggers) {

    if (!configuration.isAbstract()) {

      if (this.emergent.containsKey(configuration)) {
        return true;
      }
      Emergence descriptor = new Emergence();
      descriptor.emergentObservable = configuration;
      descriptor.triggerObservables.addAll(triggers);
      descriptor.namespaceId = configuration.getNamespace();
      this.emergent.put(configuration, descriptor);

      for (Concept trigger : triggers) {
        for (Concept tr : this.owl.flattenOperands(trigger)) {
          Set<Emergence> es = emergence.get(tr);
          if (es == null) {
            es = new HashSet<>();
            emergence.put(tr, es);
          }
          es.add(descriptor);
        }
      }

      return true;
    }

    return false;
  }

  private void createProperties(Concept ret, Ontology ns) {

    String pName = null;
    String pProp = null;
    if (ret.is(SemanticType.ATTRIBUTE)) {
      // hasX
      pName = "has" + ret.getName();
      pProp = NS.HAS_ATTRIBUTE_PROPERTY;
    } else if (ret.is(SemanticType.REALM)) {
      // inX
      pName = "in" + ret.getName();
      pProp = NS.HAS_REALM_PROPERTY;
    } else if (ret.is(SemanticType.IDENTITY)) {
      // isX
      pName = "is" + ret.getName();
      pProp = NS.HAS_IDENTITY_PROPERTY;
    }
    if (pName != null) {
      ns.add(Axiom.ObjectPropertyAssertion(pName));
      ns.add(Axiom.ObjectPropertyRange(pName, ret.getName()));
      ns.add(Axiom.SubObjectProperty(pProp, pName));
      ns.add(
          Axiom.AnnotationAssertion(
              ret.getName(), NS.TRAIT_RESTRICTING_PROPERTY, ns.getName() + ":" + pName));
    }
  }

  private Concept declare(KimConcept concept, Ontology ontology, Scope monitor) {
    return declareInternal(concept, ontology, monitor);
  }

  private Concept declareInternal(KimConcept concept, Ontology ontology, Scope monitor) {

    Concept main = null;

    var existing = owl.getConcept(concept.getUrn());
    if (existing != null) {
      return existing;
    }

    if (concept.getObservable() != null) {
      main = declareInternal(concept.getObservable(), ontology, monitor);
    } else if (concept.getName() != null) {
      main = this.owl.getConcept(concept.getName());
    }

    if (main == null) {
      return null;
    }

    var builder = SemanticsBuilder.create(main, this, monitor);

    // TODO annotations

    builder.collective(concept.isCollective());

    if (concept.getSemanticModifier() != null) {
      Concept other = null;
      if (concept.getComparisonConcept() != null) {
        other = declareInternal(concept.getComparisonConcept(), ontology, monitor);
      }
      builder.as(
          concept.getSemanticModifier(), other == null ? (Concept[]) null : new Concept[] {other});
    }

    //        if (concept.getDistributedInherent() != null) {
    //            builder.withDistributedInherency(true);
    //        }

    /*
     * transformations first
     */

    if (concept.getInherent() != null) {
      Concept c = declareInternal(concept.getInherent(), ontology, monitor);
      if (c != null) {
        builder.of(c);
      }
    }
    //        if (concept.getContext() != null) {
    //            Concept c = declareInternal(concept.getContext(), ontology, monitor);
    //            if (c != null) {
    //                if (SemanticRole.CONTEXT.equals(concept.getDistributedInherent())) {
    //                    builder.of(c);
    //                } else {
    //                    builder.within(c);
    //                }
    //            }
    //        }
    if (concept.getCompresent() != null) {
      Concept c = declareInternal(concept.getCompresent(), ontology, monitor);
      if (c != null) {
        builder.with(c);
      }
    }
    if (concept.getCausant() != null) {
      Concept c = declareInternal(concept.getCausant(), ontology, monitor);
      if (c != null) {
        builder.withCausant(c);
      }
    }
    if (concept.getCaused() != null) {
      Concept c = declareInternal(concept.getCaused(), ontology, monitor);
      if (c != null) {
        builder.withCaused(c);
      }
    }
    if (concept.getGoal() != null) {
      Concept c = declareInternal(concept.getGoal(), ontology, monitor);
      if (c != null) {
        //                if (SemanticRole.GOAL.equals(concept.getDistributedInherent())) {
        //                    builder.of(c);
        //                } else {
        builder.withGoal(c);
        //                }
      }
    }
    if (concept.getCooccurrent() != null) {
      Concept c = declareInternal(concept.getCooccurrent(), ontology, monitor);
      if (c != null) {
        builder.withCooccurrent(c);
      }
    }
    if (concept.getAdjacent() != null) {
      Concept c = declareInternal(concept.getAdjacent(), ontology, monitor);
      if (c != null) {
        builder.withAdjacent(c);
      }
    }
    if (concept.getRelationshipSource() != null) {
      Concept source = declareInternal(concept.getRelationshipSource(), ontology, monitor);
      Concept target = declareInternal(concept.getRelationshipTarget(), ontology, monitor);
      if (source != null && target != null) {
        builder.linking(source, target);
      }
    }

    for (KimConcept c : concept.getTraits()) {
      Concept trait = declareInternal(c, ontology, monitor);
      if (trait != null) {
        builder.withTrait(trait);
      }
    }

    for (KimConcept c : concept.getRoles()) {
      Concept role = declareInternal(c, ontology, monitor);
      if (role != null) {
        builder.withRole(role);
      }
    }

    Concept ret = null;
    try {

      ret = builder.buildConcept();

      /*
       * handle unions and intersections
       */
      if (!concept.getOperands().isEmpty()) {
        List<Concept> concepts = new ArrayList<>();
        concepts.add(ret);
        for (KimConcept op : concept.getOperands()) {
          concepts.add(declareInternal(op, ontology, monitor));
        }
        ret =
            concept.is(SemanticType.INTERSECTION)
                ? this.owl.getIntersection(
                    concepts, ontology, concept.getOperands().get(0).getType())
                : this.owl.getUnion(concepts, ontology, concept.getOperands().getFirst().getType());

        ((ConceptImpl) ret).setUrn(concept.getUrn());
        ret.getType()
            .add(
                concept.is(SemanticType.INTERSECTION)
                    ? SemanticType.INTERSECTION
                    : SemanticType.UNION);
      }

      //      // set the k.IM definition in the concept.This must only happen if the
      //      // concept wasn't there - within build() and repeat if mods are made
      //      if (builder.axiomsAdded()) {
      //
      //        this.owl
      //            .getOntology(ret.getNamespace())
      //            .define(
      //                Collections.singletonList(
      //                    Axiom.AnnotationAssertion(
      //                        ret.getName(), NS.CONCEPT_DEFINITION_PROPERTY, concept.getUrn())));
      //
      //        // consistency check
      //        if (!satisfiable(ret)) {
      //          ret.getType().add(SemanticType.NOTHING);
      //          monitor.error(
      //              "the definition of this concept has logical errors and " + "is inconsistent",
      //              concept);
      //        }
      //
      //        /** Now that the URN is set, put away the description */
      //        registerConcept(ret);
      //      }

    } catch (Throwable e) {
      monitor.error(e, concept);
    }

    if (concept.isNegated()) {
      ret = negated(ret);
    }

    return ret;
  }

  public Observable declare(KimObservable concept, Ontology declarationOntology, Scope monitor) {

    if (concept.getNonSemanticType() != null) {
      Concept nsmain =
          this.owl.getNonsemanticPeer(concept.getModelReference(), concept.getNonSemanticType());
      ObservableImpl observable = ObservableImpl.promote(nsmain, serviceScope());
      //			observable.setModelReference(concept.getModelReference());
      observable.setName(concept.getFormalName());
      observable.setStatedName(concept.getFormalName());
      observable.setReferenceName(concept.getFormalName());
      observable.setArtifactType(Artifact.Type.forSemantics(nsmain.getType()));
      return observable;
    }

    Concept main = declareInternal(concept.getSemantics(), declarationOntology, monitor);
    if (main == null) {
      return null;
    }

    Concept observable = main;

    Observable.Builder builder = SemanticsBuilder.create(observable, this, monitor);

    // ret.setUrl(concept.getURI());
    // builder.withUrl(concept.getURI());

    boolean unitsSet = false;

    if (concept.getUnit() != null) {
      unitsSet = true;
      builder = builder.withUnit(concept.getUnit());
    }

    if (concept.getCurrency() != null) {
      unitsSet = true;
      builder = builder.withCurrency(concept.getCurrency());
    }

    //    if (concept.getValue() != null) {
    //      Object value = concept.getValue();
    //      if (value instanceof KimConcept) {
    //        value = declareConcept((KimConcept) value);
    //      }
    //      builder = builder.withInlineValue(value);
    //    }

    if (concept.getDefaultValue() != null) {
      Object value = concept.getValue();
      if (value instanceof KimConcept) {
        value = declareConcept((KimConcept) value);
      }
      builder = builder.withDefaultValue(value);
    }

    for (var exc : concept.getResolutionExceptions()) {
      builder = builder.withResolutionException(exc);
    }

    if (concept.getRange() != null) {
      builder = builder.withRange(concept.getRange());
    }

    builder =
        builder
            .optional(concept.isOptional())
            //            .generic(concept.isGeneric()) /* .global(concept
            //        .isGlobal()) */
            .named(concept.getFormalName());

    // TODO gather generic concepts and abstract ones
    //        if (concept.isExclusive()) {
    //            builder = builder.withResolution(Observable.Resolution.Only);
    //        } else if (concept.isGlobal()) {
    //            builder = builder.withResolution(Observable.Resolution.All);
    //        } else if (concept.isGeneric()) {
    //            builder = builder.withResolution(Observable.Resolution.Any);
    //        }

    for (var operator : concept.getValueOperators()) {
      builder = builder.withValueOperator(operator.getFirst(), operator.getSecond());
    }

    for (var annotation : concept.getAnnotations()) {
      builder = builder.withAnnotation(new AnnotationImpl(annotation));
    }

    // CHECK: fluidUnits = needsUnits() && !unitsSet;

    return (Observable) builder.buildObservable();
  }

  //  public void registerConcept(Concept thing) {
  //    this.concepts.put(thing.getUrn(), thing);
  //  }

  @Override
  public Collection<Concept> rolesFor(Concept observable, Concept context) {
    throw new KlabUnimplementedException("Context-implied role computation is not implemented");
  }

  @Override
  public Concept impliedRole(Concept baseRole, Concept contextObservable) {
    throw new KlabUnimplementedException("Context-implied role computation is not implemented");
  }

  @Override
  public Collection<Concept> impliedRoles(Concept role, boolean includeRelationshipEndpoints) {
    throw new KlabUnimplementedException("Implied role closure is not implemented");
  }

  /**
   * Entry point of a semantic search. If the request has a new searchId, start a new
   * SemanticExpression and keep it until timeout or completion.
   *
   * @param request
   */
  @Override
  public SemanticSearchResponse semanticSearch(SemanticSearchRequest request) {

    var response = new SemanticSearchResponse(request.getSearchId(), request.getRequestId());

    if (request.isCancelSearch()) {
      semanticExpressions.invalidate(request.getSearchId());
    } else {

      switch (request.getSearchMode()) {
        case UNDO:

          // client may be stupid, as mine is
          var expression = semanticExpressions.getIfPresent(request.getSearchId());
          if (expression != null) {
            boolean ok = true;
            if (!expression.undo()) {
              semanticExpressions.invalidate(request.getSearchId());
              ok = false;
            }

            response.setSearchId(ok ? request.getSearchId() : null);
            if (ok) {
              response.getErrors().addAll(expression.getErrors());
              response.getCode().addAll(expression.getStyledCode());
              response.setCurrentType(expression.getObservableType());
            }
          } else {
            response.getErrors().add("Timeout during search");
          }
          break;

        case OPEN_SCOPE:
          expression = semanticExpressions.getIfPresent(response.getSearchId());
          if (expression != null) {
            expression.accept("(");
            response.setSearchId(request.getSearchId());
            response.getErrors().addAll(expression.getErrors());
            response.getCode().addAll(expression.getStyledCode());
            response.setCurrentType(expression.getObservableType());
          } else {
            response.getErrors().add("Timeout during search");
          }

          break;

        case CLOSE_SCOPE:
          expression = semanticExpressions.getIfPresent(response.getSearchId());
          if (expression != null) {
            expression.accept(")");
            response.getErrors().addAll(expression.getErrors());
            response.getCode().addAll(expression.getStyledCode());
            response.setCurrentType(expression.getObservableType());
          } else {
            response.getErrors().add("Timeout during search");
          }
          break;

        case TOKEN:
          expression = semanticExpressions.getIfPresent(response.getSearchId());
          if (expression == null) {
            expression = SemanticExpression.create(serviceScope());
            semanticExpressions.put(response.getSearchId(), expression);
          } else {
            response.getErrors().add("Timeout during search");
          }

          for (var match :
              indexer.query(
                  request.getQueryString(),
                  expression.getCurrent().getScope(),
                  request.getMaxResults())) {
            response.getMatches().add(match);
          }

          // save the matches in the expression so that we recognize a choice
          expression.setData("matches", response);

          break;
      }
    }

    response.setElapsedTimeMs(System.currentTimeMillis() - response.getElapsedTimeMs());
    return response;
  }

  @Override
  public boolean shutdown() {

    //    serviceScope()
    //        .send(
    //            Message.MessageClass.ServiceLifecycle,
    //            Message.MessageType.ServiceUnavailable,
    //            capabilities(serviceScope()));
    invalidateSemanticCaches();
    owl.reset();
    return super.shutdown();
  }

  @Override
  protected org.integratedmodelling.klab.services.configuration.ServiceConfiguration
      getServiceConfiguration() {
    return this.configuration;
  }

  @Override
  public List<ObservationStrategy> computeObservationStrategies(
      Observation observation, ContextScope scope) {
    return observationReasoner.computeMatchingStrategies(observation, scope, true);
  }

  @Override
  public IdentificationStrategy computeIdentificationStrategies(
      Observable observable, ContextScope scope) {
    return observationReasoner.computeIdentificationStrategy(observable, scope);
  }

  public Collection<Concept> collectComponents(Concept concept, Collection<SemanticType> types) {
    Set<Concept> ret = new HashSet<>();
    KimConcept peer =
        serviceScope().getService(ResourcesService.class).declareConcept(concept.getUrn());
    var visitor = new KimObservableVisitor();
    visitor.visit(peer);
    var requestedTypes = org.integratedmodelling.common.utils.Utils.Collections.asSet(types);
    for (var component : visitor.getConcepts()) {
      if (component.getName() == null
          || Sets.intersection(component.getType(), requestedTypes).size() != types.size()) {
        continue;
      }
      var resolved = resolveConcept(component.getName());
      if (resolved != null) ret.add(resolved);
    }
    return ret;
  }

  public Concept replaceComponent(Concept original, Map<Concept, Concept> replacements) {

    /*
     * TODO this is the original lexical replacement, which is risky and incomplete.
     * This should use a specialized visitor to rebuild the concept piecewise from a
     * modified KimConcept.
     */

    if (replacements.isEmpty()) {
      return original;
    }

    String declaration = original.getUrn();
    for (Concept key : replacements.keySet()) {
      String rep = replacements.get(key).toString();
      if (rep.contains(" ")) {
        rep = "(" + rep + ")";
      }
      declaration = declaration.replace(key.getUrn(), rep);
    }

    return declareConcept(
        serviceScope().getService(ResourcesService.class).declareConcept(declaration));
  }

  @Override
  public Concept buildConcept(ObservableBuildStrategy builder, Scope scope) {
    Observable.Builder ret = SemanticsBuilder.create(builder.getBaseObservable(), this, scope);
    ret = defineBuilder(builder, ret);
    return ret.buildConcept();
  }

  @Override
  public Observable buildObservable(ObservableBuildStrategy builder, Scope scope) {
    Observable.Builder ret = SemanticsBuilder.create(builder.getBaseObservable(), this, scope);
    ret = defineBuilder(builder, ret);
    return ret.buildObservable();
  }

  private Observable.Builder defineBuilder(
      ObservableBuildStrategy builder, Observable.Builder ret) {
    for (ObservableBuildStrategy.Operation op : builder.getOperations()) {
      switch (op.getType()) {
        case OF -> {
          ret = ret.of(op.getConcepts().get(0));
        }
        case WITH -> {
          ret = ret.with(op.getConcepts().get(0));
        }
        case GOAL -> {
          ret = ret.withGoal(op.getConcepts().get(0));
        }
        case FROM -> {
          ret = ret.withCausant(op.getConcepts().get(0));
        }
        case TO -> {
          ret = ret.withCaused(op.getConcepts().get(0));
        }
        case WITH_ROLE -> {
          ret = ret.withRole(op.getConcepts().get(0));
        }
        case AS -> {
          ret = ret.as(op.getOperator(), op.getConcepts().toArray(new Concept[0]));
        }
        case WITH_TRAITS -> {
          ret = ret.withTrait(op.getConcepts().toArray(new Concept[0]));
        }
        case WITHOUT -> {
          ret = ret.without(op.getConcepts().toArray(new Concept[0]));
        }
        case WITHOUT_ANY_TYPES -> {
          ret = ret.withoutAny(op.getTypes().toArray(new SemanticType[0]));
        }
        case WITHOUT_ANY_CONCEPTS -> {
          ret = ret.withoutAny(op.getConcepts().toArray(new Concept[0]));
        }
        case ADJACENT -> {
          ret = ret.withAdjacent(op.getConcepts().get(0));
        }
        case COOCCURRENT -> {
          ret = ret.withCooccurrent(op.getConcepts().get(0));
        }
        case WITH_UNIT -> {
          ret = ret.withUnit(op.getUnit());
        }
        case WITH_CURRENCY -> {
          ret = ret.withCurrency(op.getCurrency());
        }
        case WITH_RANGE -> {
          ret = ret.withRange(op.getRange());
        }
        case WITH_VALUE_OPERATOR -> {
          ret =
              ret.withValueOperator(
                  op.getValueOperation().getFirst(), op.getValueOperation().getSecond());
        }
        case LINKING -> {
          ret = ret.linking(op.getConcepts().get(0), op.getConcepts().get(1));
        }
        case NAMED -> {
          ret = ret.named((String) op.getPod());
        }
        case WITHOUT_VALUE_OPERATORS -> {
          ret = ret.withoutValueOperators();
        }
        case AS_OPTIONAL -> {
          ret = ret.optional((Boolean) op.getPod());
        }
        case WITHOUT_ROLES -> {
          ret = ret.without(op.getRoles().toArray(new SemanticRole[0]));
        }
        case WITH_TEMPORAL_INHERENT -> {
          ret = ret.withTemporalInherent(op.getConcepts().get(0));
        }
        case COLLECTIVE -> {
          ret = ret.collective((Boolean) op.getPod());
        }
        case WITH_DEFAULT_VALUE -> {
          ret = ret.withDefaultValue(op.getPod());
        }
        case WITH_RESOLUTION_EXCEPTION -> {
          ret = ret.withResolutionException(op.getResolutionException());
        }
        case WITH_ANNOTATION -> {
          for (Annotation annotation : op.getAnnotations()) {
            ret = ret.withAnnotation(annotation);
          }
        }
        default ->
            throw new KlabUnimplementedException(
                "ReasonerService::defineBuilder: unhandled " + "operation " + op.getType());
      }
    }
    return ret;
  }

  @Override
  public boolean exportNamespace(String namespace, File directory) {
    return this.owl.exportOntology(namespace, directory);
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    throw new KlabUnimplementedException(
        "Reasoner assets cannot be retrieved through this service");
  }
}
