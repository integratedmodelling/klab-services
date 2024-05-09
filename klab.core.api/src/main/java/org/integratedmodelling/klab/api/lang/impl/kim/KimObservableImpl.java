package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Observable.ResolutionException;
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
	private Literal value;
	private Literal defaultValue;
	private List<Observable.ResolutionException> resolutionExceptions = new ArrayList<>();
	private List<Pair<ValueOperator, Literal>> valueOperators = new ArrayList<>();
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
	public Literal getValue() {
		return this.value;
	}

	@Override
	public Literal getDefaultValue() {
		return this.defaultValue;
	}

	@Override
	public Collection<ResolutionException> getResolutionExceptions() {
		return this.resolutionExceptions;
	}

	@Override
	public List<Pair<ValueOperator, Literal>> getValueOperators() {
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

	public void setValue(Literal value) {
		this.value = value;
	}

	public void setDefaultValue(Literal defaultValue) {
		this.defaultValue = defaultValue;
	}

	public void setResolutionExceptions(List<ResolutionException> resolutionExceptions) {
		this.resolutionExceptions = resolutionExceptions;
	}

	public void setValueOperators(List<Pair<ValueOperator, Literal>> valueOperators) {
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
	public int hashCode() {
		return Objects.hash(urn);
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
	public void visit(KlabStatementVisitor visitor) {

	}
}
