package org.integratedmodelling.klab.api.review;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.review.ProposalEnums.SyntaxStatus;
import org.integratedmodelling.klab.api.review.ProposalSemantics.Alignment;
import org.integratedmodelling.klab.api.review.ProposalSemantics.Ambiguity;
import org.integratedmodelling.klab.api.review.ProposalSemantics.Boundaries;
import org.integratedmodelling.klab.api.review.ProposalSemantics.Clauses;
import org.integratedmodelling.klab.api.review.ProposalSemantics.Derivation;
import org.integratedmodelling.klab.api.review.ProposalSemantics.NamedComposition;
import org.integratedmodelling.klab.api.review.ProposalSemantics.Orthogonality;
import org.integratedmodelling.klab.api.review.ProposalSemantics.Predicates;
import org.integratedmodelling.klab.api.review.ProposalSemantics.SemanticCoordinates;
import org.integratedmodelling.klab.api.review.ProposalSemantics.TypeInheritance;

/** Fully articulated ontology-concept proposal. */
@JsonTypeName("concept")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ConceptProposal extends ProposalAsset {

  private boolean isAbstract;
  private String section;
  private int tier;
  private String tierRationale;
  private String conceptualDimension;
  private Boundaries boundaries;
  private SemanticCoordinates semanticCoordinates;
  private String categoryRationale;
  private TypeInheritance typeInheritance;
  private List<String> generalizedScopeAliasesUsed = new ArrayList<>();
  private Ambiguity ambiguity;
  private Orthogonality orthogonality;
  private NamedComposition namedComposition;
  private Alignment alignment;
  private Predicates predicates;
  private Derivation derivation;
  private Clauses clauses;
  private List<String> dependencies = new ArrayList<>();
  private String candidateExpression;
  private String candidateKwv;
  private SyntaxStatus syntaxStatus;
  private Map<String, Double> confidence = new LinkedHashMap<>();

  public boolean isAbstract() {
    return isAbstract;
  }

  public void setAbstract(boolean value) {
    this.isAbstract = value;
  }

  public String getSection() {
    return section;
  }

  public void setSection(String value) {
    this.section = value;
  }

  public int getTier() {
    return tier;
  }

  public void setTier(int value) {
    this.tier = value;
  }

  public String getTierRationale() {
    return tierRationale;
  }

  public void setTierRationale(String value) {
    this.tierRationale = value;
  }

  public String getConceptualDimension() {
    return conceptualDimension;
  }

  public void setConceptualDimension(String value) {
    this.conceptualDimension = value;
  }

  public Boundaries getBoundaries() {
    return boundaries;
  }

  public void setBoundaries(Boundaries value) {
    this.boundaries = value;
  }

  public SemanticCoordinates getSemanticCoordinates() {
    return semanticCoordinates;
  }

  public void setSemanticCoordinates(SemanticCoordinates value) {
    this.semanticCoordinates = value;
  }

  public String getCategoryRationale() {
    return categoryRationale;
  }

  public void setCategoryRationale(String value) {
    this.categoryRationale = value;
  }

  public TypeInheritance getTypeInheritance() {
    return typeInheritance;
  }

  public void setTypeInheritance(TypeInheritance value) {
    this.typeInheritance = value;
  }

  public List<String> getGeneralizedScopeAliasesUsed() {
    return generalizedScopeAliasesUsed;
  }

  public void setGeneralizedScopeAliasesUsed(List<String> value) {
    this.generalizedScopeAliasesUsed = value;
  }

  public Ambiguity getAmbiguity() {
    return ambiguity;
  }

  public void setAmbiguity(Ambiguity value) {
    this.ambiguity = value;
  }

  public Orthogonality getOrthogonality() {
    return orthogonality;
  }

  public void setOrthogonality(Orthogonality value) {
    this.orthogonality = value;
  }

  public NamedComposition getNamedComposition() {
    return namedComposition;
  }

  public void setNamedComposition(NamedComposition value) {
    this.namedComposition = value;
  }

  public Alignment getAlignment() {
    return alignment;
  }

  public void setAlignment(Alignment value) {
    this.alignment = value;
  }

  public Predicates getPredicates() {
    return predicates;
  }

  public void setPredicates(Predicates value) {
    this.predicates = value;
  }

  public Derivation getDerivation() {
    return derivation;
  }

  public void setDerivation(Derivation value) {
    this.derivation = value;
  }

  public Clauses getClauses() {
    return clauses;
  }

  public void setClauses(Clauses value) {
    this.clauses = value;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  public void setDependencies(List<String> value) {
    this.dependencies = value;
  }

  public String getCandidateExpression() {
    return candidateExpression;
  }

  public void setCandidateExpression(String value) {
    this.candidateExpression = value;
  }

  public String getCandidateKwv() {
    return candidateKwv;
  }

  public void setCandidateKwv(String value) {
    this.candidateKwv = value;
  }

  public SyntaxStatus getSyntaxStatus() {
    return syntaxStatus;
  }

  public void setSyntaxStatus(SyntaxStatus value) {
    this.syntaxStatus = value;
  }

  public Map<String, Double> getConfidence() {
    return confidence;
  }

  public void setConfidence(Map<String, Double> value) {
    this.confidence = value;
  }
}
