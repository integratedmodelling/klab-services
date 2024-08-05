package org.integratedmodelling.klab.api.services.runtime.kactors.messages;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentMessage;

/**
 * This message is sent to a ContextAgent to create an observation and start its resolution. It should respond
 * with the ID of the observation, which can be used to follow the observation task.
 */
public class Observe extends AgentMessage {

    private Observable observable;
    private Geometry geometry;
    private Geometry observerGeometry;
    private String name;
    private Object defaultValue;
    private String modelUrn;
    private Urn resourceUrn;

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public Observable getObservable() {
        return observable;
    }

    public void setObservable(Observable observable) {
        this.observable = observable;
    }

    public Geometry getObserverGeometry() {
        return observerGeometry;
    }

    public void setObserverGeometry(Geometry observerGeometry) {
        this.observerGeometry = observerGeometry;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getModelUrn() {
        return modelUrn;
    }

    public void setModelUrn(String modelUrn) {
        this.modelUrn = modelUrn;
    }

    public Urn getResourceUrn() {
        return resourceUrn;
    }

    public void setResourceUrn(Urn resourceUrn) {
        this.resourceUrn = resourceUrn;
    }
}