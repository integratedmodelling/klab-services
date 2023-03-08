package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimRestriction;

public class KimRestrictionImpl extends KimStatementImpl implements KimRestriction {

    private static final long serialVersionUID = 1204374369797711459L;
    private Type type;
    private Cardinality cardinality;
    private int numerosity;
    private KimConcept filler;
    private KimConcept targetSubject;
    private Object value;
    private Number range;

    @Override
    public Type getType() {
        return this.type;
    }

    @Override
    public Cardinality getCardinality() {
        return this.cardinality;
    }

    @Override
    public int getNumerosity() {
        return this.numerosity;
    }

    @Override
    public KimConcept getFiller() {
        return this.filler;
    }

    @Override
    public KimConcept getTargetSubject() {
        return this.targetSubject;
    }

    @Override
    public Object getValue() {
        // TODO may need a Literal shuttle type
        return this.value;
    }

    @Override
    public Number getRange() {
        return this.range;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setCardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
    }

    public void setNumerosity(int numerosity) {
        this.numerosity = numerosity;
    }

    public void setFiller(KimConcept filler) {
        this.filler = filler;
    }

    public void setTargetSubject(KimConcept targetSubject) {
        this.targetSubject = targetSubject;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setRange(Number range) {
        this.range = range;
    }

}
