package org.integratedmodelling.klab.api.services.runtime.impl;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public class DataflowImpl<T extends Artifact> extends ActuatorImpl implements Dataflow<T> {

	private static final long serialVersionUID = -7658717056169717443L;

	public DataflowImpl() {}
	
	public DataflowImpl(String string, Observable observable, Geometry coverage) {
		setEmpty(true);
		setName(string);
		setCoverage(coverage);
		setObservable(observable);
	}

}
