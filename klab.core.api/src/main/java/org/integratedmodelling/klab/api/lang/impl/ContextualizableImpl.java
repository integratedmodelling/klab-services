package org.integratedmodelling.klab.api.lang.impl;

import java.util.ArrayList;
import java.util.Collection;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.impl.kim.KimStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.KimClassification;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

public class ContextualizableImpl extends KimStatementImpl implements Contextualizable {

    private static final long serialVersionUID = -1700963983184974464L;

    private Type type;
    private String targetId;
    private KimObservable target;
    private String mediationTargetId;
    private String language;
    private Literal literal;
    private ServiceCall serviceCall;
    private ExpressionCode expression;
    private KimClassification classification;
    private KimLookupTable lookupTable;
    private String accordingTo;
    private String urn;
    private Collection<Pair<String, Artifact.Type>> inputs;
    private Parameters<String> parameters = Parameters.create();
    private Collection<String> interactiveParameters = new ArrayList<>();
    private Contextualizable condition;
    private Pair<ValueMediator, ValueMediator> conversion;
    private boolean negated;
    private boolean mediation;
    private Geometry geometry;
    private boolean variable;
//    private boolean isFinal;
    private boolean empty;

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
    public Literal getLiteral() {
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

    @Override
    public String getUrn() {
        return this.urn;
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

//    @Override
//    public boolean isFinal() {
//        return this.isFinal;
//    }

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

    public void setLiteral(Literal literal) {
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

//    public void setFinal(boolean isFinal) {
//        this.isFinal = isFinal;
//    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

}
