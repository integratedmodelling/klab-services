package org.integratedmodelling.klab.api.services.resources.workflow;

import java.io.Serializable;
import java.util.List;

/** Versioned proposal-review transport. Server-owned stage data is separate from client commands. */
public final class ProposalReview {
  private ProposalReview() {}
  public static final int VERSION = 1;
  public enum Operation { SUBMIT, REQUEST_CHANGES, ADVANCE, ACCEPT, REJECT }
  public enum Status { IN_REVIEW, CHANGES_REQUESTED, ACCEPTED, REJECTED }
  public enum CheckKind { IMPORT_CONTEXT, DOCUMENT_SCHEMA, PARSER, REASONER, SCIENTIFIC_REVIEW, APPLICATION, PR_HANDOFF }
  public enum CheckStatus { PASS, FAIL, NOT_RUN, BLOCKED }
  public record Artifact(String attachmentId, String checksum) implements Serializable {}
  public record Candidate(String proposalId, String revisionId, String supersedesRevision,
      Artifact proposal, Artifact ontology, List<String> actionIds, String contextDigest) implements Serializable {
    public Candidate { actionIds = actionIds == null ? List.of() : List.copyOf(actionIds); }
  }
  public record Check(CheckKind kind, CheckStatus status, List<String> messages) implements Serializable {
    public Check { messages = messages == null ? List.of() : List.copyOf(messages); }
  }
  public record Command(int version, Candidate candidate, String rationale,
      BootstrapDossier dossier) implements Serializable {}
  public record StageData(int version, Candidate candidate, Status status, String actor,
      String rationale, List<Check> validation, BootstrapDossier dossier) implements Serializable {
    public StageData { validation = validation == null ? List.of() : List.copyOf(validation); }
  }
  public enum ConceptKind { SUBJECT, QUALITY, PROCESS, RELATIONSHIP, EVENT, ATTRIBUTE, REALM, ORDERING }
  public record Evidence(String id, String source, String locator, String excerpt) implements Serializable {}
  /** Type and ancestry are reported claims until supported by the matching validator result. */
  public record Concept(String id, ConceptKind kind, String expression, String derivedType,
      List<String> ancestry, List<String> evidenceIds, List<String> qualityIds,
      List<String> affects, List<String> creates, List<String> confers,
      String sourceType, String targetType, List<String> bindings) implements Serializable {
    public Concept {
      ancestry = ancestry == null ? List.of() : List.copyOf(ancestry);
      evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
      qualityIds = qualityIds == null ? List.of() : List.copyOf(qualityIds);
      affects = affects == null ? List.of() : List.copyOf(affects);
      creates = creates == null ? List.of() : List.copyOf(creates);
      confers = confers == null ? List.of() : List.copyOf(confers);
      bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }
  }
  public record Question(String id, String text, String intent, List<String> evidenceIds, List<String> conceptIds,
      List<String> observableExpressions, List<String> invalidProbes, List<String> gaps) implements Serializable {
    public Question {
      evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
      conceptIds = conceptIds == null ? List.of() : List.copyOf(conceptIds);
      observableExpressions = observableExpressions == null ? List.of() : List.copyOf(observableExpressions);
      invalidProbes = invalidProbes == null ? List.of() : List.copyOf(invalidProbes);
      gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }
  }
  public record QualityAnalysis(String qualityId, String limitation, List<String> attributeIds,
      List<String> realmIds, List<String> orderingIds, String boundaryOrComparison, String valueStructure,
      String contextAndScope, List<String> evidenceIds, boolean unknownRatherThanCategory) implements Serializable {
    public QualityAnalysis {
      attributeIds = attributeIds == null ? List.of() : List.copyOf(attributeIds);
      realmIds = realmIds == null ? List.of() : List.copyOf(realmIds);
      orderingIds = orderingIds == null ? List.of() : List.copyOf(orderingIds);
      evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
  }
  public record BootstrapDossier(List<Evidence> evidence, List<Concept> concepts,
      List<Question> questions, List<QualityAnalysis> qualityAnalyses,
      List<String> unresolvedSemantics, List<String> coverageShortfalls) implements Serializable {
    public BootstrapDossier {
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
      concepts = concepts == null ? List.of() : List.copyOf(concepts);
      questions = questions == null ? List.of() : List.copyOf(questions);
      qualityAnalyses = qualityAnalyses == null ? List.of() : List.copyOf(qualityAnalyses);
      unresolvedSemantics = unresolvedSemantics == null ? List.of() : List.copyOf(unresolvedSemantics);
      coverageShortfalls = coverageShortfalls == null ? List.of() : List.copyOf(coverageShortfalls);
    }
  }
}
