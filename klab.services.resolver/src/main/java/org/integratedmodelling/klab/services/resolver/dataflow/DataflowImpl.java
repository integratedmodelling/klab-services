package org.integratedmodelling.klab.services.resolver.dataflow;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowImpl implements Dataflow<Observation> {

    private static final long serialVersionUID = 873406284216826384L;

    private boolean empty;
    private Coverage coverage;
    private List<Actuator> computation = new ArrayList<>();

    @Override
    public boolean isEmpty() {
        return this.empty;
    }

    @Override
    public Coverage getCoverage() {
        return this.coverage;
    }

    @Override
    public List<Actuator> getComputation() {
        return this.computation;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public void setCoverage(Coverage coverage) {
        this.coverage = coverage;
    }

    public void setComputation(List<Actuator> computation) {
        this.computation = computation;
    }

}
