package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;

/** Receiver-owned static proofs for one accepted environment. Caller holds its environment lock.
 * No executable approval or caller authorization is cached. */
final class SourceValidationProofCache {
  private static final com.fasterxml.jackson.databind.ObjectMapper JSON = JacksonConfiguration.newObjectMapper();
  private record Entry(byte[] response, SemanticDependencyWitness proof) {}
  record Hit(SemanticValidationResponse response, SemanticDependencyWitness proof) {}
  private final Map<String, Entry> proofs = new LinkedHashMap<>(32, .75f, true);
  private int bytes;
  private final java.util.concurrent.atomic.LongAdder absent = new java.util.concurrent.atomic.LongAdder();
  private final java.util.concurrent.atomic.LongAdder changed = new java.util.concurrent.atomic.LongAdder();
  private final java.util.concurrent.atomic.LongAdder unproved = new java.util.concurrent.atomic.LongAdder();
  private final java.util.concurrent.atomic.LongAdder stored = new java.util.concurrent.atomic.LongAdder();

  Map<String, Long> metrics() {
    return Map.of("absent", absent.sum(), "changed", changed.sum(), "unproved", unproved.sum(), "stored", stored.sum());
  }

  static String key(SemanticValidationRequest request) {
    if (!(request.document() instanceof KimNamespace) || request.isExecutableModels()
        || request.isDiagnosticsOnly() || request.getExecutionContext() != null
        || request.isClassifierPreparableDependencies()
        || !request.getPreparableMemberQualityDependencies().isEmpty()
        || request.document().getSourceCode() == null || request.document().getSourceCode().isBlank()) return null;
    // Hash the parsed bean as well as exact text: equal text cannot authenticate altered syntax.
    ObjectNode source = JSON.valueToTree(request.document());
    source.remove("lastUpdateTimestamp");
    if (source.get("metadata") instanceof ObjectNode metadata) {
      metadata.remove(ResourcesService.NAMESPACE_ADMISSION_METADATA);
      if (metadata.get("delegate") instanceof ObjectNode values)
        values.remove(ResourcesService.NAMESPACE_ADMISSION_METADATA);
    }
    return SemanticContentFingerprint.sha256(SemanticContentFingerprint.canonical(JSON.convertValue(source, Object.class)));
  }

  Hit get(String key, Function<List<String>, SemanticDependencyWitness> verify) {
    var entry = key == null ? null : proofs.get(key);
    if (entry == null) { absent.increment(); return null; }
    var response = decode(entry.response());
    var current = verify.apply(response.getSemanticObligations());
    if (SemanticDependencyWitness.revalidates(entry.proof(), current)) return new Hit(response, current);
    changed.increment();
    proofs.remove(key); bytes -= entry.response().length;
    return null;
  }

  void put(String key, SemanticValidationResponse response, SemanticDependencyWitness proof) {
    if (key == null || !response.valid() || !response.isSourceSemanticsComplete()
        || proof == null || !proof.completeAt(System.currentTimeMillis())) { unproved.increment(); return; }
    try {
      byte[] encoded = JSON.writeValueAsBytes(response);
      if (encoded.length > 1024 * 1024) return;
      var old = proofs.put(key, new Entry(encoded, proof));
      stored.increment();
      bytes += encoded.length - (old == null ? 0 : old.response().length);
      while (proofs.size() > 256 || bytes > 16 * 1024 * 1024) {
        var oldest = proofs.keySet().iterator().next();
        bytes -= proofs.remove(oldest).response().length;
      }
    } catch (java.io.IOException invalid) { throw new IllegalStateException("Cannot retain source proof", invalid); }
  }

  private static SemanticValidationResponse decode(byte[] bytes) {
    try { return JSON.readValue(bytes, SemanticValidationResponse.class); }
    catch (java.io.IOException invalid) { throw new IllegalStateException("Cannot restore owned source proof", invalid); }
  }

  void clear() { proofs.clear(); bytes = 0; }
}
