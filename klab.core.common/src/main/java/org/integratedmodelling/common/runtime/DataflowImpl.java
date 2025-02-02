package org.integratedmodelling.common.runtime;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowImpl implements Dataflow {

    @Serial
    private static final long serialVersionUID = 873406284216826384L;

    private boolean empty;
    private ResourceSet requirements;
    private Coverage coverage;
    private List<Actuator> computation = new ArrayList<>();
    private Observation target;
    private double resolvedCoverage;
    @Deprecated private long id;

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
    public ResourceSet getRequirements() {
        return requirements;
    }

    public void setRequirements(ResourceSet requirements) {
        this.requirements = requirements;
    }

    public void add(Dataflow dataflow, ContextScope scope) {

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

    @Override
    public Observation getTarget() {
        return target;
    }

    public void setTarget(Observation target) {
        this.target = target;
    }

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public double getResolvedCoverage() {
        return resolvedCoverage;
    }

    public void setResolvedCoverage(double resolvedCoverage) {
        this.resolvedCoverage = resolvedCoverage;
    }
}
