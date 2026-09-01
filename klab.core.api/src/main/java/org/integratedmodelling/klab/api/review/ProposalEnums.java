package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Closed vocabularies used by the domain-context proposal schema. */
public final class ProposalEnums {

  private ProposalEnums() {}

  public enum AssetType {
    @JsonProperty("concept") CONCEPT,
    @JsonProperty("ontology") ONTOLOGY,
    @JsonProperty("namespace") NAMESPACE,
    @JsonProperty("model") MODEL
  }

  public enum ProposalStatus {
    @JsonProperty("blocked") BLOCKED,
    @JsonProperty("draft") DRAFT,
    @JsonProperty("community_review") COMMUNITY_REVIEW,
    @JsonProperty("revised") REVISED,
    @JsonProperty("accepted") ACCEPTED,
    @JsonProperty("rejected") REJECTED
  }

  public enum Disposition {
    @JsonProperty("propose") PROPOSE,
    @JsonProperty("reuse") REUSE,
    @JsonProperty("align") ALIGN,
    @JsonProperty("needs_review") NEEDS_REVIEW,
    @JsonProperty("defer") DEFER,
    @JsonProperty("not_a_worldview_concept") NOT_A_WORLDVIEW_CONCEPT
  }

  public enum EpistemicStatus {
    @JsonProperty("explicit") EXPLICIT,
    @JsonProperty("inferred") INFERRED,
    @JsonProperty("modeling_choice") MODELING_CHOICE,
    @JsonProperty("contested") CONTESTED
  }

  public enum FeedbackState {
    @JsonProperty("open") OPEN,
    @JsonProperty("changes_requested") CHANGES_REQUESTED,
    @JsonProperty("accepted") ACCEPTED,
    @JsonProperty("rejected") REJECTED,
    @JsonProperty("deferred") DEFERRED
  }

  public enum FeedbackStance {
    @JsonProperty("question") QUESTION,
    @JsonProperty("support") SUPPORT,
    @JsonProperty("object") OBJECT,
    @JsonProperty("request_change") REQUEST_CHANGE
  }

  public enum FeedbackStatus {
    @JsonProperty("open") OPEN,
    @JsonProperty("accepted") ACCEPTED,
    @JsonProperty("rejected") REJECTED,
    @JsonProperty("superseded") SUPERSEDED,
    @JsonProperty("resolved") RESOLVED
  }

  public enum LifecycleState {
    @JsonProperty("proposed") PROPOSED,
    @JsonProperty("active") ACTIVE,
    @JsonProperty("deprecated") DEPRECATED,
    @JsonProperty("deleted") DELETED,
    @JsonProperty("rejected") REJECTED
  }

  public enum ObservableKind {
    @JsonProperty("thing") THING,
    @JsonProperty("agent") AGENT,
    @JsonProperty("event") EVENT,
    @JsonProperty("process") PROCESS,
    @JsonProperty("relationship") RELATIONSHIP,
    @JsonProperty("bond") BOND,
    @JsonProperty("configuration") CONFIGURATION,
    @JsonProperty("quality") QUALITY,
    @JsonProperty("class") CLASS,
    @JsonProperty("quantity") QUANTITY,
    @JsonProperty("ordering") ORDERING,
    @JsonProperty("extent") EXTENT,
    @JsonProperty("amount") AMOUNT,
    @JsonProperty("area") AREA,
    @JsonProperty("duration") DURATION,
    @JsonProperty("length") LENGTH,
    @JsonProperty("mass") MASS,
    @JsonProperty("money") MONEY,
    @JsonProperty("volume") VOLUME,
    @JsonProperty("weight") WEIGHT,
    @JsonProperty("acceleration") ACCELERATION,
    @JsonProperty("angle") ANGLE,
    @JsonProperty("charge") CHARGE,
    @JsonProperty("electric-potential") ELECTRIC_POTENTIAL,
    @JsonProperty("energy") ENERGY,
    @JsonProperty("entropy") ENTROPY,
    @JsonProperty("pressure") PRESSURE,
    @JsonProperty("priority") PRIORITY,
    @JsonProperty("resistance") RESISTANCE,
    @JsonProperty("resistivity") RESISTIVITY,
    @JsonProperty("temperature") TEMPERATURE,
    @JsonProperty("velocity") VELOCITY,
    @JsonProperty("viscosity") VISCOSITY,
    @JsonProperty("attribute") ATTRIBUTE,
    @JsonProperty("identity") IDENTITY,
    @JsonProperty("role") ROLE,
    @JsonProperty("realm") REALM,
    @JsonProperty("domain") DOMAIN
  }

  public enum SemanticFlag {
    @JsonProperty("abstract") ABSTRACT,
    @JsonProperty("subjective") SUBJECTIVE,
    @JsonProperty("individual") INDIVIDUAL,
    @JsonProperty("deliberative") DELIBERATIVE,
    @JsonProperty("interactive") INTERACTIVE,
    @JsonProperty("reactive") REACTIVE,
    @JsonProperty("functional") FUNCTIONAL,
    @JsonProperty("structural") STRUCTURAL,
    @JsonProperty("deniable") DENIABLE,
    @JsonProperty("rescaling") RESCALING
  }

  public enum Perspective {
    @JsonProperty("structural") STRUCTURAL,
    @JsonProperty("functional") FUNCTIONAL,
    @JsonProperty("cross_perspective") CROSS_PERSPECTIVE,
    @JsonProperty("not_applicable") NOT_APPLICABLE
  }

  public enum Dependence {
    @JsonProperty("independent") INDEPENDENT,
    @JsonProperty("dependent") DEPENDENT,
    @JsonProperty("relational") RELATIONAL,
    @JsonProperty("not_applicable") NOT_APPLICABLE
  }

  public enum ValueKind {
    @JsonProperty("boolean") BOOLEAN,
    @JsonProperty("concept") CONCEPT,
    @JsonProperty("number") NUMBER,
    @JsonProperty("rank") RANK,
    @JsonProperty("geometry") GEOMETRY,
    @JsonProperty("duration") DURATION,
    @JsonProperty("other") OTHER
  }

  public enum AggregationBehavior {
    @JsonProperty("extensive") EXTENSIVE,
    @JsonProperty("intensive") INTENSIVE,
    @JsonProperty("not_applicable") NOT_APPLICABLE,
    @JsonProperty("unresolved") UNRESOLVED
  }

  public enum AmbiguityStatus {
    @JsonProperty("unambiguous") UNAMBIGUOUS,
    @JsonProperty("qualified") QUALIFIED,
    @JsonProperty("unresolved") UNRESOLVED
  }

  public enum OrthogonalityRelationType {
    @JsonProperty("orthogonal") ORTHOGONAL,
    @JsonProperty("taxonomic") TAXONOMIC,
    @JsonProperty("dependent") DEPENDENT,
    @JsonProperty("derived") DERIVED,
    @JsonProperty("compositional") COMPOSITIONAL,
    @JsonProperty("partially_overlapping") PARTIALLY_OVERLAPPING,
    @JsonProperty("redundant") REDUNDANT,
    @JsonProperty("conflicting") CONFLICTING
  }

  public enum SalienceLevel {
    @JsonProperty("peripheral") PERIPHERAL,
    @JsonProperty("established") ESTABLISHED,
    @JsonProperty("core") CORE
  }

  public enum DiscourseUse {
    @JsonProperty("explanation") EXPLANATION,
    @JsonProperty("annotation") ANNOTATION,
    @JsonProperty("query") QUERY,
    @JsonProperty("model_definition") MODEL_DEFINITION,
    @JsonProperty("community_governance") COMMUNITY_GOVERNANCE
  }

  public enum CompositionOutcome {
    @JsonProperty("keep_expression") KEEP_EXPRESSION,
    @JsonProperty("alias_with_equals") ALIAS_WITH_EQUALS,
    @JsonProperty("specialize_with_is") SPECIALIZE_WITH_IS,
    @JsonProperty("unresolved") UNRESOLVED
  }

  public enum AlignmentRelation {
    @JsonProperty("is") IS,
    @JsonProperty("equals") EQUALS,
    @JsonProperty("reuse") REUSE,
    @JsonProperty("authority") AUTHORITY
  }

  public enum ReferenceKind {
    @JsonProperty("internal") INTERNAL,
    @JsonProperty("external") EXTERNAL
  }

  public enum AlignmentAction {
    @JsonProperty("implicit_type_only") IMPLICIT_TYPE_ONLY,
    @JsonProperty("reuse_exact") REUSE_EXACT,
    @JsonProperty("specialize") SPECIALIZE,
    @JsonProperty("authority_reference") AUTHORITY_REFERENCE,
    @JsonProperty("new_root_candidate") NEW_ROOT_CANDIDATE,
    @JsonProperty("unresolved") UNRESOLVED
  }

  public enum AncestryStatus {
    @JsonProperty("verified") VERIFIED,
    @JsonProperty("missing_parent") MISSING_PARENT,
    @JsonProperty("missing_tier_1_ancestor") MISSING_TIER_1_ANCESTOR,
    @JsonProperty("unresolved") UNRESOLVED,
    @JsonProperty("not_applicable") NOT_APPLICABLE
  }

  public enum SyntaxStatus {
    @JsonProperty("not_attempted") NOT_ATTEMPTED,
    @JsonProperty("illustrative") ILLUSTRATIVE,
    @JsonProperty("parsed") PARSED,
    @JsonProperty("adapted") ADAPTED,
    @JsonProperty("reasoner_validated") REASONER_VALIDATED
  }

  public enum NamespaceKind {
    @JsonProperty("namespace") NAMESPACE,
    @JsonProperty("scenario") SCENARIO,
    @JsonProperty("worldview") WORLDVIEW
  }

  public enum OntologyRole {
    @JsonProperty("root_domain") ROOT_DOMAIN,
    @JsonProperty("upper") UPPER,
    @JsonProperty("tier_1_mandatory") TIER_1_MANDATORY,
    @JsonProperty("intervening_tier") INTERVENING_TIER,
    @JsonProperty("neighboring") NEIGHBORING,
    @JsonProperty("authority") AUTHORITY,
    @JsonProperty("prior_domain") PRIOR_DOMAIN
  }

  public enum EvidenceKind {
    @JsonProperty("definition") DEFINITION,
    @JsonProperty("distinction") DISTINCTION,
    @JsonProperty("classification") CLASSIFICATION,
    @JsonProperty("mechanism") MECHANISM,
    @JsonProperty("measurement") MEASUREMENT,
    @JsonProperty("example") EXAMPLE,
    @JsonProperty("contested_claim") CONTESTED_CLAIM
  }

  public enum ActionOperation {
    @JsonProperty("add") ADD,
    @JsonProperty("rename") RENAME,
    @JsonProperty("modify") MODIFY,
    @JsonProperty("expand") EXPAND,
    @JsonProperty("delete") DELETE,
    @JsonProperty("deprecate") DEPRECATE,
    @JsonProperty("restore") RESTORE,
    @JsonProperty("split") SPLIT,
    @JsonProperty("merge") MERGE,
    @JsonProperty("replace") REPLACE
  }

  public enum ActionStatus {
    @JsonProperty("proposed") PROPOSED,
    @JsonProperty("needs_review") NEEDS_REVIEW,
    @JsonProperty("approved") APPROVED,
    @JsonProperty("rejected") REJECTED,
    @JsonProperty("applied") APPLIED,
    @JsonProperty("failed") FAILED,
    @JsonProperty("superseded") SUPERSEDED
  }

  public enum GapStatus {
    @JsonProperty("open") OPEN,
    @JsonProperty("supplied") SUPPLIED,
    @JsonProperty("resolved_upstream") RESOLVED_UPSTREAM,
    @JsonProperty("resolved_in_root") RESOLVED_IN_ROOT,
    @JsonProperty("rejected") REJECTED,
    @JsonProperty("superseded") SUPERSEDED
  }

  public enum Tier1ContextStatus {
    @JsonProperty("not_required") NOT_REQUIRED,
    @JsonProperty("present") PRESENT,
    @JsonProperty("missing") MISSING,
    @JsonProperty("incomplete") INCOMPLETE
  }

  public enum IterationState {
    @JsonProperty("open_for_feedback") OPEN_FOR_FEEDBACK,
    @JsonProperty("actions_proposed") ACTIONS_PROPOSED,
    @JsonProperty("approved_for_apply") APPROVED_FOR_APPLY,
    @JsonProperty("applied") APPLIED,
    @JsonProperty("closed") CLOSED
  }
}
