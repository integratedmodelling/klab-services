package org.integratedmodelling.common.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.common.review.ProposalEnums.EvidenceKind;
import org.integratedmodelling.common.review.ProposalEnums.OntologyRole;
import org.integratedmodelling.common.review.ProposalEnums.Tier1ContextStatus;

/** Source, scope and supplied-ontology context for a proposal. */
public final class ProposalContext {

  private ProposalContext() {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Scope {

    private String domain;
    private String purpose;
    private int requestedTier;
    private String targetCommunity;

    @JsonProperty("tier_1_context_required")
    private boolean tier1ContextRequired;

    @JsonProperty("tier_1_context_status")
    private Tier1ContextStatus tier1ContextStatus;

    private List<String> included = new ArrayList<>();
    private List<String> excluded = new ArrayList<>();
    private String spatialContext;
    private String temporalContext;
    private List<String> intendedScales = new ArrayList<>();

    public String getDomain() {
      return domain;
    }

    public void setDomain(String value) {
      this.domain = value;
    }

    public String getPurpose() {
      return purpose;
    }

    public void setPurpose(String value) {
      this.purpose = value;
    }

    public int getRequestedTier() {
      return requestedTier;
    }

    public void setRequestedTier(int value) {
      this.requestedTier = value;
    }

    public String getTargetCommunity() {
      return targetCommunity;
    }

    public void setTargetCommunity(String value) {
      this.targetCommunity = value;
    }

    public boolean isTier1ContextRequired() {
      return tier1ContextRequired;
    }

    public void setTier1ContextRequired(boolean value) {
      this.tier1ContextRequired = value;
    }

    public Tier1ContextStatus getTier1ContextStatus() {
      return tier1ContextStatus;
    }

    public void setTier1ContextStatus(Tier1ContextStatus value) {
      this.tier1ContextStatus = value;
    }

    public List<String> getIncluded() {
      return included;
    }

    public void setIncluded(List<String> value) {
      this.included = value;
    }

    public List<String> getExcluded() {
      return excluded;
    }

    public void setExcluded(List<String> value) {
      this.excluded = value;
    }

    public String getSpatialContext() {
      return spatialContext;
    }

    public void setSpatialContext(String value) {
      this.spatialContext = value;
    }

    public String getTemporalContext() {
      return temporalContext;
    }

    public void setTemporalContext(String value) {
      this.temporalContext = value;
    }

    public List<String> getIntendedScales() {
      return intendedScales;
    }

    public void setIntendedScales(List<String> value) {
      this.intendedScales = value;
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class SourcePolicy {
    private List<String> authorityCriteria = new ArrayList<>();
    private String inferencePolicy;

    public List<String> getAuthorityCriteria() {
      return authorityCriteria;
    }

    public void setAuthorityCriteria(List<String> value) {
      this.authorityCriteria = value;
    }

    public String getInferencePolicy() {
      return inferencePolicy;
    }

    public void setInferencePolicy(String value) {
      this.inferencePolicy = value;
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Source {
    private String sourceId;
    private String citation;
    private String version;
    private String locator;
    private String authorityNote;

    public String getSourceId() {
      return sourceId;
    }

    public void setSourceId(String value) {
      this.sourceId = value;
    }

    public String getCitation() {
      return citation;
    }

    public void setCitation(String value) {
      this.citation = value;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String value) {
      this.version = value;
    }

    public String getLocator() {
      return locator;
    }

    public void setLocator(String value) {
      this.locator = value;
    }

    public String getAuthorityNote() {
      return authorityNote;
    }

    public void setAuthorityNote(String value) {
      this.authorityNote = value;
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class OntologyContext {
    private String ontologyId;
    private String version;
    private OntologyRole role;
    private Integer tier;
    private String domainScope;
    private boolean mandatoryContext;

    public String getOntologyId() {
      return ontologyId;
    }

    public void setOntologyId(String value) {
      this.ontologyId = value;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(String value) {
      this.version = value;
    }

    public OntologyRole getRole() {
      return role;
    }

    public void setRole(OntologyRole value) {
      this.role = value;
    }

    public Integer getTier() {
      return tier;
    }

    public void setTier(Integer value) {
      this.tier = value;
    }

    public String getDomainScope() {
      return domainScope;
    }

    public void setDomainScope(String value) {
      this.domainScope = value;
    }

    public boolean isMandatoryContext() {
      return mandatoryContext;
    }

    public void setMandatoryContext(boolean value) {
      this.mandatoryContext = value;
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class RootScopeAlias {
    private String alias;
    private String foundationalConcept;
    private String sourceOntologyId;
    private List<String> availableFor = new ArrayList<>();

    public String getAlias() {
      return alias;
    }

    public void setAlias(String value) {
      this.alias = value;
    }

    public String getFoundationalConcept() {
      return foundationalConcept;
    }

    public void setFoundationalConcept(String value) {
      this.foundationalConcept = value;
    }

    public String getSourceOntologyId() {
      return sourceOntologyId;
    }

    public void setSourceOntologyId(String value) {
      this.sourceOntologyId = value;
    }

    public List<String> getAvailableFor() {
      return availableFor;
    }

    public void setAvailableFor(List<String> value) {
      this.availableFor = value;
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public static class Evidence {
    private String evidenceId;
    private String sourceId;
    private String locator;
    private String sourceTerm;
    private String paraphrase;
    private EvidenceKind evidenceKind;
    private String interpretation;

    public String getEvidenceId() {
      return evidenceId;
    }

    public void setEvidenceId(String value) {
      this.evidenceId = value;
    }

    public String getSourceId() {
      return sourceId;
    }

    public void setSourceId(String value) {
      this.sourceId = value;
    }

    public String getLocator() {
      return locator;
    }

    public void setLocator(String value) {
      this.locator = value;
    }

    public String getSourceTerm() {
      return sourceTerm;
    }

    public void setSourceTerm(String value) {
      this.sourceTerm = value;
    }

    public String getParaphrase() {
      return paraphrase;
    }

    public void setParaphrase(String value) {
      this.paraphrase = value;
    }

    public EvidenceKind getEvidenceKind() {
      return evidenceKind;
    }

    public void setEvidenceKind(EvidenceKind value) {
      this.evidenceKind = value;
    }

    public String getInterpretation() {
      return interpretation;
    }

    public void setInterpretation(String value) {
      this.interpretation = value;
    }
  }
}
