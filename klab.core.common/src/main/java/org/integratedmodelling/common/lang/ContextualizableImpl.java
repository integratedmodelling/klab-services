package org.integratedmodelling.common.lang;

import org.integratedmodelling.common.lang.kim.KimStatementImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;

public class ContextualizableImpl extends KimStatementImpl implements Contextualizable {

    @Serial
    private static final long serialVersionUID = -1700963983184974464L;

    private Type type;
    private String targetId;
    private KimObservable target;
    private String mediationTargetId;
    private String language;
    private Object literal;
    private ServiceCall serviceCall;
    private ExpressionCode expression;
    private KimClassification classification;
    private KimLookupTable lookupTable;
    private String accordingTo;
    private String resourceUrn;
    private Collection<Pair<String, Artifact.Type>> inputs;
    private Parameters<String> parameters = Parameters.create();
    private Collection<String> interactiveParameters = new ArrayList<>();
    private Contextualizable condition;
    private ObservationStrategy observationStrategy;
    private Pair<ValueMediator, ValueMediator> conversion;
    private boolean negated;
    private boolean mediation;
    private Geometry geometry;
    private boolean variable;
    private boolean empty;
    private String urn;

    public ContextualizableImpl() {
    }

    public ContextualizableImpl(ServiceCall serviceCall) {
        this.serviceCall = serviceCall;
    }

    public ContextualizableImpl(String resourceUrn) {
        this.resourceUrn = resourceUrn;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    @Override
    public String getTargetId() {
        return this.targetId;
    }

    @Override
    public KimObservable getTarget() {
        return this.target;
    }

    @Override
    public String getMediationTargetId() {
        return this.mediationTargetId;
    }

    @Override
    public String getLanguage() {
        return this.language;
    }

    @Override
    public Object getLiteral() {
        return this.literal;
    }

    @Override
    public ServiceCall getServiceCall() {
        return this.serviceCall;
    }

    @Override
    public ExpressionCode getExpression() {
        return this.expression;
    }

    @Override
    public KimClassification getClassification() {
        return this.classification;
    }

    @Override
    public KimLookupTable getLookupTable() {
        return this.lookupTable;
    }

    @Override
    public String getAccordingTo() {
        return this.accordingTo;
    }

    public String getUrn() {
        return urn;
    }

    @Override
    public String getResourceUrn() {
        return this.resourceUrn;
    }

    @Override
    public Collection<Pair<String, Artifact.Type>> getInputs() {
        return this.inputs;
    }

    @Override
    public Parameters<String> getParameters() {
        return this.parameters;
    }

    @Override
    public Collection<String> getInteractiveParameters() {
        return this.interactiveParameters;
    }

    @Override
    public Contextualizable getCondition() {
        return this.condition;
    }

    @Override
    public Pair<ValueMediator, ValueMediator> getConversion() {
        return this.conversion;
    }

    @Override
    public boolean isNegated() {
        return this.negated;
    }

    @Override
    public boolean isMediation() {
        return this.mediation;
    }

    @Override
    public Geometry getGeometry() {
        return this.geometry;
    }

    @Override
    public boolean isVariable() {
        return this.variable;
    }

    @Override
    public boolean isEmpty() {
        return this.empty;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void setTarget(KimObservable target) {
        this.target = target;
    }

    public void setMediationTargetId(String mediationTargetId) {
        this.mediationTargetId = mediationTargetId;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setLiteral(Object literal) {
        this.literal = literal;
    }

    public void setServiceCall(ServiceCall serviceCall) {
        this.serviceCall = serviceCall;
    }

    public void setExpression(ExpressionCode expression) {
        this.expression = expression;
    }

    public void setClassification(KimClassification classification) {
        this.classification = classification;
    }

    public void setLookupTable(KimLookupTable lookupTable) {
        this.lookupTable = lookupTable;
    }

    public void setAccordingTo(String accordingTo) {
        this.accordingTo = accordingTo;
    }

    public void setResourceUrn(String urn) {
        this.resourceUrn = urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setInputs(Collection<Pair<String, Artifact.Type>> inputs) {
        this.inputs = inputs;
    }

    public void setParameters(Parameters<String> parameters) {
        this.parameters = parameters;
    }

    public void setInteractiveParameters(Collection<String> interactiveParameters) {
        this.interactiveParameters = interactiveParameters;
    }

    public void setCondition(Contextualizable condition) {
        this.condition = condition;
    }

    public void setConversion(Pair<ValueMediator, ValueMediator> conversion) {
        this.conversion = conversion;
    }

    public void setNegated(boolean negated) {
        this.negated = negated;
    }

    public void setMediation(boolean mediation) {
        this.mediation = mediation;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public void setVariable(boolean variable) {
        this.variable = variable;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    @Override
    public ObservationStrategy getObservationStrategy() {
        return observationStrategy;
    }

    public void setObservationStrategy(ObservationStrategy observationStrategy) {
        this.observationStrategy = observationStrategy;
    }

    @Override
    public void visit(Visitor visitor) {

    }
}
