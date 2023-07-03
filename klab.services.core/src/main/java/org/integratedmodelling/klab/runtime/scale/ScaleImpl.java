package org.integratedmodelling.klab.runtime.scale;

import java.util.Iterator;
import java.util.List;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.Geometry.Dimension.Type;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.geometry.impl.NDCursor;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.TopologicallyComparable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.LogicalConnector;

public class ScaleImpl implements Scale {

	private static final long serialVersionUID = -4518044986262539876L;

	/**
	 * Internal locator class f. Uses the enclosing scale in a lazy fashion for
	 * everything and just maintains the offset[s] and the index[es] of the extend
	 * that is changing.
	 * 
	 * TODO make 2 more that consider masking in extents
	 * 
	 * @author Ferd
	 *
	 */
	abstract class ScaleLocator extends ScaleImpl {
		private static final long serialVersionUID = 797929992176158102L;
		boolean empty;
		long offset;

		abstract Scale advance();
	}

	/**
	 * For >1 extents changing at the same time.
	 * @author Ferd
	 *
	 */
	class ScaleLocatorND extends ScaleLocator {

		private static final long serialVersionUID = 2969366247696737476L;
		NDCursor cursor;

		Scale advance() {
			return this;
		}

	}

	/**
	 * Internal locator class for the situation where a single dimension is
	 * changing and the others are locked at a specified extent.
	 * 
	 * @author Ferd
	 *
	 */
	class ScaleLocator1D extends ScaleLocator {

		private static final long serialVersionUID = -4207775306893203109L;
		long offset;
		int changingIndex;
		long[] extents;
		boolean empty;

		Scale advance() {
			return this;
		}

	}

	/**
	 * The scale iterator uses a threadlocal locator to avoid constant object
	 * instantiation.
	 * 
	 * @author Ferd
	 *
	 */
	class ScaleIterator implements Iterator<Scale> {

		ThreadLocal<ScaleLocator> locator = new ThreadLocal<>();

		ScaleIterator() {
			// TODO initialize the locator to the most appropriate of the above, pointing at
			// the first usable offset
		}

		@Override
		public boolean hasNext() {
			return !locator.get().empty;
		}

		@Override
		public Scale next() {
			return locator.get().advance();
		}

	};

	@Override
	public <T extends Locator> T as(Class<T> cls) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterator<Scale> iterator() {
		return new ScaleIterator();
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
	public Scale merge(Scale other, LogicalConnector how) {
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

	@Override
	public Scale except(Type dimension) {
		// TODO Auto-generated method stub
		return null;
	}

}
