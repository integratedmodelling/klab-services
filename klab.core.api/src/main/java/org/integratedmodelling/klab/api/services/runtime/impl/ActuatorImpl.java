package org.integratedmodelling.klab.api.services.runtime.impl;

import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

public class ActuatorImpl implements Actuator {

	private static final long serialVersionUID = 2271085975604713999L;
	private String alias;
	private String name;
	private boolean empty;
	private long timestamp;
	private String id;
	private Type type;
	private Observable observable;
	private List<Actuator> children;
	private List<Contextualizable> computation;
	private boolean input;
	private boolean output;
	private boolean computed;
	private boolean reference;
	private Geometry coverage;
	private Parameters<String> data;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public long getTimestamp() {
		return timestamp;
	}

//	@Override
//	public Provenance getProvenance() {
//		// TODO Auto-generated method stub
//		return null;
//	}

	@Override
	public boolean isEmpty() {
		return empty;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getAlias() {
		return alias;
	}

//	@Override
//	public String getAlias(Observable observable) {
//		// TODO Auto-generated method stub
//		return null;
//	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public Observable getObservable() {
		return observable;
	}

	@Override
	public List<Actuator> getChildren() {
		return children;
	}

//	@Override
//	public List<Actuator> getInputs() {
//		// TODO Auto-generated method stub
//		return inputs;
//	}
//
//	@Override
//	public List<Actuator> getActuators() {
//		// TODO Auto-generated method stub
//		return actuators;
//	}
//
//	@Override
//	public List<Dataflow<?>> getDataflows() {
//		// TODO Auto-generated method stub
//		return dataflows;
//	}
//
//	@Override
//	public List<Actuator> getOutputs() {
//		// TODO Auto-generated method stub
//		return null;
//	}

	@Override
	public List<Contextualizable> getComputation() {
		return computation;
	}

	@Override
	public boolean isInput() {
		return input;
	}

	@Override
	public boolean isComputed() {
		return computed;
	}

	@Override
	public boolean isReference() {
		return reference;
	}

	@Override
	public Geometry getCoverage() {
		return coverage;
	}

	@Override
	public Parameters<String> getData() {
		return data;
	}

	@Override
	public boolean isOutput() {
		return output;
	}

	public void setOutput(boolean output) {
		this.output = output;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setEmpty(boolean empty) {
		this.empty = empty;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public void setObservable(Observable observable) {
		this.observable = observable;
	}

	public void setChildren(List<Actuator> children) {
		this.children = children;
	}

	public void setComputation(List<Contextualizable> computation) {
		this.computation = computation;
	}

	public void setInput(boolean input) {
		this.input = input;
	}

	public void setComputed(boolean computed) {
		this.computed = computed;
	}

	public void setReference(boolean reference) {
		this.reference = reference;
	}

	public void setCoverage(Geometry coverage) {
		this.coverage = coverage;
	}

	public void setData(Parameters<String> data) {
		this.data = data;
	}

}
