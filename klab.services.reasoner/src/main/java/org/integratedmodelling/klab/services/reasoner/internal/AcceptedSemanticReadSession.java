package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Worldview;

/** Pins a complete accepted actor for a read sequence; never reaches the deployment actor. */
public final class AcceptedSemanticReadSession implements AutoCloseable {
  private final CandidatePublicationCoordinator<Worldview, SemanticEnvironment>.Lease lease;
  private final SemanticEnvironment environment;
  private final AtomicBoolean closed = new AtomicBoolean();

  private AcceptedSemanticReadSession(
      CandidatePublicationCoordinator<Worldview, SemanticEnvironment>.Lease lease) {
    this.lease = lease;
    this.environment = lease.state();
  }

  public static AcceptedSemanticReadSession open(
      CandidatePublicationCoordinator<Worldview, SemanticEnvironment> coordinator) {
    return new AcceptedSemanticReadSession(coordinator.accepted().orElseThrow(
        () -> new IllegalStateException("Accepted semantic knowledge is unavailable")));
  }

  public long generation() { checkOpen(); return lease.generation(); }
  public String semanticContentFingerprint() { checkOpen(); return environment.semanticContentFingerprint(); }

  /** Returns an owned transport bean; clients cannot modify accepted caches or metadata. */
  public Concept resolveConcept(String definition) {
    synchronized (environment) {
      checkOpen(); return copy(environment.actor().resolveConcept(definition));
    }
  }

  public boolean satisfiable(Concept supplied) {
    synchronized (environment) {
      checkOpen(); return environment.actor().satisfiable(rebind(supplied));
    }
  }

  public boolean is(Concept supplied, Concept parent) {
    synchronized (environment) {
      checkOpen(); return environment.actor().is(rebind(supplied), rebind(parent));
    }
  }

  public List<Concept> parents(Concept supplied) {
    synchronized (environment) {
      checkOpen(); return environment.actor().parents(rebind(supplied)).stream()
          .map(AcceptedSemanticReadSession::copy).toList();
    }
  }

  private Concept rebind(Concept supplied) {
    if (supplied == null || supplied.getUrn() == null)
      throw new IllegalArgumentException("A source-bound semantic identity is required");
    var owned = environment.actor().resolveConcept(supplied.getUrn());
    if (owned == null || owned.is(org.integratedmodelling.klab.api.knowledge.SemanticType.NOTHING))
      throw new IllegalArgumentException(
        "Semantic identity is unavailable in this accepted generation: " + supplied.getUrn());
    return owned;
  }

  private static Concept copy(Concept value) {
    return value == null ? null : JacksonConfiguration.newObjectMapper().convertValue(value, Concept.class);
  }

  private void checkOpen() {
    if (closed.get()) throw new IllegalStateException("Accepted semantic read session is closed");
  }

  @Override public void close() {
    // Use the same actor-level monitor as reads, so close cannot dispose during an operation.
    if (closed.get()) return;
    synchronized (environment) {
      if (closed.compareAndSet(false, true)) lease.close();
    }
  }
}
