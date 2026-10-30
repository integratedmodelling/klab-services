package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;
import java.util.List;

/**
 * A checked, virtual selection of existing cohort members. This receipt is not an execution
 * witness: replay must revalidate its native sources, semantic generation and member read set.
 * IDs are durable by the time this receipt leaves the root submission transaction.
 */
public record CollectiveSelectionView(
    int version, String requested, String nativeMembers, List<String> predicates,
    List<String> families, String reasonerId, long knowledgeRevision, long observerId,
    String observerUrn, String geometry, long cohortId, List<Long> sourceIds,
    List<Member> members, List<Long> selectedIds, String scopeId,
    CollectiveSelectionBinding.Context context, String observerClass, String semanticFingerprint, SemanticEvidenceBinding semanticEvidence) implements Serializable {
  public CollectiveSelectionView(int version, String requested, String nativeMembers,
      List<String> predicates, List<String> families, String reasonerId, long knowledgeRevision,
      long observerId, String observerUrn, String geometry, long cohortId, List<Long> sourceIds,
      List<Member> members, List<Long> selectedIds, String scopeId,
      CollectiveSelectionBinding.Context context, String observerClass, String semanticFingerprint) {
    this(version, requested, nativeMembers, predicates, families, reasonerId, knowledgeRevision,
        observerId, observerUrn, geometry, cohortId, sourceIds, members, selectedIds, scopeId,
        context, observerClass, semanticFingerprint, null);
  }
  /** Old serialized receipts and constructors remain readable, but are not certified for replay. */
  public CollectiveSelectionView(int version, String requested, String nativeMembers,
      List<String> predicates, List<String> families, String reasonerId, long knowledgeRevision,
      long observerId, String observerUrn, String geometry, long cohortId, List<Long> sourceIds,
      List<Member> members, List<Long> selectedIds, String scopeId,
      CollectiveSelectionBinding.Context context, String observerClass) {
    this(version, requested, nativeMembers, predicates, families, reasonerId, knowledgeRevision,
        observerId, observerUrn, geometry, cohortId, sourceIds, members, selectedIds, scopeId,
        context, observerClass, null);
  }
  public static final String DATA_KEY = "klab.collective.selection-view";
  public static final String INPUTS_KEY = "klab.collective.selection-inputs";
  public record Member(long id, String observable, String geometry) implements Serializable {}
  public CollectiveSelectionView {
    predicates = List.copyOf(predicates);
    families = List.copyOf(families);
    sourceIds = List.copyOf(sourceIds);
    members = List.copyOf(members);
    selectedIds = List.copyOf(selectedIds);
    if ((version != 1 && version != 2 && version != 3) || requested == null || nativeMembers == null || predicates.isEmpty()
        || predicates.size() != families.size() || reasonerId == null || geometry == null || scopeId == null)
      throw new IllegalArgumentException("Invalid collective selection receipt");
    if (semanticFingerprint != null && ((version != 2 && version != 3) || !SemanticContentFingerprint.valid(semanticFingerprint)))
      throw new IllegalArgumentException("Invalid collective selection semantic fingerprint");
  }
}
