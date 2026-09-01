package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.review.ProposalContext.Evidence;
import org.integratedmodelling.klab.api.review.ProposalContext.OntologyContext;
import org.integratedmodelling.klab.api.review.ProposalContext.RootScopeAlias;
import org.integratedmodelling.klab.api.review.ProposalContext.Scope;
import org.integratedmodelling.klab.api.review.ProposalContext.Source;
import org.integratedmodelling.klab.api.review.ProposalContext.SourcePolicy;
import org.integratedmodelling.klab.api.review.ProposalEnums.ProposalStatus;
import org.integratedmodelling.klab.api.review.ProposalReview.Action;
import org.integratedmodelling.klab.api.review.ProposalReview.ChangeLogEntry;
import org.integratedmodelling.klab.api.review.ProposalReview.CorpusFeedback;
import org.integratedmodelling.klab.api.review.ProposalReview.Gap;
import org.integratedmodelling.klab.api.review.ProposalReview.IterationControl;
import org.integratedmodelling.klab.api.review.ProposalReview.OrthogonalityReview;
import org.integratedmodelling.klab.api.review.ProposalReview.Validation;

/** One immutable revision of a reviewable asset proposal. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Proposal {
  private String id;
  private String revisionId;
  private String title;
  private int iteration;
  private String supersedesRevision;
  private ProposalStatus status;
  private String generatedAt;
  private Scope scope;
  private SourcePolicy sourcePolicy;
  private List<Source> sources = new ArrayList<>();
  private List<OntologyContext> existingOntologies = new ArrayList<>();
  private List<RootScopeAlias> rootScopeAliases = new ArrayList<>();
  private List<Evidence> evidence = new ArrayList<>();
  private List<ProposalAsset> assets = new ArrayList<>();
  private List<String> dependencyOrder = new ArrayList<>();
  private List<Gap> upstreamGaps = new ArrayList<>();
  private List<Gap> rootDomainGaps = new ArrayList<>();
  private IterationControl iterationControl;
  private List<Action> actions = new ArrayList<>();
  private OrthogonalityReview orthogonalityReview;
  private Validation validation;
  private CorpusFeedback corpusFeedback;
  private List<ChangeLogEntry> changeLog = new ArrayList<>();

  public String getId() { return id; }
  public void setId(String value) { this.id = value; }
  public String getRevisionId() { return revisionId; }
  public void setRevisionId(String value) { this.revisionId = value; }
  public String getTitle() { return title; }
  public void setTitle(String value) { this.title = value; }
  public int getIteration() { return iteration; }
  public void setIteration(int value) { this.iteration = value; }
  public String getSupersedesRevision() { return supersedesRevision; }
  public void setSupersedesRevision(String value) { this.supersedesRevision = value; }
  public ProposalStatus getStatus() { return status; }
  public void setStatus(ProposalStatus value) { this.status = value; }
  public String getGeneratedAt() { return generatedAt; }
  public void setGeneratedAt(String value) { this.generatedAt = value; }
  public Scope getScope() { return scope; }
  public void setScope(Scope value) { this.scope = value; }
  public SourcePolicy getSourcePolicy() { return sourcePolicy; }
  public void setSourcePolicy(SourcePolicy value) { this.sourcePolicy = value; }
  public List<Source> getSources() { return sources; }
  public void setSources(List<Source> value) { this.sources = value; }
  public List<OntologyContext> getExistingOntologies() { return existingOntologies; }
  public void setExistingOntologies(List<OntologyContext> value) { this.existingOntologies = value; }
  public List<RootScopeAlias> getRootScopeAliases() { return rootScopeAliases; }
  public void setRootScopeAliases(List<RootScopeAlias> value) { this.rootScopeAliases = value; }
  public List<Evidence> getEvidence() { return evidence; }
  public void setEvidence(List<Evidence> value) { this.evidence = value; }
  public List<ProposalAsset> getAssets() { return assets; }
  public void setAssets(List<ProposalAsset> value) { this.assets = value; }
  public List<String> getDependencyOrder() { return dependencyOrder; }
  public void setDependencyOrder(List<String> value) { this.dependencyOrder = value; }
  public List<Gap> getUpstreamGaps() { return upstreamGaps; }
  public void setUpstreamGaps(List<Gap> value) { this.upstreamGaps = value; }
  public List<Gap> getRootDomainGaps() { return rootDomainGaps; }
  public void setRootDomainGaps(List<Gap> value) { this.rootDomainGaps = value; }
  public IterationControl getIterationControl() { return iterationControl; }
  public void setIterationControl(IterationControl value) { this.iterationControl = value; }
  public List<Action> getActions() { return actions; }
  public void setActions(List<Action> value) { this.actions = value; }
  public OrthogonalityReview getOrthogonalityReview() { return orthogonalityReview; }
  public void setOrthogonalityReview(OrthogonalityReview value) { this.orthogonalityReview = value; }
  public Validation getValidation() { return validation; }
  public void setValidation(Validation value) { this.validation = value; }
  public CorpusFeedback getCorpusFeedback() { return corpusFeedback; }
  public void setCorpusFeedback(CorpusFeedback value) { this.corpusFeedback = value; }
  public List<ChangeLogEntry> getChangeLog() { return changeLog; }
  public void setChangeLog(List<ChangeLogEntry> value) { this.changeLog = value; }

  /** Find an asset by immutable proposal identity. */
  public ProposalAsset getAsset(String assetId) {
    if (assetId == null) return null;
    return assets.stream().filter(asset -> assetId.equals(asset.getAssetId())).findFirst().orElse(null);
  }
}
