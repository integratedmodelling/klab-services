package org.integratedmodelling.common.lang.kim;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Observable.ResolutionDirective;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

import java.io.Serial;
import java.util.*;

public class KimObservableImpl extends KimStatementImpl implements KimObservable {

    @Serial
    private static final long serialVersionUID = -727467882879783393L;

    private KimConcept semantics;
    private NumericRangeImpl range;
    private String unit;
    private String currency;
    private String formalName;
    private Object value;
    private Object defaultValue;
    private List<ResolutionDirective> resolutionDirectives = new ArrayList<>();
    private List<Pair<ValueOperator, Object>> valueOperators = new ArrayList<>();
    private String attributeIdentifier;
    private boolean optional;
    private String modelReference;
    private Type nonSemanticType;
    private String codeName;
    private boolean generic;
    private String referenceName;
    private boolean global;
    private boolean exclusive;
    private String urn;
    private Version version;
    private String pattern;
    private Collection<String> patternVariables = new HashSet<>();

    @Override
    public KimConcept getSemantics() {
        return this.semantics;
    }

    @Override
    public NumericRangeImpl getRange() {
        return this.range;
    }

    @Override
    public String getUnit() {
        return this.unit;
    }

    @Override
    public String getCurrency() {
        return this.currency;
    }

    @Override
    public String getFormalName() {
        return this.formalName;
    }

    @Override
    public Object getValue() {
        return this.value;
    }

    @Override
    public Object getDefaultValue() {
        return this.defaultValue;
    }

    @Override
    public Collection<ResolutionDirective> getResolutionExceptions() {
        return this.resolutionDirectives;
    }

    @Override
    public List<Pair<ValueOperator, Object>> getValueOperators() {
        return this.valueOperators;
    }

    @Override
    public String getAttributeIdentifier() {
        return this.attributeIdentifier;
    }

    @Override
    public boolean isOptional() {
        return this.optional;
    }

    @Override
    public String getModelReference() {
        return this.modelReference;
    }

    @Override
    public Type getNonSemanticType() {
        return this.nonSemanticType;
    }

    @Override
    public String getCodeName() {
        return this.codeName;
    }

    @Override
    public boolean isGeneric() {
        return this.generic;
    }

    @Override
    public boolean isGlobal() {
        return this.global;
    }

    @Override
    public boolean isExclusive() {
        return this.exclusive;
    }

    @Override
    public Set<String> namespaces() {
        var ret = new HashSet<String>();
        return ret;
    }

    public void setSemantics(KimConcept semantics) {
        this.semantics = semantics;
    }

    public void setRange(NumericRangeImpl range) {
        this.range = range;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setFormalName(String formalName) {
        this.formalName = formalName;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setResolutionExceptions(List<ResolutionDirective> resolutionDirectives) {
        this.resolutionDirectives = resolutionDirectives;
    }

    public void setValueOperators(List<Pair<ValueOperator, Object>> valueOperators) {
        this.valueOperators = valueOperators;
    }

    public void setAttributeIdentifier(String attributeIdentifier) {
        this.attributeIdentifier = attributeIdentifier;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public void setModelReference(String modelReference) {
        this.modelReference = modelReference;
    }

    public void setNonSemanticType(Type nonSemanticType) {
        this.nonSemanticType = nonSemanticType;
    }

    public void setCodeName(String codeName) {
        this.codeName = codeName;
    }

    public void setGeneric(boolean generic) {
        this.generic = generic;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    @Override
    public String toString() {
        return this.urn;
    }

    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    @Override
    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern == null ? urn : pattern);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        KimObservableImpl other = (KimObservableImpl) obj;
        return Objects.equals(urn, other.urn);
    }

    @Override
    public void visit(Visitor visitor) {

        for (var annotation : getAnnotations()) {
            visitor.visitAnnotation(annotation);
        }

        if (semantics != null) {
            semantics.visit(visitor);
        }

        for (var vop : valueOperators) {
            // TODO literal should be KimLiteral and be visited
        }

        //		private KimConcept semantics;
        //		private NumericRangeImpl range;
        //		private String unit;
        //		private String currency;
        //		private String formalName;
        //		private Literal value;
        //		private Literal defaultValue;
        //		private List<Observable.ResolutionException> resolutionExceptions = new ArrayList<>();
        //		private List<Pair<ValueOperator, Literal>> valueOperators = new ArrayList<>();
        //		private String attributeIdentifier;
        //		private boolean optional;
        //		private String modelReference;
        //		private Type nonSemanticType;
        //		private String codeName;
        //		private boolean generic;
        //		private String referenceName;
        //		private boolean global;
        //		private boolean exclusive;
        //		private String urn;
        //		private Version version;

        // TODO metadata etc.

    }

    @Override
    public Collection<String> getPatternVariables() {
        return patternVariables;
    }

    public void setPatternVariables(Collection<String> patternVariables) {
        this.patternVariables = patternVariables;
    }

}
