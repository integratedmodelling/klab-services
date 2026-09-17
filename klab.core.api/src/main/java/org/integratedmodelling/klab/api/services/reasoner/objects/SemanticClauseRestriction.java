package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;

/** A clause restriction supplied by the Reasoner, never inferred by a UI. */
public class SemanticClauseRestriction {
  private List<StyledKimToken> code = new java.util.ArrayList<>();
  public List<StyledKimToken> getCode() { return code; }
  public void setCode(List<StyledKimToken> value) { code = value; }
  private SemanticRole role;
  private Concept filler;
  private boolean inherited;
  public SemanticClauseRestriction() {}
  public SemanticClauseRestriction(SemanticRole role, Concept filler, boolean inherited) {
    this.role = role; this.filler = filler; this.inherited = inherited;
  }
  public SemanticRole getRole() { return role; }
  public void setRole(SemanticRole value) { role = value; }
  public Concept getFiller() { return filler; }
  public void setFiller(Concept value) { filler = value; }
  public boolean isInherited() { return inherited; }
  public void setInherited(boolean value) { inherited = value; }
}
