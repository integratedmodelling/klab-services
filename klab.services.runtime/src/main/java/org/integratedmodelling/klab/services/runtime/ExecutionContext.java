package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/**
 * Object that follows the execution of the actuators. Each run produces a new context that is the one for the
 * next execution.
 */
public class ExecutionContext {

    private final Actuator actuator;
    private final ContextScope scope;
    private final Observation observation;
    private final DigitalTwin digitalTwin;
    private boolean empty;

    public ExecutionContext(Actuator rootActuator, ServiceContextScope scope, DigitalTwin digitalTwin) {
        this.actuator = rootActuator;
        this.scope = scope;
        this.digitalTwin = digitalTwin;
        // set or create the observation
        if (actuator.getId() != Observation.UNASSIGNED_ID) {
        }
    }

    public String statusLine() {
        return "Execution terminated";
    }

    public Klab.ErrorCode errorCode() {
        return Klab.ErrorCode.NO_ERROR;
    }

    public Klab.ErrorContext errorContext() {
        return Klab.ErrorContext.RUNTIME;
    }

    /**
     * TODO this should be something recognized by the notification to fully describe the context of
     * execution.
     *
     * @return
     */
    public Object statusInfo() {
        return null;
    }

    public boolean isEmpty() {
        return this.empty;
    }

    public ExecutionContext runActuator(Actuator actuator) {
        return this;
    }

}
