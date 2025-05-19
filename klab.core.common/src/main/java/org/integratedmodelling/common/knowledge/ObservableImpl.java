package org.integratedmodelling.common.knowledge;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.springframework.util.StringUtils;

import java.util.*;

public class ObservableImpl implements Observable {

  private static final long serialVersionUID = 6188649888474774359L;

  private Concept semantics;
  private Concept observerSemantics;
  private DescriptionType descriptionType;
  private Artifact.Type artifactType;
  private boolean isAbstract;
  private String urn;
  private Unit unit;
  private Currency currency;
  private NumericRange range;
  private boolean optional;
  private boolean generic;
  private Collection<ResolutionDirective> resolutionDirectives =
      EnumSet.noneOf(ResolutionDirective.class);
  private Object defaultValue;
//  private Object value;
  private String statedName;
  private List<Annotation> annotations = new ArrayList<>();
//  private List<Pair<ValueOperator, Object>> valueOperators = new ArrayList<>();
  private String referenceName;
  private String name;
  private String namespace;
  private Metadata metadata = Metadata.create();

  transient Knowledge resolving;
  private Set<Concept> genericComponents = new HashSet<>();
  private List<Pair<Concept, Concept>> specializedComponents = new ArrayList<>();

  public Knowledge getResolving() {
    return resolving;
  }

  public void setResolving(Knowledge resolving) {
    this.resolving = resolving;
  }

  public void setGenericComponents(Set<Concept> genericComponents) {
    this.genericComponents = genericComponents;
  }

  public void setSpecializedComponents(List<Pair<Concept, Concept>> specializedComponents) {
    this.specializedComponents = specializedComponents;
  }

  public ObservableImpl() {}

  public ObservableImpl(Concept concept) {
    setSemantics(concept);
  }

  private ObservableImpl(ObservableImpl other) {
    this.semantics = other.semantics;
    this.observerSemantics = other.observerSemantics;
    this.descriptionType = other.descriptionType;
    this.artifactType = other.artifactType;
    this.isAbstract = other.isAbstract;
    this.urn = other.urn;
    this.unit = other.unit;
    this.currency = other.currency;
    this.range = other.range;
    //        this.resolvedPredicates = other.resolvedPredicates;
    //        this.contextualRoles = other.contextualRoles;
    //        this.resolution = other.resolution;
    this.genericComponents.addAll(other.genericComponents);
    this.optional = other.optional;
    this.generic = other.generic;
    this.resolutionDirectives = other.resolutionDirectives;
    this.defaultValue = other.defaultValue;
//    this.value = other.value;
    this.statedName = other.statedName;
    this.annotations.addAll(other.annotations);
//    this.valueOperators = other.valueOperators;
    this.referenceName = other.referenceName;
    this.name = other.name;
    this.namespace = other.namespace;
    //        this.distributedInherency = other.distributedInherency;
    //        this.dereifiedAttribute = other.dereifiedAttribute;
    this.metadata.putAll(other.metadata);
  }

  @Override
  public String getUrn() {
    return urn;
  }

  // @Override
  // public Semantics domain() {
  // return semantics == null ? null : semantics.domain();
  // }

  // @Override
  // public boolean is(Semantics other) {
  // // TODO Auto-generated method stub
  // return false;
  // }

  @Override
  public boolean is(SemanticType type) {
    return semantics.is(type);
  }

  @Override
  public boolean isAbstract() {
    return isAbstract;
  }

  @Override
  public Collection<Concept> getGenericComponents() {
    return this.genericComponents;
  }

  @Override
  public Collection<Pair<Concept, Concept>> getSpecializedComponents() {
    return this.specializedComponents;
  }

  @Override
  public String getNamespace() {
    return namespace;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getReferenceName() {
    return referenceName;
  }

  @Override
  public Concept getSemantics() {
    return this.semantics;
  }

  @Override
  public Concept getObserverSemantics() {
    return this.observerSemantics;
  }

  @Override
  public Unit getUnit() {
    return unit;
  }

  @Override
  public Currency getCurrency() {
    return currency;
  }

//  @Override
//  public List<Pair<ValueOperator, Object>> getValueOperators() {
//    return valueOperators;
//  }

  @Override
  public List<Annotation> getAnnotations() {
    return annotations;
  }

  // @Override
  // public Collection<Concept> abstractPredicates() {
  // return abstractPredicates;
  // }

  @Override
  public DescriptionType getDescriptionType() {
    return this.descriptionType;
  }

  //    @Override
  public Observable as(DescriptionType descriptionType) {
    var ret = new ObservableImpl(this);
    // TODO check for compatibility!
    ret.setDescriptionType(descriptionType);
    return ret;
  }

  @Override
  public boolean is(DescriptionType descriptionType) {
    // TODO this can be smarter and check for instantiation or resolution in its different forms
    return this.descriptionType == descriptionType;
  }

  @Override
  public Type getArtifactType() {
    return this.artifactType;
  }

  @Override
  public String getStatedName() {
    return statedName;
  }

  @Override
  public ValueMediator mediator() {
    return this.unit == null ? (this.range == null ? this.currency : this.range) : this.unit;
  }
//
//  @Override
//  public Object getValue() {
//    return value;
//  }

  // @Override
  // public Concept context() {
  // // TODO Auto-generated method stub
  // return null;
  // }
  //
  // @Override
  // public Concept inherent() {
  // // TODO Auto-generated method stub
  // return null;
  // }
  //
  // @Override
  // public Concept temporalInherent() {
  // // TODO Auto-generated method stub
  // return null;
  // }

  @Override
  public Object getDefaultValue() {
    return defaultValue;
  }

  @Override
  public Collection<ResolutionDirective> getResolutionDirectives() {
    return resolutionDirectives;
  }

  @Override
  public boolean isGeneric() {
    return generic;
  }

  @Override
  public boolean isOptional() {
    return optional;
  }

  public NumericRange getRange() {
    return range;
  }

  public void setRange(NumericRange range) {
    this.range = range;
  }

  public void setSemantics(ConceptImpl semantics) {
    this.semantics = semantics;
  }

  public void setObserverSemantics(Concept observer) {
    this.observerSemantics = observer;
  }

  public void setDescriptionType(DescriptionType descriptionType) {
    this.descriptionType = descriptionType;
  }

  public void setArtifactType(Artifact.Type artifactType) {
    this.artifactType = artifactType;
  }

  public void setAbstract(boolean isAbstract) {
    this.isAbstract = isAbstract;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setUnit(Unit unit) {
    this.unit = unit;
  }

  public void setCurrency(Currency currency) {
    this.currency = currency;
  }

  @Override
  public String displayName() {

    StringBuilder ret = new StringBuilder(getSemantics().displayName());

//    for (Pair<ValueOperator, Object> operator : getValueOperators()) {
//
//      ret.append(StringUtils.capitalize(operator.getFirst().declaration.replace(' ', '_')));
//
//      if (operator.getSecond() instanceof KimConcept kimConcept) {
//        // FIXME use displayName for the associated concept! needs the service
//        ret.append(kimConcept.getName());
//      } else if (operator.getSecond() instanceof KimObservable kimObservable) {
//        // FIXME use displayName for the associated observable! needs the service
//        ret.append(kimObservable.getCodeName());
//      } else {
//        ret.append("_").append(operator.getSecond().toString().replace(' ', '_'));
//      }
//    }
    return ret.toString();
  }

  @Override
  public String displayLabel() {
    String ret = displayName();
    if (!ret.contains(" ")) {
      ret = StringUtils.capitalize(Utils.CamelCase.toLowerCase(ret, ' '));
    }
    return ret;
  }

  @Override
  public String codeName() {
    return getSemantics().codeName();
  }

  @Override
  public Builder builder(Scope scope) {
    return new ObservableBuildStrategy(this, scope);
  }

  public void setOptional(boolean optional) {
    this.optional = optional;
  }

  public void setGeneric(boolean generic) {
    this.generic = generic;
  }

  public void setResolutionExceptions(Collection<ResolutionDirective> resolutionDirectives) {
    this.resolutionDirectives = resolutionDirectives;
  }

  public void setDefaultValue(Object defaultValue) {
    this.defaultValue = defaultValue;
  }

  public void setStatedName(String statedName) {
    this.statedName = statedName;
  }

  public void setAnnotations(List<Annotation> annotations) {
    this.annotations = annotations;
  }

  public void setReferenceName(String referenceName) {
    this.referenceName = referenceName;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public void setSemantics(Concept semantics) {
    this.semantics = semantics;
  }

  public static ObservableImpl promote(Concept concept, Scope scope) {

    ObservableImpl ret = new ObservableImpl();

    ret.semantics = concept;
    ret.urn = concept.getUrn();
    ret.isAbstract = concept.isAbstract();
    ret.referenceName = concept.getReferenceName();
    ret.name = concept.codeName();
    if (ret.referenceName == null) {
      // only happens with non-standard observables from system ontologies
      ret.referenceName = concept.getNamespace() + "_" + concept.getName();
    }
    ret.artifactType = Artifact.Type.forSemantics(concept.getType());
    ret.descriptionType = DescriptionType.forSemantics(concept.getType(), concept.isCollective());
    return ret;
  }

  @Override
  public Concept asConcept() {
    return semantics;
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptionType, observerSemantics, urn);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    ObservableImpl other = (ObservableImpl) obj;
    return descriptionType == other.descriptionType
        && Objects.equals(observerSemantics, other.observerSemantics)
        && Objects.equals(urn, other.urn);
  }

  @Override
  public String toString() {
    return "(O) " + urn;
  }
}
