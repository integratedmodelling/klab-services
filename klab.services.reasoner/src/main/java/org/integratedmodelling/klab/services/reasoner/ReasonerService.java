package org.integratedmodelling.klab.services.reasoner;

import java.io.File;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.IConcept;
import org.integratedmodelling.klab.api.knowledge.IMetadata;
import org.integratedmodelling.klab.api.knowledge.IObservable;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategyPattern;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.impl.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ApplicableConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ParentConcept;
import org.integratedmodelling.klab.api.lang.kim.KimInstance;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimScope;
import org.integratedmodelling.klab.api.lang.kim.KimStatement;
import org.integratedmodelling.klab.api.lang.kim.KimStatement.KimVisitor;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Authentication;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticSearchResponse;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceSet.Resource;
import org.integratedmodelling.klab.configuration.Configuration;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.indexing.Indexer;
import org.integratedmodelling.klab.indexing.SemanticExpression;
import org.integratedmodelling.klab.knowledge.IntelligentMap;
import org.integratedmodelling.klab.knowledge.ObservableImpl;
import org.integratedmodelling.klab.logging.Logging;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.reasoner.configuration.ReasonerConfiguration;
import org.integratedmodelling.klab.services.reasoner.configuration.ReasonerConfiguration.ProjectConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.services.reasoner.internal.ObservableBuilder;
import org.integratedmodelling.klab.services.reasoner.owl.Axiom;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.integratedmodelling.klab.services.reasoner.owl.Ontology;
import org.integratedmodelling.klab.services.reasoner.owl.Vocabulary;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.utils.CamelCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;

@Service
public class ReasonerService extends BaseService implements Reasoner, Reasoner.Admin {

	@Serial
	private static final long serialVersionUID = 380622027752591182L;

	/**
	 * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
	 * 
	 * If passed to {@link #isCompatible(IConcept, IConcept, int)}, different realms
	 * will not determine incompatibility.
	 */
	static public final int ACCEPT_REALM_DIFFERENCES = 0x01;

	/**
	 * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
	 * 
	 * If passed to {@link #isCompatible(IConcept, IConcept, int)}, only types that
	 * have the exact same core type will be accepted.
	 */
	static public final int REQUIRE_SAME_CORE_TYPE = 0x02;

	/**
	 * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
	 * 
	 * If passed to {@link #isCompatible(IConcept, IConcept, int)}, types with roles
	 * that are more general of the roles in the first concept will be accepted.
	 */
	static public final int USE_ROLE_PARENT_CLOSURE = 0x04;

	/**
	 * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
	 * 
	 * If passed to {@link #isCompatible(IConcept, IConcept, int)}, types with
	 * traits that are more general of the traits in the first concept will be
	 * accepted.
	 */
	static public final int USE_TRAIT_PARENT_CLOSURE = 0x08;

	/**
	 * Flag for {@link #isCompatible(IConcept, IConcept, int)}.
	 * 
	 * If passed to {@link #isCompatible(IConcept, IConcept, int)} causes acceptance
	 * of subjective traits for observables.
	 */
	static public final int ACCEPT_SUBJECTIVE_OBSERVABLES = 0x10;

	private String url;
	private String localName;
	private ReasonerConfiguration configuration = new ReasonerConfiguration();
	private Map<String, String> coreConceptPeers = new HashMap<>();
	private Map<Concept, Emergence> emergent = new HashMap<>();
	private IntelligentMap<Set<Emergence>> emergence;
	// TODO fill in from classpath
	private Collection<ObservationStrategyPattern> observationStrategyPatterns = new ArrayList<>();
	private Map<String, Concept> concepts = Collections.synchronizedMap(new HashMap<>());
	private Map<String, Observable> observables = Collections.synchronizedMap(new HashMap<>());

	// /**
	// * Caches for concepts and observables, linked to the URI in the corresponding
	// {@link
	// KimScope}.
	// */
	// private LoadingCache<String, Concept> concepts = CacheBuilder.newBuilder()
	// // .expireAfterAccess(10, TimeUnit.MINUTES)
	// .build(new CacheLoader<String, Concept>(){
	// public Concept load(String key) {
	// KimConcept parsed =
	// scope.getService(ResourcesService.class).resolveConcept(key);
	// return declareConcept(parsed);
	// }
	// });
	//
	// private LoadingCache<String, Observable> observables =
	// CacheBuilder.newBuilder()
	// // .expireAfterAccess(10, TimeUnit.MINUTES)
	// .build(new CacheLoader<String, Observable>(){
	// public Observable load(String key) { // no checked exception
	// KimObservable parsed =
	// scope.getService(ResourcesService.class).resolveObservable(key);
	// return declareObservable(parsed);
	// }
	// });

	Indexer indexer;

	/**
	 * Cache for ongoing requests expires in 10 minutes. CHECK this may be less and
	 * become configurable.
	 */
	private Cache<Integer, SemanticExpression> semanticExpressions = CacheBuilder.newBuilder()
			.expireAfterAccess(10, TimeUnit.MINUTES).build();

	private Capabilities capabilities = new ReasonerCapabilities();

	private Authentication authenticationService;

	private OWL owl;

	static Pattern internalConceptPattern = Pattern.compile("[A-Z]+_[0-9]+");

	public boolean derived(Semantics c) {
		return internalConceptPattern.matcher(c.getName()).matches();
	}

	/**
	 * An emergence is the appearance of an observation triggered by another, under
	 * the assumptions stated in the worldview. It applies to processes and
	 * relationships and its emergent observable can be a configuration, subject or
	 * process.
	 * 
	 * @author Ferd
	 *
	 */
	public class Emergence {

		public Set<Concept> triggerObservables = new LinkedHashSet<>();
		public Concept emergentObservable;
		public String namespaceId;

		public Set<Observation> matches(Concept relationship, ContextScope scope) {

			for (Concept trigger : triggerObservables) {
				Set<Observation> ret = new HashSet<>();
				checkScope(trigger, scope.getCatalog(), relationship, ret);
				if (!ret.isEmpty()) {
					return ret;
				}
			}

			return Collections.emptySet();
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
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Emergence other = (Emergence) obj;
			if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
				return false;
			return Objects.equals(emergentObservable, other.emergentObservable)
					&& Objects.equals(namespaceId, other.namespaceId)
					&& Objects.equals(triggerObservables, other.triggerObservables);
		}

		/*
		 * current observable must be one of the triggers, any others need to be in
		 * scope
		 */
		private void checkScope(Concept trigger, Map<Observable, Observation> map, Concept relationship,
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
	public ReasonerService(Authentication authenticationService, ServiceScope scope) {
		super(scope);
		this.authenticationService = authenticationService;
		this.owl = new OWL(scope);
		this.owl.initialize();
		this.indexer = new Indexer(scope);
		this.emergence = new IntelligentMap<>(scope);
	}

	@Override
	public void initializeService() {

		File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "reasoner.yaml");
		if (config.exists()) {
			configuration = Utils.YAML.load(config, ReasonerConfiguration.class);
		}

		for (ProjectConfiguration authority : configuration.getAuthorities()) {
			loadAuthority(authority);
		}
	}

	@SuppressWarnings("unchecked")
	private void loadAuthority(ProjectConfiguration authority) {
		if (authority.getUrl().startsWith("classpath:")) {
			try {
				Logging.INSTANCE.info("loading authority " + authority.getProject() + " from local classpath");
				Class<? extends Authority> cls = (Class<? extends Authority>) Class
						.forName(authority.getUrl().substring("classpath:".length()));
				Services.INSTANCE.registerAuthority(cls.getDeclaredConstructor().newInstance());
				Logging.INSTANCE.info("Authority " + authority.getProject() + " ready for "
						+ (authority.isServe() ? "global" : "local") + " use");
			} catch (Exception e) {
				Logging.INSTANCE.error(e);
			}
		}
		// TODO Auto-generated method stub

	}

	private void saveConfiguration() {
		File config = new File(Configuration.INSTANCE.getDataPath() + File.separator + "reasoner.yaml");
		Utils.YAML.save(this.configuration, config);
	}

	@Override
	public Concept defineConcept(KimConceptStatement statement, Scope scope) {
		return build(statement, this.owl.requireOntology(statement.getNamespace(), null), null, scope);
	}

	@Override
	public Concept resolveConcept(String definition) {
		Concept ret = concepts.get(definition);
		if (ret == null) {
			KimConcept parsed = scope.getService(ResourcesService.class).resolveConcept(definition);
			if (parsed != null) {
				ret = declareConcept(parsed);
				concepts.put(definition, ret);
			} else {
				// TODO add an error concept in case of errors or null
			}
		}
		return ret;
	}

	@Override
	public Observable resolveObservable(String definition) {
		Observable ret = observables.get(definition);
		if (ret == null) {
			KimObservable parsed = scope.getService(ResourcesService.class).resolveObservable(definition);
			if (parsed != null) {
				ret = declareObservable(parsed);
				observables.put(definition, ret);
			} else {
				// TODO add an error observable in case of errors or null
			}
		}
		return ret;
	}

	private Observable errorObservable(String definition) {
		// TODO Auto-generated method stub
		return null;
	}

	private Concept errorConcept(String definition) {
		// TODO Auto-generated method stub
		return null;
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

	public Map<Concept, Collection<Observation>> emergentResolvables(Observation trigger, ContextScope scope) {

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

		Set<Concept> ret = collectChildren(target, new HashSet<Concept>());
		ret.add(target.asConcept());

		return ret;
	}

	private Set<Concept> collectChildren(Semantics target, Set<Concept> hashSet) {

		for (Concept c : children(target)) {
			if (!hashSet.contains(c))
				collectChildren(c, hashSet);
			hashSet.add(c);
		}
		return hashSet;
	}

	@Override
	public Collection<Concept> allParents(Semantics target) {
		return allParentsInternal(target, new HashSet<Concept>());
	}

	private Collection<Concept> allParentsInternal(Semantics target, Set<Concept> seen) {

		Set<Concept> concepts = new HashSet<>();

		if (seen.contains(target)) {
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
	public int semanticDistance(Semantics target, Semantics other) {
		return semanticDistance(target.asConcept(), other.asConcept(), null, true, null);
	}

	@Override
	public int semanticDistance(Semantics target, Semantics other, Semantics context) {
		return semanticDistance(target.asConcept(), other.asConcept(), context == null ? null : context.asConcept(), true, null);
	}

	/**
	 * The workhorse of semantic distance computation can also consider any
	 * predicates that were abstract in the lineage of the passed concept (i.e. the
	 * concept is the result of a query with the abstract predicates, which has been
	 * contextualized to incarnate them into the passed correspondence with concrete
	 * counterparts). In that case, and only in that case, the distance between a
	 * concrete candidate and one that contains its predicates in the abstract form
	 * can be positive, i.e. a concept with abstract predicates can resolve one with
	 * concrete subclasses as long as the lineage contains its resolution.
	 * 
	 * @param to
	 * @param context
	 * @param compareInherency
	 * @param resolvedAbstractPredicates
	 * @return
	 */
	public int semanticDistance(Concept from, Concept to, Concept context, boolean compareInherency,
			Map<Concept, Concept> resolvedAbstractPredicates) {

		int distance = 0;

		// String resolving = this.getDefinition();
		// String resolved = concept.getDefinition();
		// System.out.println("Does " + resolving + " resolve " + resolved + "?");

		int mainDistance = coreDistance(from, to, context, compareInherency, resolvedAbstractPredicates);
		distance += mainDistance * 50;
		if (distance < 0) {
			return distance;
		}

		// should have all the same traits - additional traits are allowed only
		// in contextual types
		Set<Concept> acceptedTraits = new HashSet<>();
		for (Concept t : traits(from)) {
			if (t.isAbstract() && resolvedAbstractPredicates != null && resolvedAbstractPredicates.containsKey(t)) {
				distance += assertedDistance(resolvedAbstractPredicates.get(t), t);
				acceptedTraits.add(resolvedAbstractPredicates.get(t));
			} else {
				boolean ok = hasTrait(to, t);
				if (!ok) {
					return -50;
				}
			}
		}

		for (Concept t : traits(to)) {
			if (!acceptedTraits.contains(t) && !hasTrait(from, t)) {
				return -50;
			}
		}

		// same with roles.
		Set<Concept> acceptedRoles = new HashSet<>();
		for (Concept t : roles(from)) {
			if (t.isAbstract() && resolvedAbstractPredicates != null && resolvedAbstractPredicates.containsKey(t)) {
				distance += assertedDistance(resolvedAbstractPredicates.get(t), t);
				acceptedRoles.add(resolvedAbstractPredicates.get(t));
			} else {
				boolean ok = hasRole(to, t);
				if (!ok) {
					return -50;
				}
			}
		}

		for (Concept t : roles(to)) {
			if (!acceptedRoles.contains(t) && !hasRole(from, t)) {
				return -50;
			}
		}

		if (context == null) {
			context = context(to);
		}

		int component;

		if (compareInherency) {

			component = distance(context(from), context, true);

			if (component < 0) {
				double d = ((double) component / 10.0);
				return -1 * (int) (d > 10 ? d : 10);
			}
			distance += component;

			/*
			 * any EXPLICIT inherency must be the same in both.
			 */
			Concept ourExplicitInherent = directInherent(from);
			Concept itsExplicitInherent = directInherent(to);

			if (ourExplicitInherent != null || itsExplicitInherent != null) {
				if (ourExplicitInherent != null && itsExplicitInherent != null) {
					component = distance(ourExplicitInherent, itsExplicitInherent, true);

					if (component < 0) {
						double d = ((double) component / 10.0);
						return -1 * (int) (d > 10 ? d : 10);
					}
					distance += component;
				} else {
					return -50;
				}
			}

			/*
			 * inherency must be same (theirs is ours) unless our inherent type is abstract
			 */
			Concept ourInherent = inherent(from);
			Concept itsInherent = inherent(to);

			if (ourInherent != null || itsInherent != null) {

				if (ourInherent != null && ourInherent.isAbstract()) {
					component = distance(ourInherent, itsInherent, false);
				} else if (ourInherent == null && itsInherent != null && context != null) {
					/*
					 * Situations like: does XXX resolve YYY of ZZZ when ZZZ is the context.
					 */
					component = distance(context, itsInherent, false);
				} else {
					component = distance(itsInherent, ourInherent, false);
				}

				if (component < 0) {
					double d = ((double) component / 10.0);
					return -1 * (int) (d > 10 ? d : 10);
				}
				distance += component;
			}

		}

		component = distance(goal(from), goal(to), false);
		if (component < 0) {
			double d = ((double) component / 10.0);
			return -1 * (int) (d > 10 ? d : 10);
		}
		distance += component;

		component = distance(cooccurrent(from), cooccurrent(to), false);
		if (component < 0) {
			double d = ((double) component / 10.0);
			return -1 * (int) (d > 10 ? d : 10);
		}
		distance += component;

		component = distance(causant(from), causant(to), false);
		if (component < 0) {
			double d = ((double) component / 10.0);
			return -1 * (int) (d > 10 ? d : 10);
		}
		distance += component;

		component = distance(caused(from), caused(to), false);
		if (component < 0) {
			double d = ((double) component / 10.0);
			return -1 * (int) (d > 10 ? d : 10);
		}
		distance += component;

		component = distance(adjacent(from), adjacent(to), false);
		if (component < 0) {
			double d = ((double) component / 10.0);
			return -1 * (int) (d > 10 ? d : 10);
		}
		distance += component;

		component = distance(compresent(from), compresent(to), false);
		if (component < 0) {
			double d = ((double) component / 10.0);
			return -1 * (int) (d > 10 ? d : 10);
		}
		distance += component;

		component = distance(relativeTo(from), relativeTo(to), false);
		if (component < 0) {
			double d = ((double) component / 10.0);
			return -1 * (int) (d > 10 ? d : 10);
		}
		distance += component;

		return distance;
	}

	/**
	 * Get the distance between the core described observables after factoring out
	 * all operators and ensuring they are the same. If not the same, the concepts
	 * are incompatible and the distance is negative.
	 * 
	 * @param to
	 * @return
	 */
	public int coreDistance(Concept from, Concept to, Concept context, boolean compareInherency,
			Map<Concept, Concept> resolvedAbstractPredicates) {

		if (from == to || from.equals(to)) {
			return 0;
		}

		Pair<Concept, List<SemanticType>> c1ops = splitOperators(from);
		Pair<Concept, List<SemanticType>> c2ops = splitOperators(to);

		if (!c1ops.getSecond().equals(c2ops.getSecond())) {
			return -50;
		}

		if (!c1ops.getSecond().isEmpty()) {
			/*
			 * if operators were extracted, the distance must take into account traits and
			 * the like for the concepts they describe, so call the main method again, which
			 * will call this and perform the core check below.
			 */
			return semanticDistance(c1ops.getFirst(), c2ops.getFirst(), context, compareInherency,
					resolvedAbstractPredicates);
		}

		Concept core1 = coreObservable(c1ops.getFirst());
		Concept core2 = coreObservable(c2ops.getFirst());

		/*
		 * FIXME this must check: have operator ? (operator == operator && coreObs ==
		 * coreObs) : coreObs == coreObs;
		 */

		if (core1 == null || core2 == null) {
			return -100;
		}

		if (!from.is(SemanticType.PREDICATE) && !core1.equals(core2)) {
			/*
			 * in order to resolve an observation, the core observables must be equal;
			 * subsumption is not OK (lidar elevation does not resolve elevation as it
			 * creates different observations; same for different observation techniques -
			 * easy strategy to annotate techs that make measurements incompatible = use a
			 * subclass instead of a related trait).
			 * 
			 * Predicates are unique in being able to resolve a more specific predicate.
			 */
			return -50;
		}

		/**
		 * Previously returning the distance, which does not work unless the core
		 * observables are the same (differentiated by predicates only) - which for
		 * example makes identities under 'type of' be compatible no matter the
		 * identity.
		 */
		return core1.equals(core2) ? assertedDistance(from, to) : (assertedDistance(from, to) == 0 ? 0 : -1);
	}

	private int distance(Concept from, Concept to, boolean acceptAbsent) {

		int ret = 0;
		if (from == null && to != null) {
			ret = acceptAbsent ? 50 : -50;
		} else if (from != null && to == null) {
			ret = -50;
		} else if (from != null && to != null) {
			ret = subsumes(to, from) ? assertedDistance(to, from) : -100;
			if (ret >= 0) {
				for (Concept t : traits(from)) {
					boolean ok = hasTrait(to, t);
					if (!ok) {
						return -50;
					}
				}
				for (Concept t : traits(to)) {
					if (!hasTrait(from, t)) {
						ret += 10;
					}
				}
			}
		}

		return ret > 100 ? 100 : ret;
	}

	@Override
	public Concept coreObservable(Semantics first) {
		String def = first.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY, String.class);
		Concept ret = first.asConcept();
		while (def != null) {
			ret = resolveConcept(def);
			if (ret.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY) != null && !ret.getUrn().equals(def)) {
				def = ret.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY, String.class);
			} else {
				break;
			}
		}
		return ret;
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
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Collection<Concept> traits(Semantics concept) {
		Set<Concept> ret = new HashSet<>();
		ret.addAll(this.owl.getRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY)));
		ret.addAll(this.owl.getRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY)));
		ret.addAll(this.owl.getRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY)));
		return ret;
	}

	@Override
	public int assertedDistance(Semantics from, Semantics to) {

		if (from == to || from.equals(to)) {
			return 0;
		}
		int ret = 1;
		while (true) {
			Collection<Concept> parents = parents(from);
			if (parents.isEmpty()) {
				break;
			}
			if (parents.contains(to)) {
				return ret;
			}
			for (Concept parent : parents) {
				int d = assertedDistance(from, parent);
				if (d >= 0) {
					return ret + d;
				}
			}
			ret++;
		}
		return -1;
	}

	@Override
	public boolean hasTrait(Semantics concept, Concept trait) {
		for (Concept c : traits(concept)) {
			if (subsumes(c, trait)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Collection<Concept> roles(Semantics concept) {
		return this.owl.getRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_ROLE_PROPERTY));
	}

	@Override
	public boolean hasRole(Semantics concept, Concept role) {
		for (Concept c : roles(concept)) {
			if (subsumes(c, role)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Concept directContext(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_CONTEXT_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept context(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_CONTEXT_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directInherent(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.IS_INHERENT_TO_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept inherent(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.IS_INHERENT_TO_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directGoal(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_PURPOSE_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept goal(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_PURPOSE_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directCooccurrent(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.OCCURS_DURING_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directCausant(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_CAUSANT_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directCaused(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_CAUSED_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directAdjacent(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.IS_ADJACENT_TO_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directCompresent(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_COMPRESENT_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept directRelativeTo(Semantics concept) {
		Collection<Concept> cls = this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.IS_COMPARED_TO_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept cooccurrent(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.OCCURS_DURING_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept causant(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_CAUSANT_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept caused(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_CAUSED_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept adjacent(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.IS_ADJACENT_TO_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept compresent(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_COMPRESENT_PROPERTY));
		return cls.isEmpty() ? null : cls.iterator().next();
	}

	@Override
	public Concept relativeTo(Semantics concept) {
		Collection<Concept> cls = this.owl.getRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.IS_COMPARED_TO_PROPERTY));
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
		return semantics instanceof Observable ? observableDisplayName((Observable) semantics)
				: conceptDisplayName(semantics.asConcept());
	}

	private String conceptDisplayName(Concept t) {

		String ret = t.getMetadata().get(NS.DISPLAY_LABEL_PROPERTY, String.class);

		if (ret == null) {
			ret = t.getMetadata().get(IMetadata.DC_LABEL, String.class);
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

		String ret = conceptDisplayName(o.asConcept());

		for (Pair<ValueOperator, Object> operator : o.getValueOperators()) {

			ret += StringUtils.capitalize(operator.getFirst().declaration.replace(' ', '_'));

			if (operator.getSecond() instanceof Concept) {
				ret += conceptDisplayName((Concept) operator.getSecond());
			} else if (operator.getSecond() instanceof IObservable) {
				ret += observableDisplayName((Observable) operator.getSecond());
			} else {
				ret += "_" + operator.getSecond().toString().replace(' ', '_');
			}
		}
		return ret;
	}

	@Override
	public String style(Concept concept) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Capabilities capabilities() {
		return this.capabilities;
	}

	public void setCapabilities(Capabilities capabilities) {
		this.capabilities = capabilities;
	}

	@Override
	public Collection<Concept> identities(Semantics concept) {
		return this.owl.getRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY));
	}

	@Override
	public Collection<Concept> attributes(Semantics concept) {
		return this.owl.getRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY));
	}

	@Override
	public Collection<Concept> realms(Semantics concept) {
		return this.owl.getRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY));
	}

	@Override
	public Concept baseParentTrait(Semantics trait) {

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
			Concept r = baseParentTrait(c);
			if (r != null) {
				return r;
			}
		}

		return null;
	}

	@Override
	public boolean hasDirectTrait(Semantics type, Concept trait) {

		for (Concept c : directTraits(type)) {
			if (subsumes(trait, c)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean hasDirectRole(Semantics type, Concept trait) {
		for (Concept c : directRoles(type)) {
			if (subsumes(trait, c)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Collection<Concept> directTraits(Semantics concept) {
		Set<Concept> ret = new HashSet<>();
		ret.addAll(
				this.owl.getDirectRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY)));
		ret.addAll(this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY)));
		ret.addAll(this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY)));
		return ret;
	}

	@Override
	public Collection<Concept> directAttributes(Semantics concept) {
		return this.owl.getDirectRestrictedClasses(concept.asConcept(),
				this.owl.getProperty(NS.HAS_ATTRIBUTE_PROPERTY));
	}

	@Override
	public Collection<Concept> directIdentities(Semantics concept) {
		return this.owl.getDirectRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_IDENTITY_PROPERTY));
	}

	@Override
	public Collection<Concept> directRealms(Semantics concept) {
		return this.owl.getDirectRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_REALM_PROPERTY));
	}

	@Override
	public Concept negated(Concept concept) {
		return this.owl.makeNegation(concept.asConcept(), this.owl.getOntology(concept.getNamespace()));
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
		return ret.size() == 0 ? null : ret.iterator().next();
	}

	@Override
	public Collection<Concept> relationshipSources(Semantics relationship) {
		return Utils.Collections.join(
				this.owl.getDirectRestrictedClasses(relationship.asConcept(),
						this.owl.getProperty(NS.IMPLIES_SOURCE_PROPERTY)),
				this.owl.getRestrictedClasses(relationship.asConcept(),
						this.owl.getProperty(NS.IMPLIES_SOURCE_PROPERTY)));
	}

	@Override
	public Concept relationshipTarget(Semantics relationship) {
		Collection<Concept> ret = relationshipTargets(relationship);
		return ret.size() == 0 ? null : ret.iterator().next();
	}

	@Override
	public Collection<Concept> relationshipTargets(Semantics relationship) {
		return Utils.Collections.join(
				this.owl.getDirectRestrictedClasses(relationship.asConcept(),
						this.owl.getProperty(NS.IMPLIES_DESTINATION_PROPERTY)),
				this.owl.getRestrictedClasses(relationship.asConcept(),
						this.owl.getProperty(NS.IMPLIES_DESTINATION_PROPERTY)));
	}

	@Override
	public boolean satisfiable(Semantics ret) {
		return this.owl.isSatisfiable(ret);
	}

	@Override
	public Collection<Concept> applicableObservables(Concept main) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Concept> directRoles(Semantics concept) {
		return this.owl.getDirectRestrictedClasses(concept.asConcept(), this.owl.getProperty(NS.HAS_ROLE_PROPERTY));
	}

	@Override
	public boolean loadKnowledge(ResourceSet resources, Scope scope) {

		boolean ret = true;
		for (Resource namespace : resources.getNamespaces()) {
			ResourcesService service = resources.getServices().get(namespace.getServiceId());
			KimNamespace parsed = service.resolveNamespace(namespace.getResourceUrn(), scope);
			if (parsed != null && !Utils.Notifications.hasErrors(parsed.getNotifications())) {
				for (KimStatement statement : parsed.getStatements()) {
					if (statement instanceof KimConceptStatement) {
						defineConcept((KimConceptStatement) statement, scope);
					} else if (statement instanceof KimSymbolDefinition) {
						// TODO RDF but only with supporting semantic info
					}
				}
				this.owl.registerWithReasoner(parsed);
			} else {
				ret = false;
				break;
			}
		}

		this.owl.flushReasoner();

		return ret;
	}

	@Override
	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Override
	public String getLocalName() {
		return localName;
	}

	public void setLocalName(String localName) {
		this.localName = localName;
	}

	@Override
	public boolean subsumes(Semantics concept, Semantics other) {

		if (concept == other || concept.equals(other)) {
			return true;
		}

		/*
		 * first use "isn't" based on the enum types to quickly cut out those that don't
		 * match. Also works with concepts in different ontologies that have the same
		 * definition.
		 */
		if (Sets.intersection(concept.asConcept().getType(), other.asConcept().getType()).size() < concept.asConcept()
				.getType().size()) {
			return false;
		}

		/*
		 * TODO this would be a good point to insert caching logics. It should also go
		 * in all remote clients.
		 */

		/*
		 * Speed up checking for logical expressions without forcing the reasoner to
		 * compute complex logics.
		 */
		if (concept.is(SemanticType.UNION)) {

			for (Concept c : operands(concept)) {
				if (subsumes(c, other)) {
					return true;
				}
			}

		} else if (concept.is(SemanticType.INTERSECTION)) {

			for (Concept c : operands(concept)) {
				if (!subsumes(c, other)) {
					return false;
				}
			}
			return true;

		} else {
			/*
			 * use the semantic closure. We may want to cache this eventually.
			 */
			Collection<Concept> collection = allParents(concept);
			collection.add(concept.asConcept());
			return collection.contains(other);
		}
		return false;
	}

	@Override
	public Semantics domain(Semantics conceptImpl) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Concept declareConcept(KimConcept conceptDeclaration) {
		return declare(conceptDeclaration, this.owl.requireOntology(conceptDeclaration.getNamespace()), scope);
	}

	@Override
	public Observable declareObservable(KimObservable observableDeclaration) {
		return declare(observableDeclaration, this.owl.requireOntology(observableDeclaration.getMain().getNamespace()),
				scope);
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

		if ((!o1.is(SemanticType.OBSERVABLE) || !o2.is(SemanticType.OBSERVABLE))
				&& !(o1.is(SemanticType.CONFIGURATION) && o2.is(SemanticType.CONFIGURATION))) {
			return false;
		}

		Concept core1 = coreObservable(o1);
		Concept core2 = coreObservable(o2);

		if (core1 == null || core2 == null || !(mustBeSameCoreType ? core1.equals(core2) : subsumes(core1, core2))) {
			return false;
		}

		Concept cc1 = context(o1);
		Concept cc2 = context(o2);

		// candidate may have no context; if both have them, they must be compatible
		if (cc1 == null && cc2 != null) {
			return false;
		}
		if (cc1 != null && cc2 != null) {
			if (!compatible(cc1, cc2, ACCEPT_REALM_DIFFERENCES)) {
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

	@Override
	public boolean hasParentRole(Semantics o1, Concept t) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean contextuallyCompatible(Semantics focus, Semantics context1, Semantics context2) {
		boolean ret = compatible(context1, context2, 0);
		if (!ret && occurrent(context1)) {
			ret = affectedBy(focus, context1);
			Concept itsContext = context(context1);
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Collection<Concept> affectedOrCreated(Semantics semantics) {
		Set<Concept> ret = new HashSet<>();
		for (Concept c : this.owl.getRestrictedClasses(semantics.asConcept(),
				this.owl.getProperty(NS.AFFECTS_PROPERTY))) {
			if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
				ret.add(c);
			}
		}
		for (Concept c : this.owl.getRestrictedClasses(semantics.asConcept(),
				this.owl.getProperty(NS.CREATES_PROPERTY))) {
			if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
				ret.add(c);
			}
		}
		return ret;
	}

	@Override
	public Collection<Concept> affected(Semantics semantics) {
		Set<Concept> ret = new HashSet<>();
		for (Concept c : this.owl.getRestrictedClasses(semantics.asConcept(),
				this.owl.getProperty(NS.AFFECTS_PROPERTY))) {
			if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
				ret.add(c);
			}
		}
		return ret;
	}

	@Override
	public Collection<Concept> created(Semantics semantics) {
		Set<Concept> ret = new HashSet<>();
		for (Concept c : this.owl.getRestrictedClasses(semantics.asConcept(),
				this.owl.getProperty(NS.CREATES_PROPERTY))) {
			if (!this.owl.getOntology(c.getNamespace()).isInternal()) {
				ret.add(c);
			}
		}
		return ret;
	}

	@Override
	public boolean affectedBy(Semantics affected, Semantics affecting) {
		Concept described = describedType(affected);
		for (Concept c : affected(affecting)) {
			if (subsumes(affected, c) || (described != null && subsumes(described, c))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean createdBy(Semantics affected, Semantics affecting) {
		Concept described = describedType(affected);
		if (described != null && subsumes(described, affecting)) {
			return true;
		}
		for (Concept c : created(affecting)) {
			if (subsumes(affected, c) || (described != null && subsumes(described, c))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Concept baseObservable(Semantics c) {
		Collection<Concept> traits = directTraits(c);
		Collection<Concept> roles = directRoles(c);
		if (traits.size() == 0 && roles.size() == 0 && derived(c)) {
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
	public Concept rawObservable(Semantics observable) {
		String def = observable.getMetadata().get(NS.CORE_OBSERVABLE_PROPERTY, String.class);
		Concept ret = observable.asConcept();
		if (def != null) {
			ret = resolveConcept(def);
		}
		return ret;
	}

	@Override
	public Builder observableBuilder(Observable observableImpl) {
		return ObservableBuilder.getBuilder(observableImpl, scope, owl);
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

	public Concept build(KimConceptStatement concept, Ontology ontology, KimConceptStatement kimObject, Scope monitor) {

		if (concept.isMacro()) {
			return null;
		}

		try {

			if (concept.isAlias()) {

				/*
				 * can only have 'is' X or 'equals' X
				 */
				Concept parent = null;
				if (concept.getUpperConceptDefined() != null) {
					parent = this.owl.getConcept(concept.getUpperConceptDefined());
					if (parent == null) {
						monitor.error("Core concept " + concept.getUpperConceptDefined() + " is unknown", concept);
					} else {
						parent.getType().addAll(concept.getType());
					}
				} else {

					List<Concept> concepts = new ArrayList<>();
					int i = 0;
					for (ParentConcept p : concept.getParents()) {

						if (i > 0) {
							monitor.error("concepts defining aliases with 'equals' cannot have more than one parent",
									p);
						}

						for (KimConcept pdecl : p.getConcepts()) {
							Concept declared = declare(pdecl, ontology, monitor);
							if (declared == null) {
								monitor.error("parent declaration " + pdecl + " does not identify known concepts",
										pdecl);
								return null;
							}
							concepts.add(declared);
						}
						i++;
					}

					if (concepts.size() == 1) {
						parent = concepts.get(0);
					}
				}

				if (parent != null) {
					ontology.addDelegateConcept(concept.getName(), ontology.getName(), parent);
				}

				return null;
			}

			Concept ret = buildInternal(concept, ontology, kimObject, monitor);

			if (ret != null) {

				Concept upperConceptDefined = null;
				if (concept.getParents().isEmpty()) {
					Concept parent = null;
					if (concept.getUpperConceptDefined() != null) {
						upperConceptDefined = parent = this.owl.getConcept(concept.getUpperConceptDefined());
						if (parent == null) {
							monitor.error("Core concept " + concept.getUpperConceptDefined() + " is unknown", concept);
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
						ontology.add(Axiom.SubClass(parent.getUrn(), ret.getName()));
					}
				}

				createProperties(ret, ontology);
				ontology.define();

				if (coreConceptPeers.containsKey(ret.toString()) && upperConceptDefined != null
						&& "true".equals(upperConceptDefined.getMetadata().get(NS.IS_CORE_KIM_TYPE, "false"))) {
					this.owl.getCoreOntology().setAsCoreType(ret);
				}

			}

			return ret;

		} catch (Throwable e) {
			monitor.error(e, concept);
		}
		return null;
	}

	private Concept buildInternal(final KimConceptStatement concept, Ontology ontology, KimConceptStatement kimObject,
			final Scope monitor) {

		Concept main = null;
		String mainId = concept.getName();

		ontology.add(Axiom.ClassAssertion(mainId,
				concept.getType().stream().map((c) -> SemanticType.valueOf(c.name())).collect(Collectors.toSet())));

		// set the k.IM definition
		ontology.add(Axiom.AnnotationAssertion(mainId, NS.CONCEPT_DEFINITION_PROPERTY,
				ontology.getName() + ":" + concept.getName()));

		// and the reference name
		ontology.add(Axiom.AnnotationAssertion(mainId, NS.REFERENCE_NAME_PROPERTY,
				OWL.getCleanFullId(ontology.getName(), concept.getName())));

		if (concept.getDocstring() != null) {
			ontology.add(Axiom.AnnotationAssertion(mainId, Vocabulary.RDFS_COMMENT, concept.getDocstring()));
		}

		if (kimObject == null) {
			ontology.add(Axiom.AnnotationAssertion(mainId, NS.BASE_DECLARATION, "true"));
		}

		/*
		 * basic attributes subjective deniable internal uni/bidirectional
		 * (relationship)
		 */
		if (concept.isAbstract()) {
			ontology.add(Axiom.AnnotationAssertion(mainId, CoreOntology.NS.IS_ABSTRACT, "true"));
		}

		ontology.define();
		main = ontology.getConcept(mainId);

		indexer.index(concept);

		for (ParentConcept parent : concept.getParents()) {

			List<Concept> concepts = new ArrayList<>();
			for (KimConcept pdecl : parent.getConcepts()) {
				Concept declared = declare(pdecl, ontology, monitor);
				if (declared == null) {
					monitor.error("parent declaration " + pdecl + " does not identify known concepts", pdecl);
					return null;
				}
				concepts.add(declared);
			}

			if (concepts.size() == 1) {
				ontology.add(Axiom.SubClass(concepts.get(0).getUrn(), mainId));
			} else {
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
					ontology.add(Axiom.SubClass(expr.getUrn(), mainId));
				}
			}
			ontology.define();
		}

		for (KimScope child : concept.getChildren()) {
			if (child instanceof KimConceptStatement) {
				try {
					// KimConceptStatement chobj = kimObject == null ? null : new
					// KimConceptStatement((IKimConceptStatement) child);
					Concept childConcept = buildInternal((KimConceptStatement) child, ontology, concept,
							/*
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
		}

		for (KimConcept inherited : concept.getTraitsInherited()) {
			Concept trait = declare(inherited, ontology, monitor);
			if (trait == null) {
				monitor.error("inherited " + inherited.getName() + " does not identify known concepts", inherited);
				// return null;
			} else {
				this.owl.addTrait(main, trait, ontology);
			}
		}

		// TODO all the rest: creates, ....
		for (KimConcept affected : concept.getQualitiesAffected()) {
			Concept quality = declare(affected, ontology, monitor);
			if (quality == null) {
				monitor.error("affected " + affected.getName() + " does not identify known concepts", affected);
			} else {
				this.owl.restrictSome(main, this.owl.getProperty(CoreOntology.NS.AFFECTS_PROPERTY), quality, ontology);
			}
		}

		for (KimConcept required : concept.getRequiredIdentities()) {
			Concept quality = declare(required, ontology, monitor);
			if (quality == null) {
				monitor.error("required " + required.getName() + " does not identify known concepts", required);
			} else {
				this.owl.restrictSome(main, this.owl.getProperty(NS.REQUIRES_IDENTITY_PROPERTY), quality, ontology);
			}
		}

		for (KimConcept affected : concept.getObservablesCreated()) {
			Concept quality = declare(affected, ontology, monitor);
			if (quality == null) {
				monitor.error("created " + affected.getName() + " does not identify known concepts", affected);
			} else {
				this.owl.restrictSome(main, this.owl.getProperty(NS.CREATES_PROPERTY), quality, ontology);
			}
		}

		for (ApplicableConcept link : concept.getSubjectsLinked()) {
			if (link.getOriginalObservable() == null && link.getSource() != null) {
				// relationship source->target
				this.owl.defineRelationship(main, declare(link.getSource(), ontology, monitor),
						declare(link.getTarget(), ontology, monitor), ontology);
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
	 * Arrange a set of concepts into the collection of the most specific members of
	 * each concept hierarchy therein.
	 * 
	 * TODO/FIXME not exposed, as I'm not sure this one is useful or intuitive
	 * enough.
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
				if (subsumes(c, kn)) {
					ret.remove(kn);
					ret.add(c);
					set = true;
				} else if (subsumes(kn, c)) {
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
	 * Return the most specific ancestor that the concepts in the passed collection
	 * have in common, or null if none.
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
					if (ret == null)
						break;
				}
		}

		return ret;
	}

	/*
	 * Register the triggers and each triggering concept in the emergence map.
	 */
	public boolean registerEmergent(Concept configuration, Collection<Concept> triggers) {

		if (!configuration.isAbstract()) {

			// DebugFile.println("CHECK for storage of " + configuration + " based on " +
			// triggers);

			if (this.emergent.containsKey(configuration)) {
				return true;
			}

			// DebugFile.println(" STORED " + configuration);

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
			ns.add(Axiom.AnnotationAssertion(ret.getName(), NS.TRAIT_RESTRICTING_PROPERTY, ns.getName() + ":" + pName));
		}
	}

	private Concept declare(KimConcept concept, Ontology ontology, Scope monitor) {
		return declareInternal(concept, ontology, monitor);
	}

	private Concept declareInternal(KimConcept concept, Ontology ontology, Scope monitor) {

		Concept existing = concepts.get(concept.getUrn());
		if (existing != null) {
			return existing;
		}

		Concept main = null;

		if (concept.getObservable() != null) {
			main = declareInternal(concept.getObservable(), ontology, monitor);
		} else if (concept.getName() != null) {
			main = this.owl.getConcept(concept.getName());
		}

		if (main == null) {
			return null;
		}

		ObservableBuilder builder = new ObservableBuilder(main, ontology, monitor, owl).withDeclaration(concept);

		if (concept.getDistributedInherent() != null) {
			builder.withDistributedInherency(true);
		}

		/*
		 * transformations first
		 */

		if (concept.getInherent() != null) {
			Concept c = declareInternal(concept.getInherent(), ontology, monitor);
			if (c != null) {
				builder.of(c);
			}
		}
		if (concept.getContext() != null) {
			Concept c = declareInternal(concept.getContext(), ontology, monitor);
			if (c != null) {
				if (SemanticRole.CONTEXT.equals(concept.getDistributedInherent())) {
					builder.of(c);
				} else {
					builder.within(c);
				}
			}
		}
		if (concept.getCompresent() != null) {
			Concept c = declareInternal(concept.getCompresent(), ontology, monitor);
			if (c != null) {
				builder.with(c);
			}
		}
		if (concept.getCausant() != null) {
			Concept c = declareInternal(concept.getCausant(), ontology, monitor);
			if (c != null) {
				builder.from(c);
			}
		}
		if (concept.getCaused() != null) {
			Concept c = declareInternal(concept.getCaused(), ontology, monitor);
			if (c != null) {
				builder.to(c);
			}
		}
		if (concept.getMotivation() != null) {
			Concept c = declareInternal(concept.getMotivation(), ontology, monitor);
			if (c != null) {
				if (SemanticRole.GOAL.equals(concept.getDistributedInherent())) {
					builder.of(c);
				} else {
					builder.withGoal(c);
				}
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

		if (concept.getSemanticModifier() != null) {
			Concept other = null;
			if (concept.getComparisonConcept() != null) {
				other = declareInternal(concept.getComparisonConcept(), ontology, monitor);
			}
			builder.as(concept.getSemanticModifier(), other == null ? (Concept[]) null : new Concept[] { other });
		}

		Concept ret = null;
		try {

			ret = builder.buildConcept();

			/*
			 * handle unions and intersections
			 */
			if (concept.getOperands().size() > 0) {
				List<Concept> concepts = new ArrayList<>();
				concepts.add(ret);
				for (KimConcept op : concept.getOperands()) {
					concepts.add(declareInternal(op, ontology, monitor));
				}
				ret = concept.getExpressionType() == KimConcept.Expression.INTERSECTION
						? this.owl.getIntersection(concepts, ontology, concept.getOperands().get(0).getType())
						: this.owl.getUnion(concepts, ontology, concept.getOperands().get(0).getType());
			}

			// set the k.IM definition in the concept.This must only happen if the
			// concept wasn't there - within build() and repeat if mods are made
			if (builder.axiomsAdded()) {

				this.owl.getOntology(ret.getNamespace()).define(Collections.singletonList(
						Axiom.AnnotationAssertion(ret.getName(), NS.CONCEPT_DEFINITION_PROPERTY, concept.getUrn())));

				// consistency check
				if (!satisfiable(ret)) {
					ret.getType().add(SemanticType.NOTHING);
					monitor.error("the definition of this concept has logical errors and is inconsistent", concept);
				}

				/**
				 * Now that the URN is set, put away the description
				 */
				registerConcept(ret);
			}

		} catch (Throwable e) {
			monitor.error(e, concept);
		}

		if (concept.isNegated()) {
			ret = negated(ret);
		}

		/**
		 * TODO/CHECK Save the declaration, including the source code which could have a
		 * different order
		 */
		if (ret != null) {
			concepts.put(ret.getUrn(), ret);
		}

		return ret;
	}

	public Observable declare(KimObservable concept, Ontology declarationOntology, Scope monitor) {

		if (concept.getNonSemanticType() != null) {
			Concept nsmain = this.owl.getNonsemanticPeer(concept.getModelReference(), concept.getNonSemanticType());
			ObservableImpl observable = ObservableImpl.promote(nsmain);
			observable.setModelReference(concept.getModelReference());
			observable.setName(concept.getFormalName());
			observable.setStatedName(concept.getFormalName());
			observable.setReferenceName(concept.getFormalName());
			return observable;
		}

		Concept main = declareInternal(concept.getMain(), declarationOntology, monitor);
		if (main == null) {
			return null;
		}

		Concept observable = main;

		Observable.Builder builder = new ObservableBuilder(observable, monitor, owl);

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

		if (concept.getValue() != null) {
			Object value = concept.getValue();
			if (value instanceof KimConcept) {
				value = declareConcept((KimConcept) value);
			}
			builder = builder.withInlineValue(value);
		}

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

        builder = builder.optional(concept.isOptional()).generic(concept
                .isGeneric())/* .global(concept.isGlobal()) */
				.named(concept.getFormalName());

		if (concept.isExclusive()) {
			builder = builder.withResolution(Observable.Resolution.Only);
		} else if (concept.isGlobal()) {
			builder = builder.withResolution(Observable.Resolution.All);
		} else if (concept.isGeneric()) {
			builder = builder.withResolution(Observable.Resolution.Any);
		}

		for (var operator : concept.getValueOperators()) {
			builder = builder.withValueOperator(operator.getFirst(), operator.getSecond());
		}

		for (var annotation : concept.getAnnotations()) {
			builder = builder.withAnnotation(new AnnotationImpl(annotation));
		}

		// CHECK: fluidUnits = needsUnits() && !unitsSet;

		return (Observable) builder.buildObservable();
	}

	public void registerConcept(Concept thing) {
		this.concepts.put(thing.getUrn(), thing);
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

	/**
	 * Entry point of a semantic search. If the request has a new searchId, start a
	 * new SemanticExpression and keep it until timeout or completion.
	 *
	 * @param request
	 * @param message
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
					expression = SemanticExpression.create(scope);
					semanticExpressions.put(response.getSearchId(), expression);
				} else {
					response.getErrors().add("Timeout during search");
				}

				for (var match : indexer.query(request.getQueryString(), expression.getCurrent().getScope(),
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<ObservationStrategy> inferStrategies(Observable observable, ContextScope scope) {

		List<ObservationStrategy> ret = new ArrayList<>();

		/*
		 * first course of action is always direct TODO unless observable is abstract or
		 * unsatisfiable?
		 */
		ret.add(ObservationStrategy.direct(observable));

		/*
		 * these should be obtained from the classpath. Plug-ins may extend them.
		 */
		List<ObservationStrategyPattern> strategies = new ArrayList<>();
		for (ObservationStrategyPattern pattern : this.observationStrategyPatterns) {
			if (pattern.matches(observable, scope)) {
				strategies.add(pattern);
			}
		}

		if (strategies.size() > 0) {
			Collections.sort(strategies, new Comparator<>() {

				@Override
				public int compare(ObservationStrategyPattern o1, ObservationStrategyPattern o2) {
					return Integer.compare(o1.getCost(observable, scope), o2.getCost(observable, scope));
				}
			});
			for (ObservationStrategyPattern strategy : strategies) {
				ret.add(strategy.getStrategy(observable, scope));
			}
		}

		return ret;
	}

	@Override
	public boolean hasDistributedInherency(Concept c) {
		return c.getMetadata().get(NS.INHERENCY_IS_DISTRIBUTED, "false").equals("true");
	}

	@Override
	public Collection<Concept> collectComponents(Concept concept, Collection<SemanticType> types) {
		Set<Concept> ret = new HashSet<>();
		KimConcept peer = scope.getService(ResourcesService.class).resolveConcept(concept.getUrn());
		peer.visit(new DefaultVisitor() {
			@Override
			public void visitReference(String conceptName, Set<SemanticType> type, KimConcept validParent) {
				Concept cn = resolveConcept(conceptName);
				if (cn != null && Sets.intersection(type, Utils.Collections.asSet(types)).size() == types.size()) {
					ret.add(cn);
				}
			}
		});
		return ret;
	}

	@Override
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

		return declareConcept(scope.getService(ResourcesService.class).resolveConcept(declaration));
	}

	/**
	 * A do-nothing KimVisitor for less painful derivations when only a few actions
	 * are needed.
	 * 
	 * @author ferdinando.villa
	 *
	 */
	public static class DefaultVisitor implements KimVisitor {

		@Override
		public void visitAuthority(String authority, String term) {
		}

		@Override
		public void visitDeclaration(KimConcept declaration) {
		}

		@Override
		public void visitReference(String conceptName, Set<SemanticType> type, KimConcept validParent) {
		}

		@Override
		public void visitNamespace(KimNamespace kimNamespace) {
		}

		@Override
		public void visitModel(KimModel kimNamespace) {
		}

		@Override
		public void visitObserver(KimInstance kimNamespace) {
		}

		@Override
		public void visitConceptStatement(KimConceptStatement kimNamespace) {
		}

		@Override
		public void visitTemplate(org.integratedmodelling.klab.api.lang.kim.KimMacro.Field valueOf,
				KimConcept validParent, boolean mandatory) {
		}

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

}
