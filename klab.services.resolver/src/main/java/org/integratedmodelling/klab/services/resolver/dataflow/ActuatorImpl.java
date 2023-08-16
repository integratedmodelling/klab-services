package org.integratedmodelling.klab.services.resolver.dataflow;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

public class ActuatorImpl implements Actuator {

    private static final long serialVersionUID = 2500101522003062757L;
    private String id;
    private long timestamp;
    private boolean empty;
    private String name;
    private String alias;
    private Type type;
    private Observable observable;
    private List<Actuator> children = new ArrayList<>();
    private List<ServiceCall> computation = new ArrayList<>();
    private boolean input;
    private boolean output;
    private boolean reference;
    private boolean deferred;
    private String observer;
    private Geometry coverage = Geometry.EMPTY;
    private Parameters<String> data = Parameters.create();

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return this.id;
    }

    @Override
    public long getTimestamp() {
        // TODO Auto-generated method stub
        return this.timestamp;
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return this.empty;
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return this.name;
    }

    @Override
    public String getAlias() {
        // TODO Auto-generated method stub
        return this.alias;
    }

    @Override
    public Type getType() {
        // TODO Auto-generated method stub
        return this.type;
    }

    @Override
    public Observable getObservable() {
        // TODO Auto-generated method stub
        return this.observable;
    }

    @Override
    public List<Actuator> getChildren() {
        // TODO Auto-generated method stub
        return this.children;
    }

    @Override
    public List<ServiceCall> getComputation() {
        // TODO Auto-generated method stub
        return this.computation;
    }

    @Override
    public boolean isInput() {
        // TODO Auto-generated method stub
        return this.input;
    }

    @Override
    public boolean isOutput() {
        // TODO Auto-generated method stub
        return this.output;
    }

    @Override
    public boolean isReference() {
        // TODO Auto-generated method stub
        return this.reference;
    }

    @Override
    public Geometry getCoverage() {
        // TODO Auto-generated method stub
        return this.coverage;
    }

    @Override
    public Parameters<String> getData() {
        // TODO Auto-generated method stub
        return this.data;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAlias(String alias) {
        this.alias = alias;
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

    public void setComputation(List<ServiceCall> computation) {
        this.computation = computation;
    }

    public void setInput(boolean input) {
        this.input = input;
    }

    public void setOutput(boolean output) {
        this.output = output;
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

    @Override
	public String getObserver() {
		return observer;
	}

	public void setObserver(String observer) {
		this.observer = observer;
	}

	@Override
    public boolean isDeferred() {
        return deferred;
    }

    public void setDeferred(boolean deferred) {
        this.deferred = deferred;
    }

    @Override
    public String toString() {
        return "ActuatorImpl{ " + this.id + "}";
    }
}
