package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.utils.Utils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AbstractExecutor implements CompiledDataflow.ContextualExecutor {

    protected final ContextScope scope;
    protected final CompiledDataflow.CallDescriptors callInfo;
    protected final Observation observation;
    protected Throwable cause;
    protected Storage storage;

    public AbstractExecutor(CompiledDataflow.CallDescriptors callInfo, Observation observation, ContextScope scope) {
        this.callInfo = callInfo;
        this.observation = observation;
        this.scope = scope;
    }

    @Override
    public boolean execute(Scheduler.Event event) {

        if (storage == null) {
            storage = scope.getDigitalTwin().getStorageManager().getStorage(observation);
        }
        if (storage == null) {
            cause = new KlabIllegalStateException("No storage available for " + observation);
            return false;
        }
        var localShardingStrategy = callInfo == null ? storage.getNativeShardingStrategy() : callInfo.shardingStrategy();
        if (localShardingStrategy == null) {
            cause = new KlabIllegalStateException("No sharding strategy available for " + observation);
            return false;
        }

        List<Callable<Object>> tasks = new ArrayList<>();

        try {
            for (var scanner : storage.scan(event, localShardingStrategy, localShardingStrategy.getScannerClass(), false)) {
                tasks.add(() -> run(event, scanner));
            }
        } catch (Throwable t) {
            cause = t;
            return false;
        }

        try (var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = executorService.invokeAll(tasks);
            return results.stream().noneMatch(objectFuture -> objectFuture.state() == Future.State.FAILED);
        } catch (Throwable t) {
            cause = t;
            return false;
        }
    }

    protected abstract boolean run(Scheduler.Event event, Storage.Scanner scanner);

    @Override
    public Throwable getCause() {
        return cause;
    }


    /**
     * Specialized argument matcher for method using or inferring all possible arguments, aware of the input/output
     * structure of the contextualizer and capable of matching observations, scanners and storage to the context by
     * name and type.
     *
     * @param method
     * @param resource
     * @param geometry
     * @param builder
     * @param observation
     * @param observable
     * @param urn
     * @param urnParameters
     * @param serviceCall    //   * @param storage
     * @param expression
     * @param lookupTable
     * @param schedulerEvent
     * @param scope
     * @return
     */
    public List<Object> matchArguments(Method method, Resource resource, Geometry geometry, Data.Builder builder, Observation observation, Observable observable, Urn urn, Parameters<String> urnParameters, ServiceCall serviceCall, Expression expression, LookupTable lookupTable, Data inputData, Scheduler.Event schedulerEvent, Scope scope) {
        List<Object> runArguments = new ArrayList<>();
        DigitalTwin digitalTwin = null;
        if (scope instanceof ContextScope contextScope) {
            digitalTwin = contextScope.getDigitalTwin();
        }
        Scale scale = geometry instanceof Scale scale1 ? scale1 : null;

        // TODO HERE match inputs to scanners through the data builder

        Map<String, Observation> observations = retrieveNamedObservations(method, digitalTwin, observation);

        if (method != null) {
            for (var argument : method.getParameterTypes()) {
                if (ContextScope.class.isAssignableFrom(argument)) {
                    // TODO consider wrapping into read-only delegating wrappers
                    runArguments.add(scope);
                } else if (Scope.class.isAssignableFrom(argument)) {
                    runArguments.add(scope);
                } else if (Observation.class.isAssignableFrom(argument)) {
                    // NO check the name and bind based on the DT and the formal parameters in the
                    runArguments.add(observation);
                } else if (Data.Builder.class.isAssignableFrom(argument)) {
                    runArguments.add(builder);
                } else if (Data.class.isAssignableFrom(argument)) {
                    runArguments.add(inputData);
                } else if (ServiceCall.class.isAssignableFrom(argument)) {
                    runArguments.add(serviceCall);
                } else if (Parameters.class.isAssignableFrom(argument)) {
                    runArguments.add(urnParameters);
                } else if (Storage.Shard.class.isAssignableFrom(argument)) {
                    // Same as the observation, but we need to check the name and bind based on the DT
                } else if (Storage.Scanner.class.isAssignableFrom(argument)) {
                    // Same as the observation, but we need to check the name and bind based on the DT

                } else if (Scale.class.isAssignableFrom(argument)) {
                    if (scale == null && geometry != null) {
                        scale = GeometryRepository.INSTANCE.scale(geometry);
                    }
                    runArguments.add(scale);
                } else if (Geometry.class.isAssignableFrom(argument)) {
                    runArguments.add(geometry);
                } else if (Observable.class.isAssignableFrom(argument)) {
                    runArguments.add(observable);
                } else if (Space.class.isAssignableFrom(argument)) {
                    if (scale == null && geometry != null) {
                        scale = GeometryRepository.INSTANCE.scale(geometry);
                    }
                    runArguments.add(scale == null ? null : scale.getSpace());
                } else if (Time.class.isAssignableFrom(argument)) {
                    if (schedulerEvent != null) {
                        runArguments.add(schedulerEvent.getTime());
                    } else if (scale == null && geometry != null) {
                        scale = GeometryRepository.INSTANCE.scale(geometry);
                    }
                    runArguments.add(scale == null ? null : scale.getTime());
                } else if (Scheduler.Event.class.isAssignableFrom(argument)) {
                    runArguments.add(schedulerEvent);
                } else if (Resource.class.isAssignableFrom(argument) && resource != null) {
                    runArguments.add(resource);
                } else if (Expression.class.isAssignableFrom(argument) && expression != null) {
                    runArguments.add(expression);
                } else if (Urn.class.isAssignableFrom(argument) && urn != null) {
                    runArguments.add(urn);
                } else if (LookupTable.class.isAssignableFrom(argument) && lookupTable != null) {
                    runArguments.add(lookupTable);
                } else {
                    scope.error("Cannot map argument of type " + argument.getCanonicalName() + " to known objects in call to " + method);
                    runArguments.add(null);
                }
            }
            return runArguments;
        }

        return null;
    }

    private Map<String, Observation> retrieveNamedObservations(Method method, DigitalTwin digitalTwin, Observation targetObservation) {
        var ret = new HashMap<String, Observation>();
        for (var parameter : method.getParameters()) {

            if (!(Observation.class.isAssignableFrom(parameter.getType())
                    || Storage.Shard.class.isAssignableFrom(parameter.getType())
                    || Storage.Scanner.class.isAssignableFrom(parameter.getType()))) {
                continue;
            }

            Set<String> observationReferences = getObservationReferences();

            if (Observation.class.isAssignableFrom(parameter.getType())) {

            } else if (Storage.Shard.class.isAssignableFrom(parameter.getType())) {

            } else if (Storage.Scanner.class.isAssignableFrom(parameter.getType())) {
            }
        }
        return ret;
    }

    private Set<String> getObservationReferences() {

        var ret = new HashSet<String>();

        if (callInfo.resource() != null) {

        } else if (callInfo.adapterDescriptor() != null) {

        } else if (callInfo.serviceInfo() != null) {

        }

        return ret;
    }

}
