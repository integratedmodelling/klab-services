package org.integratedmodelling.klab.api.services.runtime;

import java.util.List;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.lang.dataflow.DataflowDocument;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * Planned service-side provenance extraction boundary. No implementation is registered yet.
 * Implementations must read a consistent committed context snapshot beginning at provenance(),
 * order root activities by their recorded start time and descend TRIGGERED to HAS_PLAN fragments.
 * Causal dependencies must be honored; ambiguous timestamp ties require additional ordering
 * evidence. Missing information is reported, never reconstructed by running model search. See
 * docs/DATAFLOW.md for staged implementation.
 */
public interface DataflowExtractor {
  enum ExternalReferences {
    REQUIRE_INPUTS,
    INCLUDE_PRODUCERS
  }

  /** Empty roots selects all submissions; snapshot is null to request a new consistent snapshot. */
  record Request(
      String name,
      String version,
      DataflowDocument.Mode mode,
      List<String> rootActivityUrns,
      String snapshot,
      ExternalReferences externalReferences) {
    public Request {
      rootActivityUrns = List.copyOf(rootActivityUrns);
    }
  }

  enum Severity {
    INFO,
    WARNING,
    ERROR
  }

  /** Code is stable for clients; assetUrn identifies the source of an extraction gap when known. */
  record Diagnostic(Severity severity, String code, String assetUrn, String message) {}

  /**
   * Diagnostic draft only, not an executable export. Document may be null when extraction fails.
   * Snapshot identifies what was actually read. Execution validation and packaging are later
   * stages.
   */
  record Draft(DataflowDocument document, String snapshot, List<Diagnostic> diagnostics) {
    public Draft {
      diagnostics = List.copyOf(diagnostics);
    }
  }

  Draft extract(KnowledgeGraph graph, ContextScope scope, Request request);
}
