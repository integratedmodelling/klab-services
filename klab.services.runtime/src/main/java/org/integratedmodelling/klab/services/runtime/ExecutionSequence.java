package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.storage.BooleanStorage;
import org.integratedmodelling.klab.runtime.storage.DoubleStorage;
import org.integratedmodelling.klab.runtime.storage.FloatStorage;
import org.integratedmodelling.klab.runtime.storage.KeyedStorage;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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

    public static ExecutionSequence compile(List<Pair<Actuator, Integer>> pairs,
                                            ServiceContextScope contextScope, DigitalTwin digitalTwin,
                                            ComponentRegistry componentRegistry) {
        return new ExecutionSequence(pairs, contextScope, digitalTwin, componentRegistry);
    }

    public void run() {
        for (var operationGroup : sequence) {

        }
    }


    class ExecutorOperation {

        private final long id;
        private final Observation observation;
        protected List<Supplier<Boolean>> executors = new ArrayList<>();
        private boolean scalar;

        public ExecutorOperation(Actuator actuator) {
            this.id = actuator.getId();
            this.observation = scope.getObservation(this.id);
            compile(actuator);
        }

        private void compile(Actuator actuator) {

            ScalarMapper scalarMapper = null;

            // TODO separate scalar calls into groups and compile them into one assembled functor
            for (var call : actuator.getComputation()) {
                var descriptor = componentRegistry.getFunctionDescriptor(call);
                if (descriptor.serviceInfo.getGeometry().isScalar()) {

                    if (scalarMapper == null) {
                        scalarMapper = new ScalarMapper(observation, digitalTwin, scope);
                    }

                    /**
                     * Executor is a class containing all consecutive steps in a single method and
                     * calling whatever mapping strategy is configured in the scope, using a different
                     * class per strategy.
                     */
                    scalarMapper.add(call, descriptor);

                    System.out.println("SCALAR");
                } else {
                    if (scalarMapper != null) {
                        // offload the scalar mapping to the executors
                        executors.add(scalarMapper::run);
                        scalarMapper = null;
                    }

                    // we can be pretty sure that this will be a scale by now
                    var scale = Scale.create(observation.getGeometry());
                    Storage storage = digitalTwin.stateStorage().getExistingStorage(observation, Storage.class);
                    /**
                     * Create a runnable with matched parameters and have it set the context observation
                     */
                    List<Object> runArguments = new ArrayList<>();
                    if (descriptor.method != null) {
                        for (var argument : descriptor.method.getParameterTypes()) {
                            if (ContextScope.class.isAssignableFrom(argument)) {
                                // TODO consider wrapping into read-only delegating wrappers
                                runArguments.add(scope);
                            } else if (Scope.class.isAssignableFrom(argument)) {
                                runArguments.add(scope);
                            } else if (Observation.class.isAssignableFrom(argument)) {
                                runArguments.add(observation);
                            } else if (ServiceCall.class.isAssignableFrom(argument)) {
                                runArguments.add(call);
                            } else if (Parameters.class.isAssignableFrom(argument)) {
                                runArguments.add(call.getParameters());
                            } else if (DoubleStorage.class.isAssignableFrom(argument)) {
                                storage = digitalTwin.stateStorage().promoteStorage(observation, storage,
                                        DoubleStorage.class);
                                runArguments.add(storage);
                            } else if (FloatStorage.class.isAssignableFrom(argument)) {
                                storage = digitalTwin.stateStorage().promoteStorage(observation, storage,
                                        DoubleStorage.class);
                                runArguments.add(storage);
                            } else if (BooleanStorage.class.isAssignableFrom(argument)) {
                                storage = digitalTwin.stateStorage().promoteStorage(observation, storage,
                                        DoubleStorage.class);
                                runArguments.add(storage);
                            } else if (KeyedStorage.class.isAssignableFrom(argument)) {
                                storage = digitalTwin.stateStorage().promoteStorage(observation, storage,
                                        DoubleStorage.class);
                                runArguments.add(storage);
                            } else if (Scale.class.isAssignableFrom(argument)) {
                                runArguments.add(scale);
                            } else if (Geometry.class.isAssignableFrom(argument)) {
                                runArguments.add(scale);
                            } else if (Observable.class.isAssignableFrom(argument)) {
                                runArguments.add(observation.getObservable());
                            } else if (Space.class.isAssignableFrom(argument)) {
                                runArguments.add(scale.getSpace());
                            } else if (Time.class.isAssignableFrom(argument)) {
                                runArguments.add(scale.getTime());
                            } else {
                                scope.error("Cannot map argument of type " + argument.getCanonicalName()
                                        + " to known objects in call to " + call.getUrn());
                                runArguments.add(null);
                            }
                        }

                        if (descriptor.staticMethod) {
                            executors.add(() -> {
                                try {
                                    var context = descriptor.method.invoke(null, runArguments.toArray());
                                    setExecutionContext(context == null ? observation : context);
                                    return true;
                                } catch (Exception e) {
                                    scope.error(e /* TODO tracing parameters */);
                                }
                                return true;
                            });
                        } else if (descriptor.mainClassInstance != null) {

                        }

                    }


                    System.out.println("VECTOR");
                }
            }

            if (scalarMapper != null) {
                executors.add(scalarMapper::run);
            }

        }


    }

    private void setExecutionContext(Object returnedValue) {
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
