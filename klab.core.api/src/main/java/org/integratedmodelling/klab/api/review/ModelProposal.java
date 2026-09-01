package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonTypeName("model")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ModelProposal extends ProposalAsset {
  private String namespaceAssetId;
  private List<String> observables = new ArrayList<>();
  private List<String> dependencies = new ArrayList<>();
  private String strategySummary;
  private Map<String, Object> proposalData = new LinkedHashMap<>();

  public String getNamespaceAssetId() { return namespaceAssetId; }
  public void setNamespaceAssetId(String value) { this.namespaceAssetId = value; }
  public List<String> getObservables() { return observables; }
  public void setObservables(List<String> value) { this.observables = value; }
  public List<String> getDependencies() { return dependencies; }
  public void setDependencies(List<String> value) { this.dependencies = value; }
  public String getStrategySummary() { return strategySummary; }
  public void setStrategySummary(String value) { this.strategySummary = value; }
  public Map<String, Object> getProposalData() { return proposalData; }
  public void setProposalData(Map<String, Object> value) { this.proposalData = value; }
}
