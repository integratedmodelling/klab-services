package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.review.ProposalEnums.NamespaceKind;

@JsonTypeName("namespace")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NamespaceProposal extends ProposalAsset {
  private NamespaceKind namespaceKind;
  private List<String> imports = new ArrayList<>();
  private List<String> memberAssetIds = new ArrayList<>();
  private Map<String, Object> proposalData = new LinkedHashMap<>();

  public NamespaceKind getNamespaceKind() { return namespaceKind; }
  public void setNamespaceKind(NamespaceKind value) { this.namespaceKind = value; }
  public List<String> getImports() { return imports; }
  public void setImports(List<String> value) { this.imports = value; }
  public List<String> getMemberAssetIds() { return memberAssetIds; }
  public void setMemberAssetIds(List<String> value) { this.memberAssetIds = value; }
  public Map<String, Object> getProposalData() { return proposalData; }
  public void setProposalData(Map<String, Object> value) { this.proposalData = value; }
}
