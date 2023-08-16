package org.integratedmodelling.klab.services.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

/**
 * Gather information about a dataflow into one of these, so that we can validate everything in one place and
 * coherently. Should also become the basis to make cost estimates and select a more appropriate runtime if the scope
 * allows.
 */
public class DataflowInfo {
    List<ServiceCall> calls = new ArrayList<>();
    Map<String, Actuator> actuators = new HashMap<>();

    public boolean validate(ContextScope scope) {
        // TODO
        return true;
    }

    public void notifyActuator(Actuator actuator) {
        this.calls.addAll(actuator.getComputation());
        // TODO harvest coverage
        this.actuators.put(actuator.getId(), actuator);
    }

    public boolean containsActuator(String id) {
        return actuators.containsKey(id);
    }
}
