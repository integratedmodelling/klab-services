package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Root object serialized as {@code application/vnd.klab.proposal+yaml}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ProposalDocument {
  public static final String SCHEMA_RESOURCE =
      "classpath:/schemas/llm/domain-context-proposal.schema.json";
  public static final String CONTEXT_PACK_VERSION = "1.3";
  public static final String MEDIA_TYPE = "application/vnd.klab.proposal+yaml";

  private String proposalSchema = SCHEMA_RESOURCE;
  private String contextPackVersion = CONTEXT_PACK_VERSION;
  private Proposal proposal;

  public String getProposalSchema() {
    return proposalSchema;
  }

  public void setProposalSchema(String value) {
    this.proposalSchema = value;
  }

  public String getContextPackVersion() {
    return contextPackVersion;
  }

  public void setContextPackVersion(String value) {
    this.contextPackVersion = value;
  }

  public Proposal getProposal() {
    return proposal;
  }

  public void setProposal(Proposal value) {
    this.proposal = value;
  }
}
