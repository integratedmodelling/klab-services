package org.integratedmodelling.common.runtime;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowImpl implements Dataflow<Observation> {

    @Serial
    private static final long serialVersionUID = 873406284216826384L;

    private boolean empty;
    private Coverage coverage;
    private Parameters<String> resources = Parameters.create();
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

    @Override
    public Parameters<String> getResources() {
        return resources;
    }

    public void setResources(Parameters<String> resources) {
        this.resources = resources;
    }

    public void add(Dataflow<Observation> dataflow, ContextScope scope) {

        /*
         * Find the "hook point" using the observation ID
         */
        if (scope.getContextObservation() == null) {
            computation.addAll(dataflow.getComputation());
        } else {
            Actuator actuator = findActuator(scope.getContextObservation().getId());
            if (actuator == null) {
                throw new KlabIllegalArgumentException("cannot add dataflow: observation ID does not correspond to an actuator");
            }
            actuator.getChildren().addAll(dataflow.getComputation());
        }

        computeCoverage();

    }

    private Actuator findActuator(long id) {
        for (Actuator actuator : getComputation()) {
            Actuator found = findActuator(actuator, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Actuator findActuator(Actuator actuator, long id) {
        if (id == actuator.getId()) {
            return actuator;
        }
        for (Actuator child : actuator.getChildren()) {
            Actuator found = findActuator(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public void computeCoverage() {
        // TODO Auto-generated method stub

    }

}
