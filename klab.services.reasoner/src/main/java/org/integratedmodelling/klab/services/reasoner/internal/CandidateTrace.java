package org.integratedmodelling.klab.services.reasoner.internal;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.logging.Logging;

/** Opt-in, lossy diagnostics only. Never enters service discovery or changes candidate state. */
public final class CandidateTrace {
  public static final String PROPERTY = "klab.reasoner.traceCandidate";
  public enum Event {
    DISCOVERY, LOAD_REQUEST, SAVED_SOURCE_REPAIR, UPDATE_BATCH, PREFLIGHT_ALLOWED,
    FIELD_SOURCES, FIELD_ORDER, FIELD_DEPENDENCY, FIELD_CONFIGURATION, FIELD_CATALOG,
    FIELD_SELECTION, FIELD_BINDINGS, FIELD_OWNER, SUBMITTED, INVALIDATED, REJECTED,
    JOB_START, JOB_END, GUARD, BOOTSTRAP_ENTER, CACHE_HIT, CACHE_REUSE, PRODUCER_START,
    COMPONENTS_LOADED, DESCRIPTORS_VERIFIED, BEFORE_PRIVATE_LOAD, AFTER_PRIVATE_LOAD,
    CAPTURE_COMPLETE, PRODUCER_DISPOSE, FIELD_PROVIDER_INPUT, FIELD_PLAN,
    CHECKPOINT_CANCELLED, BOOTSTRAP_FAILURE, BOOTSTRAP_INVALIDATED, PHASE_START, PHASE_END, ENV_CREATED
  }
  public enum Phase {
    INPUT_PREFLIGHT, INPUT_CAPTURE, SOURCE_SUPPORT, PROVIDER_PREPARE, POSTCOMPILE_PROVIDER_VERIFY,
    CORE_MATERIALIZATION, CODELIST_MATERIALIZATION, ACTOR_CREATE, ACCEPTED_ACTOR_LOAD, BOOTSTRAP_LOAD,
    OWL_INITIALIZE, DECLARATION_REGISTRY, NAMED_DECLARATIONS, AUTHORITY_CONFIGURATION,
    DECLARATION_STRUCTURE, DOMAIN_BINDING, AUTHORED_CLAUSES, DESCRIPTION_RECONCILE, ONTOLOGY_REGISTER,
    LOAD_FLUSH, STRATEGIES, LOAD_CONSISTENCY, DOCUMENT_VALIDATE, FINAL_FLUSH,
    FINAL_CONSISTENCY, FINAL_CLASSIFY, PUBLICATION_GUARD, PRIVATE_DISPOSE, ASSET_REMOVE
  }
  public enum Reason {
    NONE, SERVICE, USER, SAME_KEY_RETRY, NEW_KEY, PREFLIGHT_EXCEPTION, EMPTY_INPUT,
    UPDATE_REJECTED, EXPLICIT, SHUTDOWN, SUCCESS, FAILURE, FAILED, CANCELLED, STALE_JOB,
    EXACT_SUCCESS, EXACT_FAILURE, PROVIDER_REUSE, POST_COMPONENTS, POST_PRIVATE_LOAD,
    POST_PRODUCER, PREPARE_ENTRY, VALID, INVALID, UNRESOLVED, UNAVAILABLE, NOT_RUN, PENDING
  }
  // Loading the disabled facade must not allocate trace buffers or per-thread state.
  private static final class State {
    private static final String BOOT = java.util.UUID.randomUUID().toString();
    private static final ArrayBlockingQueue<String> QUEUE = new ArrayBlockingQueue<>(512);
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final ThreadLocal<Long> JOB = new ThreadLocal<>();
    private static volatile boolean started;
  }
  private CandidateTrace() {}
  public static boolean enabled() { return Boolean.getBoolean(PROPERTY); }
  static void job(long generation) { if (enabled()) State.JOB.set(generation); }
  static void clearJob() { if (enabled()) State.JOB.remove(); }
  static long job() { if (!enabled()) return -1; var value=State.JOB.get(); return value==null ? -1 : value; }

  /** Disabled calls allocate no span and do not touch the lazy trace state. */
  public static PhaseSpan phase(Phase phase, String key) {
    if (!enabled()) return null;
    return phaseAt(phase, key, job());
  }
  static PhaseSpan phaseAt(Phase phase, String key, long generation) {
    if (!enabled()) return null;
    try { return new PhaseSpan(phase, key==null ? null : safeHash(key), generation); }
    catch (Throwable ignored) { return null; }
  }
  /** End means the scope exited, including exceptional exit; JOB_END records the outcome. */
  public static final class PhaseSpan implements AutoCloseable {
    private final Phase phase;
    private final String key;
    private final long generation;
    private final long started;
    private boolean closed;
    private PhaseSpan(Phase phase, String key, long generation) {
      this.phase = phase; this.key = key; this.generation = generation;
      this.started = System.nanoTime();
      enqueue(formatPhase(Event.PHASE_START,phase,key,generation,0));
    }
    @Override public void close() {
      if (closed) return;
      closed = true;
      try {
        enqueue(formatPhase(Event.PHASE_END,phase,key,generation,System.nanoTime()-started));
      } catch (Throwable ignored) { /* Diagnostics must not replace a compiler/cleanup failure. */ }
    }
  }
  static String formatPhase(Event event,Phase phase,String key,long generation,long elapsedNanos) {
    return format(event,generation,-1,-1,key,null,Reason.NONE) + " phase=" + phase.name()
        + (event==Event.PHASE_END ? " elapsedNanos=" + Math.max(0,elapsedNanos) : "");
  }
  /** Observe cancellation once and preserve its identity; no success-path trace allocation. */
  public static void checkpoint(CandidatePublicationCoordinator.Cancellation cancellation, Phase phase) {
    try { cancellation.checkpoint(); }
    catch (java.util.concurrent.CancellationException cancelled) {
      if (enabled()) try {
        enqueue(format(Event.CHECKPOINT_CANCELLED,job(),-1,-1,null,null,Reason.CANCELLED)
            + " phase=" + phase.name());
      } catch (Throwable ignored) { /* Keep the original cancellation. */ }
      throw cancelled;
    }
  }

  /** Hash canonical data before it crosses the logging boundary; no raw values are logged. */
  public static String digest(Object value) {
    if (value == null) return "-";
    try {
      byte[] bytes = JacksonConfiguration.newObjectMapper()
          .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsBytes(value);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Throwable unavailable) { return "unavailable"; }
  }
  private static String safeHash(String value) {
    return value == null ? "-" : value.matches("[a-fA-F0-9]{64}") ? value : digest(value);
  }
  static String format(Event event,long generation,long current,long epoch,
      String key,String related,Reason reason) {
    return "CANDIDATE_TRACE v=1 boot="+State.BOOT+" at="+java.time.Instant.now()
        +" event="+event.name()+" generation="+generation+" current="+current
        +" epoch="+epoch+" key="+safeHash(key)+" related="+safeHash(related)
        +" reason="+reason.name();
  }
  public static void emit(Event event,long generation,long current,long epoch,
      String key,String related,Reason reason) {
    if (!enabled()) return;
    enqueue(format(event,generation,current,epoch,key,related,reason));
  }
  static void completed(long generation,CandidatePublicationCoordinator.Status status,Reason reason) {
    if (!enabled()) return;
    enqueue(format(Event.JOB_END,generation,status.inputGeneration(),-1,null,null,reason)
        +" accepted="+status.acceptedGeneration()+" logical="+status.logicalStatus().name()
        +" bean="+status.beanStatus().name());
  }
  private static void enqueue(String line) {
    try {
      start();
      if (!State.QUEUE.offer(line)) State.DROPPED.incrementAndGet();
    } catch (Throwable ignored) { /* Diagnostics never affect runtime outcomes. */ }
  }
  static void stage(Event event,String key,long epoch,Reason reason) {
    if (enabled()) emit(event,job(),-1,epoch,key,null,reason);
  }
  /** Calls the original checkpoint exactly once, preserving its exception object. */
  static void checkpoint(CandidatePublicationCoordinator.Cancellation cancellation,
      String key,long epoch,Reason reason) {
    try { cancellation.checkpoint(); }
    catch (java.util.concurrent.CancellationException cancelled) {
      stage(Event.CHECKPOINT_CANCELLED,key,epoch,reason); throw cancelled;
    }
  }
  private static synchronized void start() {
    if (State.started) return;
    var worker=new Thread(()->{
      while (true) {
        try {
          String line=State.QUEUE.take();
          long dropped=State.DROPPED.getAndSet(0);
          Logging.INSTANCE.info(line+(dropped==0 ? "" : " dropped="+dropped));
        } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
        catch (Throwable ignored) { /* Lossy diagnostics; no producer callback. */ }
      }
    },"candidate-trace-writer");
    worker.setDaemon(true); worker.start(); State.started=true;
  }
}
