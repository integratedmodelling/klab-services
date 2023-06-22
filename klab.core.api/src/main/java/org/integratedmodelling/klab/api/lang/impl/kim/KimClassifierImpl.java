package org.integratedmodelling.klab.api.lang.impl.kim;

import java.util.ArrayList;

import org.integratedmodelling.klab.api.data.mediation.impl.Range;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimDate;
import org.integratedmodelling.klab.api.lang.kim.KimQuantity;

/**
 * Syntactic bean for a k.IM classifier, used in both classifications and lookup tables.
 * 
 * @author ferdinando.villa
 *
 */
public class KimClassifierImpl extends KimStatementImpl implements KimClassifier {

    private static final long serialVersionUID = 8284840092691497201L;
    private boolean catchAll;
    private boolean catchAnything;
    private boolean negated;
    private KimConcept conceptMatch;
    private Double numberMatch;
    private Boolean booleanMatch;
    private ArrayList<KimClassifier> classifierMatches;
    private Range intervalMatch;
    private boolean nullMatch;
    private ExpressionCode expressionMatch;
    private String stringMatch;
    private ArrayList<KimConcept> conceptMatches;
    private KimQuantity quantityMatch;
    private KimDate dateMatch;
    private Type type;

    @Override
    public boolean isCatchAll() {
        return catchAll;
    }

    @Override
    public boolean isCatchAnything() {
        return catchAnything;
    }

    @Override
    public boolean isNegated() {
        return negated;
    }

    @Override
    public KimConcept getConceptMatch() {
        return conceptMatch;
    }

    @Override
    public Double getNumberMatch() {
        return numberMatch;
    }

    @Override
    public Boolean getBooleanMatch() {
        return booleanMatch;
    }

    @Override
    public ArrayList<KimClassifier> getClassifierMatches() {
        return classifierMatches;
    }

    @Override
    public Range getIntervalMatch() {
        return intervalMatch;
    }

    @Override
    public boolean isNullMatch() {
        return nullMatch;
    }

    @Override
    public ExpressionCode getExpressionMatch() {
        return expressionMatch;
    }

    @Override
    public String getStringMatch() {
        return stringMatch;
    }

    @Override
    public ArrayList<KimConcept> getConceptMatches() {
        return conceptMatches;
    }

    @Override
    public KimQuantity getQuantityMatch() {
        return quantityMatch;
    }

    @Override
    public KimDate getDateMatch() {
        return dateMatch;
    }

    @Override
    public Type getType() {
        return type;
    }

    public void setCatchAll(boolean catchAll) {
        this.catchAll = catchAll;
    }

    public void setCatchAnything(boolean catchAnything) {
        this.catchAnything = catchAnything;
    }

    public void setNegated(boolean negated) {
        this.negated = negated;
    }

    public void setConceptMatch(KimConcept conceptMatch) {
        this.conceptMatch = conceptMatch;
    }

    public void setNumberMatch(Double numberMatch) {
        this.numberMatch = numberMatch;
    }

    public void setBooleanMatch(Boolean booleanMatch) {
        this.booleanMatch = booleanMatch;
    }

    public void setClassifierMatches(ArrayList<KimClassifier> classifierMatches) {
        this.classifierMatches = classifierMatches;
    }

    public void setIntervalMatch(Range intervalMatch) {
        this.intervalMatch = intervalMatch;
    }

    public void setNullMatch(boolean nullMatch) {
        this.nullMatch = nullMatch;
    }

    public void setExpressionMatch(ExpressionCode expressionMatch) {
        this.expressionMatch = expressionMatch;
    }

    public void setStringMatch(String stringMatch) {
        this.stringMatch = stringMatch;
    }

    public void setConceptMatches(ArrayList<KimConcept> conceptMatches) {
        this.conceptMatches = conceptMatches;
    }

    public void setQuantityMatch(KimQuantity quantityMatch) {
        this.quantityMatch = quantityMatch;
    }

    public void setDateMatch(KimDate dateMatch) {
        this.dateMatch = dateMatch;
    }

    public void setType(Type type) {
        this.type = type;
    }

}
