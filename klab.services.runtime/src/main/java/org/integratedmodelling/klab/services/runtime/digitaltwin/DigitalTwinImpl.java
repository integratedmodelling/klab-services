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
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.runtime.knowledge.DataflowGraph;
import org.integratedmodelling.klab.runtime.knowledge.ProvenanceGraph;
import org.integratedmodelling.klab.runtime.storage.StorageManagerImpl;
import org.integratedmodelling.klab.services.runtime.MemberClassifierExecutor;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedPseudograph;

/** TODO each digital twin should have its own logger */
public class DigitalTwinImpl implements DigitalTwin {

  /**
   * Only an actual successful root native commit can mint this authority. Wire receipts alone
   * cannot authorize scheduler work or replace its ordinary model/input admission checks.
   */
  public static final class CommittedMembershipChange
      implements DigitalTwin.CommittedMembershipChange {
    private final DigitalTwinImpl twin;
    private final TransactionImpl source;
    private final KnowledgeGraph.CohortMembershipChange change;
    private final long commitId, activityId, focus;
    private final Long observer;
    private final org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding
        cycle;

    private CommittedMembershipChange(
        DigitalTwinImpl twin,
        TransactionImpl source,
        KnowledgeGraph.CohortMembershipChange change,
        long commitId,
        long activityId) {
      this.twin = twin;
      this.source = source;
      this.change = change;
      this.commitId = commitId;
      this.activityId = activityId;
      this.cycle = source.scope.getResolutionCycleBinding();
      this.observer = source.scope.getObserverId();
      var context = source.scope.getContextObservation();
      this.focus = context == null ? 0 : context.getId();
    }

    public KnowledgeGraph.CohortMembershipChange change() {
      return change;
    }

    public long commitId() {
      return commitId;
    }

    public long activityId() {
      return activityId;
    }

    private void requireIdentity(ServiceContextScope execution, boolean originalFocus) {
      if (execution == null
          || execution.getDigitalTwin() != twin
          || !Objects.equals(change.contextId(), execution.getId())
          || cycle == null
          || !Objects.equals(cycle, execution.getResolutionCycleBinding())
          || !Objects.equals(observer, execution.getObserverId())
          || execution.isInterrupted()
          || originalFocus
              && focus
                  != (execution.getContextObservation() == null
                      ? 0
                      : execution.getContextObservation().getId()))
        throw new IllegalStateException(
            "Committed membership authority belongs to another execution perspective");
      synchronized (source.graph) {
        if (source.rootAttempt().attemptState != TransactionImpl.AttemptState.COMMITTED
            || source.isFailed()
            || commitId <= 0
            || activityId <= 0)
          throw new IllegalStateException(
              "Membership input did not originate in a successful root commit");
      }
    }

    public KnowledgeGraph.CohortMembershipChange require(ServiceContextScope execution) {
      requireIdentity(execution, true);
      return change;
    }

    /**
     * Register the exact committed after-state for atomic native validation before output writes.
     * Consumer-specific focus is admitted by its original plan, not inherited as arbitrary
     * authority.
     */
    public void guard(DigitalTwin.Transaction transaction, ServiceContextScope execution) {
      requireIdentity(execution, false);
      if (!(transaction instanceof DigitalTwinImpl.TransactionImpl nativeTransaction)
          || nativeTransaction.scope.getDigitalTwin() != twin
          || transaction != execution.getCurrentTransaction())
        throw new IllegalStateException(
            "Membership recomputation requires its actual owning transaction");
      nativeTransaction.guardConsequenceReadSet(change.afterReadSet());
    }

    /**
     * The physical cohort event is shared; each consumer recovers its own ordinary persisted
     * observer and parent through of(), rather than accepting caller-selected perspective.
     */
    public void guardConsumer(
        DigitalTwin.Transaction transaction,
        ServiceContextScope origin,
        ServiceContextScope execution,
        Observation actualConsumer) {
      require(origin);
      if (actualConsumer == null || actualConsumer.getId() <= 0)
        throw new IllegalStateException("Membership consumer is not durable");
      var current = twin.knowledgeGraph.getAsset(actualConsumer.getId(), origin, Observation.class);
      if (current == null || current.isEmpty())
        throw new IllegalStateException("Membership consumer is no longer current");
      var derived = origin.forConsequence(null).of(current);
      var expectedFocus = derived.getContextObservation();
      var actualFocus = execution == null ? null : execution.getContextObservation();
      if (execution == null
          || execution.getDigitalTwin() != twin
          || !Objects.equals(derived.getId(), execution.getId())
          || !Objects.equals(cycle, execution.getResolutionCycleBinding())
          || !Objects.equals(derived.getObserverId(), execution.getObserverId())
          || (expectedFocus == null ? 0 : expectedFocus.getId())
              != (actualFocus == null ? 0 : actualFocus.getId())
          || derived.getUser() == null
          || execution.getUser() == null
          || !Objects.equals(derived.getUser().getUsername(), execution.getUser().getUsername())
          || execution.isInterrupted()
          || !(transaction instanceof DigitalTwinImpl.TransactionImpl nativeTransaction)
          || nativeTransaction.scope.getDigitalTwin() != twin
          || transaction != execution.getCurrentTransaction())
        throw new IllegalStateException(
            "Membership consumer scope differs from its ordinary admitted perspective");
      // The physical source after-set alone cannot fence a different consumer's perspective.
      // Capture its actual native payload, Agent/focus and all scientific/attribution edges
      // outside the local graph monitor; the root acquires the complete sorted union at commit.
      var consumerInputs = ConsequenceNativeInputs.capture(current.getId(), List.of(), execution);
      nativeTransaction.guardConsequenceReadSet(change.afterReadSet());
      nativeTransaction.guardConsequenceReadSet(consumerInputs.reads());
    }
  }

  private final KnowledgeGraphNeo4j knowledgeGraph;
  private final StorageManager storageManager;
  private final ContextScope rootScope;
  private final Scheduler scheduler;
  private final ConsequenceEngine consequenceEngine;
  private final ResolutionCycles resolutionCycles;
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

  /**
   * Native geometry operations over private backing values, with privacy retained by derivatives.
   */
  private static final class LocalGeometry implements java.lang.reflect.InvocationHandler {
    private final Geometry source;

    private LocalGeometry(Geometry source) {
      this.source = source;
    }

    static Geometry detach(Geometry geometry) {
      var plain =
          geometry.isEmpty()
              ? Geometry.builder().build()
              : (org.integratedmodelling.klab.api.geometry.impl.GeometryImpl)
                  Geometry.create(geometry.encode());
      if (geometry.isEmpty()) {
        plain.setScalar(false);
        plain.setEmpty(true);
      }
      return geometry instanceof Scale
          ? new org.integratedmodelling.klab.runtime.scale.ScaleImpl(plain)
          : plain;
    }

    static Geometry wrap(Geometry geometry) {
      if (geometry instanceof Geometry.LocalSnapshot) return geometry;
      var contracts = new LinkedHashSet<Class<?>>();
      contracts.add(Geometry.LocalSnapshot.class);
      for (Class<?> type = geometry.getClass(); type != null; type = type.getSuperclass())
        for (var contract : type.getInterfaces())
          if (contract.getName().startsWith("org.integratedmodelling.klab.api."))
            contracts.add(contract);
      return (Geometry)
          java.lang.reflect.Proxy.newProxyInstance(
              DigitalTwinImpl.class.getClassLoader(),
              contracts.toArray(Class<?>[]::new),
              new LocalGeometry(geometry));
    }

    private static Geometry isolate(Geometry geometry) {
      return (Geometry) ObservationReadView.copy(geometry, new IdentityHashMap<>());
    }

    private static Object nativeArgument(Object value) {
      if (value != null
          && java.lang.reflect.Proxy.isProxyClass(value.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(value) instanceof LocalExtent extent)
        return extent.source;
      if (value
          instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> extent)
        return new org.integratedmodelling.klab.runtime.scale.ScaleImpl(
                Geometry.create(extent.encode()))
            .extent(extent.getType());
      if (!(value instanceof Geometry geometry)) return value;
      if (java.lang.reflect.Proxy.isProxyClass(geometry.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(geometry) instanceof LocalGeometry local)
        return local.source;
      return detach(geometry);
    }

    private static Object result(Object value) {
      if (value instanceof Geometry geometry)
        return geometry == Geometry.EMPTY || geometry == Geometry.UNIVERSAL
            ? isolate(geometry)
            : wrap(geometry);
      if (value
          instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> extent)
        return LocalExtent.wrap(extent);
      if (value instanceof List<?> list) return list.stream().map(LocalGeometry::result).toList();
      if (value instanceof Iterator<?> iterator)
        return new Iterator<Object>() {
          public boolean hasNext() {
            return iterator.hasNext();
          }

          public Object next() {
            return result(iterator.next());
          }
        };
      if (value instanceof java.util.stream.Stream<?> stream)
        return stream.map(LocalGeometry::result);
      return value;
    }

    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments)
        throws Throwable {
      if (method.getDeclaringClass() == Object.class)
        return switch (method.getName()) {
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> source.encode().hashCode();
          case "toString" -> source.encode();
          default -> throw new UnsupportedOperationException(method.toString());
        };
      if (method.getDeclaringClass() == Geometry.LocalSnapshot.class)
        return switch (method.getName()) {
          case "isolate" -> isolate((Geometry) arguments[0]);
          case "promote" ->
              source instanceof Scale
                  ? proxy
                  : wrap(
                      new org.integratedmodelling.klab.runtime.scale.ScaleImpl(
                          source, (org.integratedmodelling.klab.api.scope.Scope) arguments[0]));
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
        throw new UnsupportedOperationException(
            "Local geometry exposes API views, not mutable implementation casts");
      }
      var inputs =
          arguments == null
              ? null
              : Arrays.stream(arguments).map(LocalGeometry::nativeArgument).toArray();
      try {
        return result(method.invoke(source, inputs));
      } catch (java.lang.reflect.InvocationTargetException failure) {
        throw failure.getCause();
      }
    }
  }

  /** Keep geometry conversions from a private extent inside the same local boundary. */
  private static final class LocalExtent implements java.lang.reflect.InvocationHandler {
    private final org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> source;

    private LocalExtent(
        org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> source) {
      this.source = source;
    }

    static Object wrap(
        org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?> source) {
      var contracts = new LinkedHashSet<Class<?>>();
      contracts.add(org.integratedmodelling.klab.api.data.LocalView.class);
      for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass())
        for (var contract : type.getInterfaces())
          if (contract.getName().startsWith("org.integratedmodelling.klab.api."))
            contracts.add(contract);
      return java.lang.reflect.Proxy.newProxyInstance(
          DigitalTwinImpl.class.getClassLoader(),
          contracts.toArray(Class<?>[]::new),
          new LocalExtent(source));
    }

    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments)
        throws Throwable {
      if (method.getDeclaringClass() == Object.class)
        return switch (method.getName()) {
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> source.hashCode();
          case "toString" -> source.toString();
          default -> throw new UnsupportedOperationException(method.toString());
        };
      if (method.getName().equals("as")) {
        var requested = (Class<?>) arguments[0];
        if (requested.isInstance(proxy)) return proxy;
        if (!requested.isInterface())
          throw new UnsupportedOperationException("Private extents expose API views");
      }
      var inputs =
          arguments == null
              ? null
              : Arrays.stream(arguments).map(LocalGeometry::nativeArgument).toArray();
      try {
        return LocalGeometry.result(method.invoke(source, inputs));
      } catch (java.lang.reflect.InvocationTargetException failure) {
        throw failure.getCause();
      }
    }
  }

  /** Exact transaction-owned initialization bookkeeping, projected only on the mutated object. */
  private record SchedulerWrite(
      Observation target, boolean registration, boolean present, Object before, Object after) {}

  /** Read-only domain interfaces with detached mutable payloads; no serialization of parents. */
  private static final class ObservationReadView implements java.lang.reflect.InvocationHandler {
    private final Object source;
    private final TransactionImpl owner;
    private final IdentityHashMap<Object, Object> copies;
    private final Map<java.lang.reflect.Method, Object> values = new HashMap<>();

    private ObservationReadView(Object source, IdentityHashMap<Object, Object> copies) {
      this.source = source;
      this.owner = copies instanceof OwnedCopies owned ? owned.owner : null;
      this.copies = copies;
    }

    static Observation of(Observation source) {
      return (Observation) copy(source, new IdentityHashMap<>());
    }

    private static final class OwnedCopies extends IdentityHashMap<Object, Object> {
      final TransactionImpl owner;

      OwnedCopies(TransactionImpl owner) {
        this.owner = owner;
      }
    }

    static Observation of(Observation source, TransactionImpl owner) {
      return (Observation) copy(source, new OwnedCopies(owner));
    }

    static Observation source(Observation observation) {
      if (java.lang.reflect.Proxy.isProxyClass(observation.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(observation)
              instanceof ObservationReadView view) return (Observation) view.source;
      return observation;
    }

    private static Object copy(Object value, IdentityHashMap<Object, Object> copies) {
      if (value == null
          || value instanceof String
          || value instanceof Boolean
          || value instanceof Character
          || value instanceof Enum<?>
          || value instanceof Class<?>
          || value instanceof Byte
          || value instanceof Short
          || value instanceof Integer
          || value instanceof Long
          || value instanceof Float
          || value instanceof Double
          || value instanceof java.math.BigDecimal
          || value instanceof java.math.BigInteger
          || value instanceof java.net.URI
          || value instanceof java.net.URL
          || value instanceof UUID
          || value instanceof java.time.temporal.TemporalAccessor
          || value instanceof java.time.Duration
          || value instanceof java.time.Period) return value;
      if (copies.containsKey(value)) return copies.get(value);
      // Semantic values cross service boundaries (e.g. connection endpoint subsumption).
      // Keep their concrete transport shape and URN equality while detaching every payload.
      // A JDK proxy is neither equal to its ConceptImpl nor serializable by the wire mapper.
      if (value instanceof org.integratedmodelling.common.knowledge.ConceptImpl
          || value instanceof org.integratedmodelling.common.knowledge.ObservableImpl
          || value.getClass()
              == org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl.class
          || value.getClass()
              == org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl
                  .LexicalContextImpl.class
          || value.getClass() == org.integratedmodelling.common.lang.QuantityImpl.class) {
        try {
          var result = value.getClass().getDeclaredConstructor().newInstance();
          copies.put(value, result);
          for (var field : semanticFields(value.getClass())) {
            field.setAccessible(true);
            field.set(result, copy(field.get(value), copies));
          }
          return result;
        } catch (ReflectiveOperationException error) {
          throw new IllegalStateException("Cannot snapshot semantic value", error);
        }
      }
      if (value instanceof Geometry geometry
          && !(value
              instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Extent<?>)
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
            && result
                instanceof
                org.integratedmodelling.klab.api.collections.impl.ParametersImpl copied) {
          copied.setUnnamedKeys((List) copy(parameters.getUnnamedKeys(), copies));
          if (parameters
              instanceof
              org.integratedmodelling.klab.api.collections.impl.ParametersImpl<?> implementation)
            copied.setTemplateVariables(
                (org.integratedmodelling.klab.api.collections.Parameters<String>)
                    copy(implementation.getTemplateVariables(), copies));
        }
        return result;
      }
      if (value instanceof Collection<?> collection) {
        Collection<Object> result =
            value instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();
        copies.put(value, result);
        collection.forEach(item -> result.add(copy(item, copies)));
        return result;
      }
      if (value.getClass().isArray()) {
        int length = java.lang.reflect.Array.getLength(value);
        var result =
            java.lang.reflect.Array.newInstance(value.getClass().getComponentType(), length);
        copies.put(value, result);
        for (int i = 0; i < length; i++)
          java.lang.reflect.Array.set(
              result, i, copy(java.lang.reflect.Array.get(value, i), copies));
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
        throw new UnsupportedOperationException(
            "No safe observation read projection for " + value.getClass().getName());
      interfaces.add(org.integratedmodelling.klab.api.data.LocalView.class);
      var handler = new ObservationReadView(value, copies);
      var proxy =
          java.lang.reflect.Proxy.newProxyInstance(
              DigitalTwinImpl.class.getClassLoader(), interfaces.toArray(Class<?>[]::new), handler);
      copies.put(value, proxy);
      // Capture bean fields now, under the transaction monitor. Query methods may compute values,
      // but never return unwrapped domain objects or mutable containers from the source.
      for (var contract : interfaces)
        for (var method : contract.getMethods()) {
          if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
              && method.getParameterCount() == 0
              && readable(method.getName())
              && method.getReturnType() != void.class
              && !method.getName().equals("iterator")) {
            try {
              handler.values.put(method, copy(method.invoke(value), copies));
            } catch (java.lang.reflect.InvocationTargetException error) {
              throw new IllegalStateException("Cannot snapshot " + method, error.getCause());
            } catch (ReflectiveOperationException error) {
              throw new IllegalStateException(error);
            }
          }
        }
      return proxy;
    }

    /**
     * Canonicalize the same detached getter graph exposed to classifier code, including payloads.
     */
    static String fingerprint(Observation observation) {
      return fingerprint(observation, 0);
    }

    static String fingerprint(Observation observation, int ownChildDelta) {
      return fingerprint(observation, ownChildDelta, List.of());
    }

    static String fingerprint(
        Observation observation, int ownChildDelta, List<SchedulerWrite> writes) {
      return fingerprintValue(
          normalized(observation, ownChildDelta, writes), new IdentityHashMap<>());
    }

    /** Rewind only exact writes owned by this root; all other input payload stays visible. */
    private static Observation normalized(
        Observation observation, int ownChildDelta, List<SchedulerWrite> writes) {
      var snapshot = of(observation);
      var snapshotHandler =
          (ObservationReadView) java.lang.reflect.Proxy.getInvocationHandler(snapshot);
      for (int i = writes.size() - 1; i >= 0; i--) {
        var write = writes.get(i);
        if (write.target() != source(observation)) continue;
        if (write.registration()) {
          var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
          for (var entry : snapshotHandler.values.entrySet())
            if (entry.getKey().getName().equals("getMetadata") && seen.add(entry.getValue())) {
              var metadata = (Map<String, Object>) entry.getValue();
              var key =
                  org.integratedmodelling.klab.api.digitaltwin.Scheduler.REGISTRATION_METADATA_KEY;
              if (!metadata.containsKey(key) || !Objects.equals(metadata.get(key), write.after()))
                throw new IllegalStateException(
                    "Scheduler registration changed outside its owned write");
              if (write.present()) metadata.put(key, copy(write.before(), new IdentityHashMap<>()));
              else metadata.remove(key);
            }
        } else {
          for (var entry : snapshotHandler.values.entrySet())
            if (entry.getKey().getName().equals("getEventTimestamps")) {
              if (!Objects.equals(entry.getValue(), write.after()))
                throw new IllegalStateException(
                    "Scheduler initialization timestamps changed outside their owned write");
              entry.setValue(copy(write.before(), new IdentityHashMap<>()));
            }
        }
      }
      if (ownChildDelta != 0) {
        var handler = (ObservationReadView) java.lang.reflect.Proxy.getInvocationHandler(snapshot);
        handler.values.replaceAll(
            (method, value) ->
                method.getName().equals("getChildrenCount")
                    ? ((Integer) value) - ownChildDelta
                    : value);
      }
      return snapshot;
    }

    static Observation persistedSnapshot(
        Observation observation, int ownChildDelta, List<SchedulerWrite> writes) {
      var snapshot =
          (ObservationImpl)
              Observation.forTransport(normalized(observation, ownChildDelta, writes));
      if (source(observation) instanceof ObservationImpl original) {
        snapshot.setAnnotationPriorities(original.getAnnotationPriorities());
        snapshot.setSubstantialQuality(original.isSubstantialQuality());
      }
      return snapshot;
    }

    private static List<java.lang.reflect.Field> semanticFields(Class<?> type) {
      var fields = new ArrayList<java.lang.reflect.Field>();
      for (; type != null; type = type.getSuperclass()) {
        for (var field : type.getDeclaredFields()) {
          if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
              && !java.lang.reflect.Modifier.isTransient(field.getModifiers())) fields.add(field);
        }
      }
      return fields;
    }

    private static String fingerprintValue(Object value, IdentityHashMap<Object, Integer> seen) {
      if (value == null) return "null";
      if (value instanceof String
          || value instanceof Number
          || value instanceof Boolean
          || value instanceof Character
          || value instanceof Enum<?>
          || value instanceof Class<?>
          || value instanceof java.net.URI
          || value instanceof java.net.URL
          || value instanceof UUID
          || value instanceof java.time.temporal.TemporalAccessor
          || value instanceof java.time.Duration
          || value instanceof java.time.Period)
        return value.getClass().getName() + ":" + value.toString().length() + ":" + value;
      if (seen.containsKey(value)) return "ref:" + seen.get(value);
      seen.put(value, seen.size());
      if (value instanceof Geometry geometry)
        return "geometry:"
            + geometry.encode()
            + ":"
            + fingerprintValue(geometry.getMetadata(), seen);
      if (value instanceof Date date) return "date:" + date.getTime();
      if (value instanceof Data.ShardingStrategy strategy)
        return "sharding:" + org.integratedmodelling.common.utils.Utils.Json.asString(strategy);
      if (value instanceof org.integratedmodelling.klab.api.knowledge.Urn urn)
        return "urn:" + urn + ":" + fingerprintValue(urn.getParameters(), seen);
      if (value instanceof Map<?, ?> map) {
        var entries = new TreeMap<String, Object>();
        map.forEach(
            (key, item) -> entries.put(fingerprintValue(key, new IdentityHashMap<>()), item));
        var result = new StringBuilder("map{");
        entries.forEach(
            (key, item) ->
                result.append(key).append('=').append(fingerprintValue(item, seen)).append(';'));
        result.append('}');
        if (value instanceof org.integratedmodelling.klab.api.collections.Parameters<?> parameters)
          result.append("unnamed:").append(fingerprintValue(parameters.getUnnamedKeys(), seen));
        if (value
            instanceof
            org.integratedmodelling.klab.api.collections.impl.ParametersImpl<?> parameters)
          result
              .append("templates:")
              .append(fingerprintValue(parameters.getTemplateVariables(), seen));
        return result.toString();
      }
      if (value instanceof Collection<?> items) {
        var result = new StringBuilder("collection[");
        for (var item : items) result.append(fingerprintValue(item, seen)).append(';');
        return result.append(']').toString();
      }
      if (value.getClass().isArray()) {
        var result = new StringBuilder("array[");
        for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++)
          result.append(fingerprintValue(java.lang.reflect.Array.get(value, i), seen)).append(';');
        return result.append(']').toString();
      }
      if (value instanceof org.integratedmodelling.common.knowledge.ConceptImpl
          || value instanceof org.integratedmodelling.common.knowledge.ObservableImpl
          || value.getClass()
              == org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl.class
          || value.getClass()
              == org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl
                  .LexicalContextImpl.class
          || value.getClass() == org.integratedmodelling.common.lang.QuantityImpl.class) {
        var fields = new TreeMap<String, Object>();
        try {
          for (var field : semanticFields(value.getClass())) {
            field.setAccessible(true);
            fields.put(field.getName(), field.get(value));
          }
        } catch (IllegalAccessException error) {
          throw new IllegalStateException(error);
        }
        return "semantics:" + fingerprintValue(fields, seen);
      }
      if (java.lang.reflect.Proxy.isProxyClass(value.getClass())
          && java.lang.reflect.Proxy.getInvocationHandler(value)
              instanceof ObservationReadView view) {
        var fields = new TreeMap<String, Object>();
        view.values.forEach(
            (method, item) -> {
              // A durable observation's transport wrapper has a fresh transient identity on each
              // read.
              if (!(view.source instanceof Observation o
                  && o.getId() > 0
                  && method.getName().equals("getTransientId")))
                fields.put(method.toGenericString(), item);
            });
        return "bean:" + fingerprintValue(fields, seen);
      }
      throw new UnsupportedOperationException(
          "No classification input fingerprint for " + value.getClass());
    }

    private static boolean readable(String name) {
      return name.startsWith("get")
          || name.startsWith("is")
          || name.startsWith("has")
          || Set.of(
                  "name",
                  "identity",
                  "semantics",
                  "geometry",
                  "metadata",
                  "empty",
                  "notifications",
                  "children",
                  "dataKey",
                  "size",
                  "encode",
                  "displayName",
                  "displayLabel",
                  "codeName",
                  "classify",
                  "groupSize")
              .contains(name);
    }

    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] arguments)
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
      if (method.getName().equals("builder")
          && source instanceof org.integratedmodelling.klab.api.knowledge.Observable)
        return method.invoke(source, arguments);
      if (!readable(method.getName())
          && !Set.of(
                  "singular",
                  "collective",
                  "asConcept",
                  "dimension",
                  "at",
                  "contains",
                  "intersects")
              .contains(method.getName()))
        throw new UnsupportedOperationException(
            "Observation reads cannot invoke " + method.getName());
      try {
        return copy(method.invoke(source, arguments), copies);
      } catch (java.lang.reflect.InvocationTargetException error) {
        throw error.getCause();
      }
    }
  }

  /**
   * The transactional graph is a directed acyclic graph that represents the execution of a digital
   * twin activity, capturing the relationships and changes between assets. The graph is robust to
   * identity switching between assets as long as their ID remains consistent.
   */
  public class TransactionImpl
      implements Transaction,
          org.integratedmodelling.klab.runtime.lifecycle.CycleScopedTransaction {

    private enum AttemptState {
      OPEN,
      COMMITTING,
      COMMITTED,
      FAILED
    }

    private AttemptState attemptState = AttemptState.OPEN;
    private int preparingAttributions;

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
    // Only constructors may register execution activities. Importing old provenance into the
    // overlay must not convert a legacy receipt into evidence from this active attempt.
    private final Set<Activity> ownedExecutionActivities =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Private handler/root identity is checked before any proxy property access. This admits only
     * this open root's snapshots of ordinary Runtime semantic DTOs, never arbitrary proxies.
     */
    public boolean ownsObservationReadView(Observation observation) {
      if (observation == null || !java.lang.reflect.Proxy.isProxyClass(observation.getClass()))
        return false;
      var handler = java.lang.reflect.Proxy.getInvocationHandler(observation);
      if (!(handler instanceof ObservationReadView view)
          || view.owner != rootAttempt()
          || view.source == null
          || view.source.getClass() != ObservationImpl.class) return false;
      synchronized (graph) {
        if (rootAttempt().attemptState != AttemptState.OPEN) return false;
        var original = (ObservationImpl) view.source;
        var observable = original.getObservable();
        return observable != null
            && observable.getClass()
                == org.integratedmodelling.common.knowledge.ObservableImpl.class
            && observable.getSemantics() != null
            && observable.getSemantics().getClass()
                == org.integratedmodelling.common.knowledge.ConceptImpl.class;
      }
    }

    private final Map<Observation, Activity> processActivations = new IdentityHashMap<>();

    public void registerProcessActivation(Observation process, Activity activation) {
      synchronized (graph) {
        if (!ownsExecutionActivity(activation)
            || activation.getType() != Activity.Type.INITIALIZATION)
          throw new IllegalArgumentException("Process activation must be owned initialization");
        rootAttempt().processActivations.put(process, activation);
      }
    }

    public Activity processActivation(Observation process) {
      synchronized (graph) { return rootAttempt().processActivations.get(process); }
    }

    public boolean ownsExecutionActivity(Activity candidate) {
      synchronized (graph) {
        var root = rootAttempt();
        return root.attemptState == AttemptState.OPEN
            && root.ownedExecutionActivities.contains(candidate);
      }
    }

    private record Attribution(
        ObservationImpl original,
        ObservationImpl replacement,
        String before,
        List<Concept> families,
        Set<String> snapshots) {}

    private Map<Observation, Attribution> attributions = new IdentityHashMap<>();

    private record ConfigurationAttribution(
        ObservationImpl original,
        ObservationImpl replacement,
        ConfigurationConsequenceRuntime.Update update,
        KnowledgeGraph.ConsequenceReadSet nativeReads) {}

    private Map<Observation, ConfigurationAttribution> configurationRevisions =
        new IdentityHashMap<>();
    private Map<ObservationImpl, GuardedConfigurationPublisher.Birth> configurationBirths =
        new IdentityHashMap<>();
    private Observation target;
    private final Activity activity;
    private final ServiceContextScope scope;
    private final Queue<Throwable> failures;
    private final Graph<RuntimeAsset, RelationshipEdge> graph;
    private final Map<Observation, Executor> contextualizers;
    private final Map<Observation, Actuator> inputPlans;
    private TransactionImpl parent; // null in the root activity
    private org.integratedmodelling.klab.api.data.TemporalWriteSet temporalWrites;
    private final List<
            java.util.function.Supplier<
                org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal>>
        journalFactories = new ArrayList<>();

    @Override
    public void stageSchedulerJournal(
        java.util.function.Supplier<org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal>
            journal) {
      if (parent != null) {
        parent.stageSchedulerJournal(journal);
        return;
      }
      synchronized (graph) {
        requireOpen();
        journalFactories.add(journal);
      }
    }

    @Override
    public org.integratedmodelling.klab.api.data.TemporalWriteSet getTemporalWrites() {
      return parent == null ? temporalWrites : parent.getTemporalWrites();
    }

    @Override
    public void setTemporalWrites(org.integratedmodelling.klab.api.data.TemporalWriteSet writes) {
      if (parent != null) {
        parent.setTemporalWrites(writes);
        return;
      }
      synchronized (graph) {
        requireOpen();
        if (temporalWrites != null && temporalWrites != writes)
          throw new IllegalStateException("A root transaction has only one temporal write set");
        temporalWrites = writes;
      }
    }

    private Map<Concept, Scale> cohortGeometries = new HashMap<>();
    private List<Runnable> beforeCommitActions = new ArrayList<>();
    private List<Runnable> consequenceCommitAdmissions = new ArrayList<>();
    private List<KnowledgeGraph.ConsequenceReadSet> consequenceReadGuards = new ArrayList<>();
    private List<KnowledgeGraph.ContextCohortCatalogue> contextCatalogueGuards = new ArrayList<>();
    private Set<Long> inoperativeObservations = new HashSet<>();
    private List<ModeledCessations.Plan> modeledCessations = new ArrayList<>();
    private List<KnowledgeGraph.RoleSupportCatalogue> roleSupportGuards = new ArrayList<>();
    private List<KnowledgeGraph.ObservationCessation> publishedCessations = new ArrayList<>();
    private List<KnowledgeGraph.CohortMembershipChange> committedMembershipChanges =
        new ArrayList<>();
    private Map<Long, KnowledgeGraph.ConsequenceAssetSnapshot> consequenceBaselines =
        new HashMap<>();

    private record ClassificationAuditBinding(
        Activity activity, Map<String, Object> row, Observation member) {}

    private List<ClassificationAuditBinding> classificationAuditBindings = new ArrayList<>();
    private List<KnowledgeGraph.ClassificationReadSet> classificationReadGuards = new ArrayList<>();
    private Map<Long, KnowledgeGraph.ClassificationReadSet> classificationBaselines =
        new HashMap<>();
    private Map<Long, Observation> classificationBaselineMembers = new HashMap<>();
    private List<Runnable> classificationLocalGuards = new ArrayList<>();
    private long[] classificationMutationStamp = new long[] {0};
    // Only increments performed by this root's link() bookkeeping may be projected through a
    // frozen classification read. Arbitrary setters and all other payload changes still fail.
    private Map<Observation, Integer> classificationChildDeltas = new IdentityHashMap<>();
    private List<SchedulerWrite> classificationSchedulerWrites = new ArrayList<>();

    /** Record the scheduler's exact initialization marker before exposing it. */
    public void markSchedulerRegistered(Observation observation) {
      synchronized (graph) {
        requireOpen();
        var target = (Observation) checkPresentAsset(ObservationReadView.source(observation));
        var metadata = target.getMetadata();
        var key = org.integratedmodelling.klab.api.digitaltwin.Scheduler.REGISTRATION_METADATA_KEY;
        classificationSchedulerWrites.add(
            new SchedulerWrite(
                target,
                true,
                metadata.containsKey(key),
                ObservationReadView.copy(metadata.get(key), new IdentityHashMap<>()),
                Boolean.TRUE));
        metadata.put(key, true);
        var write = classificationSchedulerWrites.getLast();
        afterRollback(
            () -> {
              if (Boolean.TRUE.equals(metadata.get(key))) {
                if (write.present())
                  metadata.put(
                      key, ObservationReadView.copy(write.before(), new IdentityHashMap<>()));
                else metadata.remove(key);
              }
            });
      }
    }

    /** Only INIT appends use this ledger; all other timestamp mutations remain visible. */
    public void appendSchedulerInitializationTimestamps(
        Observation observation, List<Long> additions) {
      synchronized (graph) {
        requireOpen();
        var target = (Observation) checkPresentAsset(ObservationReadView.source(observation));
        if (!(target
            instanceof
            org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl mutable))
          throw new IllegalArgumentException(
              "Scheduler initialization requires a mutable registered observation");
        var before = List.copyOf(target.getEventTimestamps());
        var after = new ArrayList<>(before);
        after.addAll(additions);
        classificationSchedulerWrites.add(
            new SchedulerWrite(target, false, true, before, List.copyOf(after)));
        var frozenAfter = List.copyOf(after);
        mutable.setEventTimestamps(after);
        afterRollback(
            () -> {
              if (Objects.equals(mutable.getEventTimestamps(), frozenAfter))
                mutable.setEventTimestamps(new ArrayList<>(before));
            });
      }
    }

    public long classificationMutationStamp() {
      synchronized (graph) {
        requireOpen();
        return classificationMutationStamp[0];
      }
    }

    public void guardClassificationOverlay(
        Cohort cohort, Observation producer, List<Observation> persisted, List<String> expected) {
      synchronized (graph) {
        requireOpen();
        var frozen = List.copyOf(expected);
        var baseline = new IdentityHashMap<>(classificationChildDeltas);
        int schedulerCursor = classificationSchedulerWrites.size();
        classificationLocalGuards.add(
            () -> {
              var current =
                  classificationOverlay(
                          cohort,
                          producer,
                          persisted,
                          baseline,
                          classificationSchedulerWrites.subList(
                              schedulerCursor, classificationSchedulerWrites.size()))
                      .state();
              if (!current.equals(frozen)) {
                String detail = "entry count";
                for (int i = 0; i < Math.min(frozen.size(), current.size()); i++)
                  if (!frozen.get(i).equals(current.get(i))) {
                    var previous = frozen.get(i);
                    var next = current.get(i);
                    int offset = 0;
                    while (offset < Math.min(previous.length(), next.length())
                        && previous.charAt(offset) == next.charAt(offset)) offset++;
                    var category = previous.substring(0, previous.indexOf(':'));
                    var methods =
                        java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]*\\(\\)")
                            .matcher(previous.substring(0, offset));
                    String field = "";
                    while (methods.find()) field = methods.group();
                    detail = category + (field.isEmpty() ? "" : ":" + field);
                    break;
                  }
                throw new IllegalStateException(
                    "Classification local input or producer evidence changed (" + detail + ")");
              }
            });
      }
    }

    public record ClassificationOverlay(
        List<Observation> members, List<String> state, List<String> rawState) {
      public ClassificationOverlay {
        members = List.copyOf(members);
        state = List.copyOf(state);
        rawState = List.copyOf(rawState);
      }
    }

    /** Fast local-only capture. Never invoke services or await work while holding this monitor. */
    public ClassificationOverlay classificationOverlay(Cohort cohort, Observation producer) {
      return classificationOverlay(cohort, producer, List.of());
    }

    public ClassificationOverlay classificationOverlay(
        Cohort cohort, Observation producer, List<Observation> persisted) {
      return classificationOverlay(cohort, producer, persisted, null);
    }

    private ClassificationOverlay classificationOverlay(
        Cohort cohort,
        Observation producer,
        List<Observation> persisted,
        Map<Observation, Integer> childBaseline) {
      return classificationOverlay(cohort, producer, persisted, childBaseline, List.of());
    }

    private ClassificationOverlay classificationOverlay(
        Cohort cohort,
        Observation producer,
        List<Observation> persisted,
        Map<Observation, Integer> childBaseline,
        List<SchedulerWrite> writes) {
      synchronized (graph) {
        if (rootAttempt().attemptState == AttemptState.FAILED
            || rootAttempt().attemptState == AttemptState.COMMITTED)
          throw new IllegalStateException("Classification attempt is no longer live");
        var members = new ArrayList<Observation>();
        var state = new ArrayList<String>();
        var rawState = new ArrayList<String>();
        var originals = new LinkedHashMap<String, Observation>();
        for (var baseline : persisted) {
          var member = (Observation) checkPresentAsset(baseline);
          originals.put("id:" + member.getId(), member);
        }
        var actualCohort = checkPresentAsset(cohort);
        if (graph.containsVertex(actualCohort))
          for (var edge : graph.outgoingEdgesOf(actualCohort))
            if (edge.relationship == GraphModel.Relationship.HAS_MEMBER
                && graph.getEdgeTarget(edge) instanceof Observation member)
              originals.put(
                  member.getId() > 0
                      ? "id:" + member.getId()
                      : "transient:" + member.getTransientId(),
                  member);
        for (var member : originals.values()) {
          var view = readObservation(member);
          members.add(view);
          int delta =
              childBaseline == null
                  ? 0
                  : classificationChildDeltas.getOrDefault(member, 0)
                      - childBaseline.getOrDefault(member, 0);
          rawState.add("payload:" + ObservationReadView.fingerprint(member, delta, writes));
          state.add(
              "payload:"
                  + ObservationReadView.fingerprint(member, delta, writes)
                  + ":"
                  + ObservationReadView.fingerprint(view, delta, writes));
          rawState.add(
              (member.getId() > 0 ? "id:" + member.getId() : "transient:" + member.getTransientId())
                  + ":"
                  + member.getObservable().getUrn()
                  + ":"
                  + member.getGeometry().encode());
          state.add(
              (member.getId() > 0 ? "id:" + member.getId() : "transient:" + member.getTransientId())
                  + ":"
                  + member.getObservable().getUrn()
                  + ":"
                  + view.getObservable().getUrn()
                  + ":"
                  + member.getGeometry().encode());
        }
        int producerDelta =
            childBaseline == null
                ? 0
                : classificationChildDeltas.getOrDefault(producer, 0)
                    - childBaseline.getOrDefault(producer, 0);
        state.add(
            "producerPayload:" + ObservationReadView.fingerprint(producer, producerDelta, writes));
        rawState.add(
            "producerPayload:" + ObservationReadView.fingerprint(producer, producerDelta, writes));
        state.add(
            "producerIdentity:"
                + producer.getTransientId()
                + ":"
                + producer.getId()
                + ":"
                + producer.getObservable().getUrn()
                + ":"
                + producer.getGeometry().encode());
        for (var link : incoming(producer))
          if (link.source() instanceof Activity a
              && link.type()
                  == GraphModel.Relationship.forContextualization(
                      producer.getObservable().getContextualization())) {
            var witness =
                "producer:"
                    + a.getTransientId()
                    + ":"
                    + a.getType()
                    + ":"
                    + a.getOutcome()
                    + ":"
                    + a.getMetadata().get(Metadata.IM_EXECUTION_GEOMETRY);
            state.add(witness);
            rawState.add(witness);
          }
        for (var link : incoming(producer))
          if (link.type() == GraphModel.Relationship.HAS_CHILD) {
            var witness = "producerContext:" + link.source().getId();
            state.add(witness);
            rawState.add(witness);
          }
        for (var link : outgoing(producer))
          if (link.type() == GraphModel.Relationship.CONTRIBUTED_TO) {
            var witness = "producerCohort:" + link.target().getId();
            state.add(witness);
            rawState.add(witness);
          }
        rawState.add(
            "producerIdentity:"
                + producer.getTransientId()
                + ":"
                + producer.getId()
                + ":"
                + producer.getObservable().getUrn()
                + ":"
                + producer.getGeometry().encode());
        Collections.sort(state);
        Collections.sort(rawState);
        return new ClassificationOverlay(members, state, rawState);
      }
    }

    /**
     * Accept only the expected projected snapshot, then atomically record this hop's own effects.
     */
    public ClassificationOverlay stageClassificationSnapshot(
        List<MemberClassifierExecutor.PendingAttribution> pending,
        ContextScope execution,
        Cohort cohort,
        Observation producer,
        List<Observation> persisted,
        List<String> expected) {
      return prepareAndStageAttributions(
          pending,
          execution,
          new AttributionProjection(
              cohort, producer, List.copyOf(persisted), List.copyOf(expected)));
    }

    /** Guards run on the exact native root-commit transaction, before any root writes. */
    public void guardClassificationReadSet(KnowledgeGraph.ClassificationReadSet readSet) {
      synchronized (graph) {
        requireOpen();
        classificationReadGuards.add(readSet);
      }
    }

    /** Source, recipient and structural evidence is rechecked in the same native root commit. */
    /** Admission is possible only through the private actual-compiled-output capability. */
    /** Own the real root's drain accounting before any modeled-effect semantic RPC. */
    public void prepareModeledCessation(Runnable preparation) {
      beginCycleDriver();
      AutoCloseable lease = null;
      try {
        var cycle = scope.getResolutionCycle();
        if (cycle == null)
          throw new IllegalStateException("Modeled cessation has no actual resolution cycle");
        lease = cycle.retain();
        synchronized (graph) {
          requireOpen();
        }
        if (scope.isInterrupted())
          throw new java.util.concurrent.CancellationException(
              "Modeled cessation was cancelled before preparation");
        preparation.run();
      } finally {
        try {
          if (lease != null) lease.close();
        } catch (Exception failure) {
          throw new IllegalStateException("Cannot release modeled-cessation preparation", failure);
        } finally {
          endCycleDriver();
        }
      }
    }

    public void stageModeledCessation(ModeledCessations.Plan plan) {
      prepareModeledCessation(() -> stageModeledCessationWithCycle(plan));
    }

    private void stageModeledCessationWithCycle(ModeledCessations.Plan plan) {
      plan.require();
      if (plan.authority().root() != rootAttempt())
        throw new IllegalStateException("Cessation belongs to another actual root");
      synchronized (graph) {
        requireOpen();
        if (!Collections.disjoint(inoperativeObservations, plan.ids()))
          throw new UnsupportedOperationException(
              "Duplicate cessation of the same target in one root transition");
        guardConsequenceReadSet(plan.reads());
        guardConsequenceReadSet(plan.roles().inputs());
        roleSupportGuards.add(plan.roles());
        modeledCessations.add(plan);
        inoperativeObservations.addAll(plan.ids());
        consequenceCommitAdmissions.add(plan::require);
        classificationMutationStamp[0]++;
      }
    }

    /**
     * Only canonical new assets owned by this actual open root are current before durable birth.
     */
    public Optional<Boolean> currentStatusFor(Observation observation) {
      synchronized (graph) {
        var root = rootAttempt();
        if (root.attemptState == AttemptState.FAILED) return Optional.of(false);
        if (observation == null) return Optional.of(false);
        // Only our private snapshot handler may unwrap to a canonical staged birth.
        // Keep this local to current-status checks; footprint ownership remains OPEN-only.
        if (java.lang.reflect.Proxy.isProxyClass(observation.getClass())) {
          var handler = java.lang.reflect.Proxy.getInvocationHandler(observation);
          if (!(handler instanceof ObservationReadView view)
              || view.owner != root
              || !(view.source instanceof Observation original)) return Optional.empty();
          observation = original;
        }
        // Owned views and transaction links can expose our private staged replacement.
        // Recover only that exact object, never a caller-supplied same-ID copy.
        for (var asset : graph.vertexSet()) {
          if (asset instanceof Observation canonical && staged(asset) == observation) {
            observation = canonical;
            break;
          }
        }
        if (inoperativeObservations.contains(observation.getId())) return Optional.of(false);
        if ((root.attemptState == AttemptState.OPEN || root.attemptState == AttemptState.COMMITTING)
            && observation.getId() != Observation.QUERY_ID
            && observation.getId() <= 0
            && containsCanonicalObservation(observation)) return Optional.of(true);
        return Optional.empty();
      }
    }

    private boolean containsCanonicalObservation(Observation observation) {
      for (var asset : graph.vertexSet()) if (asset == observation) return true;
      return false;
    }

    /**
     * Exact server-held durable input identity; DTO equality never grants modeled-effect authority.
     */
    public boolean ownsModeledCessationInput(
        Observation observation, ServiceContextScope execution) {
      if (observation == null
          || execution == null
          || execution.getDigitalTwin() != DigitalTwinImpl.this) return false;
      Observation original = observation;
      if (java.lang.reflect.Proxy.isProxyClass(observation.getClass())) {
        var handler = java.lang.reflect.Proxy.getInvocationHandler(observation);
        if (!(handler instanceof ObservationReadView view)
            || view.owner != rootAttempt()
            || !(view.source instanceof Observation source)) return false;
        original = source;
      }
      if (original.getId() <= 0) return false; // This protocol requires a committed beforeimage.
      return knowledgeGraph.getAsset(original.getId(), execution, Observation.class) == original;
    }

    public void guardConsequenceReadSet(KnowledgeGraph.ConsequenceReadSet readSet) {
      synchronized (graph) {
        requireOpen();
        if (readSet == null
            || !Objects.equals(readSet.contextId(), scope.getId())
            || readSet.assets().isEmpty())
          throw new IllegalArgumentException(
              "Consequence read evidence belongs to another context or is empty");
        consequenceReadGuards.add(readSet);
      }
    }

    public void guardContextCohortCatalogue(KnowledgeGraph.ContextCohortCatalogue catalogue) {
      synchronized (graph) {
        requireOpen();
        if (catalogue == null || !Objects.equals(scope.getId(), catalogue.contextId()))
          throw new IllegalArgumentException("Catalogue has another context");
        contextCatalogueGuards.add(catalogue);
      }
    }

    /**
     * Server-owned request admission; invoked before native locking, never transported as a claim.
     */
    public void guardConsequenceCommitAdmission(Runnable admission) {
      synchronized (graph) {
        requireOpen();
        if (scope.getConsequenceCause() == null)
          throw new IllegalStateException(
              "Consequence admission requires a causal execution scope");
        consequenceCommitAdmissions.add(Objects.requireNonNull(admission));
        classificationMutationStamp[0]++;
      }
    }

    /**
     * Bind exact captured native payloads before any role decision, outside semantic/native locks.
     */
    public void guardConsequenceBaseline(
        KnowledgeGraph.ConsequenceReadSet readSet, ContextScope execution) {
      beginCycleDriver();
      try {
        guardConsequenceBaselineWithCycle(readSet, execution);
      } finally {
        endCycleDriver();
      }
    }

    private void guardConsequenceBaselineWithCycle(
        KnowledgeGraph.ConsequenceReadSet readSet, ContextScope execution) {
      requireAttributionExecution(execution);
      if (readSet == null
          || !Objects.equals(readSet.contextId(), scope.getId())
          || readSet.assets().isEmpty())
        throw new IllegalArgumentException(
            "Consequence native baseline belongs to another context or is empty");
      var materialized = new LinkedHashMap<Long, Observation>();
      for (var asset : readSet.assets())
        if (asset.labels().contains(GraphModel.Labels.OBSERVATION)
            && !Boolean.FALSE.equals(asset.properties().get(KnowledgeGraph.CURRENT_PROPERTY))) {
          var observation = knowledgeGraph.materializeConsequenceObservation(asset, execution);
          if (observation == null
              || observation.getId() != asset.id()
              || materialized.putIfAbsent(asset.id(), observation) != null)
            throw new IllegalStateException(
                "Consequence native observation materialization is incomplete");
        }
      synchronized (graph) {
        requireOpen();
        try {
          var originals = new LinkedHashMap<Long, Observation>();
          var expected = new LinkedHashMap<Long, Map<String, Object>>();
          for (var asset : readSet.assets()) {
            var previous = consequenceBaselines.get(asset.id());
            if (previous != null && !previous.equals(asset))
              throw new IllegalStateException("Earliest consequence input changed");
            var member = materialized.get(asset.id());
            if (member == null) continue;
            var original = (Observation) checkPresentAsset(ObservationReadView.source(member));
            var nativeInput =
                org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
                    .persistedObservationProperties(
                        ObservationReadView.persistedSnapshot(member, 0, List.of()));
            if (!nativeInput.equals(classificationPersistedInput(original)))
              throw new IllegalStateException(
                  "Existing consequence input differs from captured native payload");
            originals.put(asset.id(), original);
            expected.put(asset.id(), nativeInput);
          }
          guardConsequenceReadSet(readSet);
          readSet.assets().forEach(asset -> consequenceBaselines.putIfAbsent(asset.id(), asset));
          originals.forEach(classificationBaselineMembers::putIfAbsent);
          classificationLocalGuards.add(
              () -> {
                for (var entry : expected.entrySet())
                  if (!entry
                      .getValue()
                      .equals(classificationPersistedInput(originals.get(entry.getKey()))))
                    throw new IllegalStateException(
                        "Consequence native baseline changed before commit");
              });
        } catch (RuntimeException failure) {
          fail(failure);
          throw failure;
        }
      }
    }

    public boolean hasClassificationBaseline(long cohortId) {
      synchronized (graph) {
        requireOpen();
        return classificationBaselines.containsKey(cohortId);
      }
    }

    /**
     * Pin the earliest persisted input before any member read/effect. A later snapshot must never
     * replace it and bless results derived from an older payload with unchanged semantics.
     */
    public void guardClassificationBaseline(
        KnowledgeGraph.ClassificationReadSet readSet, List<Observation> freshMaterializedMembers) {
      beginCycleDriver();
      try {
        guardClassificationBaselineWithCycle(readSet, freshMaterializedMembers);
      } finally {
        endCycleDriver();
      }
    }

    private void guardClassificationBaselineWithCycle(
        KnowledgeGraph.ClassificationReadSet readSet, List<Observation> freshMaterializedMembers) {
      synchronized (graph) {
        requireOpen();
        try {
          if (readSet == null
              || readSet.cohortId() <= 0
              || !Objects.equals(readSet.contextId(), scope.getId()))
            throw new IllegalArgumentException(
                "Native classification baseline belongs to another context");
          var previous = classificationBaselines.get(readSet.cohortId());
          if (previous != null) {
            if (!previous.equals(readSet))
              throw new IllegalStateException("Earliest classification baseline changed");
            return;
          }
          var fresh = new LinkedHashMap<Long, Observation>();
          for (var member : freshMaterializedMembers) {
            if (member == null
                || member.getId() <= 0
                || fresh.putIfAbsent(member.getId(), member) != null)
              throw new IllegalArgumentException(
                  "Invalid or duplicate native classification input");
          }
          if (fresh.size() != readSet.members().size())
            throw new IllegalArgumentException(
                "Classification baseline materialization is incomplete");
          var expected = new LinkedHashMap<Long, Map<String, Object>>();
          var originals = new LinkedHashMap<Long, Observation>();
          var capturedIds = new HashSet<Long>();
          for (var captured : readSet.members()) {
            if (!capturedIds.add(captured.id()))
              throw new IllegalArgumentException("Duplicate captured classification member");
            var member = fresh.get(captured.id());
            if (member == null
                || !Objects.equals(member.getObservable().getUrn(), captured.observable())
                || member.getGeometry() == null
                || !Objects.equals(
                    Geometry.forTransport(member.getGeometry()).encode(),
                    Geometry.create(captured.geometry()).encode()))
              throw new IllegalStateException(
                  "Classification baseline differs from captured native input");
            var original = (Observation) checkPresentAsset(ObservationReadView.source(member));
            var nativeInput =
                org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
                    .persistedObservationProperties(
                        ObservationReadView.persistedSnapshot(member, 0, List.of()));
            var localInput = classificationPersistedInput(original);
            if (!nativeInput.equals(localInput))
              throw new IllegalStateException(
                  "Existing classification input is stale before baseline capture");
            expected.put(captured.id(), nativeInput);
            originals.put(captured.id(), original);
          }
          // Existing canonical originals have passed the comparison. For members absent locally,
          // future reads must prefer this captured native bean over an older scope-cache object.
          originals.forEach(classificationBaselineMembers::putIfAbsent);
          classificationBaselines.put(readSet.cohortId(), readSet);
          classificationReadGuards.add(readSet);
          classificationLocalGuards.add(
              () -> {
                for (var input : expected.entrySet()) {
                  var original = (Observation) checkPresentAsset(originals.get(input.getKey()));
                  if (!input.getValue().equals(classificationPersistedInput(original)))
                    throw new IllegalStateException(
                        "Classification persisted input changed after baseline capture");
                }
              });
        } catch (RuntimeException failure) {
          fail(failure);
          throw failure;
        }
      }
    }

    private Map<String, Object> classificationPersistedInput(Observation original) {
      return org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph
          .persistedObservationProperties(
              ObservationReadView.persistedSnapshot(
                  original,
                  classificationChildDeltas.getOrDefault(original, 0),
                  classificationSchedulerWrites));
    }

    private List<Runnable> afterCommitActions = new ArrayList<>();
    private KnowledgeGraph.Commit committedChanges;
    private long committedObserverId;
    private List<Runnable> rollbackActions = new ArrayList<>();
    private List<org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal> schedulerJournal =
        new ArrayList<>();

    @Override
    public Executor getExecutor(Observation observation) {
      return contextualizers.get(observation);
    }

    @Override
    public Actuator getInputPlan(Observation observation) {
      synchronized (graph) {
        if (rootAttempt().attemptState == AttemptState.FAILED) return null;
        var executor = getExecutor(observation);
        return executor == null ? inputPlans.get(observation) : executor.getActuator();
      }
    }

    /** Retain compiled bindings for INIT validation without inventing executable work. */
    public void registerInputPlan(Observation observation, Actuator plan) {
      synchronized (graph) {
        requireOpen();
        inputPlans.put(observation, plan);
      }
    }

    /** Claim a single semantic writer in this transaction and the committed graph. */
    /**
     * Exact portable collective-input association; existing consumers retain their normal plan and
     * source/query guards. Source IDs are bound after genuine same-transaction allocation.
     */
    public void linkCollectiveInput(
        Cohort cohort, Observation consumer, Observation producer, String reductionJson) {
      synchronized (graph) {
        requireOpen();
        if (cohort == null
            || consumer == null
            || cohort.getObservable() == null
            || producer == null && reductionJson == null)
          throw new IllegalArgumentException(
              "Collective dependency requires its actual compiled binding");
        if (producer != null && producer.getId() == Observation.QUERY_ID)
          throw new UnsupportedOperationException(
              "Generic multi-contributor query inputs require an issued query receipt association");
        graph.addVertex(cohort);
        graph.addVertex(consumer);
        var edge =
            new RelationshipEdge(
                GraphModel.Relationship.AFFECTS,
                org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE,
                org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.PREREQUISITE,
                org.integratedmodelling.klab.api.digitaltwin.ProcessPlan
                    .COLLECTIVE_COHORT_OBSERVABLE,
                cohort.getObservable().getUrn());
        if (reductionJson != null)
          edge.properties.put(
              org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding.DATA_KEY,
              reductionJson);
        graph.addEdge(cohort, consumer, edge);
        if (producer != null)
          edge.properties.put(
              org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.COLLECTIVE_SOURCE_OBSERVATION,
              producer.getId());
        if (producer != null)
          beforeCommitActions.add(
              () -> {
                if (producer.getId() <= 0)
                  throw new IllegalStateException(
                      "Compiled collective source did not receive its durable identity");
                edge.properties.put(
                    org.integratedmodelling.klab.api.digitaltwin.ProcessPlan
                        .COLLECTIVE_SOURCE_OBSERVATION,
                    producer.getId());
              });
        classificationMutationStamp[0]++;
      }
    }

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
        consequenceCommitAdmissions.clear();
        consequenceReadGuards.clear();
        contextCatalogueGuards.clear();
        consequenceBaselines.clear();
        classificationAuditBindings.clear();
        afterCommitActions.clear();
        schedulerJournal.clear();
        journalFactories.clear();
        contextualizers.clear();
        inputPlans.clear();
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
                  observation
                      .getMetadata()
                      .put(Scheduler.EXECUTION_METADATA_KEY, previousExecution);
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

    private ResolutionCycles.Admission cycleAdmission;
    private int activeCycleDrivers;

    private void beginCycleDriver() {
      synchronized (graph) {
        requireOpen();
        rootAttempt().activeCycleDrivers++;
      }
    }

    private boolean beginCycleCommitDriver() {
      synchronized (graph) {
        if (rootAttempt().attemptState == AttemptState.FAILED) return false;
        requireOpen();
        rootAttempt().activeCycleDrivers++;
        return true;
      }
    }

    private void endCycleDriver() {
      synchronized (graph) {
        rootAttempt().activeCycleDrivers--;
      }
      releaseTerminalCycleIfDrained();
    }

    private void releaseTerminalCycleIfDrained() {
      // Nested native failure paths still own this monitor. Their outer driver finally will
      // retry release after unlocking; no semantic release RPC may run under the graph monitor.
      if (Thread.holdsLock(graph)) return;
      var root = rootAttempt();
      boolean release;
      synchronized (graph) {
        release =
            root.activeCycleDrivers == 0
                && root.preparingAttributions == 0
                && (root.attemptState == AttemptState.COMMITTED
                    || root.attemptState == AttemptState.FAILED);
      }
      if (release) root.releaseDirectCycle();
    }

    @Override
    public ServiceContextScope executionScope() {
      return scope;
    }

    private void releaseDirectCycle() {
      if (cycleAdmission != null)
        try {
          cycleAdmission.close();
        } catch (Throwable cleanupFailure) {
          // A completed native transaction retains its durable result even if pin cleanup fails.
          try {
            scope.warn("Cannot release completed transaction cycle", cleanupFailure);
          } catch (Throwable notificationFailure) {
            cleanupFailure.addSuppressed(notificationFailure);
          }
        }
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
      this.inputPlans = new ConcurrentHashMap<>();
      this.cohortGeometries = new HashMap<>();
      boolean activityLinked = false;

      synchronized (graph) {
        this.graph.addVertex(activity);
        ownedExecutionActivities.add(activity);
        if (scope.getConsequenceCause() != null) {
          instrumentConsequenceActivity();
          var trigger =
              knowledgeGraph.getAsset(
                  scope.getConsequenceCause().triggeringActivityId(), scope, Activity.class);
          if (trigger == null || trigger.getId() <= 0)
            throw new IllegalStateException("Consequence triggering activity is unavailable");
          this.graph.addVertex(trigger);
          this.graph.addEdge(
              trigger, activity, new RelationshipEdge(GraphModel.Relationship.TRIGGERED));
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
      this.configurationRevisions = parent.configurationRevisions;
      this.configurationBirths = parent.configurationBirths;
      this.modified = parent.modified;
      this.added = parent.added;
      this.failures = parent.failures;
      this.cohortGeometries = parent.cohortGeometries;
      this.contextualizers = parent.contextualizers;
      this.inputPlans = parent.inputPlans;
      this.beforeCommitActions = parent.beforeCommitActions;
      this.consequenceCommitAdmissions = parent.consequenceCommitAdmissions;
      this.consequenceReadGuards = parent.consequenceReadGuards;
      this.contextCatalogueGuards = parent.contextCatalogueGuards;
      this.inoperativeObservations = parent.inoperativeObservations;
      this.modeledCessations = parent.modeledCessations;
      this.roleSupportGuards = parent.roleSupportGuards;
      this.publishedCessations = parent.publishedCessations;
      this.committedMembershipChanges = parent.committedMembershipChanges;
      this.consequenceBaselines = parent.consequenceBaselines;
      this.classificationAuditBindings = parent.classificationAuditBindings;
      this.classificationReadGuards = parent.classificationReadGuards;
      this.classificationBaselines = parent.classificationBaselines;
      this.classificationBaselineMembers = parent.classificationBaselineMembers;
      this.classificationLocalGuards = parent.classificationLocalGuards;
      this.classificationMutationStamp = parent.classificationMutationStamp;
      this.classificationChildDeltas = parent.classificationChildDeltas;
      this.classificationSchedulerWrites = parent.classificationSchedulerWrites;
      this.afterCommitActions = parent.afterCommitActions;
      this.rollbackActions = parent.rollbackActions;
      this.schedulerJournal = parent.schedulerJournal;

      synchronized (graph) {
        this.graph.addVertex(activity);
        rootAttempt().ownedExecutionActivities.add(activity);
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

    /**
     * Preserve execution phase types while identifying engine submissions and their actual actor.
     */
    private void instrumentConsequenceActivity() {
      var cause = scope.getConsequenceCause();
      if (cause == null) return;
      activity
          .getMetadata()
          .put(
              org.integratedmodelling.klab.api.digitaltwin.Consequence.METADATA_KEY,
              Utils.Json.asString(cause));
      if (activity instanceof ActivityImpl mutable
          && activity.getType() == Activity.Type.SUBMISSION)
        mutable.setType(Activity.Type.CONSEQUENCE_SUBMISSION);
      var agent = knowledgeGraph.klab();
      if (agent == null || agent.getId() <= 0)
        throw new IllegalStateException(
            "Consequence provenance requires the persisted k.LAB agent");
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
          .orElse(classificationBaselineMembers.getOrDefault(asset.getId(), (Observation) asset));
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
          int previousCount = sourceObs.getChildrenCount();
          sourceObs.setChildrenCount(previousCount + 1);
          classificationChildDeltas.merge(sourceObs, 1, Integer::sum);
          rollbackActions.add(
              () -> sourceObs.setChildrenCount(Math.max(0, sourceObs.getChildrenCount() - 1)));
          // Attribution copies own primitive counters. Keep this exact link increment visible in
          // the staged object as well, so projection and native storage use the same owned delta.
          if (staged(sourceObs) instanceof ObservationImpl replacement
              && replacement != sourceObs) {
            replacement.setChildrenCount(replacement.getChildrenCount() + 1);
            rollbackActions.add(
                () ->
                    replacement.setChildrenCount(Math.max(0, replacement.getChildrenCount() - 1)));
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
      prepareAndStageAttributions(pending, executionScope, null);
    }

    private record ConsequenceRoleInput(
        ConsequenceRoleAttribution request,
        Observation source,
        String sourceState,
        ObservationImpl member,
        Attribution previous,
        ObservationImpl current,
        Observable observable,
        String originalState,
        String currentState,
        String familiesState,
        String requestState,
        Concept role) {}

    /**
     * Add roles from independently admitted consequence rules, without inventing a classifier
     * invocation or weakening the existing model-classification entry point. Applicability and
     * semantic admission occur outside the graph monitor; no changes are visible until the complete
     * batch is revalidated. Source and recipient native inputs must be guarded first.
     */
    public List<Long> stageConsequenceRoles(
        List<ConsequenceRoleAttribution> pending,
        ContextScope executionScope,
        ConsequenceRoleAdmission admission) {
      return stageConsequenceRoleSupports(pending, executionScope, admission)
          .acquiredRecipientIds();
    }

    public ConsequenceRoleSupportResult stageConsequenceRoleSupports(
        List<ConsequenceRoleAttribution> pending,
        ContextScope executionScope,
        ConsequenceRoleAdmission admission) {
      beginCycleDriver();
      try {
        return stageConsequenceRolesWithCycle(pending, executionScope, admission);
      } finally {
        endCycleDriver();
      }
    }

    private ConsequenceRoleSupportResult stageConsequenceRolesWithCycle(
        List<ConsequenceRoleAttribution> pending,
        ContextScope executionScope,
        ConsequenceRoleAdmission admission) {
      Objects.requireNonNull(admission, "Consequence semantic admission");
      boolean registered = false;
      try {
        requireAttributionExecution(executionScope);
        var inputs = new ArrayList<ConsequenceRoleInput>();
        final long stamp;
        synchronized (graph) {
          requireOpen();
          var cause = executionScope.getConsequenceCause();
          if (cause == null
              || !Objects.equals(cause.contextId(), scope.getId())
              || cause.kind()
                      != org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind
                          .ROLE_CONFERRAL
                  && cause.kind()
                      != org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind
                          .ROLE_IMPLICATION)
            throw new IllegalStateException(
                "Additive roles require a consequence attribution context");
          if (activity.getType() != Activity.Type.CLASSIFICATION)
            throw new IllegalStateException(
                "Role attribution requires a CLASSIFICATION scientific activity");
          if (!failures.isEmpty()) throw new IllegalStateException("Transaction already failed");
          var guarded =
              consequenceBaselines.values().stream()
                  .filter(snapshot -> snapshot.labels().contains(GraphModel.Labels.OBSERVATION))
                  .map(KnowledgeGraph.ConsequenceAssetSnapshot::id)
                  .collect(java.util.stream.Collectors.toSet());
          for (var value : List.copyOf(pending)) {
            var source =
                (Observation) checkPresentAsset(ObservationReadView.source(value.source()));
            var canonical = checkPresentAsset(ObservationReadView.source(value.recipient()));
            if (source == null
                || source.getId() <= 0
                || !(canonical instanceof ObservationImpl member)
                || member.getId() <= 0
                || source.getId() != value.source().getId()
                || member.getId() != value.recipient().getId()
                || !guarded.contains(source.getId())
                || !guarded.contains(member.getId()))
              throw new IllegalArgumentException(
                  "Role attribution lacks durable source and recipient read guards");
            var previous = attributions.get(member);
            var current = previous == null ? member : previous.replacement();
            var observable = ObservationReadView.of(current).getObservable();
            if (observable == null
                || observable.getSemantics().isCollective()
                || !SemanticType.isEnumerableSubstantial(observable.getSemantics().getType()))
              throw new IllegalArgumentException(
                  "Roles can only be attributed to individual substantials");
            var role = attributionConceptSnapshot(value.role());
            if (!role.is(SemanticType.ROLE) || role.is(SemanticType.NOTHING))
              throw new IllegalArgumentException("A consequence must attribute a legal role");
            inputs.add(
                new ConsequenceRoleInput(
                    value,
                    source,
                    ObservationReadView.fingerprint(source),
                    member,
                    previous,
                    current,
                    observable,
                    ObservationReadView.fingerprint(member),
                    ObservationReadView.fingerprint(current),
                    attributionFamiliesState(previous),
                    attributionConceptState(value.role()),
                    role));
          }
          stamp = classificationMutationStamp[0];
          rootAttempt().preparingAttributions++;
          registered = true;
        }
        var reasoner = executionScope.getService(Reasoner.class);
        var batch = new LinkedHashMap<ObservationImpl, PreparedAttribution>();
        var preparedRows = new ArrayList<ConsequenceRoleAdmission.Prepared>();
        var audit = new ArrayList<Map<String, Object>>();
        for (var input : inputs) {
          requireAttributionExecution(executionScope);
          var prepared = batch.get(input.member());
          var before = prepared == null ? input.observable() : prepared.after();
          // Additive role inheritance does not impose classifier-family exclusivity. Existing
          // roles remain, and normal builder applicability/satisfiability decides compatibility.
          boolean acquired = !reasoner.hasRole(before, input.role());
          var after = before;
          if (acquired) {
            var builder = before.builder(executionScope).withRole(input.role());
            after = builder.buildObservable();
            if (after == null
                || Utils.Notifications.hasErrors(builder.getNotifications())
                || !reasoner.satisfiable(after.getSemantics())
                || !reasoner.hasRole(after, input.role()))
              throw new IllegalArgumentException("Incompatible consequence role attribution");
            var snapshots =
                new HashSet<String>(
                    prepared != null
                        ? prepared.snapshots()
                        : input.previous() == null ? Set.of() : input.previous().snapshots());
            snapshots.add(input.member().getObservable().getUrn());
            snapshots.add(after.getUrn());
            var families =
                prepared != null
                    ? prepared.families()
                    : input.previous() == null ? List.<Concept>of() : input.previous().families();
            batch.put(
                input.member(),
                new PreparedAttribution(input.member(), after, families, Set.copyOf(snapshots)));
          }
          preparedRows.add(
              new ConsequenceRoleAdmission.Prepared(
                  input.source().getId(),
                  input.member().getId(),
                  input.request().ruleId(),
                  input.role(),
                  before,
                  after));
          var item = new LinkedHashMap<String, Object>();
          item.put("sourceId", input.source().getId());
          item.put("recipientId", input.member().getId());
          item.put("ruleId", input.request().ruleId());
          item.put("role", input.role().getUrn());
          item.put("before", before.getUrn());
          item.put("after", after.getUrn());
          item.put("supportContract", ConsequenceRoleSupportResult.CONTRACT);
          item.put("acquired", acquired);
          item.put("contextId", executionScope.getId());
          item.put(
              "observerId",
              executionScope.getObserverId() == null ? 0L : executionScope.getObserverId());
          item.put(
              "focusId",
              executionScope.getContextObservation() == null
                  ? 0L
                  : executionScope.getContextObservation().getId());
          item.put(
              "recipientObserverSemantics",
              before.getObserverSemantics() == null ? "" : before.getObserverSemantics().getUrn());
          audit.add(item);
        }
        admission.validate(List.copyOf(preparedRows), executionScope);
        var semanticEvidence = admission.semanticEvidence();
        if (semanticEvidence == null
            || !org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding.VERSION.equals(
                semanticEvidence.contractVersion())
            || !semanticEvidence.complete()
            || semanticEvidence.witness() == null
            || !semanticEvidence.witness().completeAt(System.currentTimeMillis()))
          throw new IllegalStateException("Consequence execution accounting is unavailable");
        var cycle =
            Objects.requireNonNull(executionScope.getResolutionCycleBinding(), "Consequence cycle")
                .semanticBinding();
        var evidenceRequest =
            new org.integratedmodelling.klab.api.services.reasoner.objects
                .SemanticDependencyRequest(
                cycle.reasonerServiceId(),
                cycle.knowledgeRevision(),
                cycle.semanticFingerprint(),
                semanticEvidence.obligations());
        var evidenceWitness = semanticEvidence.witness();
        if (!Objects.equals(evidenceRequest.inputHash(), evidenceWitness.inputHash())
            || !Objects.equals(evidenceWitness.serviceId(), cycle.reasonerServiceId())
            || evidenceWitness.knowledgeRevision() != cycle.knowledgeRevision()
            || !Objects.equals(evidenceWitness.baseFingerprint(), cycle.semanticFingerprint()))
          throw new IllegalStateException(
              "Consequence execution accounting belongs to different inputs or epoch");
        var evidenceJson = Utils.Json.asString(semanticEvidence);
        var authority = Utils.Json.asString(admission.provenance());
        requireAttributionExecution(executionScope);
        synchronized (graph) {
          requireOpen();
          if (!failures.isEmpty() || classificationMutationStamp[0] != stamp)
            throw new IllegalStateException(
                "Consequence graph changed during semantic preparation");
          for (var input : inputs) {
            if (checkPresentAsset(input.member()) != input.member()
                || attributions.get(input.member()) != input.previous()
                || !input.originalState().equals(ObservationReadView.fingerprint(input.member()))
                || !input.currentState().equals(ObservationReadView.fingerprint(input.current()))
                || !input.sourceState().equals(ObservationReadView.fingerprint(input.source()))
                || !input.familiesState().equals(attributionFamiliesState(input.previous()))
                || !input.requestState().equals(attributionConceptState(input.request().role())))
              throw new IllegalStateException(
                  "Consequence attribution input changed during semantic preparation");
          }
          requireAttributionExecution(executionScope);
          for (var prepared : batch.values()) {
            var input =
                inputs.stream()
                    .filter(value -> value.member() == prepared.member())
                    .findFirst()
                    .orElseThrow();
            var original =
                input.previous() == null
                    ? input.member().getObservable().getUrn()
                    : input.previous().before();
            attributions.put(
                prepared.member(),
                new Attribution(
                    prepared.member(),
                    input.current().copyForAttribution(prepared.after()),
                    original,
                    prepared.families(),
                    prepared.snapshots()));
            if (prepared.member().getId() > 0) modified.add(prepared.member());
          }
          for (var item : audit) {
            item.put("authority", authority);
            var member =
                inputs.stream()
                    .map(ConsequenceRoleInput::member)
                    .filter(o -> o.getId() == ((Number) item.get("recipientId")).longValue())
                    .findFirst()
                    .orElseThrow();
            link(
                activity,
                member,
                GraphModel.Relationship.CLASSIFIED,
                "sourceId",
                item.get("sourceId"),
                "ruleId",
                item.get("ruleId"),
                "role",
                item.get("role"),
                "before",
                item.get("before"),
                "after",
                item.get("after"),
                "authority",
                authority,
                "supportContract",
                item.get("supportContract"),
                "acquired",
                item.get("acquired"),
                "contextId",
                item.get("contextId"),
                "observerId",
                item.get("observerId"),
                "focusId",
                item.get("focusId"),
                "recipientObserverSemantics",
                item.get("recipientObserverSemantics"));
          }
          var previousAudit = activity.getMetadata().get("klab.consequence.roleAttributions");
          var combined = new ArrayList<Object>();
          if (previousAudit instanceof List<?> previous) combined.addAll(previous);
          combined.addAll(audit);
          activity.getMetadata().put("klab.consequence.roleAttributions", combined);
          activity
              .getMetadata()
              .put(
                  org.integratedmodelling.klab.api.knowledge.SemanticEvidenceBinding.DATA_KEY,
                  evidenceJson);
          classificationLocalGuards.add(
              () -> {
                for (var input : inputs)
                  if (!input.originalState().equals(ObservationReadView.fingerprint(input.member()))
                      || !input
                          .sourceState()
                          .equals(ObservationReadView.fingerprint(input.source())))
                    throw new IllegalStateException(
                        "Consequence source or recipient changed before commit");
              });
          if (!audit.isEmpty())
            consequenceCommitAdmissions.add(
                () -> {
                  admission.admitCommit();
                  var renewed = reasoner.semanticDependencies(evidenceRequest, executionScope);
                  if (!org.integratedmodelling.klab.api.services.reasoner.objects
                      .SemanticDependencyWitness.revalidates(evidenceWitness, renewed))
                    throw new IllegalStateException(
                        "Consequence execution evidence changed before commit");
                });
          classificationMutationStamp[0]++;
          rootAttempt().preparingAttributions--;
          registered = false;
          return new ConsequenceRoleSupportResult(
              batch.keySet().stream().map(Observation::getId).toList(), !audit.isEmpty());
        }
      } catch (RuntimeException failure) {
        synchronized (graph) {
          fail(failure);
        }
        throw failure;
      } finally {
        if (registered)
          synchronized (graph) {
            rootAttempt().preparingAttributions--;
          }
      }
    }

    private record AttributionProjection(
        Cohort cohort, Observation producer, List<Observation> persisted, List<String> expected) {}

    private record AttributionMemberInput(
        ObservationImpl member,
        Attribution previous,
        ObservationImpl current,
        Observable observable,
        String originalUrn,
        String originalState,
        String currentState,
        String familiesState,
        List<Concept> families,
        long memberId,
        long transientId) {}

    private record AttributionRequest(
        AttributionMemberInput input,
        MemberClassifierExecutor.PendingAttribution value,
        String originalUrn,
        String semanticState,
        String support,
        String event,
        Concept family,
        Concept predicate,
        Observation requestSource,
        String requestMemberState,
        String requestSourceState,
        String observableState) {}

    private record PreparedAttribution(
        ObservationImpl member, Observable after, List<Concept> families, Set<String> snapshots) {}

    private static String attributionConceptState(Concept concept) {
      return ObservationReadView.fingerprintValue(
          ObservationReadView.copy(concept, new IdentityHashMap<>()), new IdentityHashMap<>());
    }

    /**
     * Semantic RPC inputs must not retain caller-owned mutable concepts, including API wrappers.
     */
    private static Concept attributionConceptSnapshot(Concept concept) {
      if (concept instanceof org.integratedmodelling.common.knowledge.ConceptImpl)
        return (Concept) ObservationReadView.copy(concept, new IdentityHashMap<>());
      var snapshot = new org.integratedmodelling.common.knowledge.ConceptImpl();
      snapshot.setUrn(concept.getUrn());
      snapshot.setNamespace(concept.getNamespace());
      snapshot.setName(concept.getName());
      snapshot.setReferenceName(concept.getReferenceName());
      snapshot.setServiceId(concept.getServiceId());
      snapshot.setAbstract(concept.isAbstract());
      snapshot.setCollective(concept.isCollective());
      snapshot.setQualifier(concept.getQualifier());
      snapshot.setDescriptionType(concept.getDescriptionType());
      var types = EnumSet.noneOf(SemanticType.class);
      if (concept.getType() != null) types.addAll(concept.getType());
      for (var type : SemanticType.values()) if (concept.is(type)) types.add(type);
      snapshot.setType(types);
      if (concept.getMetadata() != null)
        snapshot.setMetadata(
            (Metadata) ObservationReadView.copy(concept.getMetadata(), new IdentityHashMap<>()));
      if (concept.getAnnotations() != null)
        snapshot.setAnnotations(
            (List) ObservationReadView.copy(concept.getAnnotations(), new IdentityHashMap<>()));
      if (concept.getNotifications() != null)
        snapshot.setNotifications(
            (List) ObservationReadView.copy(concept.getNotifications(), new IdentityHashMap<>()));
      return snapshot;
    }

    private static String attributionObservableState(Observable observable) {
      return ObservationReadView.fingerprintValue(
          ObservationReadView.copy(observable, new IdentityHashMap<>()), new IdentityHashMap<>());
    }

    private static String attributionFamiliesState(Attribution previous) {
      return previous == null
          ? ""
          : previous.families().stream()
              .map(TransactionImpl::attributionConceptState)
              .collect(java.util.stream.Collectors.joining(";"));
    }

    private static String attributionRequestState(
        MemberClassifierExecutor.PendingAttribution value) {
      return attributionConceptState(value.abstractPredicate())
          + ":"
          + attributionConceptState(value.predicate())
          + ":role="
          + value.predicate().is(SemanticType.ROLE)
          + ":predicate="
          + value.predicate().is(SemanticType.PREDICATE);
    }

    private void requireAttributionProjection(AttributionProjection projection) {
      if (projection != null
          && !classificationOverlay(
                  projection.cohort(), projection.producer(), projection.persisted())
              .state()
              .equals(projection.expected()))
        throw new IllegalStateException("Classification projected input changed");
    }

    private static void requireAttributionExecution(ContextScope execution) {
      if (execution.isInterrupted() || Thread.currentThread().isInterrupted())
        throw new java.util.concurrent.CancellationException(
            "Classification preparation cancelled");
    }

    /**
     * Semantic services may call back into this graph. Freeze locally, release, then revalidate.
     */
    private ClassificationOverlay prepareAndStageAttributions(
        List<MemberClassifierExecutor.PendingAttribution> pending,
        ContextScope executionScope,
        AttributionProjection projection) {
      beginCycleDriver();
      try {
        return prepareAndStageAttributionsWithCycle(pending, executionScope, projection);
      } finally {
        endCycleDriver();
      }
    }

    private ClassificationOverlay prepareAndStageAttributionsWithCycle(
        List<MemberClassifierExecutor.PendingAttribution> pending,
        ContextScope executionScope,
        AttributionProjection projection) {
      boolean registered = false;
      try {
        requireAttributionExecution(executionScope);
        var requests = new ArrayList<AttributionRequest>();
        var inputs = new LinkedHashMap<Long, AttributionMemberInput>();
        final long capturedStamp;
        synchronized (graph) {
          if (activity.getType() != Activity.Type.CLASSIFICATION)
            throw new IllegalStateException("Attributions require a CLASSIFICATION activity");
          requireOpen();
          if (!failures.isEmpty()) throw new IllegalStateException("Transaction already failed");
          requireAttributionProjection(projection);
          for (var value : List.copyOf(pending)) {
            var requestSource = ObservationReadView.source(value.member());
            var canonical = checkPresentAsset(requestSource);
            if (value.member().getId() != requestSource.getId()
                || requestSource.getId() != canonical.getId())
              throw new IllegalStateException(
                  "Classification request member association changed before preparation");
            var input = inputs.get(value.member().getId());
            if (input == null) {
              if (!(canonical instanceof ObservationImpl member)
                  || member.getId() == 0
                  || member.getId() == -1)
                throw new IllegalArgumentException("Classification requires a registered member");
              var previous = attributions.get(member);
              var current = previous == null ? member : previous.replacement();
              // Use the detached semantic value from an immutable member read projection.
              var observable = ObservationReadView.of(current).getObservable();
              input =
                  new AttributionMemberInput(
                      member,
                      previous,
                      current,
                      observable,
                      member.getObservable().getUrn(),
                      ObservationReadView.fingerprint(member),
                      ObservationReadView.fingerprint(current),
                      attributionFamiliesState(previous),
                      previous == null
                          ? List.of()
                          : previous.families().stream()
                              .map(TransactionImpl::attributionConceptSnapshot)
                              .toList(),
                      member.getId(),
                      member.getTransientId());
              inputs.put(member.getId(), input);
            }
            requests.add(
                new AttributionRequest(
                    input,
                    value,
                    value.originalObservable().getUrn(),
                    attributionRequestState(value),
                    value.support().encode(),
                    value.event().toKey(),
                    attributionConceptSnapshot(value.abstractPredicate()),
                    attributionConceptSnapshot(value.predicate()),
                    requestSource,
                    ObservationReadView.fingerprint(value.member()),
                    ObservationReadView.fingerprint(requestSource),
                    attributionObservableState(value.originalObservable())));
          }
          capturedStamp = classificationMutationStamp[0];
          rootAttempt().preparingAttributions++;
          registered = true;
        }

        // All Reasoner/builder calls, including callbacks that materialize an observer/focus,
        // happen outside the graph monitor. The batch remains invisible until validation below.
        var reasoner = executionScope.getService(Reasoner.class);
        var batch = new LinkedHashMap<ObservationImpl, PreparedAttribution>();
        var audit = new ArrayList<Map<String, Object>>();
        var auditedMembers = new ArrayList<ObservationImpl>();
        for (var request : requests) {
          requireAttributionExecution(executionScope);
          var input = request.input();
          var member = input.member();
          var prepared = batch.get(member);
          var previous = input.previous();
          var currentObservable = prepared != null ? prepared.after() : input.observable();
          var previousSnapshots =
              prepared != null
                  ? prepared.snapshots()
                  : previous == null ? Set.<String>of() : previous.snapshots();
          if (prepared == null && previous == null
              ? !Objects.equals(input.originalUrn(), request.originalUrn())
              : previous != null && !Objects.equals(input.originalUrn(), previous.before())
                  || !previousSnapshots.contains(request.originalUrn()))
            throw new IllegalStateException("Concurrent member classification");
          var families =
              new ArrayList<Concept>(prepared != null ? prepared.families() : input.families());
          for (var family : families)
            if (Objects.equals(family.getUrn(), request.family().getUrn())
                || reasoner.is(family, request.family())
                || reasoner.is(request.family(), family)
                || reasoner.is(request.predicate(), family))
              throw new IllegalStateException(
                  "Repeated classification in the same predicate family");
          var existing = new ArrayList<Concept>(reasoner.directTraits(currentObservable));
          existing.addAll(reasoner.directRoles(currentObservable));
          for (var predicate : existing)
            if (Objects.equals(predicate.getUrn(), request.family().getUrn())
                || reasoner.is(predicate, request.family()))
              throw new IllegalStateException(
                  "Member is already classified in this predicate family");
          var builder = currentObservable.builder(executionScope);
          if (request.predicate().is(SemanticType.ROLE)) builder.withRole(request.predicate());
          else if (request.predicate().is(SemanticType.PREDICATE))
            builder.withTrait(request.predicate());
          else
            throw new IllegalArgumentException("Classification result is neither trait nor role");
          var after = builder.buildObservable();
          if (after == null || !reasoner.satisfiable(after.getSemantics()))
            throw new IllegalArgumentException("Inconsistent attributed observable");
          families.add(request.family());
          var snapshots = new HashSet<>(previousSnapshots);
          snapshots.add(input.originalUrn());
          snapshots.add(after.getUrn());
          batch.put(
              member,
              new PreparedAttribution(member, after, List.copyOf(families), Set.copyOf(snapshots)));
          var item = new LinkedHashMap<String, Object>();
          item.put("memberTransientId", input.transientId());
          item.put("memberId", input.memberId());
          item.put("before", currentObservable.getUrn());
          item.put("after", after.getUrn());
          item.put("abstractPredicate", request.family().getUrn());
          item.put("predicate", request.predicate().getUrn());
          item.put("support", request.support());
          item.put("event", request.event());
          audit.add(item);
          auditedMembers.add(member);
        }
        requireAttributionExecution(executionScope);
        synchronized (graph) {
          requireOpen();
          if (!failures.isEmpty()) throw new IllegalStateException("Transaction already failed");
          if (classificationMutationStamp[0] != capturedStamp)
            throw new IllegalStateException(
                "Classification graph changed during semantic preparation");
          requireAttributionProjection(projection);
          for (var input : inputs.values()) {
            if (checkPresentAsset(input.member()) != input.member()
                || attributions.get(input.member()) != input.previous()
                || !input.originalState().equals(ObservationReadView.fingerprint(input.member()))
                || !input.currentState().equals(ObservationReadView.fingerprint(input.current()))
                || !input.familiesState().equals(attributionFamiliesState(input.previous())))
              throw new IllegalStateException(
                  "Classification input changed during semantic preparation");
          }
          for (var request : requests) {
            var value = request.value();
            if (ObservationReadView.source(value.member()) != request.requestSource()
                || !request
                    .requestMemberState()
                    .equals(ObservationReadView.fingerprint(value.member()))
                || !request
                    .requestSourceState()
                    .equals(ObservationReadView.fingerprint(request.requestSource()))
                || !request
                    .observableState()
                    .equals(attributionObservableState(value.originalObservable()))
                || !Objects.equals(request.originalUrn(), value.originalObservable().getUrn())
                || !request.semanticState().equals(attributionRequestState(value))
                || !request.support().equals(value.support().encode())
                || !request.event().equals(value.event().toKey()))
              throw new IllegalStateException(
                  "Classification request changed during semantic preparation");
          }
          requireAttributionExecution(executionScope);
          var stagedBatch = new LinkedHashMap<Observation, Attribution>();
          for (var prepared : batch.values()) {
            var input = inputs.get(prepared.member().getId());
            var replacement = input.current().copyForAttribution(prepared.after());
            stagedBatch.put(
                prepared.member(),
                new Attribution(
                    prepared.member(),
                    replacement,
                    input.originalUrn(),
                    prepared.families(),
                    prepared.snapshots()));
          }
          int i = 0;
          for (var member : auditedMembers) {
            var attribution = stagedBatch.get(member);
            var item = audit.get(i++);
            var data = new ArrayList<Object>();
            item.forEach(
                (key, value) -> {
                  data.add(key);
                  data.add(value);
                });
            link(
                activity,
                attribution.original(),
                GraphModel.Relationship.CLASSIFIED,
                data.toArray());
            classificationAuditBindings.add(
                new ClassificationAuditBinding(activity, item, attribution.original()));
            if (attribution.original().getId() > 0) modified.add(attribution.original());
          }
          attributions.putAll(stagedBatch);
          var previousAudit = activity.getMetadata().get(Metadata.IM_ATTRIBUTIONS);
          if (previousAudit instanceof List<?> entries) {
            var combined = new ArrayList<Object>(entries);
            combined.addAll(audit);
            activity.getMetadata().put(Metadata.IM_ATTRIBUTIONS, combined);
          } else activity.getMetadata().put(Metadata.IM_ATTRIBUTIONS, audit);
          classificationMutationStamp[0]++;
          rootAttempt().preparingAttributions--;
          registered = false;
          return projection == null
              ? null
              : classificationOverlay(
                  projection.cohort(), projection.producer(), projection.persisted());
        }
      } catch (RuntimeException failure) {
        synchronized (graph) {
          fail(failure);
        }
        throw failure;
      } finally {
        if (registered)
          synchronized (graph) {
            rootAttempt().preparingAttributions--;
          }
      }
    }

    @Override
    public Observation readObservation(Observation observation) {
      synchronized (graph) {
        if (!failures.isEmpty()) throw new IllegalStateException("Transaction already failed");
        return ObservationReadView.of(
            (Observation) staged(checkPresentAsset(ObservationReadView.source(observation))),
            rootAttempt());
      }
    }

    private boolean managedConfigurationParameters(Object[] data) {
      for (int i = 0; i + 1 < data.length; i += 2)
        if ("role".equals(data[i]) && "configuration-input".equals(data[i + 1])) return true;
      return false;
    }

    private boolean managedConfigurationAuthority(
        List<? extends ConfigurationConsequenceRuntime.AuthorityReceipt> authorities) {
      return authorities.stream()
          .anyMatch(
              a ->
                  a instanceof ConfigurationConsequenceRuntime.SourceReceipt source
                          && source.sourceResponseJson() != null
                      || a instanceof ConfigurationConsequenceRuntime.AffectsReceipt);
    }

    /** Stage a same-identity configuration revision without mutating its committed observation. */
    public Observation stageConfiguration(
        ConfigurationConsequenceRuntime.Update update, ContextScope execution, Runnable admission) {
      beginCycleDriver();
      try {
        return stageConfigurationWithCycle(update, execution, admission);
      } finally {
        endCycleDriver();
      }
    }

    /** Pure formatting of immutable guard operands; no read, mutation, coercion or repair. */
    private String configurationPriorStateDetails(
        ConfigurationConsequenceRuntime.Update update,
        ContextScope execution,
        KnowledgeGraph.ConsequenceAssetSnapshot baseline,
        KnowledgeGraph.ConsequenceReadSet nativeRead,
        Observation observation,
        Set<Long> priorParticipants) {
      var facts = new LinkedHashMap<String, Object>();
      facts.put("contextId", execution.getId());
      facts.put("configurationId", observation.getId());
      facts.put("previous", update.previous());
      facts.put("proposedNext", update.next());
      facts.put("capturedNativeRevision", baseline.nativeProperties().get("configurationRevision"));
      facts.put("capturedNativeGeometry", baseline.geometry());
      facts.put(
          "materializedGeometry",
          observation.getGeometry() == null ? null : observation.getGeometry().encode());
      facts.put("strictNativeParticipants", List.copyOf(priorParticipants));
      facts.put(
          "participantsEqual",
          priorParticipants.equals(new TreeSet<>(update.previous().participants())));
      facts.put(
          "supportEqual",
          observation.getGeometry() != null
              && Objects.equals(observation.getGeometry().encode(), update.previous().support()));
      facts.put(
          "rawSupportEqual", Objects.equals(baseline.geometry(), update.previous().support()));
      var types = new ArrayList<String>();
      for (Object id : (List<?>) update.previous().participants())
        types.add(id == null ? "null" : id.getClass().getName());
      facts.put("previousParticipantTypes", types);
      var edges = new ArrayList<Map<String, Object>>();
      for (var edge : nativeRead.edges())
        if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM && edges.size() < 32) {
          var row = new LinkedHashMap<String, Object>();
          row.put("elementId", edge.elementId());
          row.put("sourceId", edge.source().id());
          row.put(
              "sourceType",
              edge.source().id() == null ? null : edge.source().id().getClass().getName());
          row.put("targetId", edge.target().id());
          row.put(
              "targetType",
              edge.target().id() == null ? null : edge.target().id().getClass().getName());
          row.put("strictSourceMatch", Objects.equals(edge.source().id(), observation.getId()));
          row.put("targetIsLong", edge.target().id() instanceof Long);
          edges.add(row);
        }
      facts.put("capturedEmergenceEdges", edges);
      return Utils.Json.asString(facts);
    }

    private void diagnoseConfigurationPriorState(
        ConfigurationConsequenceRuntime.Update update,
        ContextScope execution,
        KnowledgeGraph.ConsequenceAssetSnapshot baseline,
        KnowledgeGraph.ConsequenceReadSet nativeRead,
        Observation observation,
        Set<Long> priorParticipants) {
      if (!Boolean.getBoolean("klab.diagnostics.configurationRevision")) return;
      try {
        // Consequence-scope warnings are buffered; direct local output preserves point evidence.
        org.integratedmodelling.common.logging.Logging.INSTANCE.info(
            "CONFIGURATION_PRIOR_STATE_DIAGNOSTIC "
                + configurationPriorStateDetails(
                    update, execution, baseline, nativeRead, observation, priorParticipants));
      } catch (RuntimeException ignored) {
        /* Diagnostics cannot replace the mandatory invariant. */
      }
    }

    private Observation stageConfigurationWithCycle(
        ConfigurationConsequenceRuntime.Update update, ContextScope execution, Runnable admission) {
      try {
        requireAttributionExecution(execution);
        boolean managedPerspective;
        synchronized (graph) {
          var existing = classificationBaselineMembers.get(update.previous().observationId());
          managedPerspective =
              managedConfigurationAuthority(update.authorities())
                  || existing instanceof Observation observation
                      && Boolean.TRUE.equals(
                          observation.getMetadata().get("klab.configuration.managedInputs"));
        }
        var perspective = managedPerspective ? ConfigurationPerspective.capture(execution) : null;
        synchronized (graph) {
          requireOpen();
          if (execution.getConsequenceCause() == null
              || execution.getConsequenceCause().kind()
                  != org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind
                      .CONFIGURATION_DETECTION
              || activity.getType() != Activity.Type.DETECTION)
            throw new IllegalStateException(
                "Configuration update requires a causal DETECTION transaction");
          var baseline = consequenceBaselines.get(update.previous().observationId());
          var original = classificationBaselineMembers.get(update.previous().observationId());
          if (baseline == null
              || !(original instanceof ObservationImpl observation)
              || observation.getObservable() == null
              || !observation.getObservable().is(SemanticType.CONFIGURATION)
              || configurationRevisions.containsKey(observation)
              || attributions.containsKey(observation))
            throw new IllegalStateException(
                "Configuration lacks an exclusive captured native/local baseline");
          if ((managedConfigurationAuthority(update.authorities())
                  || Boolean.TRUE.equals(
                      observation.getMetadata().get("klab.configuration.managedInputs")))
              && (perspective == null
                  || !ConfigurationPerspective.read(observation).equals(perspective)))
            throw new IllegalStateException(
                "Managed configuration perspective changed before staging");
          var previousRevision = baseline.nativeProperties().get("configurationRevision");
          if ((previousRevision instanceof Number number ? number.longValue() : 0)
              != update.previous().number())
            throw new IllegalStateException(
                "Configuration participant revision changed before staging");
          if (update.next().triggeringCommitId()
              != execution.getConsequenceCause().triggeringCommitId())
            throw new IllegalStateException(
                "Configuration revision has a different triggering commit");
          var nativeRead = mergeConfigurationReads(update.reads());
          var priorParticipants = new TreeSet<Long>();
          for (var edge : nativeRead.edges())
            if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM
                && Objects.equals(edge.source().id(), observation.getId())
                && edge.target().id() instanceof Long id) priorParticipants.add(id);
          // Revisions use the materialized support encoding, as does catalogue capture. The raw
          // native encoding remains pinned and revalidated by the unmodified read-set guard.
          if (!priorParticipants.equals(new TreeSet<>(update.previous().participants()))
              || observation.getGeometry() == null
              || !Objects.equals(observation.getGeometry().encode(), update.previous().support())) {
            diagnoseConfigurationPriorState(
                update, execution, baseline, nativeRead, observation, priorParticipants);
            throw new IllegalStateException(
                "Configuration prior history differs from captured current participants/support");
          }
          if (!nativeRead
              .selections()
              .contains(
                  new KnowledgeGraph.ConsequenceEdgeSelection(
                      observation.getId(),
                      GraphModel.Relationship.EMERGED_FROM,
                      GraphModel.Relationship.Direction.OUTGOING)))
            throw new IllegalStateException("Configuration participant edge set is not captured");
          var replacement = observation.copyForAttribution(observation.getObservable());
          replacement.setGeometry(Geometry.create(update.next().support()));
          replacement
              .getMetadata()
              .put("klab.configuration.current", Utils.Json.asString(update.next()));
          replacement
              .getMetadata()
              .put("klab.configuration.authorities", Utils.Json.asString(update.authorities()));
          if (managedConfigurationAuthority(update.authorities()))
            replacement.getMetadata().put("klab.configuration.managedInputs", true);
          var row =
              Map.of(
                  "previous",
                  update.previous(),
                  "next",
                  update.next(),
                  "authorities",
                  update.authorities());
          var previousRows = activity.getMetadata().get("klab.configuration.revisions");
          var rows = new ArrayList<Object>();
          if (previousRows instanceof List<?> values) rows.addAll(values);
          rows.add(Utils.Json.asString(row));
          activity.getMetadata().put("klab.configuration.revisions", List.copyOf(rows));
          configurationRevisions.put(
              observation,
              new ConfigurationAttribution(observation, replacement, update, nativeRead));
          modified.add(observation);
          link(
              activity,
              observation,
              GraphModel.Relationship.DETECTED,
              "configurationRevision",
              update.next().number());
          // New local edges replace only this configuration's current participant set in getLinks.
          for (var id : update.next().participants()) {
            var participant = classificationBaselineMembers.get(id);
            if (participant == null)
              throw new IllegalStateException(
                  "Configuration participant has no captured canonical baseline");
            link(
                observation,
                participant,
                GraphModel.Relationship.EMERGED_FROM,
                "revision",
                update.next().number());
            if (Boolean.TRUE.equals(
                replacement.getMetadata().get("klab.configuration.managedInputs")))
              link(
                  participant,
                  observation,
                  GraphModel.Relationship.AFFECTS,
                  "role",
                  "configuration-input",
                  "revision",
                  update.next().number());
          }
          if (Boolean.TRUE.equals(
              replacement.getMetadata().get("klab.configuration.managedInputs"))) {
            var rawContext = replacement.getMetadata().get("klab.configuration.applicationContext");
            if (rawContext instanceof Number number
                && number.longValue() > 0
                && !update.next().participants().contains(number.longValue())) {
              var context = classificationBaselineMembers.get(number.longValue());
              if (context == null)
                throw new IllegalStateException(
                    "Configuration application context was not captured");
              link(
                  context,
                  observation,
                  GraphModel.Relationship.AFFECTS,
                  "role",
                  "configuration-input",
                  "revision",
                  update.next().number());
            }
          }
          guardConsequenceCommitAdmission(admission);
          classificationMutationStamp[0]++;
          // Owned mutable tentative output for the scientific executor; ordinary reads stay
          // detached.
          return replacement;
        }
      } catch (RuntimeException failure) {
        fail(failure);
        throw failure;
      }
    }

    public Observation stageConfigurationBirth(
        GuardedConfigurationPublisher.Birth birth,
        ServiceContextScope execution,
        Runnable admission) {
      beginCycleDriver();
      try {
        return stageConfigurationBirthWithCycle(birth, execution, admission);
      } finally {
        endCycleDriver();
      }
    }

    private Observation stageConfigurationBirthWithCycle(
        GuardedConfigurationPublisher.Birth birth,
        ServiceContextScope execution,
        Runnable admission) {
      try {
        requireAttributionExecution(execution);
        var perspective =
            managedConfigurationAuthority(birth.authorities())
                ? ConfigurationPerspective.capture(execution)
                : null;
        synchronized (graph) {
          requireOpen();
          var observation = birth.observation();
          if (activity.getType() != Activity.Type.DETECTION
              || execution.getConsequenceCause() == null
              || execution.getConsequenceCause().kind()
                  != org.integratedmodelling.klab.api.digitaltwin.Consequence.Kind
                      .CONFIGURATION_DETECTION
              || observation.getId() > 0
              || observation.isEmpty()
              || observation.getObservable() == null
              || !observation.getObservable().is(SemanticType.CONFIGURATION)
              || observation.getObservable().isAbstract()
              || observation.getObservable().getSemantics().isCollective())
            throw new IllegalArgumentException(
                "Configuration birth requires admitted concrete detection output");
          var placement = checkPresentAsset(birth.placement());
          var placementRead = consequenceBaselines.get(placement.getId());
          var relation =
              placement instanceof Cohort
                  ? GraphModel.Relationship.HAS_MEMBER
                  : GraphModel.Relationship.HAS_CHILD;
          if (placement.getId() <= 0
              || placementRead == null
              || birth.reads().stream()
                  .noneMatch(
                      read ->
                          read.selections()
                              .contains(
                                  new KnowledgeGraph.ConsequenceEdgeSelection(
                                      placement.getId(),
                                      relation,
                                      GraphModel.Relationship.Direction.OUTGOING))))
            throw new IllegalStateException(
                "Configuration birth lacks complete placement/catalogue absence guard");
          if (placement instanceof Observation bearer
              && (bearer.getObservable() == null
                  || !SemanticType.isEnumerableSubstantial(
                      bearer.getObservable().getSemantics().getType())))
            throw new IllegalArgumentException(
                "Dependent configuration requires its real substantial bearer");
          if (!ConfigurationPerspective.read(observation)
              .equals(ConfigurationPerspective.capture(execution)))
            throw new IllegalStateException(
                "Configuration birth has a different observed perspective");
          long expectedContext =
              placement instanceof Observation
                  ? placement.getId()
                  : ConfigurationPerspective.read(observation).focusId();
          var rawContext = observation.getMetadata().get("klab.configuration.applicationContext");
          long actualContext = rawContext instanceof Number number ? number.longValue() : 0;
          if (rawContext != null
                  && !(rawContext instanceof Byte
                      || rawContext instanceof Short
                      || rawContext instanceof Integer
                      || rawContext instanceof Long)
              || actualContext != expectedContext
              || actualContext > 0
                  && (!consequenceBaselines.containsKey(actualContext)
                      || !classificationBaselineMembers.containsKey(actualContext)))
            throw new IllegalStateException(
                "Configuration application context differs from its guarded actual bearer/focus");
          var matched = new TreeSet<Long>();
          for (var receipt : birth.authorities()) {
            if (receipt instanceof ConfigurationConsequenceRuntime.SourceReceipt source) {
              if (!scope.getId().equals(source.match().contextId())
                  || source.match().observerId() != execution.getConsequenceCause().observerId())
                throw new IllegalStateException(
                    "Configuration birth authority belongs to another context or perspective");
              matched.addAll(source.match().ingredientIds());
            } else if (receipt instanceof ConfigurationConsequenceRuntime.AffectsReceipt affects) {
              var proof =
                  Utils.Json.parseObject(
                      affects.modelPlanJson(),
                      ConfigurationConsequenceRuntime.RebuildAuthority.class);
              if (proof.configurationId() != affects.configurationId())
                throw new IllegalStateException(
                    "Configuration split has a different prior identity");
              for (var match : proof.matches()) {
                if (!scope.getId().equals(match.contextId())
                    || match.observerId() != execution.getConsequenceCause().observerId())
                  throw new IllegalStateException(
                      "Configuration split evidence has another perspective");
                matched.addAll(match.ingredientIds());
              }
            }
          }
          if (!matched.equals(new TreeSet<>(birth.participants())))
            throw new IllegalStateException(
                "Configuration birth participant set differs from source matches");
          for (var id : birth.participants())
            if (!consequenceBaselines.containsKey(id)
                || !classificationBaselineMembers.containsKey(id))
              throw new IllegalStateException(
                  "Configuration birth participant is not canonically captured");
          if (observation.getId() == Observation.UNASSIGNED_ID)
            observation.setId(execution.getNextObservationId());
          if (observation.getId() >= Observation.UNASSIGNED_ID)
            throw new IllegalStateException(
                "Configuration birth requires a valid transaction-local identity");
          if (perspective != null)
            observation
                .getMetadata()
                .put(ConfigurationPerspective.METADATA, Utils.Json.asString(perspective));
          observation.getMetadata().put("klab.configuration.lineage", birth.lineageJson());
          observation
              .getMetadata()
              .put("klab.configuration.authorities", Utils.Json.asString(birth.authorities()));
          if (managedConfigurationAuthority(birth.authorities()))
            observation.getMetadata().put("klab.configuration.managedInputs", true);
          // Parent identity is needed by the still-private explanatory compilation before native
          // close.
          observation.setParentId(placement.getId());
          execution.registerProvisionalObservation(observation);
          add(observation);
          setTarget(observation);
          link(placement, observation, relation);
          link(
              activity, observation, GraphModel.Relationship.DETECTED, "configurationRevision", 1L);
          for (var id : birth.participants()) {
            link(
                observation,
                classificationBaselineMembers.get(id),
                GraphModel.Relationship.EMERGED_FROM,
                "revision",
                1L);
            if (Boolean.TRUE.equals(
                observation.getMetadata().get("klab.configuration.managedInputs")))
              link(
                  classificationBaselineMembers.get(id),
                  observation,
                  GraphModel.Relationship.AFFECTS,
                  "role",
                  "configuration-input",
                  "revision",
                  1L);
          }
          if (Boolean.TRUE.equals(observation.getMetadata().get("klab.configuration.managedInputs"))
              && actualContext > 0
              && !birth.participants().contains(actualContext))
            link(
                classificationBaselineMembers.get(actualContext),
                observation,
                GraphModel.Relationship.AFFECTS,
                "role",
                "configuration-input",
                "revision",
                1L);
          configurationBirths.put(observation, birth);
          beforeCommit(
              () -> {
                if (observation.getId() <= 0)
                  throw new IllegalStateException("Configuration birth was not allocated durably");
                var revision =
                    new ConfigurationConsequenceRuntime.Revision(
                        observation.getId(),
                        1,
                        execution.getConsequenceCause().triggeringCommitId(),
                        birth.participants(),
                        observation.getGeometry().encode());
                observation
                    .getMetadata()
                    .put("klab.configuration.current", Utils.Json.asString(revision));
                activity
                    .getMetadata()
                    .put(
                        "klab.configuration.birth." + observation.getId(),
                        Utils.Json.asString(
                            Map.of(
                                "next",
                                revision,
                                "authorities",
                                birth.authorities(),
                                "lineage",
                                birth.lineageJson(),
                                "catalogues",
                                birth.catalogues())));
                modified.add(observation);
                modified.add(activity);
              });
          guardConsequenceCommitAdmission(admission);
          return observation;
        }
      } catch (RuntimeException failure) {
        fail(failure);
        throw failure;
      }
    }

    private KnowledgeGraph.ConsequenceReadSet mergeConfigurationReads(
        List<KnowledgeGraph.ConsequenceReadSet> reads) {
      var assets = new TreeMap<Long, KnowledgeGraph.ConsequenceAssetSnapshot>();
      var edges = new TreeMap<String, KnowledgeGraph.ConsequenceEdgeSnapshot>();
      // Native capture canonicalizes this closed-world set by anchor/type/direction.
      // Match that representation without discarding any selected neighborhood.
      var selections =
          new TreeSet<KnowledgeGraph.ConsequenceEdgeSelection>(
              Comparator.comparingLong(KnowledgeGraph.ConsequenceEdgeSelection::assetId)
                  .thenComparing(selection -> selection.relationship().name())
                  .thenComparing(selection -> selection.direction().name()));
      for (var read : reads) {
        if (!scope.getId().equals(read.contextId()))
          throw new IllegalStateException("Configuration input context changed");
        for (var asset : read.assets()) {
          var previous = assets.putIfAbsent(asset.id(), asset);
          if (previous != null && !previous.equals(asset))
            throw new IllegalStateException("Conflicting configuration baseline");
        }
        for (var edge : read.edges()) {
          var previous = edges.putIfAbsent(edge.elementId(), edge);
          if (previous != null && !previous.equals(edge))
            throw new IllegalStateException("Conflicting configuration structural evidence");
        }
        selections.addAll(read.selections());
      }
      return new KnowledgeGraph.ConsequenceReadSet(
          scope.getId(),
          List.copyOf(assets.values()),
          List.copyOf(selections),
          List.copyOf(edges.values()));
    }

    @Override
    public Set<Long> configurationParticipantReplacements() {
      synchronized (graph) {
        return java.util.stream.Stream.concat(
                configurationRevisions.keySet().stream(), configurationBirths.keySet().stream())
            .map(Observation::getId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
      }
    }

    private RuntimeAsset staged(RuntimeAsset asset) {
      var configuration = configurationRevisions.get(asset);
      if (configuration != null) return configuration.replacement();
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
      if (!beginCycleCommitDriver()) return -1;
      AutoCloseable membershipLease = null;
      try {
        boolean retain;
        synchronized (graph) {
          retain = parent == null && !modeledCessations.isEmpty();
        }
        if (retain) {
          var cycle = scope.getResolutionCycle();
          if (cycle == null)
            throw new IllegalStateException("Modeled cessation lost its server-owned cycle");
          membershipLease = cycle.retain();
        }
        long committed = commitWithCycle();
        if (parent == null && committed > 0) {
          final List<Runnable> activations;
          synchronized (graph) {
            activations = List.copyOf(afterCommitActions);
            afterCommitActions.clear();
          }
          // Activation may synchronously run a simulation and call services that read this graph.
          // Retain the cycle driver, but never the graph monitor, across these callbacks.
          for (var action : activations) {
            try {
              action.run();
            } catch (Throwable failure) {
              scope.error(failure);
            }
          }
          publishCommittedConsequences();
          propagateCommittedMembership(committed);
        }
        return committed;
      } finally {
        endCycleDriver();
        if (membershipLease != null)
          try {
            membershipLease.close();
          } catch (Exception failure) {
            scope.warn("Cannot release committed membership cycle", failure);
          }
      }
    }

    /**
     * Runs only after commitWithCycle releases its graph monitor, after clock/activation callbacks,
     * and while commit() still owns the source cycle lease.
     */
    private void propagateCommittedMembership(long commitId) {
      if (!committedMembershipChanges.isEmpty()) {
        for (var change : List.copyOf(committedMembershipChanges))
          try {
            var token =
                new CommittedMembershipChange(
                    DigitalTwinImpl.this, this, change, commitId, activity.getId());
            var outcome = getScheduler().membershipChanged(token, scope);
            if (outcome != Scheduler.MembershipPropagationOutcome.APPLIED
                && outcome != Scheduler.MembershipPropagationOutcome.ALREADY_EXECUTED)
              scope.warn(
                  "Committed cohort input propagation is "
                      + outcome
                      + " for cohort "
                      + change.cohortId());
          } catch (RuntimeException failure) {
            scope.warn("Cannot propagate committed cohort membership", failure);
          }
        committedMembershipChanges.clear();
        modeledCessations.clear();
        roleSupportGuards.clear();
      }
    }

    private long commitWithCycle() {
      final List<Runnable> admissions;
      final long stamp;
      synchronized (graph) {
        var root = rootAttempt();
        if (root.attemptState == AttemptState.FAILED) return -1;
        requireOpen();
        if (parent != null || consequenceCommitAdmissions.isEmpty()) return commitLocked();
        if (root.preparingAttributions > 0) {
          failLocked(
              new IllegalStateException(
                  "Cannot admit commit while semantic preparation is active"));
          return -1;
        }
        admissions = List.copyOf(consequenceCommitAdmissions);
        stamp = classificationMutationStamp[0];
        root.preparingAttributions++;
      }
      try {
        // Service checks bind this consequence cycle and must not run under the graph monitor.
        for (var admission : admissions) admission.run();
      } catch (RuntimeException failure) {
        synchronized (graph) {
          rootAttempt().preparingAttributions--;
          failLocked(failure);
        }
        return -1;
      } catch (Error failure) {
        synchronized (graph) {
          rootAttempt().preparingAttributions--;
          failLocked(failure);
        }
        throw failure;
      }
      synchronized (graph) {
        rootAttempt().preparingAttributions--;
        if (rootAttempt().attemptState == AttemptState.FAILED) return -1;
        requireOpen();
        if (classificationMutationStamp[0] != stamp
            || !admissions.equals(consequenceCommitAdmissions)) {
          failLocked(
              new IllegalStateException("Consequence inputs changed during commit admission"));
          return -1;
        }
        return commitLocked();
      }
    }

    private long commitLocked() {
      // Commit owns the monitor through close and publication. A cancellation acquiring it
      // first aborts the attempt; one arriving after durable commit cannot undo that commit.
      synchronized (graph) {
        var root = rootAttempt();
        if (root.attemptState == AttemptState.FAILED) return -1;
        requireOpen();
        if (root.preparingAttributions > 0) {
          failLocked(
              new IllegalStateException(
                  "Cannot commit while classification semantic preparation is active"));
          return -1;
        }
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
        // the graph monitor throughout this check, native validation, durable close and
        // publication.
        for (var guard : List.copyOf(classificationLocalGuards)) guard.run();
        prepareObservationsForStorage(commitId);
        var activeObserver = scope.getObserver();
        var kgTransaction = knowledgeGraph.createTransaction(scope);
        var stored = new ArrayList<RuntimeAsset>();
        var linked = new ArrayList<Triple<Long, Long, String>>();
        try (kgTransaction) {
          try {
            if (!classificationReadGuards.isEmpty()
                || !consequenceReadGuards.isEmpty()
                || !contextCatalogueGuards.isEmpty()) {
              var lockIds = new TreeSet<Long>();
              for (var read : classificationReadGuards)
                for (var member : read.members()) lockIds.add(member.id());
              for (var read : consequenceReadGuards) lockIds.addAll(read.assetIds());
              for (var catalogue : contextCatalogueGuards) lockIds.addAll(catalogue.cohortIds());
              for (var asset : modified)
                if (asset instanceof Observation observation && observation.getId() > 0)
                  lockIds.add(observation.getId());
              if (consequenceReadGuards.isEmpty() && contextCatalogueGuards.isEmpty())
                kgTransaction.lockClassificationInputs(lockIds);
              else kgTransaction.lockConsequenceInputs(lockIds);
              for (var read : List.copyOf(classificationReadGuards))
                kgTransaction.validateClassificationMembers(read);
              for (var read : List.copyOf(consequenceReadGuards))
                kgTransaction.validateConsequenceInputs(read);
              for (var catalogue : List.copyOf(contextCatalogueGuards))
                kgTransaction.validateContextCohortCatalogue(catalogue);
              for (var catalogue : List.copyOf(roleSupportGuards))
                kgTransaction.validateRoleSupportCatalogue(catalogue);
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

              var lifecycleWrites = new ArrayList<KnowledgeGraph.ObservationCessation>();
              for (var plan : modeledCessations)
                lifecycleWrites.addAll(plan.nativeWrites(commitId));
              var lifecyclePublication =
                  lifecycleWrites.isEmpty()
                      ? null
                      : kgTransaction.prepareCessations(lifecycleWrites);
              for (var action : List.copyOf(beforeCommitActions)) action.run();
              if (!journalFactories.isEmpty()) {
                journalFactories.forEach(factory -> schedulerJournal.add(factory.get()));
                activity
                    .getMetadata()
                    .put(
                        org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal.METADATA_KEY,
                        schedulerJournal.stream()
                            .map(j -> Utils.Json.asString(j.committed(commitId)))
                            .toList());
                modified.add(activity);
              }

              var configurationWrites = new ArrayList<KnowledgeGraph.ConfigurationUpdate>();
              for (var birthEntry : configurationBirths.entrySet()) {
                var observation = birthEntry.getKey();
                var birth = birthEntry.getValue();
                var ids = new TreeSet<Long>(birth.participants());
                ids.add(observation.getId());
                if (observation.getMetadata().get("klab.configuration.applicationContext")
                        instanceof Number context
                    && context.longValue() > 0) ids.add(context.longValue());
                var created =
                    kgTransaction.captureConsequenceInputs(
                        ids,
                        List.of(
                            new KnowledgeGraph.ConsequenceEdgeSelection(
                                observation.getId(),
                                GraphModel.Relationship.EMERGED_FROM,
                                GraphModel.Relationship.Direction.OUTGOING),
                            new KnowledgeGraph.ConsequenceEdgeSelection(
                                observation.getId(),
                                GraphModel.Relationship.AFFECTS,
                                GraphModel.Relationship.Direction.INCOMING)));
                var revision =
                    new ConfigurationConsequenceRuntime.Revision(
                        observation.getId(),
                        1,
                        scope.getConsequenceCause().triggeringCommitId(),
                        birth.participants(),
                        observation.getGeometry().encode());
                configurationWrites.add(
                    new KnowledgeGraph.ConfigurationUpdate(
                        observation,
                        created,
                        birth.participants(),
                        1,
                        Utils.Json.asString(
                            Map.of(
                                "next",
                                revision,
                                "authorities",
                                birth.authorities(),
                                "lineage",
                                birth.lineageJson(),
                                "catalogues",
                                birth.catalogues()))));
              }
              for (var configurationUpdate : configurationRevisions.values()) {
                var update = configurationUpdate.update();
                configurationWrites.add(
                    new KnowledgeGraph.ConfigurationUpdate(
                        configurationUpdate.replacement(),
                        configurationUpdate.nativeReads(),
                        update.next().participants(),
                        update.next().number(),
                        Utils.Json.asString(
                            Map.of(
                                "previous",
                                update.previous(),
                                "next",
                                update.next(),
                                "authorities",
                                update.authorities(),
                                "catalogues",
                                update.catalogues()))));
              }
              configurationWrites.sort(
                  Comparator.comparingLong(update -> update.replacement().getId()));
              if (!configurationWrites.isEmpty())
                kgTransaction.updateConfigurations(configurationWrites);

              for (var asset :
                  modified.stream()
                      .sorted(Comparator.comparingLong(RuntimeAsset::getId))
                      .toList()) {
                if (asset == activity && !schedulerJournal.isEmpty()) continue;
                var configurationUpdate = configurationRevisions.get(asset);
                var attribution = attributions.get(asset);
                if (configurationUpdate != null) {
                  // Configuration replacement and immutable history were written before outputs.
                } else if (attribution != null) {
                  if (attribution.original().getId() > 0) {
                    int ownedChildren =
                        classificationChildDeltas.getOrDefault(attribution.original(), 0);
                    if (ownedChildren == 0)
                      kgTransaction.updateSemantics(
                          attribution.replacement(), attribution.before());
                    else
                      kgTransaction.updateSemantics(
                          attribution.replacement(), attribution.before(), ownedChildren);
                  }
                } else kgTransaction.update(asset);
              }

              for (var edge :
                  graph.edgeSet().stream()
                      .sorted(
                          Comparator.comparingInt(
                              edge ->
                                  edge.relationship == GraphModel.Relationship.HAS_CHILD ? 0 : 1))
                      .toList()) {
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
                boolean replacedConfigurationEdge =
                    edge.relationship == GraphModel.Relationship.EMERGED_FROM
                            && java.util.stream.Stream.concat(
                                    configurationRevisions.keySet().stream(),
                                    configurationBirths.keySet().stream())
                                .anyMatch(configuration -> configuration.getId() == source.getId())
                        || edge.relationship == GraphModel.Relationship.AFFECTS
                            && managedConfigurationParameters(relationshipData)
                            && java.util.stream.Stream.concat(
                                    configurationRevisions.keySet().stream(),
                                    configurationBirths.keySet().stream())
                                .anyMatch(configuration -> configuration.getId() == target.getId());
                if (!replacedConfigurationEdge)
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
                } else
                  for (var journal : schedulerJournal) {
                    if (journal.publicationPending()
                        || journal.kind() == Scheduler.Event.Type.EVENT) {
                      kgTransaction.perceive(
                          activeObserver,
                          org.integratedmodelling.klab.api.geometry.Geometry.create(
                              journal.support()));
                      modified.add(activeObserver);
                    }
                  }
              }
              if (lifecyclePublication != null) {
                committedMembershipChanges.addAll(
                    kgTransaction.publishCessations(lifecyclePublication));
                publishedCessations.addAll(lifecycleWrites);
              }
              if (!schedulerJournal.isEmpty()) {
                var snapshot =
                    createCommit(commitId, scope.getUser().getUsername(), stored, modified, linked);
                decorateConfigurationCommit(snapshot);
                decorateCessationCommit(snapshot);
                snapshot.setConsequenceCause(scope.getConsequenceCause());
                var encodedDeltas =
                    activity
                        .getMetadata()
                        .get(
                            org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta
                                .METADATA_KEY);
                var deltas =
                    new ArrayList<
                        org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta>();
                if (encodedDeltas instanceof List<?> values)
                  for (var value : values)
                    deltas.add(
                        Utils.Json.parseObject(
                            value.toString(),
                            org.integratedmodelling.klab.api.digitaltwin.TemporalStateDelta.class));
                activity
                    .getMetadata()
                    .put(
                        org.integratedmodelling.klab.api.digitaltwin.TransitionCommit.METADATA_KEY,
                        Utils.Json.asString(
                            new org.integratedmodelling.klab.api.digitaltwin.TransitionCommit(
                                1,
                                scope.getId(),
                                snapshot,
                                schedulerJournal.stream().map(j -> j.committed(commitId)).toList(),
                                deltas)));
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
        for (var configuration : configurationRevisions.values()) {
          var original = configuration.original();
          var replacement = configuration.replacement();
          original.setGeometry(replacement.getGeometry());
          original.getMetadata().putAll(replacement.getMetadata());
          scope.invalidateObservation(original.getId());
        }
        var commit =
            createCommit(commitId, scope.getUser().getUsername(), stored, modified, linked);
        decorateConfigurationCommit(commit);
        decorateCessationCommit(commit);
        commit.setConsequenceCause(scope.getConsequenceCause());
        commitCache.put(commit.getId(), commit);
        committedChanges = commit;
        committedObserverId = activeObserver == null ? 0 : activeObserver.getId();

        beforeCommitActions.clear();
        consequenceCommitAdmissions.clear();
        consequenceReadGuards.clear();
        contextCatalogueGuards.clear();
        consequenceBaselines.clear();
        classificationAuditBindings.clear();
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

      return ret;
    }

    private void publishCommittedConsequences() {
      if (committedChanges != null && getConsequenceEngine() != null) {
        try {
          getConsequenceEngine()
              .committed(committedChanges, scope, activity.getId(), committedObserverId);
        } catch (RuntimeException failure) {
          // A scheduling failure must never turn a durable commit into a failed submission.
          scope.warn("Cannot enqueue consequences for commit " + committedChanges.getId(), failure);
        }
      }

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
          metadata.putIfAbsent(
              Metadata.IM_CREATION_AGENT,
              scope.getConsequenceCause() == null
                  ? scope.getUser().getUsername()
                  : knowledgeGraph.klab().getName());
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
              Comparator.comparing(
                  candidate -> candidate.getType() == null || !candidate.getType().isSubmission()))
          .findFirst()
          .orElse(null);
    }

    private void prepareActivityForStorage(ActivityImpl storedActivity) {
      // Birth-transaction classifiers legitimately use registered transient member IDs. The
      // exact staged member/edge association, not semantic text, supplies their durable identity.
      for (var binding : classificationAuditBindings)
        if (binding.activity() == storedActivity
            && binding.row().get("memberId") instanceof Number registered
            && registered.longValue() < -1) {
          var member = (Observation) staged(binding.member());
          if (member.getId() <= 0)
            throw new IllegalStateException("Classifier member did not receive a durable identity");
          var edge =
              graph.outgoingEdgesOf(storedActivity).stream()
                  .filter(
                      candidate ->
                          candidate.relationship == GraphModel.Relationship.CLASSIFIED
                              && graph.getEdgeTarget(candidate) == binding.member()
                              && candidate.properties.equals(binding.row()))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Classifier audit differs from its staged member edge"));
          edge.properties.put("memberIdBeforeCommit", registered.longValue());
          edge.properties.put("memberId", member.getId());
          binding.row().put("memberIdBeforeCommit", registered.longValue());
          binding.row().put("memberId", member.getId());
        }
      graph.incomingEdgesOf(storedActivity).stream()
          .filter(edge -> edge.relationship == GraphModel.Relationship.TRIGGERED)
          .map(graph::getEdgeSource)
          .filter(Activity.class::isInstance)
          .map(Activity.class::cast)
          // Preserve the explicit immediate trigger when additional causal ancestors are linked.
          .min(Comparator.comparing(parentActivity -> !Objects.equals(
              parentActivity.getUrn(), storedActivity.getTriggeringActivityUrn())))
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

    private void decorateCessationCommit(CommitImpl commit) {
      commit.setObservationCessations(publishedCessations);
      commit.setCohortMembershipChanges(committedMembershipChanges);
      for (var cessation : publishedCessations)
        commit.getDeletedAssets().add(cessation.observationId());
      for (var change : committedMembershipChanges)
        commit.getModifiedAssets().add(change.cohortId());
    }

    private void decorateConfigurationCommit(CommitImpl commit) {
      for (var configuration : configurationRevisions.values())
        for (var edge : configuration.nativeReads().edges())
          if (edge.relationship() == GraphModel.Relationship.EMERGED_FROM
              && Objects.equals(edge.source().id(), configuration.original().getId())
              && edge.target().id() instanceof Long target
              && !configuration.update().next().participants().contains(target))
            commit
                .getDeletedLinks()
                .add(
                    Triple.of(
                        configuration.original().getId(),
                        target,
                        GraphModel.Relationship.EMERGED_FROM.name()));
      for (var configuration : configurationRevisions.values())
        for (var edge : configuration.nativeReads().edges())
          if (edge.relationship() == GraphModel.Relationship.AFFECTS
              && "configuration-input".equals(edge.properties().get("role"))
              && Objects.equals(edge.target().id(), configuration.original().getId())
              && edge.source().id() instanceof Long source
              && !configuration.update().next().participants().contains(source)
              && !(configuration
                          .replacement()
                          .getMetadata()
                          .get("klab.configuration.applicationContext")
                      instanceof Number context
                  && context.longValue() == source))
            commit
                .getDeletedLinks()
                .add(
                    Triple.of(
                        source,
                        configuration.original().getId(),
                        GraphModel.Relationship.AFFECTS.name()));
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
      beginCycleDriver();
      try {
        return getChildWithCycle(activity, scope, runtimeAssets);
      } finally {
        endCycleDriver();
      }
    }

    private Transaction getChildWithCycle(
        Activity activity, ContextScope scope, Object... runtimeAssets) {
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
              ret.add(
                  value instanceof Geometry g
                      ? g.encode()
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
      Transaction failed;
      synchronized (graph) {
        var root = rootAttempt();
        // Reentrant no-op calls do not own the surrounding commit/rollback cleanup.
        if (root.attemptState == AttemptState.COMMITTED || root.attemptState == AttemptState.FAILED)
          return this;
        failed = failWithCycle(compilationError);
      }
      releaseTerminalCycleIfDrained();
      return failed;
    }

    private Transaction failWithCycle(Throwable compilationError) {
      synchronized (graph) {
        var state = rootAttempt().attemptState;
        if (state == AttemptState.COMMITTED || state == AttemptState.FAILED) return this;
        // Only a reentrant commit callback can observe COMMITTING while holding this monitor.
        // Throwing aborts the surrounding graph transaction instead of clearing its write set.
        if (state == AttemptState.COMMITTING)
          throw new IllegalStateException(
              "Cannot cancel inside a commit callback", compilationError);
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
      ((org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl)
              scope.getConfiguration())
          .setWorldviewCommitment(configuration.getWorldviewCommitment());
    ((org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl) scope.getConfiguration())
        .setGridAlignment(configuration.getGridAlignment());
    if (configuration.getGridAlignment() != null) {
      scope
          .getData()
          .put(
              org.integratedmodelling.klab.api.digitaltwin.GridAlignment.SCOPE_KEY,
              configuration.getGridAlignment());
      for (String warning : configuration.getGridAlignment().emittedWarnings()) scope.warn(warning);
    }
    this.storageManager = new StorageManagerImpl(service, scope);
    this.resolutionCycles = new ResolutionCycles(service, scopeId);
    this.scheduler = new SchedulerImpl(scope, this);
    this.consequenceEngine = new ConsequenceEngine(scope, scopeId);
    if (service
        instanceof org.integratedmodelling.klab.services.runtime.RuntimeService localRuntime)
      localRuntime.installConsequenceHandlers(consequenceEngine, resolutionCycles);
  }

  public ResolutionCycles getResolutionCycles() {
    return resolutionCycles;
  }

  public ConsequenceEngine getConsequenceEngine() {
    return consequenceEngine;
  }

  @Override
  public Transaction transaction(Activity activity, ContextScope scope, Object... runtimeAssets) {
    var context = (ServiceContextScope) scope;
    // Public semantic writes outside submit/advance are explicit roots too. No-Reasoner native
    // transactions stay usable; their optional semantic consequences are explicitly deferred.
    var admission = resolutionCycles.admit(context, true).join();
    try {
      var transaction = new TransactionImpl(activity, admission.scope(), runtimeAssets);
      transaction.cycleAdmission = admission;
      return transaction;
    } catch (Throwable failure) {
      admission.close();
      throw failure;
    }
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
    return new ProvenanceGraph(
        this.knowledgeGraph, context == null ? this.rootScope : context.getRootContextScope());
  }

  @Override
  public Dataflow getDataflowGraph(ContextScope context) {
    return new DataflowGraph(
        this.knowledgeGraph, context == null ? this.rootScope : context.getRootContextScope());
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
    resolutionCycles.close();
    ((SchedulerImpl) scheduler).close();
    consequenceEngine.close();
    resolutionCycles.awaitQuiescence(10, java.util.concurrent.TimeUnit.SECONDS);
    this.knowledgeGraph.deleteContext();
    this.storageManager.clear();
  }

  /** Release process-local resources without deleting a persistent context or its data files. */
  public void shutdown() {
    resolutionCycles.close();
    ((SchedulerImpl) scheduler).close();
    consequenceEngine.close();
    resolutionCycles.awaitQuiescence(10, java.util.concurrent.TimeUnit.SECONDS);
    ((StorageManagerImpl) storageManager).close();
  }
}
