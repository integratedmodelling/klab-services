package org.integratedmodelling.klab.runtime.scale;

import org.integratedmodelling.klab.api.geometry.Geometry.Dimension;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl.DimensionImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;

public abstract class ExtentImpl<T extends Extent<T>> extends DimensionImpl implements Extent<T> {

	private static final long serialVersionUID = 2023537570363422007L;
	private ExtentMask mask;
	
	protected ExtentImpl(Dimension.Type type) {
	    setType(type);
	}
	
	public ExtentMask getMask() {
		return this.mask;
	}

}
