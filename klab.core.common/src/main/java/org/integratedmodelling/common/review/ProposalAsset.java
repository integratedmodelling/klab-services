package org.integratedmodelling.common.review;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.review.ProposalEnums.AssetType;
import org.integratedmodelling.common.review.ProposalEnums.Disposition;
import org.integratedmodelling.common.review.ProposalEnums.EpistemicStatus;
import org.integratedmodelling.common.review.ProposalReview.Feedback;
import org.integratedmodelling.common.review.ProposalReview.Lifecycle;
import org.integratedmodelling.common.review.ProposalReview.NameHistory;

/** Common identity, evidence, feedback and lifecycle envelope for proposed assets. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "asset_type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ConceptProposal.class, name = "concept"),
  @JsonSubTypes.Type(value = OntologyProposal.class, name = "ontology"),
  @JsonSubTypes.Type(value = NamespaceProposal.class, name = "namespace"),
  @JsonSubTypes.Type(value = ModelProposal.class, name = "model")
})
public abstract class ProposalAsset {

  private String assetId;
  private AssetType assetType;
  private String qualifiedName;
  private int recordVersion;
  private String recordHash;
  private List<NameHistory> nameHistory = new ArrayList<>();
  private String label;
  private List<String> aliases = new ArrayList<>();
  private Disposition disposition;
  private String definition;
  private String extractionRationale;
  private List<String> evidenceRefs = new ArrayList<>();
  private EpistemicStatus epistemicStatus;
  private Feedback feedback;
  private Lifecycle lifecycle;
  private List<String> openQuestions = new ArrayList<>();
  private List<Object> alternatives = new ArrayList<>();
  private Map<String, Object> metadata = new LinkedHashMap<>();

  public String getAssetId() {
    return assetId;
  }

  public void setAssetId(String value) {
    this.assetId = value;
  }

  public AssetType getAssetType() {
    return assetType;
  }

  public void setAssetType(AssetType value) {
    this.assetType = value;
  }

  public String getQualifiedName() {
    return qualifiedName;
  }

  public void setQualifiedName(String value) {
    this.qualifiedName = value;
  }

  public int getRecordVersion() {
    return recordVersion;
  }

  public void setRecordVersion(int value) {
    this.recordVersion = value;
  }

  public String getRecordHash() {
    return recordHash;
  }

  public void setRecordHash(String value) {
    this.recordHash = value;
  }

  public List<NameHistory> getNameHistory() {
    return nameHistory;
  }

  public void setNameHistory(List<NameHistory> value) {
    this.nameHistory = value;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String value) {
    this.label = value;
  }

  public List<String> getAliases() {
    return aliases;
  }

  public void setAliases(List<String> value) {
    this.aliases = value;
  }

  public Disposition getDisposition() {
    return disposition;
  }

  public void setDisposition(Disposition value) {
    this.disposition = value;
  }

  public String getDefinition() {
    return definition;
  }

  public void setDefinition(String value) {
    this.definition = value;
  }

  public String getExtractionRationale() {
    return extractionRationale;
  }

  public void setExtractionRationale(String value) {
    this.extractionRationale = value;
  }

  public List<String> getEvidenceRefs() {
    return evidenceRefs;
  }

  public void setEvidenceRefs(List<String> value) {
    this.evidenceRefs = value;
  }

  public EpistemicStatus getEpistemicStatus() {
    return epistemicStatus;
  }

  public void setEpistemicStatus(EpistemicStatus value) {
    this.epistemicStatus = value;
  }

  public Feedback getFeedback() {
    return feedback;
  }

  public void setFeedback(Feedback value) {
    this.feedback = value;
  }

  public Lifecycle getLifecycle() {
    return lifecycle;
  }

  public void setLifecycle(Lifecycle value) {
    this.lifecycle = value;
  }

  public List<String> getOpenQuestions() {
    return openQuestions;
  }

  public void setOpenQuestions(List<String> value) {
    this.openQuestions = value;
  }

  public List<Object> getAlternatives() {
    return alternatives;
  }

  public void setAlternatives(List<Object> value) {
    this.alternatives = value;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> value) {
    this.metadata = value;
  }
}
