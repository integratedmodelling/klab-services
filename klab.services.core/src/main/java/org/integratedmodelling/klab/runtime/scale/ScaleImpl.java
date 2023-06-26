package org.integratedmodelling.klab.runtime.scale;

import java.util.Iterator;
import java.util.List;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

public class ScaleImpl implements Scale {

	private static final long serialVersionUID = -4518044986262539876L;

	@Override
	public Geometry geometry() {
		return null;
	}

	@Override
	public double getCoverage() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public <T extends Locator> T as(Class<T> cls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<Locator> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String encode(Encoding... options) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Geometry getChild() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Dimension> getDimensions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Dimension dimension(Type type) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Granularity getGranularity() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isGeneric() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isScalar() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long size() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean infiniteTime() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean is(String dimensionSpecifications) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean contains(Scale o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean overlaps(Scale o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean intersects(Scale o) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Space getSpace() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Time getTime() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isTemporallyDistributed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isSpatiallyDistributed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int getExtentCount() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<Extent> getExtents() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Scale mergeContext(Scale scale, Type... dimensions) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale merge(TopologicallyComparable<?> other, LogicalConnector how, MergingOption... options) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Scale initialization() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale termination() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale without(Type dimension) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Scale at(Object... dimensions) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Scale collapse(Type... dimensions) {
		// TODO Auto-generated method stub
		return null;
	}

}
