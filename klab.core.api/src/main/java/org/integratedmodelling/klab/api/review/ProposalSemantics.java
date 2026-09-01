package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.review.ProposalEnums.AggregationBehavior;
import org.integratedmodelling.klab.api.review.ProposalEnums.AlignmentAction;
import org.integratedmodelling.klab.api.review.ProposalEnums.AlignmentRelation;
import org.integratedmodelling.klab.api.review.ProposalEnums.AmbiguityStatus;
import org.integratedmodelling.klab.api.review.ProposalEnums.AncestryStatus;
import org.integratedmodelling.klab.api.review.ProposalEnums.CompositionOutcome;
import org.integratedmodelling.klab.api.review.ProposalEnums.Dependence;
import org.integratedmodelling.klab.api.review.ProposalEnums.DiscourseUse;
import org.integratedmodelling.klab.api.review.ProposalEnums.ObservableKind;
import org.integratedmodelling.klab.api.review.ProposalEnums.OrthogonalityRelationType;
import org.integratedmodelling.klab.api.review.ProposalEnums.Perspective;
import org.integratedmodelling.klab.api.review.ProposalEnums.ReferenceKind;
import org.integratedmodelling.klab.api.review.ProposalEnums.SalienceLevel;
import org.integratedmodelling.klab.api.review.ProposalEnums.SemanticFlag;
import org.integratedmodelling.klab.api.review.ProposalEnums.ValueKind;

/** Typed semantic structures used by concept proposal assets. */
public final class ProposalSemantics {

  private ProposalSemantics() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Boundaries {
    private List<String> includes = new ArrayList<>();
    private List<String> excludes = new ArrayList<>();
    private List<String> examples = new ArrayList<>();
    private List<String> counterexamples = new ArrayList<>();

    public List<String> getIncludes() { return includes; }
    public void setIncludes(List<String> value) { this.includes = value; }
    public List<String> getExcludes() { return excludes; }
    public void setExcludes(List<String> value) { this.excludes = value; }
    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> value) { this.examples = value; }
    public List<String> getCounterexamples() { return counterexamples; }
    public void setCounterexamples(List<String> value) { this.counterexamples = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class SemanticCoordinates {
    private ObservableKind kind;
    private List<SemanticFlag> flags = new ArrayList<>();
    private Perspective perspective;
    private Dependence dependence;
    /** Integer, the string {@code >=1}, or null as defined by the schema. */
    private Object arity;
    private Boolean countable;
    private List<String> bearerTypes = new ArrayList<>();
    private List<String> participantTypes = new ArrayList<>();
    private String relationshipSource;
    private String relationshipTarget;
    private ValueKind valueKind;
    private AggregationBehavior aggregationBehavior;

    public ObservableKind getKind() { return kind; }
    public void setKind(ObservableKind value) { this.kind = value; }
    public List<SemanticFlag> getFlags() { return flags; }
    public void setFlags(List<SemanticFlag> value) { this.flags = value; }
    public Perspective getPerspective() { return perspective; }
    public void setPerspective(Perspective value) { this.perspective = value; }
    public Dependence getDependence() { return dependence; }
    public void setDependence(Dependence value) { this.dependence = value; }
    public Object getArity() { return arity; }
    public void setArity(Object value) { this.arity = value; }
    public Boolean getCountable() { return countable; }
    public void setCountable(Boolean value) { this.countable = value; }
    public List<String> getBearerTypes() { return bearerTypes; }
    public void setBearerTypes(List<String> value) { this.bearerTypes = value; }
    public List<String> getParticipantTypes() { return participantTypes; }
    public void setParticipantTypes(List<String> value) { this.participantTypes = value; }
    public String getRelationshipSource() { return relationshipSource; }
    public void setRelationshipSource(String value) { this.relationshipSource = value; }
    public String getRelationshipTarget() { return relationshipTarget; }
    public void setRelationshipTarget(String value) { this.relationshipTarget = value; }
    public ValueKind getValueKind() { return valueKind; }
    public void setValueKind(ValueKind value) { this.valueKind = value; }
    public AggregationBehavior getAggregationBehavior() { return aggregationBehavior; }
    public void setAggregationBehavior(AggregationBehavior value) { this.aggregationBehavior = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class TypeInheritance {
    private String declarationKeyword;
    @JsonProperty("odo_im_chain")
    private String odoImChain;
    @JsonProperty("explicit_odo_derivation_required")
    private boolean explicitOdoDerivationRequired;
    private String rationale;

    public String getDeclarationKeyword() { return declarationKeyword; }
    public void setDeclarationKeyword(String value) { this.declarationKeyword = value; }
    public String getOdoImChain() { return odoImChain; }
    public void setOdoImChain(String value) { this.odoImChain = value; }
    public boolean isExplicitOdoDerivationRequired() { return explicitOdoDerivationRequired; }
    public void setExplicitOdoDerivationRequired(boolean value) { this.explicitOdoDerivationRequired = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Ambiguity {
    private AmbiguityStatus status;
    private List<String> sourceSenses = new ArrayList<>();
    private String selectedSense;
    private List<String> excludedSenses = new ArrayList<>();
    private String resolutionRationale;

    public AmbiguityStatus getStatus() { return status; }
    public void setStatus(AmbiguityStatus value) { this.status = value; }
    public List<String> getSourceSenses() { return sourceSenses; }
    public void setSourceSenses(List<String> value) { this.sourceSenses = value; }
    public String getSelectedSense() { return selectedSense; }
    public void setSelectedSense(String value) { this.selectedSense = value; }
    public List<String> getExcludedSenses() { return excludedSenses; }
    public void setExcludedSenses(List<String> value) { this.excludedSenses = value; }
    public String getResolutionRationale() { return resolutionRationale; }
    public void setResolutionRationale(String value) { this.resolutionRationale = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class OrthogonalityRelation {
    private String otherAssetId;
    private OrthogonalityRelationType relation;
    @JsonProperty("a_varies_with_b_fixed")
    private Boolean aVariesWithBFixed;
    @JsonProperty("b_varies_with_a_fixed")
    private Boolean bVariesWithAFixed;
    private String rationale;
    private String requiredAction;

    public String getOtherAssetId() { return otherAssetId; }
    public void setOtherAssetId(String value) { this.otherAssetId = value; }
    public OrthogonalityRelationType getRelation() { return relation; }
    public void setRelation(OrthogonalityRelationType value) { this.relation = value; }
    public Boolean getAVariesWithBFixed() { return aVariesWithBFixed; }
    public void setAVariesWithBFixed(Boolean value) { this.aVariesWithBFixed = value; }
    public Boolean getBVariesWithAFixed() { return bVariesWithAFixed; }
    public void setBVariesWithAFixed(Boolean value) { this.bVariesWithAFixed = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }
    public String getRequiredAction() { return requiredAction; }
    public void setRequiredAction(String value) { this.requiredAction = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Orthogonality {
    private boolean atomicDimension;
    private List<String> compositeOf = new ArrayList<>();
    private List<String> independentlyVariableFrom = new ArrayList<>();
    private List<OrthogonalityRelation> relations = new ArrayList<>();
    private String assessment;

    public boolean isAtomicDimension() { return atomicDimension; }
    public void setAtomicDimension(boolean value) { this.atomicDimension = value; }
    public List<String> getCompositeOf() { return compositeOf; }
    public void setCompositeOf(List<String> value) { this.compositeOf = value; }
    public List<String> getIndependentlyVariableFrom() { return independentlyVariableFrom; }
    public void setIndependentlyVariableFrom(List<String> value) { this.independentlyVariableFrom = value; }
    public List<OrthogonalityRelation> getRelations() { return relations; }
    public void setRelations(List<OrthogonalityRelation> value) { this.relations = value; }
    public String getAssessment() { return assessment; }
    public void setAssessment(String value) { this.assessment = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class NamedComposition {
    private boolean applicable;
    private String domainTerm;
    private String articulatedExpression;
    private String namingRationale;
    private Salience salience;
    private boolean equalsVsIsReviewRequired;
    private CompositionOutcome proposedOutcome;
    private EquivalenceAssessment equivalenceAssessment;
    private Object reviewerDecision;

    public boolean isApplicable() { return applicable; }
    public void setApplicable(boolean value) { this.applicable = value; }
    public String getDomainTerm() { return domainTerm; }
    public void setDomainTerm(String value) { this.domainTerm = value; }
    public String getArticulatedExpression() { return articulatedExpression; }
    public void setArticulatedExpression(String value) { this.articulatedExpression = value; }
    public String getNamingRationale() { return namingRationale; }
    public void setNamingRationale(String value) { this.namingRationale = value; }
    public Salience getSalience() { return salience; }
    public void setSalience(Salience value) { this.salience = value; }
    public boolean isEqualsVsIsReviewRequired() { return equalsVsIsReviewRequired; }
    public void setEqualsVsIsReviewRequired(boolean value) { this.equalsVsIsReviewRequired = value; }
    public CompositionOutcome getProposedOutcome() { return proposedOutcome; }
    public void setProposedOutcome(CompositionOutcome value) { this.proposedOutcome = value; }
    public EquivalenceAssessment getEquivalenceAssessment() { return equivalenceAssessment; }
    public void setEquivalenceAssessment(EquivalenceAssessment value) { this.equivalenceAssessment = value; }
    public Object getReviewerDecision() { return reviewerDecision; }
    public void setReviewerDecision(Object value) { this.reviewerDecision = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Salience {
    private SalienceLevel level;
    private List<String> citationEvidenceRefs = new ArrayList<>();
    private Integer occurrenceCount;
    private Integer independentSourceCount;
    private List<String> canonicalDefinitionRefs = new ArrayList<>();
    private List<DiscourseUse> discourseUses = new ArrayList<>();

    public SalienceLevel getLevel() { return level; }
    public void setLevel(SalienceLevel value) { this.level = value; }
    public List<String> getCitationEvidenceRefs() { return citationEvidenceRefs; }
    public void setCitationEvidenceRefs(List<String> value) { this.citationEvidenceRefs = value; }
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer value) { this.occurrenceCount = value; }
    public Integer getIndependentSourceCount() { return independentSourceCount; }
    public void setIndependentSourceCount(Integer value) { this.independentSourceCount = value; }
    public List<String> getCanonicalDefinitionRefs() { return canonicalDefinitionRefs; }
    public void setCanonicalDefinitionRefs(List<String> value) { this.canonicalDefinitionRefs = value; }
    public List<DiscourseUse> getDiscourseUses() { return discourseUses; }
    public void setDiscourseUses(List<DiscourseUse> value) { this.discourseUses = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class EquivalenceAssessment {
    private Boolean sameIntension;
    private Boolean sameExtension;
    private Boolean samePerspectiveAndCategory;
    private Boolean sameContextAndDependence;
    private Boolean sameValueSpaceAndPredicates;
    private String rationale;

    public Boolean getSameIntension() { return sameIntension; }
    public void setSameIntension(Boolean value) { this.sameIntension = value; }
    public Boolean getSameExtension() { return sameExtension; }
    public void setSameExtension(Boolean value) { this.sameExtension = value; }
    public Boolean getSamePerspectiveAndCategory() { return samePerspectiveAndCategory; }
    public void setSamePerspectiveAndCategory(Boolean value) { this.samePerspectiveAndCategory = value; }
    public Boolean getSameContextAndDependence() { return sameContextAndDependence; }
    public void setSameContextAndDependence(Boolean value) { this.sameContextAndDependence = value; }
    public Boolean getSameValueSpaceAndPredicates() { return sameValueSpaceAndPredicates; }
    public void setSameValueSpaceAndPredicates(Boolean value) { this.sameValueSpaceAndPredicates = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class AlignmentCandidate {
    private String concept;
    private AlignmentRelation relation;
    private double score;
    private String rationale;

    public String getConcept() { return concept; }
    public void setConcept(String value) { this.concept = value; }
    public AlignmentRelation getRelation() { return relation; }
    public void setRelation(AlignmentRelation value) { this.relation = value; }
    public double getScore() { return score; }
    public void setScore(double value) { this.score = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class AncestryReference {
    private ReferenceKind refKind;
    private String assetId;
    private String qualifiedName;

    public ReferenceKind getRefKind() { return refKind; }
    public void setRefKind(ReferenceKind value) { this.refKind = value; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String value) { this.assetId = value; }
    public String getQualifiedName() { return qualifiedName; }
    public void setQualifiedName(String value) { this.qualifiedName = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Alignment {
    private AlignmentAction action;
    private String selectedParent;
    private Integer directParentTier;
    @JsonProperty("tier_1_ancestor")
    private String tier1Ancestor;
    @JsonProperty("ancestry_to_tier_1")
    private List<AncestryReference> ancestryToTier1 = new ArrayList<>();
    private AncestryStatus ancestryStatus;
    private String contextualizedWithin;
    private List<AlignmentCandidate> candidates = new ArrayList<>();
    private List<Object> rejectedCandidates = new ArrayList<>();

    public AlignmentAction getAction() { return action; }
    public void setAction(AlignmentAction value) { this.action = value; }
    public String getSelectedParent() { return selectedParent; }
    public void setSelectedParent(String value) { this.selectedParent = value; }
    public Integer getDirectParentTier() { return directParentTier; }
    public void setDirectParentTier(Integer value) { this.directParentTier = value; }
    public String getTier1Ancestor() { return tier1Ancestor; }
    public void setTier1Ancestor(String value) { this.tier1Ancestor = value; }
    public List<AncestryReference> getAncestryToTier1() { return ancestryToTier1; }
    public void setAncestryToTier1(List<AncestryReference> value) { this.ancestryToTier1 = value; }
    public AncestryStatus getAncestryStatus() { return ancestryStatus; }
    public void setAncestryStatus(AncestryStatus value) { this.ancestryStatus = value; }
    public String getContextualizedWithin() { return contextualizedWithin; }
    public void setContextualizedWithin(String value) { this.contextualizedWithin = value; }
    public List<AlignmentCandidate> getCandidates() { return candidates; }
    public void setCandidates(List<AlignmentCandidate> value) { this.candidates = value; }
    public List<Object> getRejectedCandidates() { return rejectedCandidates; }
    public void setRejectedCandidates(List<Object> value) { this.rejectedCandidates = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Predicates {
    private List<String> identities = new ArrayList<>();
    private List<String> realms = new ArrayList<>();
    private List<String> attributes = new ArrayList<>();
    private List<String> roles = new ArrayList<>();

    public List<String> getIdentities() { return identities; }
    public void setIdentities(List<String> value) { this.identities = value; }
    public List<String> getRealms() { return realms; }
    public void setRealms(List<String> value) { this.realms = value; }
    public List<String> getAttributes() { return attributes; }
    public void setAttributes(List<String> value) { this.attributes = value; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> value) { this.roles = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Derivation {
    private String operator;
    private String operand;
    private String secondaryOperand;
    private String rationale;

    public String getOperator() { return operator; }
    public void setOperator(String value) { this.operator = value; }
    public String getOperand() { return operand; }
    public void setOperand(String value) { this.operand = value; }
    public String getSecondaryOperand() { return secondaryOperand; }
    public void setSecondaryOperand(String value) { this.secondaryOperand = value; }
    public String getRationale() { return rationale; }
    public void setRationale(String value) { this.rationale = value; }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Clauses {
    private List<String> inherits = new ArrayList<>();
    private List<String> appliesTo = new ArrayList<>();
    private Object links;
    private List<String> creates = new ArrayList<>();
    private List<String> affects = new ArrayList<>();
    private List<String> emergesFrom = new ArrayList<>();
    private List<String> implies = new ArrayList<>();
    private Object describes;
    private List<Object> requires = new ArrayList<>();
    private List<Object> children = new ArrayList<>();

    public List<String> getInherits() { return inherits; }
    public void setInherits(List<String> value) { this.inherits = value; }
    public List<String> getAppliesTo() { return appliesTo; }
    public void setAppliesTo(List<String> value) { this.appliesTo = value; }
    public Object getLinks() { return links; }
    public void setLinks(Object value) { this.links = value; }
    public List<String> getCreates() { return creates; }
    public void setCreates(List<String> value) { this.creates = value; }
    public List<String> getAffects() { return affects; }
    public void setAffects(List<String> value) { this.affects = value; }
    public List<String> getEmergesFrom() { return emergesFrom; }
    public void setEmergesFrom(List<String> value) { this.emergesFrom = value; }
    public List<String> getImplies() { return implies; }
    public void setImplies(List<String> value) { this.implies = value; }
    public Object getDescribes() { return describes; }
    public void setDescribes(Object value) { this.describes = value; }
    public List<Object> getRequires() { return requires; }
    public void setRequires(List<Object> value) { this.requires = value; }
    public List<Object> getChildren() { return children; }
    public void setChildren(List<Object> value) { this.children = value; }
  }
}
