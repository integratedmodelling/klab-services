package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.review.ProposalEnums.ActionOperation;
import org.integratedmodelling.klab.api.review.ProposalEnums.ActionStatus;
import org.integratedmodelling.klab.api.review.ProposalEnums.FeedbackStance;
import org.integratedmodelling.klab.api.review.ProposalEnums.FeedbackState;
import org.integratedmodelling.klab.api.review.ProposalEnums.FeedbackStatus;
import org.integratedmodelling.klab.api.review.ProposalEnums.GapStatus;
import org.integratedmodelling.klab.api.review.ProposalEnums.IterationState;
import org.integratedmodelling.klab.api.review.ProposalEnums.LifecycleState;

/** Review, revision and apply records shared by all proposal asset types. */
public final class ProposalReview {

  private ProposalReview() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Feedback {
    private FeedbackState state;
    private List<Comment> comments = new ArrayList<>();
    private List<Decision> decisions = new ArrayList<>();
    private List<String> proposedActionIds = new ArrayList<>();
    private List<String> appliedActionIds = new ArrayList<>();
    private Integer lastReviewedIteration;

    public FeedbackState getState() { return state; }
    public void setState(FeedbackState state) { this.state = state; }
    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }
    public List<Decision> getDecisions() { return decisions; }
    public void setDecisions(List<Decision> decisions) { this.decisions = decisions; }
    public List<String> getProposedActionIds() { return proposedActionIds; }
    public void setProposedActionIds(List<String> value) { this.proposedActionIds = value; }
    public List<String> getAppliedActionIds() { return appliedActionIds; }
    public void setAppliedActionIds(List<String> value) { this.appliedActionIds = value; }
    public Integer getLastReviewedIteration() { return lastReviewedIteration; }
    public void setLastReviewedIteration(Integer value) { this.lastReviewedIteration = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Comment {
    private String commentId;
    private String author;
    private int iteration;
    private String proposalRevisionId;
    private String target;
    private String field;
    private String area;
    private FeedbackStance stance;
    private String comment;
    private List<String> evidenceRefs = new ArrayList<>();
    private List<String> affectedAssetIds = new ArrayList<>();
    private String proposedChange;
    private List<String> proposedActionIds = new ArrayList<>();
    private FeedbackStatus status;
    private String resolution;
    private Integer resolvedInIteration;

    public String getCommentId() { return commentId; }
    public void setCommentId(String value) { this.commentId = value; }
    public String getAuthor() { return author; }
    public void setAuthor(String value) { this.author = value; }
    public int getIteration() { return iteration; }
    public void setIteration(int value) { this.iteration = value; }
    public String getProposalRevisionId() { return proposalRevisionId; }
    public void setProposalRevisionId(String value) { this.proposalRevisionId = value; }
    public String getTarget() { return target; }
    public void setTarget(String value) { this.target = value; }
    public String getField() { return field; }
    public void setField(String value) { this.field = value; }
    public String getArea() { return area; }
    public void setArea(String value) { this.area = value; }
    public FeedbackStance getStance() { return stance; }
    public void setStance(FeedbackStance value) { this.stance = value; }
    public String getComment() { return comment; }
    public void setComment(String value) { this.comment = value; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> value) { this.evidenceRefs = value; }
    public List<String> getAffectedAssetIds() { return affectedAssetIds; }
    public void setAffectedAssetIds(List<String> value) { this.affectedAssetIds = value; }
    public String getProposedChange() { return proposedChange; }
    public void setProposedChange(String value) { this.proposedChange = value; }
    public List<String> getProposedActionIds() { return proposedActionIds; }
    public void setProposedActionIds(List<String> value) { this.proposedActionIds = value; }
    public FeedbackStatus getStatus() { return status; }
    public void setStatus(FeedbackStatus value) { this.status = value; }
    public String getResolution() { return resolution; }
    public void setResolution(String value) { this.resolution = value; }
    public Integer getResolvedInIteration() { return resolvedInIteration; }
    public void setResolvedInIteration(Integer value) { this.resolvedInIteration = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Decision {
    private String decisionId;
    private int iteration;
    private String decision;
    private String rationale;
    private List<String> evidenceRefs = new ArrayList<>();
    private List<String> actionIds = new ArrayList<>();

    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String value) { this.decisionId = value; }
    public int getIteration() { return iteration; }
    public void setIteration(int value) { this.iteration = value; }
    public String getDecision() { return decision; }
    public void setDecision(String value) { this.decision = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> value) { this.evidenceRefs = value; }
    public List<String> getActionIds() { return actionIds; }
    public void setActionIds(List<String> value) { this.actionIds = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Lifecycle {
    private LifecycleState state;
    private int introducedInIteration;
    private int lastModifiedInIteration;
    private Integer deletedInIteration;
    private List<String> replacementAssetIds = new ArrayList<>();

    public LifecycleState getState() { return state; }
    public void setState(LifecycleState value) { this.state = value; }
    public int getIntroducedInIteration() { return introducedInIteration; }
    public void setIntroducedInIteration(int value) { this.introducedInIteration = value; }
    public int getLastModifiedInIteration() { return lastModifiedInIteration; }
    public void setLastModifiedInIteration(int value) { this.lastModifiedInIteration = value; }
    public Integer getDeletedInIteration() { return deletedInIteration; }
    public void setDeletedInIteration(Integer value) { this.deletedInIteration = value; }
    public List<String> getReplacementAssetIds() { return replacementAssetIds; }
    public void setReplacementAssetIds(List<String> value) { this.replacementAssetIds = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class NameHistory {
    private String qualifiedName;
    private int fromIteration;
    private int toIteration;
    private String renameActionId;

    public String getQualifiedName() { return qualifiedName; }
    public void setQualifiedName(String value) { this.qualifiedName = value; }
    public int getFromIteration() { return fromIteration; }
    public void setFromIteration(int value) { this.fromIteration = value; }
    public int getToIteration() { return toIteration; }
    public void setToIteration(int value) { this.toIteration = value; }
    public String getRenameActionId() { return renameActionId; }
    public void setRenameActionId(String value) { this.renameActionId = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Action {
    private String actionId;
    private String proposalId;
    private String baseRevisionId;
    private int requestedInIteration;
    private List<String> requestedByFeedbackIds = new ArrayList<>();
    private ActionOperation operation;
    private List<String> targetAssetIds = new ArrayList<>();
    private ActionStatus status;
    private boolean semanticReviewRequired;
    private Map<String, Object> preconditions = new LinkedHashMap<>();
    private Map<String, Object> payload = new LinkedHashMap<>();
    private Map<String, Object> impact = new LinkedHashMap<>();
    private List<String> requiredValidations = new ArrayList<>();
    private Map<String, Object> approval = new LinkedHashMap<>();
    private Map<String, Object> applyResult = new LinkedHashMap<>();

    public String getActionId() { return actionId; }
    public void setActionId(String value) { this.actionId = value; }
    public String getProposalId() { return proposalId; }
    public void setProposalId(String value) { this.proposalId = value; }
    public String getBaseRevisionId() { return baseRevisionId; }
    public void setBaseRevisionId(String value) { this.baseRevisionId = value; }
    public int getRequestedInIteration() { return requestedInIteration; }
    public void setRequestedInIteration(int value) { this.requestedInIteration = value; }
    public List<String> getRequestedByFeedbackIds() { return requestedByFeedbackIds; }
    public void setRequestedByFeedbackIds(List<String> value) { this.requestedByFeedbackIds = value; }
    public ActionOperation getOperation() { return operation; }
    public void setOperation(ActionOperation value) { this.operation = value; }
    public List<String> getTargetAssetIds() { return targetAssetIds; }
    public void setTargetAssetIds(List<String> value) { this.targetAssetIds = value; }
    public ActionStatus getStatus() { return status; }
    public void setStatus(ActionStatus value) { this.status = value; }
    public boolean isSemanticReviewRequired() { return semanticReviewRequired; }
    public void setSemanticReviewRequired(boolean value) { this.semanticReviewRequired = value; }
    public Map<String, Object> getPreconditions() { return preconditions; }
    public void setPreconditions(Map<String, Object> value) { this.preconditions = value; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> value) { this.payload = value; }
    public Map<String, Object> getImpact() { return impact; }
    public void setImpact(Map<String, Object> value) { this.impact = value; }
    public List<String> getRequiredValidations() { return requiredValidations; }
    public void setRequiredValidations(List<String> value) { this.requiredValidations = value; }
    public Map<String, Object> getApproval() { return approval; }
    public void setApproval(Map<String, Object> value) { this.approval = value; }
    public Map<String, Object> getApplyResult() { return applyResult; }
    public void setApplyResult(Map<String, Object> value) { this.applyResult = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Gap {
    private String gapId;
    private String gapType;
    private Integer requiredTier;
    private String requiredConcept;
    private String requestedAlias;
    private String intendedFoundationalMeaning;
    private List<String> intendedScopeUses = new ArrayList<>();
    private List<String> evidenceRefs = new ArrayList<>();
    private String reason;
    private String rationale;
    private GapStatus status;
    private String rootRevisionResolvingGap;

    public String getGapId() { return gapId; }
    public void setGapId(String value) { this.gapId = value; }
    public String getGapType() { return gapType; }
    public void setGapType(String value) { this.gapType = value; }
    public Integer getRequiredTier() { return requiredTier; }
    public void setRequiredTier(Integer value) { this.requiredTier = value; }
    public String getRequiredConcept() { return requiredConcept; }
    public void setRequiredConcept(String value) { this.requiredConcept = value; }
    public String getRequestedAlias() { return requestedAlias; }
    public void setRequestedAlias(String value) { this.requestedAlias = value; }
    public String getIntendedFoundationalMeaning() { return intendedFoundationalMeaning; }
    public void setIntendedFoundationalMeaning(String value) { this.intendedFoundationalMeaning = value; }
    public List<String> getIntendedScopeUses() { return intendedScopeUses; }
    public void setIntendedScopeUses(List<String> value) { this.intendedScopeUses = value; }
    public List<String> getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(List<String> value) { this.evidenceRefs = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }
    public GapStatus getStatus() { return status; }
    public void setStatus(GapStatus value) { this.status = value; }
    public String getRootRevisionResolvingGap() { return rootRevisionResolvingGap; }
    public void setRootRevisionResolvingGap(String value) { this.rootRevisionResolvingGap = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class IterationControl {
    private int currentIteration;
    private String previousRevisionId;
    private int nextIteration;
    private IterationState state;
    private List<String> unresolvedFeedbackIds = new ArrayList<>();
    private List<String> proposedActionIds = new ArrayList<>();
    private List<String> appliedActionIds = new ArrayList<>();

    public int getCurrentIteration() { return currentIteration; }
    public void setCurrentIteration(int value) { this.currentIteration = value; }
    public String getPreviousRevisionId() { return previousRevisionId; }
    public void setPreviousRevisionId(String value) { this.previousRevisionId = value; }
    public int getNextIteration() { return nextIteration; }
    public void setNextIteration(int value) { this.nextIteration = value; }
    public IterationState getState() { return state; }
    public void setState(IterationState value) { this.state = value; }
    public List<String> getUnresolvedFeedbackIds() { return unresolvedFeedbackIds; }
    public void setUnresolvedFeedbackIds(List<String> value) { this.unresolvedFeedbackIds = value; }
    public List<String> getProposedActionIds() { return proposedActionIds; }
    public void setProposedActionIds(List<String> value) { this.proposedActionIds = value; }
    public List<String> getAppliedActionIds() { return appliedActionIds; }
    public void setAppliedActionIds(List<String> value) { this.appliedActionIds = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Validation {
    private boolean schemaValid;
    private boolean referencesResolve;
    private boolean acyclic;
    private boolean requestedTierValid;
    @JsonProperty("mandatory_tier_1_context_present")
    private boolean mandatoryTier1ContextPresent;
    private boolean tierAncestryValid;
    private boolean orthogonalityReviewed;
    private boolean internallyUnambiguous;
    private boolean namedCompositionReviewsComplete;
    private String semanticReview;
    private String syntaxValidation;
    private List<String> warnings = new ArrayList<>();

    public boolean isSchemaValid() { return schemaValid; }
    public void setSchemaValid(boolean value) { this.schemaValid = value; }
    public boolean isReferencesResolve() { return referencesResolve; }
    public void setReferencesResolve(boolean value) { this.referencesResolve = value; }
    public boolean isAcyclic() { return acyclic; }
    public void setAcyclic(boolean value) { this.acyclic = value; }
    public boolean isRequestedTierValid() { return requestedTierValid; }
    public void setRequestedTierValid(boolean value) { this.requestedTierValid = value; }
    public boolean isMandatoryTier1ContextPresent() { return mandatoryTier1ContextPresent; }
    public void setMandatoryTier1ContextPresent(boolean value) { this.mandatoryTier1ContextPresent = value; }
    public boolean isTierAncestryValid() { return tierAncestryValid; }
    public void setTierAncestryValid(boolean value) { this.tierAncestryValid = value; }
    public boolean isOrthogonalityReviewed() { return orthogonalityReviewed; }
    public void setOrthogonalityReviewed(boolean value) { this.orthogonalityReviewed = value; }
    public boolean isInternallyUnambiguous() { return internallyUnambiguous; }
    public void setInternallyUnambiguous(boolean value) { this.internallyUnambiguous = value; }
    public boolean isNamedCompositionReviewsComplete() { return namedCompositionReviewsComplete; }
    public void setNamedCompositionReviewsComplete(boolean value) { this.namedCompositionReviewsComplete = value; }
    public String getSemanticReview() { return semanticReview; }
    public void setSemanticReview(String value) { this.semanticReview = value; }
    public String getSyntaxValidation() { return syntaxValidation; }
    public void setSyntaxValidation(String value) { this.syntaxValidation = value; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> value) { this.warnings = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class OrthogonalityReview {
    private boolean hardUnambiguityGatePassed;
    private boolean pairwiseReviewComplete;
    private List<Map<String, Object>> matrix = new ArrayList<>();
    private List<Object> unresolvedAmbiguities = new ArrayList<>();
    private List<Object> unresolvedOverlaps = new ArrayList<>();

    public boolean isHardUnambiguityGatePassed() { return hardUnambiguityGatePassed; }
    public void setHardUnambiguityGatePassed(boolean value) { this.hardUnambiguityGatePassed = value; }
    public boolean isPairwiseReviewComplete() { return pairwiseReviewComplete; }
    public void setPairwiseReviewComplete(boolean value) { this.pairwiseReviewComplete = value; }
    public List<Map<String, Object>> getMatrix() { return matrix; }
    public void setMatrix(List<Map<String, Object>> value) { this.matrix = value; }
    public List<Object> getUnresolvedAmbiguities() { return unresolvedAmbiguities; }
    public void setUnresolvedAmbiguities(List<Object> value) { this.unresolvedAmbiguities = value; }
    public List<Object> getUnresolvedOverlaps() { return unresolvedOverlaps; }
    public void setUnresolvedOverlaps(List<Object> value) { this.unresolvedOverlaps = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class CorpusFeedback {
    private FeedbackState state;
    private List<Object> coverageComments = new ArrayList<>();
    private List<Object> structuralComments = new ArrayList<>();
    private List<Object> crossCuttingIssues = new ArrayList<>();
    private List<Object> decisions = new ArrayList<>();
    private List<Object> requestedChanges = new ArrayList<>();

    public FeedbackState getState() { return state; }
    public void setState(FeedbackState value) { this.state = value; }
    public List<Object> getCoverageComments() { return coverageComments; }
    public void setCoverageComments(List<Object> value) { this.coverageComments = value; }
    public List<Object> getStructuralComments() { return structuralComments; }
    public void setStructuralComments(List<Object> value) { this.structuralComments = value; }
    public List<Object> getCrossCuttingIssues() { return crossCuttingIssues; }
    public void setCrossCuttingIssues(List<Object> value) { this.crossCuttingIssues = value; }
    public List<Object> getDecisions() { return decisions; }
    public void setDecisions(List<Object> value) { this.decisions = value; }
    public List<Object> getRequestedChanges() { return requestedChanges; }
    public void setRequestedChanges(List<Object> value) { this.requestedChanges = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class ChangeLogEntry {
    private int iteration;
    private String revisionId;
    private String previousRevisionId;
    private List<String> changes = new ArrayList<>();
    private List<String> feedbackResolved = new ArrayList<>();
    private List<String> actionsApplied = new ArrayList<>();

    public int getIteration() { return iteration; }
    public void setIteration(int value) { this.iteration = value; }
    public String getRevisionId() { return revisionId; }
    public void setRevisionId(String value) { this.revisionId = value; }
    public String getPreviousRevisionId() { return previousRevisionId; }
    public void setPreviousRevisionId(String value) { this.previousRevisionId = value; }
    public List<String> getChanges() { return changes; }
    public void setChanges(List<String> value) { this.changes = value; }
    public List<String> getFeedbackResolved() { return feedbackResolved; }
    public void setFeedbackResolved(List<String> value) { this.feedbackResolved = value; }
    public List<String> getActionsApplied() { return actionsApplied; }
    public void setActionsApplied(List<String> value) { this.actionsApplied = value; }
  }
}
