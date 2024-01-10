package org.integratedmodelling.klab.api.lang.impl.kim;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.lang.Action;
import org.integratedmodelling.klab.api.lang.Contextualizable;

public class ActionImpl extends KimStatementImpl implements Action {

    private static final long serialVersionUID = -6208591665170784114L;

    private Type type;
    private Trigger trigger;
    private String targetStateId;
    private List<Contextualizable> computation = new ArrayList<>();
    private String urn;

    @Override
    public Action.Type getType() {
        return this.type;
    }

    @Override
    public Trigger getTrigger() {
        return this.trigger;
    }

    @Override
    public String getTargetStateId() {
        return this.targetStateId;
    }

    @Override
    public List<Contextualizable> getComputation() {
        return this.computation;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    public void setTargetStateId(String targetStateId) {
        this.targetStateId = targetStateId;
    }

    public void setComputation(List<Contextualizable> computation) {
        this.computation = computation;
    }

    @Override
    public String getUrn() {
        return this.urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }
}
