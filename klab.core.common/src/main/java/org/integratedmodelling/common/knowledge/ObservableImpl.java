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
import org.integratedmodelling.klab.api.services.Reasoner;
import org.springframework.util.StringUtils;

import java.util.*;

public class ObservableImpl implements Observable {

    private static final long serialVersionUID = 6188649888474774359L;

    private Concept semantics;
    private Concept observer;
    private DescriptionType descriptionType;
    private Artifact.Type artifactType;
    private boolean isAbstract;
    private String urn;
    private Unit unit;
    private Currency currency;
    private NumericRange range;
    //    private Map<Concept, Concept> resolvedPredicates;
//    private Collection<Concept> contextualRoles;
//    private Resolution resolution;
    private boolean optional;
    private boolean generic;
    private boolean collective;
    private Collection<ResolutionException> resolutionExceptions = EnumSet.noneOf(ResolutionException.class);
    private Object defaultValue;
    private Object value;
    private String statedName;
    private List<Annotation> annotations = new ArrayList<>();
    private List<Pair<ValueOperator, Object>> valueOperators = new ArrayList<>();
    private String referenceName;
    private String name;
    private String namespace;
//    private boolean distributedInherency;
//    private String dereifiedAttribute;
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

    public ObservableImpl() {
    }

    public ObservableImpl(Concept concept) {
        setSemantics(concept);

    }

    private ObservableImpl(ObservableImpl other) {
        this.semantics = other.semantics;
        this.observer = other.observer;
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
        this.resolutionExceptions = other.resolutionExceptions;
        this.defaultValue = other.defaultValue;
        this.value = other.value;
        this.statedName = other.statedName;
        this.annotations.addAll(other.annotations);
        this.valueOperators = other.valueOperators;
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
    public Unit getUnit() {
        return unit;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public List<Pair<ValueOperator, Object>> getValueOperators() {
        return valueOperators;
    }

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

    @Override
    public Object getValue() {
        return value;
    }

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
    public Collection<ResolutionException> getResolutionExceptions() {
        return resolutionExceptions;
    }

    @Override
    public boolean isGeneric() {
        return generic;
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public Concept getObserver() {
        return observer;
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

    public void setObserver(Concept observer) {
        this.observer = observer;
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

        for (Pair<ValueOperator, Object> operator : getValueOperators()) {

            ret.append(StringUtils.capitalize(operator.getFirst().declaration.replace(' ', '_')));

            if (operator.getSecond() instanceof KimConcept kimConcept) {
                // FIXME use displayName for the associated concept! needs the service
                ret.append(kimConcept.getName());
            } else if (operator.getSecond() instanceof KimObservable kimObservable) {
                // FIXME use displayName for the associated observable! needs the service
                ret.append(kimObservable.getCodeName());
            } else {
                ret.append("_").append(operator.getSecond().toString().replace(' ', '_'));
            }
        }
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

    public void setResolutionExceptions(Collection<ResolutionException> resolutionExceptions) {
        this.resolutionExceptions = resolutionExceptions;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setStatedName(String statedName) {
        this.statedName = statedName;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public void setValueOperators(List<Pair<ValueOperator, Object>> valueOperators) {
        this.valueOperators = valueOperators;
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
//
//    @Override
//    public boolean isDistributedInherency() {
//        return distributedInherency;
//    }
//
//    public void setDistributedInherency(boolean distributedInherency) {
//        this.distributedInherency = distributedInherency;
//    }
//
//    @Override
//    public String getDereifiedAttribute() {
//        return dereifiedAttribute;
//    }
//
//    public void setDereifiedAttribute(String dereifiedAttribute) {
//        this.dereifiedAttribute = dereifiedAttribute;
//    }

    public void setSemantics(Concept semantics) {
        this.semantics = semantics;
    }

    public static ObservableImpl promote(Concept concept, Scope scope) {

        ObservableImpl ret = new ObservableImpl();

        ret.semantics = concept;
        ret.urn = concept.getUrn();
        ret.isAbstract = concept.isAbstract();
        // ret.generic = concept.is(SemanticType.ROLE);
        ret.referenceName = concept.getReferenceName();
        ret.name = concept.codeName();
        if (ret.referenceName == null) {
            // only happens with non-standard observables from system ontologies
            ret.referenceName = concept.getNamespace() + "_" + concept.getName();
        }
        ret.artifactType = Artifact.Type.forSemantics(concept.getType());

//        var reasoner = scope.getService(Reasoner.class);
        // FIXME unsure of all this
//        boolean distributedDescription = concept.is(SemanticType.TRAIT)
//                                         ? reasoner.inherent(concept) != null
//                                         : concept.isCollective();

        ret.collective = concept.isCollective();
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
    public boolean isCollective() {
        return collective;
    }

    public void setCollective(boolean collective) {
        this.collective = collective;
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptionType, observer, urn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ObservableImpl other = (ObservableImpl) obj;
        return descriptionType == other.descriptionType && Objects.equals(observer, other.observer)
                && Objects.equals(urn, other.urn);
    }

    @Override
    public String toString() {
        return org.integratedmodelling.common.utils.Utils.Strings.capitalize(getDescriptionType().getVerbalForm().toLowerCase()) + " " + urn;
    }

}
