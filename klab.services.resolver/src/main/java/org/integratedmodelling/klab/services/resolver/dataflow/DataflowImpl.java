package org.integratedmodelling.klab.services.resolver.dataflow;

import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowImpl<T extends Artifact> implements Dataflow<T> {

    private static final long serialVersionUID = -5993388532318351699L;

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getAlias() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getAlias(Observable observable) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Type getType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Observable getObservable() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Actuator> getChildren() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Actuator> getInputs() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Actuator> getActuators() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Dataflow<?>> getDataflows() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Actuator> getOutputs() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Contextualizable> getComputation() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isInput() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isFilter() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isComputed() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isReference() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Coverage getCoverage() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Parameters<String> getData() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long getTimestamp() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Provenance getProvenance() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

}
