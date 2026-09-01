package org.integratedmodelling.common.review;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonTypeName("ontology")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OntologyProposal extends ProposalAsset {

  private Integer tier;
  private String domainExpression;
  private List<String> imports = new ArrayList<>();
  private List<String> memberAssetIds = new ArrayList<>();
  private Map<String, Object> proposalData = new LinkedHashMap<>();

  public Integer getTier() {
    return tier;
  }

  public void setTier(Integer value) {
    this.tier = value;
  }

  public String getDomainExpression() {
    return domainExpression;
  }

  public void setDomainExpression(String value) {
    this.domainExpression = value;
  }

  public List<String> getImports() {
    return imports;
  }

  public void setImports(List<String> value) {
    this.imports = value;
  }

  public List<String> getMemberAssetIds() {
    return memberAssetIds;
  }

  public void setMemberAssetIds(List<String> value) {
    this.memberAssetIds = value;
  }

  public Map<String, Object> getProposalData() {
    return proposalData;
  }

  public void setProposalData(Map<String, Object> value) {
    this.proposalData = value;
  }
}
