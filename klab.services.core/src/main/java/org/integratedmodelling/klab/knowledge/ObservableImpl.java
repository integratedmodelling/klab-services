package org.integratedmodelling.klab.knowledge;

import java.util.Collection;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.impl.Range;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.KValueMediator;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Activity.Description;
import org.integratedmodelling.klab.utils.CamelCase;
import org.springframework.util.StringUtils;

public class ObservableImpl implements Observable {

    private static final long serialVersionUID = 6188649888474774359L;

    private Concept semantics;
    private Version version;
    private DirectObservation observer;
    private Activity.Description descriptionType;
    private Artifact.Type artifactType;
    private boolean isAbstract;
    private String urn;
    private Unit unit;
    private Currency currency;
    private Range range;
    private String url;

    private boolean specialized;

    private boolean dereified;

    private Map<Concept, Concept> resolvedPredicates;

    private Collection<Concept> abstractPredicates;

    private Collection<Concept> contextualRoles;

    private Resolution resolution;

    private boolean global;

    private boolean optional;

    private boolean generic;

    private Collection<ResolutionException> resolutionExceptions;

    private Literal defaultValue;

    private Literal value;

    private String statedName;

    private Collection<Annotation> annotations;

    private Collection<Pair<ValueOperator, Object>> valueOperators;

    private String referenceName;

    private String name;

    private String namespace;
    
    private boolean mustContextualizeAtResolution;
    private Concept targetPredicate;
    private boolean distributedInherency;
    private Concept temporalInherent;
    private String dereifiedAttribute;
    private Observable incarnatedAbstractObservable;
    private Observable deferredTarget;
    
    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public Semantics semantics() {
        return semantics;
    }

    @Override
    public Semantics domain() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean is(Semantics other) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean is(SemanticType type) {
        return semantics.is(type);
    }

    @Override
    public boolean isAbstract() {
        return isAbstract;
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
    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
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
    public Collection<Pair<ValueOperator, Object>> getValueOperators() {
        return valueOperators;
    }

    @Override
    public Collection<Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public Collection<Concept> abstractPredicates() {
        return abstractPredicates;
    }

    @Override
    public Description getDescriptionType() {
        return this.descriptionType;
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
    public KValueMediator mediator() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Literal getValue() {
        return value;
    }

    @Override
    public Concept context() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept inherent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept temporalInherent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Literal getDefaultValue() {
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
    public DirectObservation getObserver() {
        return observer;
    }

    @Override
    public boolean isGlobal() {
        return global;
    }

    @Override
    public Resolution getResolution() {
        return resolution;
    }

    @Override
    public Collection<Concept> getContextualRoles() {
        return contextualRoles;
    }

    @Override
    public boolean resolves(Observable other, Concept context) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> getAbstractPredicates() {
        return abstractPredicates;
    }

    @Override
    public Map<Concept, Concept> getResolvedPredicates() {
        return resolvedPredicates;
    }

    @Override
    public boolean isDereified() {
        return dereified;
    }

    @Override
    public boolean isSpecialized() {
        return specialized;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public void setSemantics(ConceptImpl semantics) {
        this.semantics = semantics;
    }

    public void setObserver(DirectObservation observer) {
        this.observer = observer;
    }

    public void setDescriptionType(Activity.Description descriptionType) {
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

        String ret = getSemantics().displayName();

        for (Pair<ValueOperator, Object> operator : getValueOperators()) {

            ret += StringUtils.capitalize(operator.getFirst().declaration.replace(' ', '_'));

            if (operator.getSecond() instanceof Concept) {
                ret += ((Concept) operator.getSecond()).displayName();
            } else if (operator.getSecond() instanceof Observable) {
                ret += ((Observable) operator.getSecond()).displayName();
            } else {
                ret += "_" + operator.getSecond().toString().replace(' ', '_');
            }
        }
        return ret;
    }

    @Override
    public String displayLabel() {
        String ret = displayName();
        if (!ret.contains(" ")) {
            ret = StringUtils.capitalize(CamelCase.toLowerCase(ret, ' '));
        }
        return ret;
    }

    @Override
    public String codeName() {
        return getSemantics().codeName();
    }

    @Override
    public Builder builder() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setSpecialized(boolean specialized) {
        this.specialized = specialized;
    }

    public void setDereified(boolean dereified) {
        this.dereified = dereified;
    }

    public void setResolvedPredicates(Map<Concept, Concept> resolvedPredicates) {
        this.resolvedPredicates = resolvedPredicates;
    }

    public void setAbstractPredicates(Collection<Concept> abstractPredicates) {
        this.abstractPredicates = abstractPredicates;
    }

    public void setContextualRoles(Collection<Concept> contextualRoles) {
        this.contextualRoles = contextualRoles;
    }

    public void setResolution(Resolution resolution) {
        this.resolution = resolution;
    }

    public void setGlobal(boolean global) {
        this.global = global;
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

    public void setDefaultValue(Literal defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setValue(Literal value) {
        this.value = value;
    }

    public void setStatedName(String statedName) {
        this.statedName = statedName;
    }

    public void setAnnotations(Collection<Annotation> annotations) {
        this.annotations = annotations;
    }

    public void setValueOperators(Collection<Pair<ValueOperator, Object>> valueOperators) {
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

    @Override
    public boolean isMustContextualizeAtResolution() {
        return mustContextualizeAtResolution;
    }

    public void setMustContextualizeAtResolution(boolean mustContextualizeAtResolution) {
        this.mustContextualizeAtResolution = mustContextualizeAtResolution;
    }

    @Override
    public Concept getTargetPredicate() {
        return targetPredicate;
    }

    public void setTargetPredicate(Concept targetPredicate) {
        this.targetPredicate = targetPredicate;
    }

    @Override
    public boolean isDistributedInherency() {
        return distributedInherency;
    }

    public void setDistributedInherency(boolean distributedInherency) {
        this.distributedInherency = distributedInherency;
    }

    @Override
    public Concept getTemporalInherent() {
        return temporalInherent;
    }

    public void setTemporalInherent(Concept temporalInherent) {
        this.temporalInherent = temporalInherent;
    }

    @Override
    public String getDereifiedAttribute() {
        return dereifiedAttribute;
    }

    public void setDereifiedAttribute(String dereifiedAttribute) {
        this.dereifiedAttribute = dereifiedAttribute;
    }

    @Override
    public Observable getIncarnatedAbstractObservable() {
        return incarnatedAbstractObservable;
    }

    public void setIncarnatedAbstractObservable(Observable incarnatedAbstractObservable) {
        this.incarnatedAbstractObservable = incarnatedAbstractObservable;
    }

    @Override
    public Observable getDeferredTarget() {
        return deferredTarget;
    }

    public void setDeferredTarget(Observable deferredTarget) {
        this.deferredTarget = deferredTarget;
    }

    public void setSemantics(Concept semantics) {
        this.semantics = semantics;
    }

    @Override
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
