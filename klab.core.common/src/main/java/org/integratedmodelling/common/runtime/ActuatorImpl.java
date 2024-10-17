package org.integratedmodelling.common.runtime;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class ActuatorImpl implements Actuator {

    @Serial
    private static final long serialVersionUID = 2500101522003062757L;
    private long id;
//    private long timestamp;
    private String name;
//    private String alias;
    private Artifact.Type type;
    private Observable observable;
    private String strategyUrn;
    private List<Actuator> children = new ArrayList<>();
    private List<ServiceCall> computation = new ArrayList<>();
//    private boolean input;
//    private boolean output;
    private boolean reference;
//    private boolean deferred;
//    private String observer;
    private Geometry coverage = Geometry.EMPTY;
    private Parameters<String> data = Parameters.create();
//    private Queue<ObservationStrategy> deferrals = new ConcurrentLinkedQueue<>();

    @Override
    public long getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

//    @Override
//    public String getAlias() {
//        return this.alias;
//    }

    @Override
    public Artifact.Type getType() {
        return this.type;
    }

    @Override
    public Observable getObservable() {
        return this.observable;
    }

    @Override
    public List<Actuator> getChildren() {
        return this.children;
    }

    @Override
    public List<ServiceCall> getComputation() {
        return this.computation;
    }

//    @Override
//    public boolean isInput() {
//        return this.input;
//    }
//
//    @Override
//    public boolean isOutput() {
//        return this.output;
//    }


    @Override
    public String getStrategyUrn() {
        return strategyUrn;
    }

    public void setStrategyUrn(String strategyUrn) {
        this.strategyUrn = strategyUrn;
    }

    @Override
    public boolean isReference() {
        return this.reference;
    }

    @Override
    public Geometry getCoverage() {
        return this.coverage;
    }

    @Override
    public Parameters<String> getData() {
        return this.data;
    }

    public void setId(long id) {
        this.id = id;
    }

//    public void setTimestamp(long timestamp) {
//        this.timestamp = timestamp;
//    }

    public void setName(String name) {
        this.name = name;
    }
//
//    public void setAlias(String alias) {
//        this.alias = alias;
//    }

    public void setType(Artifact.Type type) {
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

//    public void setInput(boolean input) {
//        this.input = input;
//    }
//
//    public void setOutput(boolean output) {
//        this.output = output;
//    }

    public void setReference(boolean reference) {
        this.reference = reference;
    }

    public void setCoverage(Geometry coverage) {
        this.coverage = coverage;
    }

    public void setData(Parameters<String> data) {
        this.data = data;
    }

//    @Override
//    public String getObserver() {
//        return observer;
//    }
//
//    @Override
//    public Queue<ObservationStrategy> getDeferrals() {
//        return this.deferrals;
//    }

//    public void setObserver(String observer) {
//        this.observer = observer;
//    }

//    @Override
//    public boolean isDeferred() {
//        return deferred;
//    }
//
//    public void setDeferred(boolean deferred) {
//        this.deferred = deferred;
//    }

    @Override
    public String toString() {
        return "ActuatorImpl{ " + this.id + "}";
    }
}
