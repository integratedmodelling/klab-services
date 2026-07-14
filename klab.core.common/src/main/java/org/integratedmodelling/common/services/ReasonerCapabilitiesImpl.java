package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;

public class ReasonerCapabilitiesImpl extends AbstractServiceCapabilities
    implements Reasoner.Capabilities {

  private KlabService.Type type;
  private String worldviewId;
  private boolean consistent;
  private long knowledgeRevision;

  @Override
  public KlabService.Type getType() {
    return type;
  }

  public void setType(KlabService.Type type) {
    this.type = type;
  }

  @Override
  public String getWorldviewId() {
    return worldviewId;
  }

  public void setWorldviewId(String worldviewId) {
    this.worldviewId = worldviewId;
  }

  @Override
  public boolean isConsistent() {
    return consistent;
  }

  public void setConsistent(boolean consistent) {
    this.consistent = consistent;
  }

  @Override
  public long getKnowledgeRevision() {
    return knowledgeRevision;
  }

  public void setKnowledgeRevision(long knowledgeRevision) {
    this.knowledgeRevision = knowledgeRevision;
  }
}
