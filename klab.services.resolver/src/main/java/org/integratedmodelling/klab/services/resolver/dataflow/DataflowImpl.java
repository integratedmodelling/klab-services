package org.integratedmodelling.klab.services.resolver.dataflow;

import java.util.List;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowImpl implements Dataflow<Observation> {

    private static final long serialVersionUID = 873406284216826384L;

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
    public boolean isOutput() {
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
    public Geometry getCoverage() {
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
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        return false;
    }

}
