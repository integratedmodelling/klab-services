package org.integratedmodelling.klab.api.lang.kim.impl;

import java.util.List;

import org.integratedmodelling.klab.api.lang.ActionTrigger;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.KimAction;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

public class KimActionImpl extends KimStatementImpl implements KimAction {

    private static final long serialVersionUID = -6208591665170784114L;

    private Type type;
    private ActionTrigger trigger;
    private String targetStateId;
    private List<KimConcept> triggeringEvents;
    private List<Contextualizable> computation;

    @Override
    public KimAction.Type getType() {
        return this.type;
    }

    @Override
    public ActionTrigger getTrigger() {
        return this.trigger;
    }

    @Override
    public String getTargetStateId() {
        return this.targetStateId;
    }

    @Override
    public List<KimConcept> getTriggeringEvents() {
        return this.triggeringEvents;
    }

    @Override
    public List<Contextualizable> getComputation() {
        return this.computation;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setTrigger(ActionTrigger trigger) {
        this.trigger = trigger;
    }

    public void setTargetStateId(String targetStateId) {
        this.targetStateId = targetStateId;
    }

    public void setTriggeringEvents(List<KimConcept> triggeringEvents) {
        this.triggeringEvents = triggeringEvents;
    }

    public void setComputation(List<Contextualizable> computation) {
        this.computation = computation;
    }

}
