package org.integratedmodelling.klab.services.runtime.tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.integratedmodelling.contrib.jgrapht.graph.DefaultEdge;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.runtime.DigitalTwin;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

public class ObservationTask implements Future<Observation> {

	AtomicBoolean running = new AtomicBoolean(false);
	AtomicReference<Observation> result = new AtomicReference<>(null);

	public ObservationTask(Dataflow<Observation> dataflow, ContextScope scope, boolean start) {
		if (start) {
			new Thread() {
				@Override
				public void run() {
					result.set(runDataflow(dataflow, scope));
				}
			}.start();
		}
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCancelled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isDone() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Observation get() throws InterruptedException, ExecutionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Observation get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		// TODO Auto-generated method stub
		return null;
	}

	public Observation runDataflow(Dataflow<Observation> dataflow, ContextScope scope) {

		var dt = getContextData(scope);
		var notifications = new ArrayList<Notification>();
		var executionOrder = sortComputation(dataflow, notifications);
		for (Notification notification : notifications) {
			scope.send(notification);
		}
		if (Utils.Notifications.hasErrors(notifications)) {
			return Observation.empty();
		}

		this.running.set(true);
		for (Actuator actuator : executionOrder) {
			if (!dt.runActuator(actuator, scope)) {
				break;
			}
		}
		this.running.set(false);

		return dt.getObservation(dataflow.getComputation().iterator().next().getId());
	}

	public DigitalTwin getContextData(ContextScope scope) {
		var dt = scope.getData().get(DigitalTwin.KEY, DigitalTwin.class);
		if (dt == null) {
			dt = new DigitalTwin();
			scope.getData().put(DigitalTwin.KEY, dt);
		}
		return dt;
	}

	/**
	 * Establish the order of execution. Each root actuator should be sorted by
	 * dependency and appended in order to the result list. Successive roots can
	 * refer to the previous roots but they must be executed sequentially.
	 * 
	 * TODO while doing this we should ensure we have all we need to run the
	 * contextualizer calls, using the scope to load components as needed.
	 * 
	 * We should also collect all the observables being used, so we have a blueprint
	 * to produce what we need only, and build the influence graph which could
	 * simply use strings given that the actuator and observation IDs are identical.
	 * Actuators and observations should be quickly available through the ID.
	 * 
	 * @param dataflow
	 * @return
	 */
	private List<Actuator> sortComputation(Dataflow<Observation> dataflow, List<Notification> notifications) {
		List<Actuator> ret = new ArrayList<>();
		// keep track of those executed in previous root calls
		Set<String> computed = new HashSet<>();
		for (Actuator root : dataflow.getComputation()) {
			Map<String, Actuator> branch = new HashMap<>();
			collectActuators(Collections.singletonList(root), branch);
			TopologicalOrderIterator<Actuator, DefaultEdge> order = new TopologicalOrderIterator<>(
					createDependencyGraph(branch));
			while (order.hasNext()) {
				Actuator next = order.next();
				if (!computed.contains(next.getId())) {
					ret.add(next);
					computed.add(next.getId());
				}
			}
		}
		return ret;
	}

	private void collectActuators(List<Actuator> actuators, Map<String, Actuator> ret) {
		for (Actuator actuator : actuators) {
			if (!actuator.isReference()) {
				/*
				 * TODO compile a list of all services + versions, validate the actuator, create
				 * any needed notifications
				 */
				ret.put(actuator.getId(), actuator);
			}
			collectActuators(actuator.getChildren(), ret);
		}
	}

	/**
	 * Build and return the dependency graph for the passed actuators. Save
	 * externally if appropriate - caching does create issues in contextualization
	 * and scheduling.
	 * 
	 * @return
	 */
	public Graph<Actuator, DefaultEdge> createDependencyGraph(Map<String, Actuator> actuators) {
		Graph<Actuator, DefaultEdge> ret = new DefaultDirectedGraph<>(DefaultEdge.class);
		for (Actuator actuator : actuators.values()) {
			ret.addVertex(actuator);
			for (Actuator child : actuator.getChildren()) {
				var ref = actuators.get(child.getId());
				if (ref != null) {
					ret.addVertex(ref);
					ret.addEdge(child, actuator);
				}
			}
		}
		return ret;
	}

}
