package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.LanguageService;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.util.ArrayList;
import java.util.List;

/**
 * Object that follows the execution of the actuators. Each run produces a new context that is the one for the
 * next execution.
 */
public class ExecutionSequence {

    private final ServiceContextScope scope;
    private final DigitalTwin digitalTwin;
    private final Language languageService;
    private final ComponentRegistry componentRegistry;
    private List<List<ExecutorOperation>> sequence = new ArrayList<>();
    private boolean empty;

    private ExecutionSequence(List<Pair<Actuator, Integer>> pairs, ServiceContextScope contextScope,
                             DigitalTwin digitalTwin, ComponentRegistry componentRegistry) {
        this.scope = contextScope;
        this.digitalTwin = digitalTwin;
        this.languageService = ServiceConfiguration.INSTANCE.getService(Language.class);
        this.componentRegistry = componentRegistry;
        List<ExecutorOperation> current = null;
        int currentGroup = -1;
        for (var pair : pairs) {
            if (currentGroup != pair.getSecond()) {
                if (current != null) {
                    sequence.add(current);
                }
                current = new ArrayList<>();
            }
            currentGroup = pair.getSecond();
            current.add(new ExecutorOperation(pair.getFirst()));
        }

        if (current != null) {
            sequence.add(current);
        }

    }

    public static ExecutionSequence compile(List<Pair<Actuator, Integer>> pairs, ServiceContextScope contextScope,
                                            DigitalTwin digitalTwin, ComponentRegistry componentRegistry) {
        return new ExecutionSequence(pairs, contextScope, digitalTwin, componentRegistry);
    }

    public void run() {
        System.out.println("AHA");
    }

    class ExecutorOperation {

        private final long id;
        protected List<ServiceCall> serviceCallList = new ArrayList<>();
        private boolean scalar;

        public ExecutorOperation(Actuator actuator) {
            this.id = actuator.getId();
            compile(actuator);
        }

        private void compile(Actuator actuator) {

            // TODO separate scalar calls into groups and compile them into one assembled method
            for (var call : actuator.getComputation()) {
                var descriptor = componentRegistry.getFunctionDescriptor(call);
                System.out.println("DIO COLIFLOR");
            }
        }


    }

    //    public ExecutionSequence(Actuator rootActuator, ServiceContextScope scope, DigitalTwin
    //    digitalTwin) {
    //        this.actuator = rootActuator;
    //        this.scope = scope;
    //        this.digitalTwin = digitalTwin;
    //        // set or create the observation
    //        if (actuator.getId() != Observation.UNASSIGNED_ID) {
    //            this.observation = scope.getObservation(actuator.getId());
    //        } else {
    //            throw new KlabUnimplementedException("Creating observations when running external
    //            dataflows");
    //            // TODO this is needed for external dataflows
    //        }
    //    }

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

    public ExecutionSequence runActuator(Actuator actuator) {
        return this;
    }

}
