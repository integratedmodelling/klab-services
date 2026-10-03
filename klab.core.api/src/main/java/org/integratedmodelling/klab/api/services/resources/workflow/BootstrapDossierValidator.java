package org.integratedmodelling.klab.api.services.resources.workflow;

import java.util.*;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;

/** Structural checks only: never establishes scientific validity, consensus or parser support. */
public final class BootstrapDossierValidator {
  private BootstrapDossierValidator() {}
  public static void validate(BootstrapDossier dossier) {
    var errors = errors(dossier);
    if (!errors.isEmpty()) throw new KlabIllegalStateException(String.join("; ", errors));
  }
  public static List<String> errors(BootstrapDossier d) {
    var errors = new ArrayList<String>();
    if (d == null) return errors;
    long records = (long) d.evidence().size() + d.concepts().size() + d.questions().size()
        + d.qualityAnalyses().size() + d.unresolvedSemantics().size() + d.coverageShortfalls().size();
    if (records > ProposalReview.MAX_DOSSIER_RECORDS) return List.of("Dossier record count limit exceeded");
    var evidence = ids(safe(d.evidence()).stream().map(Evidence::id).toList(), "evidence", errors);
    var concepts = ids(safe(d.concepts()).stream().map(Concept::id).toList(), "concept", errors);
    ids(safe(d.questions()).stream().map(Question::id).toList(), "question", errors);
    for (var c : safe(d.concepts())) {
      if (c.kind() == null) errors.add("Concept " + c.id() + " has no kind");
      refs(c.evidenceIds(), evidence, "evidence", errors);
      refs(c.qualityIds(), concepts, "quality", errors);
      refs(c.affects(), concepts, "affects", errors);
      refs(c.creates(), concepts, "creates", errors);
      refs(c.confers(), concepts, "confers", errors);
      if (c.kind() == ConceptKind.RELATIONSHIP && (blank(c.sourceType()) || blank(c.targetType())))
        errors.add("Relationship " + c.id() + " needs typed source and target");
    }
    for (var q : safe(d.questions())) {
      if (blank(q.text()) || blank(q.intent())) errors.add("Question " + q.id() + " needs text and precise intent");
      refs(q.evidenceIds(), evidence, "question evidence", errors);
      refs(q.conceptIds(), concepts, "question concept", errors);
    }
    for (var q : safe(d.qualityAnalyses())) {
      if (!concepts.contains(q.qualityId())) errors.add("Unknown analyzed quality " + q.qualityId());
      refs(q.attributeIds(), concepts, "attribute", errors);
      refs(q.realmIds(), concepts, "realm", errors);
      refs(q.orderingIds(), concepts, "ordering", errors);
      refs(q.evidenceIds(), evidence, "quality evidence", errors);
      if (q.unknownRatherThanCategory() && (!safe(q.attributeIds()).isEmpty() || !safe(q.realmIds()).isEmpty()
          || !safe(q.orderingIds()).isEmpty())) errors.add("Unknown information must not be minted as a domain category");
    }
    return List.copyOf(errors);
  }
  /** Coverage counts are diagnostics. Missing targets require explanation, never fabricated entries. */
  public static Map<String, Integer> coverage(BootstrapDossier d) {
    var counts = new LinkedHashMap<String, Integer>();
    for (var kind : ConceptKind.values()) counts.put(kind.name(), 0);
    if (d != null) for (var c : safe(d.concepts())) if (c.kind() != null) counts.computeIfPresent(c.kind().name(), (k,v) -> v + 1);
    counts.put("QUESTIONS", d == null ? 0 : safe(d.questions()).size());
    return Collections.unmodifiableMap(counts);
  }
  private static Set<String> ids(List<String> values, String kind, List<String> errors) {
    var ids = new HashSet<String>();
    for (var id : values) if (blank(id) || !ids.add(id)) errors.add("Missing or duplicate " + kind + " ID: " + id);
    return ids;
  }
  private static void refs(List<String> refs, Set<String> ids, String kind, List<String> errors) {
    for (String ref : safe(refs)) if (!ids.contains(ref)) errors.add("Unknown " + kind + " reference: " + ref);
  }
  private static boolean blank(String value) { return value == null || value.isBlank(); }
  private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
