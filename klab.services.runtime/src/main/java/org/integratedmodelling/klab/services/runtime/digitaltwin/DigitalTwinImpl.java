package org.integratedmodelling.klab.services.runtime.digitaltwin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.runtime.MemberClassifierExecutor;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.runtime.knowledge.DataflowGraph;
import org.integratedmodelling.klab.runtime.knowledge.ProvenanceGraph;
import org.integratedmodelling.klab.runtime.storage.StorageManagerImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedPseudograph;
import org.jgrapht.graph.DefaultEdge;

/** TODO each digital twin should have its own logger */
public class DigitalTwinImpl implements DigitalTwin {

  private final KnowledgeGraphNeo4j knowledgeGraph;
  private final StorageManager storageManager;
  private final ContextScope rootScope;
  private final Scheduler scheduler;
  private final ConsequenceEngine consequenceEngine;
  private final Configuration configuration;
  private long transientId = Klab.getNextId();
  private long parentTransientId = -1000;
  private Map<Object, TransactionImpl> processWriterClaims = new HashMap<>();
  private Cache<Long, KnowledgeGraph.Commit> commitCache =
      CacheBuilder.newBuilder()
          .maximumSize(/* TODO initialize from service settings */ 200)
          .expireAfterAccess(/* TODO this too */ 10, TimeUnit.MINUTES)
          .build();

  @Override
  public long getId() {
    return CONTEXT_ASSET.getId();
  }

  @Override
  public long getParentId() {
    return 0;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  @Override
  public int getChildrenCount() {
    return -1; // TODO should implement so that we have a main obs count
  }

  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  @Override
  public long getParentTransientId() {
    return parentTransientId;
  }

  public void setParentTransientId(long parentTransientId) {
    this.parentTransientId = parentTransientId;
  }

  @Override
  public Type classify() {
    return Type.CONTEXT;
  }

  /** Native geometry operations over private backing values, with privacy retained by derivatives. */
  private static final class LocalGeometry implements java.lang.reflect.InvocationHandler {
    private final Geometry source;

    private LocalGeometry(Geometry source) { this.source = source; }

    static Geometry detach(Geometry geometry) {
      var plain = geometry.isEmpty() ? Geometry.builder().build()
          : (org.integratedmodelling.klab.api.geometry.impl.GeometryImpl) Geometry.create(geometry.encode());
      if (geometry.isEmpty()) { plain.setScalar(false); plain.setEmpty(true); }
      return geometry instanceof Scale
          ? new org.integratedmodelling.klab.runtime.scale.ScaleImpl(plain) : plain;
    }

    static Geometry wrap(Geometry geometry) {
      if (geometry instanceof Geometry.LocalSnapshot) return geometry;
      var contracts = new LinkedHashSet<Class<?>>();
      contracts.add(Geometry.LocalSnapshot.class);
      for (Class<?> type = geometry.getClass(); type != null; type = type.getSuperclass())
        for (var contract : type.getInterfaces())
          if (contract.getName().startsWith("org.integratedmodelling.klab.api.")) contracts.add(contract);
      return (Geometry) java.lang.reflect.Proxy.newProxyInstance(DigitalTwinImpl.class.getClassLoader(),
          contracts.toArray(Class<?>[]::new), new LocalGeometry(geometry));
    }

    private static Geometry isolate(Geometry geometry) {
      return (Geometry) ObservationReadView.copy(geometry, new IdentityHashMap<>());
    }

    private static Object nativeArgument(Object value) {
      if (value != null && java.lang.reflect.Proxy.isProxyClass(value.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(value) instanceof LocalExtent extent)
        return extent.source;
      if (value instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> extent)
        return new org.integratedmodelling.klab.runtime.scale.ScaleImpl(Geometry.create(extent.encode()))
            .extent(extent.getType());
      if (!(value instanceof Geometry geometry)) return value;
      if (java.lang.reflect.Proxy.isProxyClass(geometry.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(geometry) instanceof LocalGeometry local)
        return local.source;
      return detach(geometry);
    }

    private static Object result(Object value) {
      if (value instanceof Geometry geometry)
        return geometry == Geometry.EMPTY || geometry == Geometry.UNIVERSAL ? isolate(geometry) : wrap(geometry);
      if (value instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> extent)
        return LocalExtent.wrap(extent);
      if (value instanceof List<?> list) return list.stream().map(LocalGeometry::result).toList();
      if (value instanceof Iterator<?> iterator) return new Iterator<Object>() {
        public boolean hasNext() { return iterator.hasNext(); }
        public Object next() { return result(iterator.next()); }
      };
      if (value instanceof java.util.stream.Stream<?> stream) return stream.map(LocalGeometry::result);
      return value;
    }

    @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments)
        throws Throwable {
      if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
        case "equals" -> proxy == arguments[0];
        case "hashCode" -> source.encode().hashCode();
        case "toString" -> source.encode();
        default -> throw new UnsupportedOperationException(method.toString());
      };
      if (method.getDeclaringClass() == Geometry.LocalSnapshot.class) return switch (method.getName()) {
        case "isolate" -> isolate((Geometry) arguments[0]);
        case "promote" -> source instanceof Scale ? proxy
            : wrap(new org.integratedmodelling.klab.runtime.scale.ScaleImpl(source,
                (org.integratedmodelling.klab.api.scope.Scope) arguments[0]));
        default -> throw new UnsupportedOperationException(method.toString());
      };
      if (method.getName().equals("key"))
        return org.integratedmodelling.klab.api.utils.Utils.Strings.hash(source.encode());
      if (method.getName().equals("as")) {
        var requested = (Class<?>) arguments[0];
        if (requested == Scale.class) return ((Geometry.LocalSnapshot) proxy).promote(null);
        if (requested == Geometry.class && source instanceof Scale)
          return isolate(Geometry.create(source.encode()));
        if (requested.isInstance(proxy)) return proxy;
        throw new UnsupportedOperationException("Local geometry exposes API views, not mutable implementation casts");
      }
      var inputs = arguments == null ? null : Arrays.stream(arguments).map(LocalGeometry::nativeArgument).toArray();
      try { return result(method.invoke(source, inputs)); }
      catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
    }
  }
  /** Keep geometry conversions from a private extent inside the same local boundary. */
  private static final class LocalExtent implements java.lang.reflect.InvocationHandler {
    private final org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> source;
    private LocalExtent(org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> source) {
      this.source = source;
    }
    static Object wrap(org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> source) {
      var contracts = new LinkedHashSet<Class<?>>();
      for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass())
        for (var contract : type.getInterfaces())
          if (contract.getName().startsWith("org.integratedmodelling.klab.api.")) contracts.add(contract);
      return java.lang.reflect.Proxy.newProxyInstance(DigitalTwinImpl.class.getClassLoader(),
          contracts.toArray(Class<?>[]::new), new LocalExtent(source));
    }
    @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments)
        throws Throwable {
      if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
        case "equals" -> proxy == arguments[0];
        case "hashCode" -> source.hashCode();
        case "toString" -> source.toString();
        default -> throw new UnsupportedOperationException(method.toString());
      };
      if (method.getName().equals("as")) {
        var requested = (Class<?>) arguments[0];
        if (requested.isInstance(proxy)) return proxy;
        if (!requested.isInterface()) throw new UnsupportedOperationException("Private extents expose API views");
      }
      var inputs = arguments == null ? null : Arrays.stream(arguments).map(LocalGeometry::nativeArgument).toArray();
      try { return LocalGeometry.result(method.invoke(source, inputs)); }
      catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
    }
  }
  /** Read-only domain interfaces with detached mutable payloads; no serialization of parents. */
  private static final class ObservationReadView implements java.lang.reflect.InvocationHandler {
    private final Object source;
    private final IdentityHashMap<Object, Object> copies;
    private final Map<java.lang.reflect.Method, Object> values = new HashMap<>();

    private ObservationReadView(Object source, IdentityHashMap<Object, Object> copies) {
      this.source = source;
      this.copies = copies;
    }

    static Observation of(Observation source) {
      return (Observation) copy(source, new IdentityHashMap<>());
    }

    static Observation source(Observation observation) {
      if (java.lang.reflect.Proxy.isProxyClass(observation.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(observation) instanceof ObservationReadView view)
        return (Observation) view.source;
      return observation;
    }

    private static Object copy(Object value, IdentityHashMap<Object, Object> copies) {
      if (value == null || value instanceof String || value instanceof Boolean
          || value instanceof Character || value instanceof Enum<?> || value instanceof Class<?>
          || value instanceof Byte || value instanceof Short || value instanceof Integer
          || value instanceof Long || value instanceof Float || value instanceof Double
          || value instanceof java.math.BigDecimal || value instanceof java.math.BigInteger
          || value instanceof java.net.URI || value instanceof java.net.URL
          || value instanceof UUID || value instanceof java.time.temporal.TemporalAccessor
          || value instanceof java.time.Duration || value instanceof java.time.Period) return value;
      if (copies.containsKey(value)) return copies.get(value);
      if (value instanceof Geometry geometry
          && !(value instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?>)
          && !(value instanceof org.integratedmodelling.klab.api.services.resolver.Coverage)) {
        var detached = LocalGeometry.detach(geometry);
        var result = LocalGeometry.wrap(detached);
        copies.put(value, result);
        detached.getMetadata().putAll((Metadata) copy(geometry.getMetadata(), copies));
        return result;
      }
      if (value instanceof Data.ShardingStrategy strategy) return strategy.copy();
      if (value instanceof org.integratedmodelling.klab.api.knowledge.Urn urn) {
        var result = org.integratedmodelling.klab.api.knowledge.Urn.of(urn.toString());
        result.getParameters().clear();
        result.getParameters().putAll(urn.getParameters());
        copies.put(value, result);
        return result;
      }
      if (value instanceof Map<?, ?> map) {
        Map<Object, Object> result;
        if (value instanceof Metadata) result = (Map) Metadata.create();
        else if (value instanceof org.integratedmodelling.klab.api.lang.Annotation annotation)
          result = (Map) new org.integratedmodelling.klab.api.lang.AnnotationImpl();
        else if (value instanceof org.integratedmodelling.klab.api.collections.Parameters<?>)
          result = (Map) org.integratedmodelling.klab.api.collections.Parameters.create();
        else result = new LinkedHashMap<>();
        copies.put(value, result);
        map.forEach((key, item) -> result.put(copy(key, copies), copy(item, copies)));
        if (value instanceof org.integratedmodelling.klab.api.collections.Parameters<?> parameters
            && result instanceof org.integratedmodelling.klab.api.collections.impl.ParametersImpl copied) {
          copied.setUnnamedKeys((List) copy(parameters.getUnnamedKeys(), copies));
          if (parameters instanceof org.integratedmodelling.klab.api.collections.impl.ParametersImpl<?> implementation)
            copied.setTemplateVariables((org.integratedmodelling.klab.api.collections.Parameters<String>)
                copy(implementation.getTemplateVariables(), copies));
        }
        return result;
      }
      if (value instanceof Collection<?> collection) {
        Collection<Object> result = value instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();
        copies.put(value, result);
        collection.forEach(item -> result.add(copy(item, copies)));
        return result;
      }
      if (value.getClass().isArray()) {
        int length = java.lang.reflect.Array.getLength(value);
        var result = java.lang.reflect.Array.newInstance(value.getClass().getComponentType(), length);
        copies.put(value, result);
        for (int i = 0; i < length; i++)
          java.lang.reflect.Array.set(result, i, copy(java.lang.reflect.Array.get(value, i), copies));
        return result;
      }
      if (value instanceof Date date) return new Date(date.getTime());
      var interfaces = new LinkedHashSet<Class<?>>();
      for (var type = value.getClass(); type != null; type = type.getSuperclass()) {
        for (var contract : type.getInterfaces()) {
          if (contract.getName().startsWith("org.integratedmodelling.klab.api."))
            interfaces.add(contract);
        }
      }
      if (interfaces.isEmpty())
        throw new UnsupportedOperationException("No safe observation read projection for " + value.getClass().getName());
      var handler = new ObservationReadView(value, copies);
      var proxy = java.lang.reflect.Proxy.newProxyInstance(DigitalTwinImpl.class.getClassLoader(),
          interfaces.toArray(Class<?>[]::new), handler);
      copies.put(value, proxy);
      // Capture bean fields now, under the transaction monitor. Query methods may compute values,
      // but never return unwrapped domain objects or mutable containers from the source.
      for (var contract : interfaces) for (var method : contract.getMethods()) {
        if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
            && method.getParameterCount() == 0 && readable(method.getName())
            && method.getReturnType() != void.class && !method.getName().equals("iterator")) {
          try { handler.values.put(method, copy(method.invoke(value), copies)); }
          catch (java.lang.reflect.InvocationTargetException error) {
            throw new IllegalStateException("Cannot snapshot " + method, error.getCause());
          } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
      }
      return proxy;
    }

    /** Canonicalize the same detached getter graph exposed to classifier code, including payloads. */
    static String fingerprint(Observation observation) {
      return fingerprint(observation,0);
    }
    static String fingerprint(Observation observation,int ownChildDelta) {
      var snapshot=of(observation);
      if(ownChildDelta!=0) {
        var handler=(ObservationReadView)java.lang.reflect.Proxy.getInvocationHandler(snapshot);
        handler.values.replaceAll((method,value)->method.getName().equals("getChildrenCount")
            ? ((Integer)value)-ownChildDelta : value);
      }
      return fingerprintValue(snapshot,new IdentityHashMap<>());
    }
    private static String fingerprintValue(Object value,IdentityHashMap<Object,Integer> seen) {
      if(value==null) return "null";
      if(value instanceof String || value instanceof Number || value instanceof Boolean
          || value instanceof Character || value instanceof Enum<?> || value instanceof Class<?>
          || value instanceof java.net.URI || value instanceof java.net.URL || value instanceof UUID
          || value instanceof java.time.temporal.TemporalAccessor || value instanceof java.time.Duration
          || value instanceof java.time.Period) return value.getClass().getName()+":"+value.toString().length()+":"+value;
      if(seen.containsKey(value)) return "ref:"+seen.get(value);
      seen.put(value,seen.size());
      if(value instanceof Geometry geometry) return "geometry:"+geometry.encode()+":"+fingerprintValue(geometry.getMetadata(),seen);
      if(value instanceof Date date) return "date:"+date.getTime();
      if(value instanceof Data.ShardingStrategy strategy) return "sharding:"+org.integratedmodelling.common.utils.Utils.Json.asString(strategy);
      if(value instanceof org.integratedmodelling.klab.api.knowledge.Urn urn)
        return "urn:"+urn+":"+fingerprintValue(urn.getParameters(),seen);
      if(value instanceof Map<?,?> map) {
        var entries=new TreeMap<String,Object>();
        map.forEach((key,item)->entries.put(fingerprintValue(key,new IdentityHashMap<>()),item));
        var result=new StringBuilder("map{");entries.forEach((key,item)->result.append(key).append('=').append(fingerprintValue(item,seen)).append(';'));
        result.append('}');
        if(value instanceof org.integratedmodelling.klab.api.collections.Parameters<?> parameters)
          result.append("unnamed:").append(fingerprintValue(parameters.getUnnamedKeys(),seen));
        if(value instanceof org.integratedmodelling.klab.api.collections.impl.ParametersImpl<?> parameters)
          result.append("templates:").append(fingerprintValue(parameters.getTemplateVariables(),seen));
        return result.toString();
      }
      if(value instanceof Collection<?> items) {
        var result=new StringBuilder("collection[");for(var item:items)result.append(fingerprintValue(item,seen)).append(';');return result.append(']').toString();
      }
      if(value.getClass().isArray()) {
        var result=new StringBuilder("array[");for(int i=0;i<java.lang.reflect.Array.getLength(value);i++)result.append(fingerprintValue(java.lang.reflect.Array.get(value,i),seen)).append(';');return result.append(']').toString();
      }
      if(java.lang.reflect.Proxy.isProxyClass(value.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(value) instanceof ObservationReadView view) {
        var fields=new TreeMap<String,Object>();view.values.forEach((method,item)->{
          // A durable observation's transport wrapper has a fresh transient identity on each read.
          if(!(view.source instanceof Observation o && o.getId()>0 && method.getName().equals("getTransientId")))
            fields.put(method.toGenericString(),item);
        });
        return "bean:"+fingerprintValue(fields,seen);
      }
      throw new UnsupportedOperationException("No classification input fingerprint for "+value.getClass());
    }

    private static boolean readable(String name) {
      return name.startsWith("get") || name.startsWith("is") || name.startsWith("has")
          || Set.of("name", "identity", "semantics", "geometry", "metadata", "empty",
              "notifications", "children", "dataKey", "size", "encode", "displayName",
              "displayLabel", "codeName", "classify", "groupSize").contains(name);
    }

    @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments)
        throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "toString" -> source.toString();
          case "hashCode" -> source.hashCode();
          case "equals" -> proxy == arguments[0];
          default -> throw new UnsupportedOperationException(method.toString());
        };
      }
      if (values.containsKey(method)) return values.get(method);
      if (method.getName().equals("iterator") && source instanceof Observation)
        return List.of(proxy).iterator();
      // Observable builders construct independent specifications by contract.
      if (method.getName().equals("builder") && source instanceof org.integratedmodelling.klab.api.knowledge.Observable)
        return method.invoke(source, arguments);
      if (!readable(method.getName()) && !Set.of("singular", "collective", "asConcept", "dimension", "at", "contains", "intersects").contains(method.getName()))
        throw new UnsupportedOperationException("Observation reads cannot invoke " + method.getName());
      try { return copy(method.invoke(source, arguments), copies); }
      catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
    }
  }

  /**
   * The transactional graph is a directed acyclic graph that represents the execution of a digital
   * twin activity, capturing the relationships and changes between assets. The graph is robust to
   * identity switching between assets as long as their ID remains consistent.
   */
  public class TransactionImpl implements Transaction {

    private enum AttemptState { OPEN, COMMITTING, COMMITTED, FAILED }
    private AttemptState attemptState = AttemptState.OPEN;

    private TransactionImpl rootAttempt() {
      return parent == null ? this : parent.rootAttempt();
    }

    private void requireOpen() {
      if (rootAttempt().attemptState != AttemptState.OPEN)
        throw new IllegalStateException("Transaction attempt is " + rootAttempt().attemptState);
    }

    private final String id = Utils.Names.fastName();
    private final Set<RuntimeAsset> modified;
    private final Set<RuntimeAsset> added;

    private record Attribution(
        ObservationImpl original, ObservationImpl replacement, String before,
        List<Concept> families, Set<String> snapshots) {}

    private Map<Observation, Attribution> attributions = new IdentityHashMap<>();
    private Observation target;
    private final Activity activity;
    private final ServiceContextScope scope;
    private final Queue<Throwable> failures;
    private final Graph<RuntimeAsset, RelationshipEdge> graph;
    private final Map<Observation, Executor> contextualizers;
    private TransactionImpl parent; // null in the root activity
    private org.integratedmodelling.klab.api.data.TemporalWriteSet temporalWrites;
    private final List<java.util.function.Supplier<org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal>> journalFactories = new ArrayList<>();

    @Override public void stageSchedulerJournal(java.util.function.Supplier<org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal> journal) {
      if (parent != null) { parent.stageSchedulerJournal(journal); return; }
      synchronized (graph) { requireOpen(); journalFactories.add(journal); }
    }

    @Override public org.integratedmodelling.klab.api.data.TemporalWriteSet getTemporalWrites() {
      return parent == null ? temporalWrites : parent.getTemporalWrites();
    }
    @Override public void setTemporalWrites(org.integratedmodelling.klab.api.data.TemporalWriteSet writes) {
      if (parent != null) { parent.setTemporalWrites(writes); return; }
      synchronized (graph) {
        requireOpen();
        if (temporalWrites != null && temporalWrites != writes)
          throw new IllegalStateException("A root transaction has only one temporal write set");
        temporalWrites = writes;
      }
    }
    private Map<Concept, Scale> cohortGeometries = new HashMap<>();
    private List<Runnable> beforeCommitActions = new ArrayList<>();
    private List<KnowledgeGraph.ClassificationReadSet> classificationReadGuards = new ArrayList<>();
    private List<Runnable> classificationLocalGuards = new ArrayList<>();
    private long[] classificationMutationStamp = new long[]{0};
    // Only increments performed by this root's link() bookkeeping may be projected through a
    // frozen classification read. Arbitrary setters and all other payload changes still fail.
    private Map<Observation,Integer> classificationChildDeltas = new IdentityHashMap<>();
    public long classificationMutationStamp() { synchronized(graph) { requireOpen(); return classificationMutationStamp[0]; } }


    public void guardClassificationOverlay(Cohort cohort, Observation producer, List<Observation> persisted, List<String> expected) {
      synchronized(graph) {
        requireOpen();var frozen=List.copyOf(expected);
        var baseline=new IdentityHashMap<>(classificationChildDeltas);
        classificationLocalGuards.add(()->{
          if(!classificationOverlay(cohort,producer,persisted,baseline).state().equals(frozen))
            throw new IllegalStateException("Classification local input or producer evidence changed");
        });
      }
    }


    public record ClassificationOverlay(List<Observation> members, List<String> state, List<String> rawState) {
      public ClassificationOverlay { members=List.copyOf(members);state=List.copyOf(state);rawState=List.copyOf(rawState); }
    }

    /** Fast local-only capture. Never invoke services or await work while holding this monitor. */
    public ClassificationOverlay classificationOverlay(Cohort cohort, Observation producer) {
      return classificationOverlay(cohort,producer,List.of());
    }
    public ClassificationOverlay classificationOverlay(Cohort cohort, Observation producer, List<Observation> persisted) {
      return classificationOverlay(cohort,producer,persisted,null);
    }
    private ClassificationOverlay classificationOverlay(Cohort cohort, Observation producer, List<Observation> persisted,
        Map<Observation,Integer> childBaseline) {
      synchronized(graph) {
        if(rootAttempt().attemptState==AttemptState.FAILED || rootAttempt().attemptState==AttemptState.COMMITTED)
          throw new IllegalStateException("Classification attempt is no longer live");
        var members=new ArrayList<Observation>();var state=new ArrayList<String>();var rawState=new ArrayList<String>();
        var originals=new LinkedHashMap<String,Observation>();
        for(var baseline:persisted) {
          var member=(Observation)checkPresentAsset(baseline);
          originals.put("id:"+member.getId(),member);
        }
        var actualCohort=checkPresentAsset(cohort);
        if(graph.containsVertex(actualCohort)) for(var edge:graph.outgoingEdgesOf(actualCohort))
          if(edge.relationship==GraphModel.Relationship.HAS_MEMBER && graph.getEdgeTarget(edge) instanceof Observation member)
            originals.put(member.getId()>0?"id:"+member.getId():"transient:"+member.getTransientId(),member);
        for(var member:originals.values()) {
          var view=readObservation(member);members.add(view);
          int delta=childBaseline==null?0:classificationChildDeltas.getOrDefault(member,0)-childBaseline.getOrDefault(member,0);
          rawState.add("payload:"+ObservationReadView.fingerprint(member,delta));
          state.add("payload:"+ObservationReadView.fingerprint(member,delta)+":"+ObservationReadView.fingerprint(view,delta));
          rawState.add((member.getId()>0?"id:"+member.getId():"transient:"+member.getTransientId())+":"+member.getObservable().getUrn()+":"+member.getGeometry().encode());
          state.add((member.getId()>0?"id:"+member.getId():"transient:"+member.getTransientId())+":"+member.getObservable().getUrn()+":"+view.getObservable().getUrn()+":"+member.getGeometry().encode());
        }
        int producerDelta=childBaseline==null?0:classificationChildDeltas.getOrDefault(producer,0)-childBaseline.getOrDefault(producer,0);
        state.add("producerPayload:"+ObservationReadView.fingerprint(producer,producerDelta));
        rawState.add("producerPayload:"+ObservationReadView.fingerprint(producer,producerDelta));
        state.add("producerIdentity:"+producer.getTransientId()+":"+producer.getId()+":"+producer.getObservable().getUrn()+":"+producer.getGeometry().encode());
        for(var link:incoming(producer)) if(link.source() instanceof Activity a
            && link.type()==GraphModel.Relationship.forContextualization(producer.getObservable().getContextualization())) {
          var witness="producer:"+a.getTransientId()+":"+a.getType()+":"+a.getOutcome()+":"+a.getMetadata().get(Metadata.IM_EXECUTION_GEOMETRY);
          state.add(witness);rawState.add(witness);
        }
        for(var link:incoming(producer)) if(link.type()==GraphModel.Relationship.HAS_CHILD) {
          var witness="producerContext:"+link.source().getId();state.add(witness);rawState.add(witness);
        }
        for(var link:outgoing(producer)) if(link.type()==GraphModel.Relationship.CONTRIBUTED_TO) {
          var witness="producerCohort:"+link.target().getId();state.add(witness);rawState.add(witness);
        }
        rawState.add("producerIdentity:"+producer.getTransientId()+":"+producer.getId()+":"+producer.getObservable().getUrn()+":"+producer.getGeometry().encode());
        Collections.sort(state);Collections.sort(rawState);
        return new ClassificationOverlay(members,state,rawState);
      }
    }

    /** Accept only the expected projected snapshot, then atomically record this hop's own effects. */
    public ClassificationOverlay stageClassificationSnapshot(
        List<MemberClassifierExecutor.PendingAttribution> pending, ContextScope execution,
        Cohort cohort, Observation producer, List<Observation> persisted, List<String> expected) {
      synchronized(graph) {
        requireOpen();
        if(!classificationOverlay(cohort,producer,persisted).state().equals(expected))
          throw new IllegalStateException("Classification projected input changed");
        stageAttributions(pending,execution);
        return classificationOverlay(cohort,producer,persisted);
      }
    }

    /** Guards run on the exact native root-commit transaction, before any root writes. */
    public void guardClassificationReadSet(KnowledgeGraph.ClassificationReadSet readSet) {
      synchronized(graph) { requireOpen(); classificationReadGuards.add(readSet); }
    }

    private List<Runnable> afterCommitActions = new ArrayList<>();
    private List<Runnable> rollbackActions = new ArrayList<>();
    private List<org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal> schedulerJournal =
        new ArrayList<>();

    @Override
    public Executor getExecutor(Observation observation) {
      return contextualizers.get(observation);
    }

    /** Claim a single semantic writer in this transaction and the committed graph. */
    public void linkProcessInfluence(
        Observation process, Observation quality, String model, String name) {
      linkProcessInfluence(process, quality, model, name, List.of("AFFECTS"));
    }

    public void linkProcessInfluence(
        Observation process,
        Observation quality,
        String model,
        String name,
        List<String> relations) {
      synchronized (graph) {
        requireOpen();
        var owner = this;
        while (owner.parent != null) owner = owner.parent;
        final var rootOwner = owner;
        final Object key = quality.getId() > 0 ? Long.valueOf(quality.getId()) : quality;
        synchronized (DigitalTwinImpl.this) {
          if (processWriterClaims == null) processWriterClaims = new HashMap<>();
          var claimed = processWriterClaims.putIfAbsent(key, rootOwner);
          if (claimed != null && claimed != rootOwner)
            throw new IllegalStateException("Concurrent process writer registration for " + name);
          if (claimed == null) {
            Runnable release =
                () -> {
                  synchronized (DigitalTwinImpl.this) {
                    processWriterClaims.remove(key, rootOwner);
                  }
                };
            afterCommit(release);
            afterRollback(release);
          }
        }
        if (quality.getId() > 0) {
          for (var edge :
              knowledgeGraph.getLinks(
                  quality,
                  GraphModel.Relationship.Direction.INCOMING,
                  scope,
                  GraphModel.Relationship.AFFECTS)) {
            if (org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.INFLUENCE.equals(
                    edge.properties()
                        .get(org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE))
                && edge.source().getId() != process.getId())
              throw new IllegalStateException("Competing process writers for " + name);
          }
        }
        if (graph.containsVertex(quality)) {
          for (var edge : graph.incomingEdgesOf(quality)) {
            var writer = graph.getEdgeSource(edge);
            if (edge.relationship == GraphModel.Relationship.AFFECTS
                && org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.INFLUENCE.equals(
                    edge.properties.get(
                        org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE))
                && writer != process
                && (process.getId() <= 0 || writer.getId() != process.getId()))
              throw new IllegalStateException("Competing process writers for " + name);
          }
        }
        link(
            process,
            quality,
            GraphModel.Relationship.AFFECTS,
            org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE,
            org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.INFLUENCE,
            "effect",
            "AFFECTED",
            "model",
            model,
            "binding",
            name,
            "writeState",
            "NEXT_COMMITTED",
            "semanticRelations",
            relations);
      }
    }

    @Override
    public void beforeCommit(Runnable action) {
      synchronized (graph) {
        requireOpen();
        beforeCommitActions.add(action);
      }
    }

    @Override
    public void afterCommit(Runnable action) {
      synchronized (graph) {
        requireOpen();
        afterCommitActions.add(action);
      }
    }

    @Override
    public void afterRollback(Runnable action) {
      synchronized (graph) {
        requireOpen();
        rollbackActions.add(action);
      }
    }

    @Override
    public void stageSchedulerJournal(
        org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal journal) {
      if (journal.commitId() != 0) throw new IllegalArgumentException("Journal already committed");
      synchronized (graph) {
        requireOpen();
        schedulerJournal.add(journal);
      }
    }

    private void rollbackSchedulerState() {
      synchronized (graph) {
        for (var action : rollbackActions.reversed()) {
          try {
            action.run();
          } catch (Throwable failure) {
            scope.error(failure);
          }
        }
        rollbackActions.clear();
        beforeCommitActions.clear();
        afterCommitActions.clear();
        schedulerJournal.clear();
        journalFactories.clear();
        contextualizers.clear();
      }
    }

    static class RelationshipEdge extends DefaultEdge {
      GraphModel.Relationship relationship;
      Geometry geometry;
      int sequence = -1;
      Map<String, Object> properties = new LinkedHashMap<>();

      public RelationshipEdge(GraphModel.Relationship relationship, Object... data) {
        this.relationship = relationship;
        if (data != null) {
          for (int i = 0; i + 1 < data.length; i += 2) {
            if (data[i] instanceof String key && data[i + 1] != null) {
              properties.put(key, data[i + 1]);
              if ((key.equals("sequence") || key.equals("rank")) && data[i + 1] instanceof Number n)
                sequence = n.intValue();
              if (key.equals("geometry") && data[i + 1] instanceof Geometry g) geometry = g;
            }
          }
        }
      }
    }

    /** Call this before contextualization to commit to register any new executors. */
    @Override
    public void registerExecutors() {
      synchronized (graph) {
        requireOpen();
        for (var observation : contextualizers.keySet()) {
          var executor = contextualizers.get(observation);
          var previousExecution = observation.getMetadata().get(Scheduler.EXECUTION_METADATA_KEY);
          observation.getMetadata().put(Scheduler.EXECUTION_METADATA_KEY, true);
          afterRollback(
              () -> {
                if (previousExecution == null)
                  observation.getMetadata().remove(Scheduler.EXECUTION_METADATA_KEY);
                else
                  observation.getMetadata().put(Scheduler.EXECUTION_METADATA_KEY, previousExecution);
              });
          update(observation);
          afterCommit(() -> scheduler.registerExecutor(observation, executor::run));
        }
      }
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public Transaction getParent() {
      return parent;
    }

    public TransactionImpl(Activity activity, ServiceContextScope scope, Object... data) {

      // The same endpoints may have prerequisites and several descriptive property classes.
      this.graph = new DirectedPseudograph<>(RelationshipEdge.class);
      this.activity = activity;
      this.scope = scope;
      this.modified = new HashSet<>();
      this.added = new HashSet<>();
      this.failures = new ConcurrentLinkedQueue<>();
      this.contextualizers = new ConcurrentHashMap<>();
      this.cohortGeometries = new HashMap<>();
      boolean activityLinked = false;

      synchronized (graph) {
        this.graph.addVertex(activity);
        if (scope.getConsequenceCause() != null) {
          instrumentConsequenceActivity();
          var trigger = knowledgeGraph.getAsset(scope.getConsequenceCause().triggeringActivityId(),
              scope, Activity.class);
          if (trigger == null || trigger.getId() <= 0)
            throw new IllegalStateException("Consequence triggering activity is unavailable");
          this.graph.addVertex(trigger);
          this.graph.addEdge(trigger, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));
          // Available on ActivityStarted, before persistence assigns this activity's own identity.
          if (activity instanceof ActivityImpl mutable) {
            mutable.setTriggeringActivityUrn(trigger.getUrn());
            mutable.setParentId(trigger.getId());
            mutable.setParentTransientId(trigger.getTransientId());
          }
          activityLinked = true;
        }

        if (data != null) {
          for (Object datum : data) {
            if (datum == RuntimeAsset.PROVENANCE_ASSET
                || datum == RuntimeAsset.CONTEXT_ASSET
                || datum == RuntimeAsset.DATAFLOW_ASSET) {
              this.graph.addVertex((RuntimeAsset) datum);
            } else if (datum instanceof Agent agent) {
              if (scope.getConsequenceCause() != null) continue;
              this.graph.addVertex(agent);
              this.graph.addEdge(
                  activity, agent, new RelationshipEdge(GraphModel.Relationship.BY_AGENT));
            } else if (datum instanceof Activity activity1) {
              if (scope.getConsequenceCause() != null) continue;
              this.graph.addVertex(activity1);
              this.graph.addEdge(
                  activity1, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));
              activityLinked = true;
            } else if (datum instanceof Observation observation) {
              setTarget(observation);
            }
          }
        }

        if (!activityLinked) {
          this.graph.addEdge(
              RuntimeAsset.PROVENANCE_ASSET,
              activity,
              new RelationshipEdge(GraphModel.Relationship.HAS_CHILD));
        }
      }

      scope.registerTransaction(this);
    }

    private TransactionImpl(TransactionImpl parent, Activity activity, Object... data) {

      this.graph = parent.graph;
      this.target = parent.target;
      this.activity = activity;
      this.scope = parent.scope; // TODO careful with executing() being called outside
      this.parent = parent;
      // A child transaction contributes to the one atomic root commit. Keep all mutable commit
      // state root-shared just like the graph itself; otherwise modifications and failures from
      // secondary submissions disappear when the root assembles its commit result.
      this.attributions = parent.attributions;
      this.modified = parent.modified;
      this.added = parent.added;
      this.failures = parent.failures;
      this.cohortGeometries = parent.cohortGeometries;
      this.contextualizers = parent.contextualizers;
      this.beforeCommitActions = parent.beforeCommitActions;
      this.classificationReadGuards = parent.classificationReadGuards;
      this.classificationLocalGuards = parent.classificationLocalGuards;
      this.classificationMutationStamp = parent.classificationMutationStamp;
      this.classificationChildDeltas = parent.classificationChildDeltas;
      this.afterCommitActions = parent.afterCommitActions;
      this.rollbackActions = parent.rollbackActions;
      this.schedulerJournal = parent.schedulerJournal;

      synchronized (graph) {
        this.graph.addVertex(activity);
        instrumentConsequenceActivity();
        this.graph.addEdge(
            parent.activity, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));

        this.graph.addVertex(activity);
        if (data != null) {
          for (Object datum : data) {
            if (datum instanceof Agent agent) {
              if (scope.getConsequenceCause() != null) continue;
              this.graph.addVertex(agent);
              this.graph.addEdge(
                  activity, agent, new RelationshipEdge(GraphModel.Relationship.BY_AGENT));
            } else if (datum instanceof Observation observation) {
              // only link contextualization to the contextualized observation
              if (activity.getType().isContextualization()) {
                // A persisted target need not already occur in this attempt's local graph.
                // link canonicalizes existing IDs and registers endpoints without marking the
                // target as newly added or creating another observation.
                link(
                    activity,
                    observation,
                    GraphModel.Relationship.forContextualization(
                        activity.getType().getContextualization()));
              }
            } // TODO see if we need anything else
          }
        }
      }
    }

    /** Preserve execution phase types while identifying engine submissions and their actual actor. */
    private void instrumentConsequenceActivity() {
      var cause = scope.getConsequenceCause();
      if (cause == null) return;
      activity.getMetadata().put(org.integratedmodelling.klab.api.digitaltwin.Consequence.METADATA_KEY,
          Utils.Json.asString(cause));
      if (activity instanceof ActivityImpl mutable && activity.getType() == Activity.Type.SUBMISSION)
        mutable.setType(Activity.Type.CONSEQUENCE_SUBMISSION);
      var agent = knowledgeGraph.klab();
      if (agent == null || agent.getId() <= 0)
        throw new IllegalStateException("Consequence provenance requires the persisted k.LAB agent");
      graph.addVertex(agent);
      graph.addEdge(activity, agent, new RelationshipEdge(GraphModel.Relationship.BY_AGENT));
    }

    public void setTarget(Observation observation) {
      synchronized (graph) {
        requireOpen();
        this.target = observation;
        this.graph.addVertex(observation);
        this.graph.addEdge(
            activity, observation, new RelationshipEdge(GraphModel.Relationship.CREATED));
      }
    }

    @Override
    public Activity getActivity() {
      return this.activity;
    }

    @Override
    public void checkCohortGeometry(Cohort cohort, Geometry observedGeometry) {
      if (cohort == null || observedGeometry == null) {
        return;
      }
      synchronized (graph) {
        requireOpen();
        var cohortObservable = cohort.getObservable().getSemantics();
        var currentGeometry = cohortGeometries.get(cohortObservable);
        var newGeometry =
            mergeCohortGeometry(
                cohortObservable.getType(),
                currentGeometry == null ? cohort.getGeometry() : currentGeometry,
                observedGeometry);

        this.cohortGeometries.put(cohortObservable, newGeometry);
        applyCohortGeometry(cohort, newGeometry);
      }
    }

    static Scale mergeCohortGeometry(
        Set<SemanticType> semanticTypes, Geometry currentGeometry, Geometry observedGeometry) {
      var current =
          currentGeometry == null || currentGeometry.isUniversal()
              ? null
              : GeometryRepository.INSTANCE.scale(currentGeometry);
      var observed = GeometryRepository.INSTANCE.scale(observedGeometry);
      if (!SemanticType.isOccurrentSubstantial(semanticTypes)) {
        current = withoutTime(current);
        observed = withoutTime(observed);
      }
      if (current == null || current.isUniversal()) {
        return observed;
      }
      return GeometryRepository.INSTANCE.outerUnion(observed, current);
    }

    private static Scale withoutTime(Scale scale) {
      return scale != null && scale.getTime() != null
          ? scale.without(Geometry.Dimension.Type.TIME)
          : scale;
    }

    /**
     * This allows us to keep the graph consistent without having to enforce object identity during
     * contextualization. The logic only applies to observations, as all other assets are generated
     * contextually to the transaction and their IDs may overlap those of observations.
     *
     * @param asset
     * @return
     */
    private RuntimeAsset checkPresentAsset(RuntimeAsset asset) {
      if (!(asset instanceof Observation) || asset.getId() == Observation.UNASSIGNED_ID) {
        return asset; // should never happen, but just in case, never deduplicate UNASSIGNED_ID —
        // IDs are not yet
        // stable
      }
      return graph.vertexSet().stream()
          .filter(a -> a instanceof Observation && a.getId() == asset.getId())
          .findFirst()
          .orElse(asset);
    }

    @Override
    public void add(RuntimeAsset asset) {
      synchronized (graph) {
        requireOpen();
        classificationMutationStamp[0]++;
        asset = checkPresentAsset(asset);
        graph.addVertex(asset);
        added.add(asset);
      }
    }

    @Override
    public void link(
        RuntimeAsset sourceOrig,
        RuntimeAsset destinationOrig,
        GraphModel.Relationship relationship,
        Object... data) {
      synchronized (graph) {
        requireOpen();
        classificationMutationStamp[0]++;
        var source = checkPresentAsset(sourceOrig);
        var destination = checkPresentAsset(destinationOrig);

        graph.addVertex(source);
        graph.addVertex(destination);
        graph.addEdge(source, destination, new RelationshipEdge(relationship, data));

        // TODO do this for all others as well?
        if (source instanceof ObservationImpl sourceObs
            && destination instanceof ObservationImpl targetObs
            && relationship == GraphModel.Relationship.HAS_CHILD) {
          int previousCount=sourceObs.getChildrenCount();
          sourceObs.setChildrenCount(previousCount + 1);
          classificationChildDeltas.merge(sourceObs,1,Integer::sum);
          rollbackActions.add(()->sourceObs.setChildrenCount(Math.max(0,sourceObs.getChildrenCount()-1)));
          // Attribution copies own primitive counters. Keep this exact link increment visible in
          // the staged object as well, so projection and native storage use the same owned delta.
          if(staged(sourceObs) instanceof ObservationImpl replacement && replacement!=sourceObs) {
            replacement.setChildrenCount(replacement.getChildrenCount()+1);
            rollbackActions.add(()->replacement.setChildrenCount(Math.max(0,replacement.getChildrenCount()-1)));
          }
          update(sourceObs);
          targetObs.setParentTransientId(sourceObs.getTransientId());
        } else if (source instanceof CohortImpl sourceCohort
            && destination instanceof ObservationImpl targetObs
            && relationship == GraphModel.Relationship.HAS_MEMBER) {
          sourceCohort.setChildrenCount(sourceCohort.getChildrenCount() + 1);
          update(sourceCohort);
          targetObs.setParentTransientId(sourceCohort.getTransientId());
        }
      }
    }

    @Override
    public void update(RuntimeAsset asset) {
      synchronized (graph) {
        requireOpen();
        classificationMutationStamp[0]++;
        modified.add(checkPresentAsset(asset));
      }
    }

    /** All members are validated and copied before any staged state becomes visible. */
    public void stageAttributions(
        List<MemberClassifierExecutor.PendingAttribution> pending, ContextScope executionScope) {
      synchronized (graph) {
        try {
          stageValidatedAttributions(pending, executionScope);
          classificationMutationStamp[0]++;
        } catch (RuntimeException failure) {
          fail(failure);
          throw failure;
        }
      }
    }

    private void stageValidatedAttributions(
        List<MemberClassifierExecutor.PendingAttribution> pending, ContextScope executionScope) {
      if (activity.getType() != Activity.Type.CLASSIFICATION)
        throw new IllegalStateException("Attributions require a CLASSIFICATION activity");
      synchronized (graph) {
        requireOpen();
        if (!failures.isEmpty()) throw new IllegalStateException("Transaction already failed");
        var reasoner = executionScope.getService(Reasoner.class);
        var batch = new LinkedHashMap<Observation, Attribution>();
        var audit = new ArrayList<Map<String, Object>>();
        var auditedMembers = new ArrayList<Observation>();
        for (var value : pending) {
          var canonical = checkPresentAsset(ObservationReadView.source(value.member()));
          // The graph is unchanged until the entire batch validates, including aliases of a
          // registered member supplied more than once within this batch.
          for (var stagedMember : batch.keySet()) {
            if (stagedMember.getId() == value.member().getId()) {
              canonical = stagedMember;
              break;
            }
          }
          if (!(canonical instanceof ObservationImpl member)
              || member.getId() == 0
              || member.getId() == -1)
            throw new IllegalArgumentException("Classification requires a registered member");
          var previous = batch.containsKey(member) ? batch.get(member) : attributions.get(member);
          var current = previous == null ? member : previous.replacement();
          if (previous == null
              ? !Objects.equals(member.getObservable().getUrn(), value.originalObservable().getUrn())
              : !Objects.equals(member.getObservable().getUrn(), previous.before())
                  || !previous.snapshots().contains(value.originalObservable().getUrn()))
            throw new IllegalStateException("Concurrent member classification");
          var families = new ArrayList<Concept>();
          if (previous != null) families.addAll(previous.families());
          for (var family : families) {
            if (Objects.equals(family.getUrn(), value.abstractPredicate().getUrn())
                || reasoner.is(family, value.abstractPredicate())
                || reasoner.is(value.abstractPredicate(), family)
                || reasoner.is(value.predicate(), family))
              throw new IllegalStateException("Repeated classification in the same predicate family");
          }
          // A concrete attribution can belong to overlapping families even when neither family
          // specializes the other. Never overwrite an existing result in the requested family.
          var existing = new ArrayList<Concept>(reasoner.directTraits(current.getObservable()));
          existing.addAll(reasoner.directRoles(current.getObservable()));
          for (var predicate : existing) {
            if (Objects.equals(predicate.getUrn(), value.abstractPredicate().getUrn())
                || reasoner.is(predicate, value.abstractPredicate()))
              throw new IllegalStateException("Member is already classified in this predicate family");
          }
          var builder = current.getObservable().builder(executionScope);
          if (value.predicate().is(SemanticType.ROLE)) builder.withRole(value.predicate());
          else if (value.predicate().is(SemanticType.PREDICATE))
            builder.withTrait(value.predicate());
          else
            throw new IllegalArgumentException("Classification result is neither trait nor role");
          var after = builder.buildObservable();
          if (after == null || !reasoner.satisfiable(after.getSemantics()))
            throw new IllegalArgumentException("Inconsistent attributed observable");
          var replacement = current.copyForAttribution(after);
          families.add(value.abstractPredicate());
          var snapshots = new HashSet<String>();
          if (previous != null) snapshots.addAll(previous.snapshots());
          snapshots.add(member.getObservable().getUrn());
          snapshots.add(after.getUrn());
          batch.put(member, new Attribution(member, replacement, member.getObservable().getUrn(),
              List.copyOf(families), Set.copyOf(snapshots)));
          var item = new LinkedHashMap<String, Object>();
          item.put("memberTransientId", member.getTransientId());
          item.put("memberId", member.getId());
          item.put("before", current.getObservable().getUrn());
          item.put("after", after.getUrn());
          item.put("abstractPredicate", value.abstractPredicate().getUrn());
          item.put("predicate", value.predicate().getUrn());
          item.put("support", value.support().encode());
          item.put("event", value.event().toKey());
          audit.add(item);
          auditedMembers.add(member);
        }
        int i = 0;
        for (var member : auditedMembers) {
          var attribution = batch.get(member);
          var item = audit.get(i++);
          var data = new ArrayList<Object>();
          item.forEach(
              (key, value) -> {
                data.add(key);
                data.add(value);
              });
          link(
              activity, attribution.original(), GraphModel.Relationship.CLASSIFIED, data.toArray());
          if (attribution.original().getId() > 0) modified.add(attribution.original());
        }
        attributions.putAll(batch);
        var previousAudit = activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS);
        if (previousAudit instanceof List<?> entries) {
          var combined = new ArrayList<Object>(entries);
          combined.addAll(audit);
          activity.getMetadata().put(Metadata.IM_ATTRIBUTIONS, combined);
        } else activity.getMetadata().put(Metadata.IM_ATTRIBUTIONS, audit);
      }
    }

    @Override
    public Observation readObservation(Observation observation) {
      synchronized (graph) {
        if (!failures.isEmpty()) throw new IllegalStateException("Transaction already failed");
        return ObservationReadView.of((Observation) staged(checkPresentAsset(ObservationReadView.source(observation))));
      }
    }

    private RuntimeAsset staged(RuntimeAsset asset) {
      var attribution = attributions.get(asset);
      return attribution == null ? asset : attribution.replacement();
    }

    private void publishAttributions() {
      synchronized (graph) {
        for (var value : attributions.values()) {
          var original = value.original();
          var replacement = value.replacement();
          original.setObservable(replacement.getObservable());
          original.setId(replacement.getId());
          original.setUrn(replacement.getUrn());
          original.setParentId(replacement.getParentId());
          original.getMetadata().putAll(replacement.getMetadata());
          scope.invalidateObservation(original.getId());
        }
      }
    }

    @Override
    public void resolveWith(Observation observation, Executor executor) {
      synchronized (graph) {
        requireOpen();
        this.contextualizers.compute(
            observation,
            (key, previous) -> {
              if (previous != null
                  && previous != executor
                  && ((previous.getActuator() != null
                          && previous.getActuator().getExecutionRole()
                              != Actuator.ExecutionRole.INITIALIZATION)
                      || (executor.getActuator() != null
                          && executor.getActuator().getExecutionRole()
                              != Actuator.ExecutionRole.INITIALIZATION)))
                throw new UnsupportedOperationException(
                    "Multiple occurrence plans for one observation require coverage selection");
              return executor;
            });
      }
    }

    @Override
    public long commit() {
      // Commit owns the monitor through close and publication. A cancellation acquiring it
      // first aborts the attempt; one arriving after durable commit cannot undo that commit.
      synchronized (graph) {
        var root = rootAttempt();
        if (root.attemptState == AttemptState.FAILED) return -1;
        requireOpen();
        classificationMutationStamp[0]++;
        if (parent != null) return commitOpen();
        attemptState = AttemptState.COMMITTING;
        try {
          return commitOpen();
        } catch (Throwable failure) {
          if (attemptState == AttemptState.COMMITTED) throw failure;
          failLocked(failure);
          scope.error(failure);
          return -1;
        }
      }
    }

    @Override
    public boolean isFailed() {
      synchronized (graph) {
        return rootAttempt().attemptState == AttemptState.FAILED || !failures.isEmpty();
      }
    }

    private long commitOpen() {
      if (!failures.isEmpty()) {
        rollbackSchedulerState();
        failures.forEach(t -> scope.error(t));
        return -1;
      }

      if (activity instanceof ActivityImpl activity1) {
        activity1.setEnd(System.currentTimeMillis());
        activity1.setOutcome(Activity.Outcome.SUCCESS);
        activity1.setName(activity.getType().name().substring(0, 3) + " OK");
      }

      // if nothing was done, we just store the HAS_CHILD relationships that point to observations.
      // TODO/CHECK the logics here may require some attention
      //      var trivial = false;
      //      synchronized (graph) {
      //        trivial =
      //            contextualizers.isEmpty()
      //                && graph.vertexSet().stream().noneMatch(a -> a instanceof Storage.Shard);
      //      }

      var ret = Transaction.INTERMEDIATE_COMMIT_ID;
      KnowledgeGraph.Commit committedChanges = null;
      long committedObserverId = 0;

      /*
      Open transaction in the knowledge graph and store everything that needs to, then make all connections
       */
      if (parent == null) {
        // Reserve the commit identity before storing assets so the durable observation metadata and
        // the object returned by submit() expose the same finalized lifecycle information.
        var commitId = knowledgeGraph.nextKey();
        if (!schedulerJournal.isEmpty()) {
          activity
              .getMetadata()
              .put(
                  org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal.METADATA_KEY,
                  schedulerJournal.stream()
                      .map(entry -> Utils.Json.asString(entry.committed(commitId)))
                      .toList());
        }
        // Local input validation precedes our own lifecycle metadata mutations. Root commit owns
        // the graph monitor throughout this check, native validation, durable close and publication.
        for(var guard:List.copyOf(classificationLocalGuards)) guard.run();
        prepareObservationsForStorage(commitId);
        var activeObserver = scope.getObserver();
        var kgTransaction = knowledgeGraph.createTransaction(scope);
        var stored = new ArrayList<RuntimeAsset>();
        var linked = new ArrayList<Triple<Long, Long, String>>();
        try (kgTransaction) {
          try {
            if(!classificationReadGuards.isEmpty()) {
              var lockIds=new TreeSet<Long>();
              for(var read:classificationReadGuards) for(var member:read.members()) lockIds.add(member.id());
              for(var asset:modified) if(asset instanceof Observation observation && observation.getId()>0) lockIds.add(observation.getId());
              kgTransaction.lockClassificationInputs(lockIds);
              for(var read:List.copyOf(classificationReadGuards)) kgTransaction.validateClassificationMembers(read);
            }
            registerCohortUpdates();

            synchronized (graph) {
              var assets = new ArrayList<>(graph.vertexSet());
              // Observations receive their persistent URNs before activities snapshot the URNs of
              // the observations they created or resolved.
              assets.sort(Comparator.comparing(asset -> asset instanceof Activity));
              for (var originalAsset : assets) {
                var asset = staged(originalAsset);
                if (asset instanceof ActivityImpl storedActivity) {
                  prepareActivityForStorage(storedActivity);
                }
                if (setupForStorage(asset, false)) {
                  kgTransaction.store(asset);
                  if (asset.getId() <= 0) {
                    throw new KlabInternalErrorException(
                        "Knowledge graph did not persist " + asset.getClass().getSimpleName());
                  }
                  stored.add(asset);
                }
              }

              for (var action : List.copyOf(beforeCommitActions)) action.run();
              if (!journalFactories.isEmpty()) {
                journalFactories.forEach(factory -> schedulerJournal.add(factory.get()));
                activity.getMetadata().put(org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal.METADATA_KEY,
                    schedulerJournal.stream().map(j -> Utils.Json.asString(j.committed(commitId))).toList());
                modified.add(activity);
              }

              for (var asset :
                  modified.stream()
                      .sorted(Comparator.comparingLong(RuntimeAsset::getId))
                      .toList()) {
                if (asset == activity && !schedulerJournal.isEmpty()) continue;
                var attribution = attributions.get(asset);
                if (attribution != null) {
                  if (attribution.original().getId() > 0) {
                    int ownedChildren=classificationChildDeltas.getOrDefault(attribution.original(),0);
                    if(ownedChildren==0)kgTransaction.updateSemantics(attribution.replacement(),attribution.before());
                    else kgTransaction.updateSemantics(attribution.replacement(),attribution.before(),ownedChildren);
                  }
                } else kgTransaction.update(asset);
              }

              for (var edge : graph.edgeSet().stream().sorted(Comparator.comparingInt(edge -> edge.relationship==GraphModel.Relationship.HAS_CHILD?0:1)).toList()) {
                var source = staged(graph.getEdgeSource(edge));
                var target = staged(graph.getEdgeTarget(edge));
                //              if (trivial
                //                  && !(target instanceof Observation)
                //                  && edge.relationship != GraphModel.Relationship.HAS_CHILD) {
                //                continue;
                //              }
                var relationshipData = getRelationshipData(edge);
                // KLAB-DEBUG-GUARD: links are intentionally still submitted and advertised; expose
                // unassigned endpoints without changing the existing commit behavior.
                if (source.getId() == 0 || target.getId() == 0) {
                  Logging.INSTANCE.warn(
                      "KLAB-DEBUG-GUARD: KG link has unassigned endpoint: source={}({}) target={}({}) "
                          + "relationship={} activity={} trivial={}",
                      source.getId(),
                      source.getClass().getName(),
                      target.getId(),
                      target.getClass().getName(),
                      edge.relationship,
                      activity.getId(),
                      false);
                }
                kgTransaction.link(source, target, edge.relationship, relationshipData);
                // Only advertise relationships that were actually persisted. In particular, trivial
                // transactions contain transient activity edges that are intentionally skipped.
                linked.add(Triple.of(source.getId(), target.getId(), edge.relationship.name()));
              }
              if (activeObserver != null
                  && target != null
                  && activeObserver.getId() != target.getId()) {
                if (schedulerJournal.isEmpty()) {
                  kgTransaction.perceive(activeObserver, target.getGeometry());
                  modified.add(activeObserver);
                } else for (var journal : schedulerJournal) {
                  if (journal.publicationPending() || journal.kind()==Scheduler.Event.Type.EVENT) {
                    kgTransaction.perceive(activeObserver, org.integratedmodelling.klab.api.geometry.Geometry.create(journal.support()));
                    modified.add(activeObserver);
                  }
                }
              }
              if (!schedulerJournal.isEmpty()) {
                var snapshot = createCommit(commitId,scope.getUser().getUsername(),stored,modified,linked);
                snapshot.setConsequenceCause(scope.getConsequenceCause());
                var encodedDeltas = activity.getMetadata().get(
                    org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta.METADATA_KEY);
                var deltas = new ArrayList<org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta>();
                if (encodedDeltas instanceof List<?> values) for (var value : values)
                  deltas.add(Utils.Json.parseObject(value.toString(),org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta.class));
                activity.getMetadata().put(org.integratedmodelling.klab.api.digitaltwin.TransitionCommit.METADATA_KEY,
                    Utils.Json.asString(new org.integratedmodelling.klab.api.digitaltwin.TransitionCommit(
                        1,scope.getId(),snapshot,schedulerJournal.stream().map(j -> j.committed(commitId)).toList(),deltas)));
                kgTransaction.update(activity);
              }
            }
          } catch (Exception failure) {
            kgTransaction.fail(failure);
            throw failure;
          }
        } catch (Exception e) {
          rollbackSchedulerState();
          attributions.clear();
          rootAttempt().attemptState = AttemptState.FAILED;
          failures.add(e); // A failed durable commit cannot be resumed with a cleared write set.
          scope.error(e);
          kgTransaction.fail(e);
          ((ActivityImpl) activity).setOutcome(Activity.Outcome.INTERNAL_FAILURE);
          ((ActivityImpl) activity)
              .setName(activity.getType().name().substring(0, 3) + " EXCEPTION");
          ((ActivityImpl) activity).setEnd(System.currentTimeMillis());
          ((ActivityImpl) activity).setStackTrace(Utils.Exceptions.stackTrace(e));
          return -1;
        }

        attemptState = AttemptState.COMMITTED;
        publishAttributions();
        var commit =
            createCommit(commitId, scope.getUser().getUsername(), stored, modified, linked);
        commit.setConsequenceCause(scope.getConsequenceCause());
        commitCache.put(commit.getId(), commit);
        committedChanges = commit;
        committedObserverId = activeObserver == null ? 0 : activeObserver.getId();

        for (var action : List.copyOf(afterCommitActions)) {
          // The graph is committed; failed activation is recovered from durable metadata.
          try {
            action.run();
          } catch (Throwable failure) {
            scope.error(failure);
          }
        }
        afterCommitActions.clear();
        beforeCommitActions.clear();
        rollbackActions.clear();
        schedulerJournal.clear();
        journalFactories.clear();

        ret = commit.getId();
      }

      /* Storage mutates the exact submitted object. Never recover it by observable equality: an
       * instantiator routinely creates multiple observations with identical semantics. */
      finalizeCommittedTarget(parent == null, target, (ActivityImpl) activity);

      ((ActivityImpl) activity).setOutcome(Activity.Outcome.SUCCESS);
      ((ActivityImpl) activity).setName(activity.getType().name().substring(0, 3) + " OK");
      ((ActivityImpl) activity).setEnd(System.currentTimeMillis());

      scope.unregisterTransaction(this);

      if (committedChanges != null && getConsequenceEngine() != null) {
        try {
          getConsequenceEngine().committed(committedChanges, scope, activity.getId(), committedObserverId);
        } catch (RuntimeException failure) {
          // A scheduling failure must never turn a durable commit into a failed submission.
          scope.warn("Cannot enqueue consequences for commit " + ret, failure);
        }
      }

      return ret;
    }

    static void finalizeCommittedTarget(
        boolean rootCommit, Observation target, ActivityImpl activity) {
      if (!rootCommit || target == null) {
        return;
      }
      if (target.getId() <= 0) {
        throw new KlabInternalErrorException(
            "Committed transaction did not finalize its target observation");
      }
      activity.setObservationUrn(target.getUrn());
    }

    /**
     * Materialize the public observation identity and a compact provenance summary before Neo4j
     * takes its property snapshot. Detailed provenance remains represented by Activity links.
     */
    private void prepareObservationsForStorage(long commitId) {
      synchronized (graph) {
        for (var asset : graph.vertexSet()) {
          if (!(staged(asset) instanceof Observation observation)) {
            continue;
          }
          if (observation instanceof ObservationImpl implementation) {
            implementation.setName(observation.getName());
          }
          var creator = creationActivity((Observation) asset);
          var metadata = observation.getMetadata();
          metadata.put(Metadata.IM_COMMIT_ID, commitId);
          metadata.putIfAbsent(Metadata.IM_CONTEXT_ID, scope.getId());
          metadata.putIfAbsent(Metadata.IM_CREATION_AGENT, scope.getConsequenceCause() == null
              ? scope.getUser().getUsername() : knowledgeGraph.klab().getName());
          if (creator != null) {
            metadata.putIfAbsent(
                Metadata.IM_CREATION_ACTIVITY_TYPE,
                creator.getType() == null ? null : creator.getType().name());
            metadata.putIfAbsent(Metadata.IM_CREATION_TIMESTAMP, creator.getStart());
            metadata.putIfAbsent(Metadata.IM_CREATION_SERVICE_ID, creator.getServiceId());
            metadata.putIfAbsent(
                Metadata.IM_CREATION_SERVICE_TYPE,
                creator.getServiceType() == null ? null : creator.getServiceType().name());
          }
          metadata.values().removeIf(Objects::isNull);
        }
      }
    }

    private Activity creationActivity(Observation observation) {
      return graph.incomingEdgesOf(observation).stream()
          .filter(edge -> edge.relationship == GraphModel.Relationship.CREATED)
          .map(graph::getEdgeSource)
          .filter(Activity.class::isInstance)
          .map(Activity.class::cast)
          .sorted(
              Comparator.comparing(candidate -> candidate.getType() == null || !candidate.getType().isSubmission()))
          .findFirst()
          .orElse(null);
    }

    private void prepareActivityForStorage(ActivityImpl storedActivity) {
      graph.incomingEdgesOf(storedActivity).stream()
          .filter(edge -> edge.relationship == GraphModel.Relationship.TRIGGERED)
          .map(graph::getEdgeSource)
          .filter(Activity.class::isInstance)
          .map(Activity.class::cast)
          .findFirst()
          .ifPresent(
              parentActivity -> {
                storedActivity.setTriggeringActivityUrn(parentActivity.getUrn());
                storedActivity.setParentId(parentActivity.getId());
                storedActivity.setParentTransientId(parentActivity.getTransientId());
              });
      graph.outgoingEdgesOf(storedActivity).stream()
          .filter(
              edge ->
                  edge.relationship == GraphModel.Relationship.CREATED
                      || edge.relationship == GraphModel.Relationship.RESOLVED)
          .map(graph::getEdgeTarget)
          .filter(Observation.class::isInstance)
          .map(Observation.class::cast)
          .map(Observation::getUrn)
          .filter(Objects::nonNull)
          .findFirst()
          .ifPresent(storedActivity::setObservationUrn);
    }

    static CommitImpl createCommit(
        long commitId,
        String owner,
        Collection<RuntimeAsset> stored,
        Collection<RuntimeAsset> modified,
        Collection<Triple<Long, Long, String>> linked) {
      var commit = new CommitImpl();
      commit.setId(commitId);
      commit.setTimestamp(System.currentTimeMillis());
      commit.setOwner(owner);
      commit.getAddedAssets().addAll(stored.stream().map(RuntimeAsset::getId).toList());
      commit
          .getAddedObservations()
          .addAll(
              stored.stream()
                  .filter(a -> a instanceof Observation)
                  .map(RuntimeAsset::getId)
                  .toList());
      commit
          .getAddedCohorts()
          .addAll(
              stored.stream().filter(a -> a instanceof Cohort).map(RuntimeAsset::getId).toList());
      commit.getAddedLinks().addAll(linked);
      /*
       * Cohorts are durable context-catalog assets and may have been created in a short
       * knowledge-graph transaction before this observation transaction existed. Reassert their
       * context ownership in the observation commit so a client whose Context adjacency is
       * already loaded learns about the cohort. This is a synchronization assertion only: the
       * Context -HAS_CHILD-> Cohort relationship is already present in Neo4j and is not written a
       * second time.
       */
      modified.stream()
          .filter(Cohort.class::isInstance)
          .map(RuntimeAsset::getId)
          .filter(id -> id > 0)
          .map(
              id ->
                  Triple.of(
                      RuntimeAsset.CONTEXT_ASSET_ID, id, GraphModel.Relationship.HAS_CHILD.name()))
          .forEach(commit.getAddedLinks()::add);
      commit
          .getModifiedAssets()
          .addAll(modified.stream().map(RuntimeAsset::getId).filter(id -> id > 0).toList());

      // Secondary submissions return before the root commit exists. Attach the final commit ID to
      // every newly stored observation now, not only to the root observation returned by submit().
      stored.stream()
          .filter(Observation.class::isInstance)
          .map(Observation.class::cast)
          .forEach(observation -> observation.getMetadata().put(Metadata.IM_COMMIT_ID, commitId));
      return commit;
    }

    /**
     * Register cohorts whose geometry was updated, either in an existing new cohort or as a
     * modified cohort in the modified array.
     */
    private void registerCohortUpdates() {
      synchronized (graph) {
        for (var cohortObservable : cohortGeometries.keySet()) {
          var geometry = cohortGeometries.get(cohortObservable);
          for (var asset : graph.vertexSet()) {
            if (asset instanceof Cohort cohort
                && cohort.getObservable().getSemantics().equals(cohortObservable)) {
              applyCohortGeometry(cohort, geometry);
            }
          }
        }
      }
    }

    private void applyCohortGeometry(Cohort cohort, Scale geometry) {
      if (cohort instanceof CohortImpl cohortImpl && geometry != null) {
        cohortImpl.setGeometry(GeometryRepository.INSTANCE.geometry(geometry));
        synchronized (graph) {
          modified.add(cohortImpl);
        }
      }
    }

    @Override
    public Transaction getChild(Activity activity, ContextScope scope, Object... runtimeAssets) {
      synchronized (graph) {
        requireOpen();
        try {
          var ret = new TransactionImpl(this, activity, runtimeAssets);
          if (scope instanceof ServiceContextScope serviceContextScope) {
            serviceContextScope.registerTransaction(ret);
          }
          return ret;
        } catch (RuntimeException | Error failure) {
          // Construction can already have contributed vertices/edges to the shared graph.
          // Such an attempt must not commit a partial provenance record.
          try {
            fail(failure);
          } catch (RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
          }
          throw failure;
        }
      }
    }

    private Object[] getRelationshipData(RelationshipEdge edge) {
      var ret = new ArrayList<Object>();
      if (edge.relationship == GraphModel.Relationship.AFFECTS) {
        ret.add("sequence");
        ret.add(edge.sequence);
      }
      edge.properties.forEach(
          (key, value) -> {
            if (!key.equals("sequence")) {
              ret.add(key);
              ret.add(value instanceof Geometry g ? g.encode()
                  : value instanceof RuntimeAsset asset ? asset.getId() : value);
            }
          });
      return ret.toArray();
    }

    private boolean setupForStorage(RuntimeAsset asset, boolean trivial) {
      return switch (asset) {
        case Observation observation -> observation.getId() < 0;
        case Cohort cohort -> cohort.getId() < 0;
        case Actuator actuator -> !trivial;
        case Activity activity -> {
          var ret = activity.getId() < 0 && !trivial;
          //          if (ret
          //              && activity.getType() == Activity.Type.RESOLUTION
          //              && activity.getMetadata().containsKey(Metadata.IM_RESOLUTION_GRAPH)) {
          //            ret =
          //                // don't store resolutions that produced nothing, i.e. just the
          // observation is in
          //                // the graph
          //                activity
          //                        .getMetadata()
          //                        .get(Metadata.IM_RESOLUTION_GRAPH,
          // GraphModel.KnowledgeGraph.class)
          //                        .getNodes()
          //                        .size()
          //                    > 1;
          //          }
          yield ret;
        }
        case Storage.Shard shard -> shard.getId() < 0;
        default -> false;
      };
    }

    @Override
    public Transaction fail(Throwable compilationError) {
      synchronized (graph) {
        var state = rootAttempt().attemptState;
        if (state == AttemptState.COMMITTED || state == AttemptState.FAILED) return this;
        // Only a reentrant commit callback can observe COMMITTING while holding this monitor.
        // Throwing aborts the surrounding graph transaction instead of clearing its write set.
        if (state == AttemptState.COMMITTING)
          throw new IllegalStateException("Cannot cancel inside a commit callback", compilationError);
        return failLocked(compilationError);
      }
    }

    private Transaction failLocked(Throwable compilationError) {
      rootAttempt().attemptState = AttemptState.FAILED;
      rollbackSchedulerState();
      ((ActivityImpl) activity).setOutcome(Activity.Outcome.FAILURE);
      ((ActivityImpl) activity).setName(activity.getType().name().substring(0, 3) + " FAIL");
      ((ActivityImpl) activity).setEnd(System.currentTimeMillis());
      this.failures.add(
          compilationError == null
              ? new IllegalStateException("Transaction failed")
              : compilationError);
      synchronized (graph) {
        attributions.clear();
      }
      if (compilationError != null) {
        ((ActivityImpl) activity).setStackTrace(Utils.Exceptions.stackTrace(compilationError));
      }
      scope.unregisterTransaction(this);
      return this;
    }

    @Override
    public Collection<RuntimeAsset> assets() {
      synchronized (graph) {
        return graph.vertexSet().stream().map(this::staged).toList();
      }
    }

    @Override
    public Collection<KnowledgeGraph.Link> incoming(RuntimeAsset asset) {
      var ret = new ArrayList<KnowledgeGraph.Link>();
      synchronized (graph) {
        asset = checkPresentAsset(asset);
        if (graph.vertexSet().contains(asset)) {
          for (var edge : graph.incomingEdgesOf(asset)) {
            var link =
                new LinkImpl(staged(graph.getEdgeSource(edge)), staged(asset), edge.relationship);
            link.properties().putAll(edge.properties);
            link.setSequence(edge.sequence);
            link.setGeometry(edge.geometry);
            ret.add(link);
          }
        }
      }
      return ret;
    }

    @Override
    public Collection<KnowledgeGraph.Link> outgoing(RuntimeAsset asset) {
      var ret = new ArrayList<KnowledgeGraph.Link>();
      synchronized (graph) {
        asset = checkPresentAsset(asset);
        if (graph.vertexSet().contains(asset)) {
          for (var edge : graph.outgoingEdgesOf(asset)) {
            var link =
                new LinkImpl(staged(asset), staged(graph.getEdgeTarget(edge)), edge.relationship);
            link.properties().putAll(edge.properties);
            link.setSequence(edge.sequence);
            link.setGeometry(edge.geometry);
            ret.add(link);
          }
        }
      }
      return ret;
    }

    @Override
    public GraphModel.KnowledgeGraph getGraph() {
      var ret = new GraphModel.KnowledgeGraph();
      synchronized (graph) {
        // the graph has everything; add only the nodes that correspond to the assets added in the
        // transaction.
        for (var asset : graph.vertexSet()) {
          if (added.contains(asset)) {
            ret.getNodes().put(asset.getId() + "", encodeRuntimeAsset(asset, ret.getNodes()));
          }
        }
        for (var edge : graph.edgeSet()) {
          var source = graph.getEdgeSource(edge);
          var target = graph.getEdgeTarget(edge);
          if (added.contains(source) && added.contains(target)) {
            ret.getEdges()
                .add(encodeLink(source, target, edge.relationship, getRelationshipData(edge)));
          }
        }
      }
      return ret;
    }
  }

  private GraphModel.KnowledgeGraph.Edge encodeLink(
      RuntimeAsset source,
      RuntimeAsset target,
      GraphModel.Relationship relationship,
      Object... relationshipData) {
    return new GraphModel.KnowledgeGraph.Edge(
        (source.getId() < 0 ? source.getTransientId() : source.getId()) + "",
        (target.getId() < 0 ? target.getTransientId() : target.getId()) + "",
        relationship.name(),
        true,
        Utils.Maps.makeStringMap(relationshipData));
  }

  private GraphModel.KnowledgeGraph.Node encodeRuntimeAsset(
      RuntimeAsset asset, Map<String, GraphModel.KnowledgeGraph.Node> nodes) {
    return nodes.computeIfAbsent(
        (asset.getId() < 0 ? asset.getTransientId() : asset.getId()) + "",
        id -> new GraphModel.KnowledgeGraph.Node(asset));
  }

  public DigitalTwinImpl(
      RuntimeService service,
      ServiceContextScope scope, // root scope used by the scheduler
      String scopeId, // the ID to use. The scope may or may not have it according to state
      UserScope userScope,
      KnowledgeGraphNeo4j database) {
    this.rootScope = scope;
    this.configuration =
        DigitalTwin.Configuration.builder(scope.getConfiguration())
            .id(scopeId)
            .serviceId(service.serviceId())
            .build();
    this.knowledgeGraph = (KnowledgeGraphNeo4j) database.contextualize(configuration, userScope);
    if (configuration.getWorldviewCommitment() != null)
      ((org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl) scope.getConfiguration()).setWorldviewCommitment(configuration.getWorldviewCommitment());
    ((org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl) scope.getConfiguration()).setGridAlignment(configuration.getGridAlignment());
    if (configuration.getGridAlignment() != null) {
      scope.getData().put(org.integratedmodelling.klab.api.digitaltwin.GridAlignment.SCOPE_KEY, configuration.getGridAlignment());
      for (String warning : configuration.getGridAlignment().emittedWarnings()) scope.warn(warning);
    }
    this.storageManager = new StorageManagerImpl(service, scope);
    this.scheduler = new SchedulerImpl(scope, this);
    this.consequenceEngine = new ConsequenceEngine(scope, scopeId);
  }

  public ConsequenceEngine getConsequenceEngine() {
    return consequenceEngine;
  }

  @Override
  public Transaction transaction(Activity activity, ContextScope scope, Object... runtimeAssets) {
    return new TransactionImpl(activity, (ServiceContextScope) scope, runtimeAssets);
  }

  @Override
  public KnowledgeGraph getKnowledgeGraph() {
    return this.knowledgeGraph;
  }

  @Override
  public StorageManager getStorageManager() {
    return this.storageManager;
  }

  @Override
  public Scheduler getScheduler() {
    return this.scheduler;
  }

  @Override
  public Provenance getProvenanceGraph(ContextScope context) {
    return new ProvenanceGraph(this.knowledgeGraph, context == null ? this.rootScope : context.getRootContextScope());
  }

  @Override
  public Dataflow getDataflowGraph(ContextScope context) {
    return new DataflowGraph(this.knowledgeGraph, context == null ? this.rootScope : context.getRootContextScope());
  }

  public KnowledgeGraph.Commit getCommit(long id) {
    return commitCache.getIfPresent(id);
  }

  @Override
  public Configuration getOptions() {
    return this.configuration;
  }

  @Override
  public boolean isClient() {
    return false;
  }

  @Override
  public void dispose() {
    ((SchedulerImpl) scheduler).close();
    consequenceEngine.close();
    this.knowledgeGraph.deleteContext();
    this.storageManager.clear();
  }

  /** Release process-local resources without deleting a persistent context or its data files. */
  public void shutdown() {
    ((SchedulerImpl) scheduler).close();
    consequenceEngine.close();
    ((StorageManagerImpl) storageManager).close();
  }
}
